package org.sonar.server.db.migrations.v51;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.sonar.core.persistence.Database;
import org.sonar.core.persistence.DbTester;
import org.sonar.server.db.migrations.DatabaseMigration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class AddIssuesColumnsTest {

  @ClassRule
  public static DbTester db = new DbTester().schema(AddIssuesColumnsTest.class, "schema.sql");

  DatabaseMigration migration;

  @Before
  public void setUp() throws Exception {
    migration = new AddIssuesColumns(db.database());
  }

  @Test
  public void update_columns() throws Exception {
    migration.execute();
  }

  @Test
  @Ignore
  public void generate_sql() throws Exception {
    assertThat(new AddIssuesColumns(mock(Database.class)).generateSql())
      .isEqualTo("ALTER TABLE issues ADD (issue_creation_date_ms BIGINT NULL, issue_update_date_ms BIGINT NULL, issue_close_date_ms BIGINT NULL)");
  }

}
