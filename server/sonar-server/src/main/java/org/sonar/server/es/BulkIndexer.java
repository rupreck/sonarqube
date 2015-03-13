/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.es;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsResponse;
import org.elasticsearch.action.admin.indices.settings.put.UpdateSettingsRequestBuilder;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.unit.ByteSizeUnit;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.SearchHit;
import org.picocontainer.Startable;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.server.util.ProgressLogger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Helper to bulk requests in an efficient way :
 * <ul>
 *   <li>bulk request is sent on the wire when its size is higher than 5Mb</li>
 *   <li>on large table indexing, replicas and automatic refresh can be temporarily disabled</li>
 *   <li>index refresh is optional (enabled by default)</li>
 * </ul>
 */
public class BulkIndexer implements Startable {

  private static final Logger LOGGER = Loggers.get(BulkIndexer.class);
  private static final long FLUSH_BYTE_SIZE = new ByteSizeValue(1, ByteSizeUnit.MB).bytes();
  private static final String REFRESH_INTERVAL_SETTING = "index.refresh_interval";
  private static final String ALREADY_STARTED_MESSAGE = "Bulk indexing is already started";

  private final EsClient client;
  private final String indexName;
  private boolean large = false;
  private BulkRequestBuilder bulkRequest = null;
  private Map<String, Object> largeInitialSettings = null;
  private final AtomicLong counter = new AtomicLong(0L);
  private final int concurrentRequests;
  private final Semaphore semaphore;
  private final ProgressLogger progress;

  public BulkIndexer(EsClient client, String indexName) {
    this.client = client;
    this.indexName = indexName;
    this.progress = new ProgressLogger(String.format("Progress[BulkIndexer[%s]]", indexName), counter, LOGGER)
      .setPluralLabel("requests");

    this.concurrentRequests = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    this.semaphore = new Semaphore(concurrentRequests);
  }

  /**
   * Large indexing is an heavy operation that populates an index generally from scratch. Replicas and
   * automatic refresh are disabled during bulk indexing and lucene segments are optimized at the end.
   */

  public BulkIndexer setLarge(boolean b) {
    Preconditions.checkState(bulkRequest == null, ALREADY_STARTED_MESSAGE);
    this.large = b;
    return this;
  }

  @Override
  public void start() {
    Preconditions.checkState(bulkRequest == null, ALREADY_STARTED_MESSAGE);
    if (large) {
      largeInitialSettings = Maps.newHashMap();
      Map<String, Object> bulkSettings = Maps.newHashMap();
      GetSettingsResponse settingsResp = client.nativeClient().admin().indices().prepareGetSettings(indexName).get();

      // deactivate replicas
      int initialReplicas = Integer.parseInt(settingsResp.getSetting(indexName, IndexMetaData.SETTING_NUMBER_OF_REPLICAS));
      if (initialReplicas > 0) {
        largeInitialSettings.put(IndexMetaData.SETTING_NUMBER_OF_REPLICAS, initialReplicas);
        bulkSettings.put(IndexMetaData.SETTING_NUMBER_OF_REPLICAS, 0);
      }

      // deactivate periodical refresh
      String refreshInterval = settingsResp.getSetting(indexName, REFRESH_INTERVAL_SETTING);
      largeInitialSettings.put(REFRESH_INTERVAL_SETTING, refreshInterval);
      bulkSettings.put(REFRESH_INTERVAL_SETTING, "-1");

      updateSettings(bulkSettings);
    }
    bulkRequest = client.prepareBulk().setRefresh(false);
    counter.set(0L);
    progress.start();
  }

  public void add(ActionRequest request) {
    bulkRequest.request().add(request);
    if (bulkRequest.request().estimatedSizeInBytes() >= FLUSH_BYTE_SIZE) {
      executeBulk();
    }
  }

