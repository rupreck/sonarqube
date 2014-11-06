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
package org.sonar.data.issues;

import com.google.common.collect.Iterables;
import org.apache.ibatis.session.ResultContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.issue.Issue;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.rule.Severity;
import org.sonar.api.utils.System2;
import org.sonar.core.component.ComponentDto;
import org.sonar.core.issue.db.IssueDto;
import org.sonar.core.persistence.DbSession;
import org.sonar.core.persistence.TestDatabase;
import org.sonar.core.rule.RuleDto;
import org.sonar.server.component.ComponentTesting;
import org.sonar.server.component.db.ComponentDao;
import org.sonar.server.issue.IssueTesting;
import org.sonar.server.issue.db.IssueDao;
import org.sonar.server.rule.RuleTesting;
import org.sonar.server.rule.db.RuleDao;
import org.sonar.server.search.DbSynchronizationHandler;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Lists.newArrayList;
import static org.fest.assertions.Assertions.assertThat;

public class IssuesDbExtractionTest extends AbstractTest {

  static final Logger LOGGER = LoggerFactory.getLogger(IssuesDbExtractionTest.class);

  final static int RULES_NUMBER = 25;
  final static int USERS_NUMBER = 100;

  final static int PROJECTS_NUMBER = 100;
  final static int NUMBER_FILES_PER_PROJECT = 100;
  final static int NUMBER_ISSUES_PER_FILE = 100;

  final static int ISSUE_COUNT = PROJECTS_NUMBER * NUMBER_FILES_PER_PROJECT * NUMBER_ISSUES_PER_FILE;

  @Rule
  public TestDatabase db = new TestDatabase();

  DbSession session;

  Iterator<RuleDto> rules;
  Iterator<String> users;
  Iterator<String> severities;
  Iterator<String> statuses;
  Iterator<String> closedResolutions;
  Iterator<String> resolvedResolutions;

  ProxyIssueDao issueDao;
  RuleDao ruleDao;
  ComponentDao componentDao;

  @Before
  public void setUp() throws Exception {
    issueDao = new ProxyIssueDao();
    ruleDao = new RuleDao();
    componentDao = new ComponentDao(System2.INSTANCE);

    session = db.myBatis().openSession(false);

    rules = Iterables.cycle(generateRules(session)).iterator();
    users = Iterables.cycle(generateUsers()).iterator();
    severities = Iterables.cycle(Severity.ALL).iterator();
    statuses = Iterables.cycle(Issue.STATUS_OPEN, Issue.STATUS_CONFIRMED, Issue.STATUS_REOPENED, Issue.STATUS_RESOLVED, Issue.STATUS_CLOSED).iterator();
    closedResolutions = Iterables.cycle(Issue.RESOLUTION_FALSE_POSITIVE, Issue.RESOLUTION_FIXED, Issue.RESOLUTION_REMOVED).iterator();
    resolvedResolutions = Iterables.cycle(Issue.RESOLUTION_FALSE_POSITIVE, Issue.RESOLUTION_FIXED).iterator();
  }

  @After
  public void closeSession() throws Exception {
    session.close();
  }

  @Test
  public void extract_issues() throws Exception {
    int issueInsertCount = ISSUE_COUNT;

    long start = System.currentTimeMillis();
    for (long projectIndex = 1; projectIndex <= PROJECTS_NUMBER; projectIndex++) {
      ComponentDto project = ComponentTesting.newProjectDto()
        .setKey("project-" + projectIndex)
        .setName("Project " + projectIndex)
        .setLongName("Project " + projectIndex);
      componentDao.insert(session, project);

      for (int fileIndex = 0; fileIndex < NUMBER_FILES_PER_PROJECT; fileIndex++) {
        String index = projectIndex * PROJECTS_NUMBER + fileIndex + "";
        ComponentDto file = ComponentTesting.newFileDto(project)
          .setKey("file-" + index)
          .setName("File " + index)
          .setLongName("File " + index);
        componentDao.insert(session, file);

        for (int issueIndex = 1; issueIndex < NUMBER_ISSUES_PER_FILE + 1; issueIndex++) {
          String status = statuses.next();
          String resolution = null;
          if (status.equals(Issue.STATUS_CLOSED)) {
            resolution = closedResolutions.next();
          } else if (status.equals(Issue.STATUS_RESOLVED)) {
            resolution = resolvedResolutions.next();
          }
          RuleDto rule = rules.next();
          IssueDto issue = IssueTesting.newDto(rule, file, project)
            .setMessage("Message from rule " + rule.getKey().toString() + " on line " + issueIndex)
            .setLine(issueIndex)
            .setAssignee(users.next())
            .setReporter(users.next())
            .setAuthorLogin(users.next())
            .setSeverity(severities.next())
            .setStatus(status)
            .setResolution(resolution);
          issueDao.insert(session, issue);
        }
        session.commit();
      }
    }
    LOGGER.info("Inserted {} Issues in {} ms", ISSUE_COUNT, System.currentTimeMillis() - start);

    start = System.currentTimeMillis();
    issueDao.synchronizeAfter(session);
    long stop = System.currentTimeMillis();

    assertThat(issueDao.synchronizedIssues).isEqualTo(issueInsertCount);

    long time = stop - start;
    LOGGER.info("Extracted {} Issues in {} ms with avg {} Issue/second", ISSUE_COUNT, time, documentPerSecond(time));
    assertDurationAround(time, Long.parseLong(getProperty("IssuesDbExtractionTest.extract_issues")));
  }

  protected List<RuleDto> generateRules(DbSession session) {
    List<RuleDto> rules = newArrayList();
    for (int i = 0; i < RULES_NUMBER; i++) {
      rules.add(RuleTesting.newDto(RuleKey.of("rule-repo", "rule-key-" + i)));
    }
    ruleDao.insert(this.session, rules);
    session.commit();
    return rules;
  }

  protected List<String> generateUsers() {
    List<String> users = newArrayList();
    for (int i = 0; i < USERS_NUMBER; i++) {
      users.add("user-" + i);
    }
    return users;
  }

  private int documentPerSecond(long time) {
    return (int) Math.round(ISSUE_COUNT / (time / 1000.0));
  }

  class ProxyIssueDao extends IssueDao {
    public Integer synchronizedIssues = 0;

    @Override
    protected boolean hasIndex() {
      return false;
    }

    @Override
    protected DbSynchronizationHandler getSynchronizationResultHandler(DbSession session, Map<String, String> params) {
      return new DbSynchronizationHandler(session, params) {

        @Override
        public void handleResult(ResultContext context) {
          synchronizedIssues++;
        }

        @Override
        public void enqueueCollected() {
        }
      };
    }
  }

}