package org.sonar.server.db.migrations;

import org.junit.Test;
import org.sonar.core.persistence.dialect.MySql;
import org.sonar.core.persistence.dialect.PostgreSql;

import static org.assertj.core.api.Assertions.assertThat;

public class AddAlterTableBuilderTest {

  @Test
  public void generate_alter_add_on_default_db() throws Exception {
    assertThat(new DdlChange.AddAlterTableBuilder("issues", new MySql())
      .addColumn(new DdlChange.ColumnDef()
        .setName("name")
        .setType(DdlChange.ColumnDef.Type.BIG_INTEGER)
        .setNullable(true))
      .addColumn(new DdlChange.ColumnDef()
        .setName("name")
        .setType(DdlChange.ColumnDef.Type.STRING)
        .setNullable(false)
        .setLimit(10))
      .build()).isEqualTo("ALTER TABLE issues ADD (name BIGINT NULL, name VARCHAR (10) NOT NULL)");
  }

  @Test
  public void generate_alter_add_on_postgresql() throws Exception {
    assertThat(new DdlChange.AddAlterTableBuilder("issues", new PostgreSql())
      .addColumn(new DdlChange.ColumnDef()
        .setName("name")
        .setType(DdlChange.ColumnDef.Type.BIG_INTEGER)
        .setNullable(true))
      .addColumn(new DdlChange.ColumnDef()
        .setName("name")
        .setType(DdlChange.ColumnDef.Type.STRING)
        .setNullable(false)
        .setLimit(10))
      .build()).isEqualTo("ALTER TABLE issues ADD COLUMN name BIGINT NULL, ADD COLUMN name VARCHAR (10) NOT NULL");
  }
}