  public void addDeletion(SearchRequestBuilder searchRequest) {
    searchRequest
      .setScroll(TimeValue.timeValueMinutes(5))
      .setSearchType(SearchType.SCAN)
      // load only doc ids, not _source fields
      .setFetchSource(false);

    // this search is synchronous. An optimization would be to be non-blocking,
    // but it requires to tracking pending requests in close().
    // Same semaphore can't be reused because of potential deadlock (requires to acquire
    // two locks)
    SearchResponse searchResponse = searchRequest.get();
    searchResponse = client.prepareSearchScroll(searchResponse.getScrollId()).get();
    for (SearchHit hit : searchResponse.getHits()) {
      add(client.prepareDelete(hit.index(), hit.type(), hit.getId()).request());
    }
  }

  /**
   * Delete all the documents matching the given search request. This method is blocking.
   * Index is refreshed, so docs are not searchable as soon as method is executed.
   */
  public static void delete(EsClient client, String indexName, SearchRequestBuilder searchRequest) {
    BulkIndexer bulk = new BulkIndexer(client, indexName);
    bulk.start();
    bulk.addDeletion(searchRequest);
    bulk.stop();
  }

  @Override
  public void stop() {
    if (bulkRequest.numberOfActions() > 0) {
      executeBulk();
    }
    try {
      if (semaphore.tryAcquire(concurrentRequests, 10, TimeUnit.MINUTES)) {
        semaphore.release(concurrentRequests);
      }
    } catch (InterruptedException e) {
      throw new IllegalStateException("Elasticsearch bulk requests still being executed after 10 minutes", e);
    }
    progress.stop();

    client.prepareRefresh(indexName).get();
    if (large) {
      // optimize lucene segments and revert index settings
      // Optimization must be done before re-applying replicas:
      // http://www.elasticsearch.org/blog/performance-considerations-elasticsearch-indexing/
      client.prepareOptimize(indexName).get();

      updateSettings(largeInitialSettings);
    }
    bulkRequest = null;
  }

  private void updateSettings(Map<String, Object> settings) {
    UpdateSettingsRequestBuilder req = client.nativeClient().admin().indices().prepareUpdateSettings(indexName);
    req.setSettings(settings);
    req.get();
  }

  private void executeBulk() {
    final BulkRequestBuilder req = this.bulkRequest;
    this.bulkRequest = client.prepareBulk().setRefresh(false);
    semaphore.acquireUninterruptibly();
    req.execute(new ActionListener<BulkResponse>() {
      @Override
      public void onResponse(BulkResponse response) {
        counter.addAndGet(response.getItems().length);

        List<ActionRequest> retries = Lists.newArrayList();
        for (BulkItemResponse item : response.getItems()) {
          if (item.isFailed()) {
            ActionRequest retry = req.request().requests().get(item.getItemId());
            retries.add(retry);
          }
        }

        if (!retries.isEmpty()) {
          LOGGER.warn(String.format("%d index requests failed. Trying again.", retries.size()));
          BulkRequestBuilder retryBulk = client.prepareBulk();
          for (ActionRequest retry : retries) {
            retryBulk.request().add(retry);
          }
          BulkResponse retryBulkResponse = retryBulk.get();
          if (retryBulkResponse.hasFailures()) {
            LOGGER.error("New attempt to index documents failed");
            for (int index = 0; index < retryBulkResponse.getItems().length; index++) {
              BulkItemResponse item = retryBulkResponse.getItems()[index];
              if (item.isFailed()) {
                StringBuilder sb = new StringBuilder();
                String msg = sb.append("\n[").append(index)
                  .append("]: index [").append(item.getIndex()).append("], type [").append(item.getType()).append("], id [").append(item.getId())
                  .append("], message [").append(item.getFailureMessage()).append("]").toString();
                LOGGER.error(msg);
              }
            }
          } else {
            LOGGER.info("New index attempt succeeded");
          }
        }
        semaphore.release();
      }

      @Override
      public void onFailure(Throwable e) {
        LOGGER.error("Fail to execute bulk index request: " + req, e);
        semaphore.release();
      }
    });
  }
}
