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

package org.sonar.server.measure.persistence;

import com.google.common.collect.Lists;
import org.sonar.api.ServerComponent;
import org.sonar.core.measure.db.MeasureDto;
import org.sonar.core.measure.db.MeasureMapper;
import org.sonar.core.persistence.DaoComponent;
import org.sonar.core.persistence.DbSession;

import javax.annotation.CheckForNull;

import java.util.Collections;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

public class MeasureDao implements ServerComponent, DaoComponent {

  public boolean existsByKey(DbSession session, String componentKey, String metricKey) {
    return mapper(session).countByComponentAndMetric(componentKey, metricKey) > 0;
  }

  @CheckForNull
  public MeasureDto findByComponentKeyAndMetricKey(DbSession session, String componentKey, String metricKey) {
    return mapper(session).selectByComponentAndMetric(componentKey, metricKey);
  }

  public List<MeasureDto> findByComponentKeyAndMetricKeys(DbSession session, String componentKey, List<String> metricKeys) {
    if (metricKeys.isEmpty()) {
      return Collections.emptyList();
    }
    List<MeasureDto> measures = newArrayList();
    List<List<String>> partitions = Lists.partition(newArrayList(metricKeys), 1000);
    for (List<String> partition : partitions) {
      measures.addAll(mapper(session).selectByComponentAndMetrics(componentKey, partition));
    }
    return measures;
  }

  public void insert(DbSession session, MeasureDto measureDto) {
    mapper(session).insert(measureDto);
  }

  private MeasureMapper mapper(DbSession session) {
    return session.getMapper(MeasureMapper.class);
  }
}
