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
package org.sonar.batch.report;

import org.apache.commons.lang.StringUtils;
import org.sonar.api.CoreProperties;
import org.sonar.api.batch.bootstrap.ProjectDefinition;
import org.sonar.api.batch.bootstrap.ProjectReactor;
import org.sonar.api.resources.Language;
import org.sonar.api.resources.Resource;
import org.sonar.api.resources.ResourceUtils;
import org.sonar.batch.index.BatchResource;
import org.sonar.batch.index.ResourceCache;
import org.sonar.batch.protocol.Constants;
import org.sonar.batch.protocol.Constants.ComponentLinkType;
import org.sonar.batch.protocol.output.BatchReport;
import org.sonar.batch.protocol.output.BatchReport.ComponentLink;
import org.sonar.batch.protocol.output.BatchReportWriter;

import javax.annotation.CheckForNull;

/**
 * Adds components and analysis metadata to output report
 */
public class ComponentsPublisher implements ReportPublisher {

  private final ResourceCache resourceCache;
  private final ProjectReactor reactor;

  public ComponentsPublisher(ProjectReactor reactor, ResourceCache resourceCache) {
    this.reactor = reactor;
    this.resourceCache = resourceCache;
  }

  @Override
  public void publish(BatchReportWriter writer) {
    BatchResource rootProject = resourceCache.get(reactor.getRoot().getKeyWithBranch());
    recursiveWriteComponent(rootProject, writer);
  }

  private void recursiveWriteComponent(BatchResource batchResource, BatchReportWriter writer) {
    Resource r = batchResource.resource();
    BatchReport.Component.Builder builder = BatchReport.Component.newBuilder();

    // non-null fields
    builder.setRef(batchResource.batchId());
    builder.setType(getType(r));

    // protocol buffers does not accept null values

    String uuid = r.getUuid();
    if (uuid != null) {
      builder.setUuid(uuid);
    }
    Integer sid = batchResource.snapshotId();
    if (sid != null) {
      builder.setSnapshotId(sid);
    }
    if (ResourceUtils.isFile(r)) {
      builder.setIsTest(ResourceUtils.isUnitTestClass(r));
    }
    String name = getName(r);
    if (name != null) {
      builder.setName(name);
    }
    String path = r.getPath();
    if (path != null) {
      builder.setPath(path);
    }
    String lang = getLanguageKey(r);
    if (lang != null) {
      builder.setLanguage(lang);
    }
    for (BatchResource child : batchResource.children()) {
      builder.addChildRefs(child.batchId());
    }
    if (ResourceUtils.isProject(r)) {
      ProjectDefinition def = getProjectDefinition(reactor, r.getKey());
      ComponentLink.Builder linkBuilder = ComponentLink.newBuilder();

      writeProjectLink(builder, def, linkBuilder, CoreProperties.LINKS_HOME_PAGE, ComponentLinkType.HOME);
      writeProjectLink(builder, def, linkBuilder, CoreProperties.LINKS_CI, ComponentLinkType.CI);
      writeProjectLink(builder, def, linkBuilder, CoreProperties.LINKS_ISSUE_TRACKER, ComponentLinkType.ISSUE);
      writeProjectLink(builder, def, linkBuilder, CoreProperties.LINKS_SOURCES, ComponentLinkType.SCM);
      writeProjectLink(builder, def, linkBuilder, CoreProperties.LINKS_SOURCES_DEV, ComponentLinkType.SCM_DEV);
    }
    writer.writeComponent(builder.build());

    for (BatchResource child : batchResource.children()) {
      recursiveWriteComponent(child, writer);
    }
  }

  private ProjectDefinition getProjectDefinition(ProjectReactor reactor, String keyWithBranch) {
    for (ProjectDefinition p : reactor.getProjects()) {
      if (keyWithBranch.equals(p.getKeyWithBranch())) {
        return p;
      }
    }
    return null;
  }

  private void writeProjectLink(BatchReport.Component.Builder componentBuilder, ProjectDefinition def, ComponentLink.Builder linkBuilder, String linkProp,
    ComponentLinkType linkType) {
    String link = def.properties().get(linkProp);
    if (StringUtils.isNotBlank(link)) {
      linkBuilder.setType(linkType);
      linkBuilder.setHref(link);
      componentBuilder.addLinks(linkBuilder.build());
      linkBuilder.clear();
    }
  }

  @CheckForNull
  private String getLanguageKey(Resource r) {
    Language language = r.getLanguage();
    return ResourceUtils.isFile(r) && language != null ? language.getKey() : null;
  }

  @CheckForNull
  private String getName(Resource r) {
    // Don't return name for directories and files since it can be guessed from the path
    return (ResourceUtils.isFile(r) || ResourceUtils.isDirectory(r)) ? null : r.getName();
  }

  private Constants.ComponentType getType(Resource r) {
    if (ResourceUtils.isFile(r)) {
      return Constants.ComponentType.FILE;
    } else if (ResourceUtils.isDirectory(r)) {
      return Constants.ComponentType.DIRECTORY;
    } else if (ResourceUtils.isModuleProject(r)) {
      return Constants.ComponentType.MODULE;
    } else if (ResourceUtils.isRootProject(r)) {
      return Constants.ComponentType.PROJECT;
    } else if (ResourceUtils.isView(r)) {
      return Constants.ComponentType.VIEW;
    } else if (ResourceUtils.isSubview(r)) {
      return Constants.ComponentType.SUBVIEW;
    }
    throw new IllegalArgumentException("Unknown resource type: " + r);
  }

}
