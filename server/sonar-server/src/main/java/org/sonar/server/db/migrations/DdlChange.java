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
package org.sonar.server.db.migrations;

import org.apache.commons.dbutils.DbUtils;
import org.sonar.core.persistence.Database;
import org.sonar.core.persistence.dialect.Dialect;
import org.sonar.core.persistence.dialect.PostgreSql;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

public abstract class DdlChange implements DatabaseMigration {

  private final Database db;

  public DdlChange(Database db) {
    this.db = db;
  }

  @Override
  public final void execute() throws SQLException {
    Connection writeConnection = null;
    try {
      writeConnection = db.getDataSource().getConnection();
      writeConnection.setAutoCommit(false);
      Context context = new Context(writeConnection);
      execute(context);

    } finally {
      DbUtils.closeQuietly(writeConnection);
    }
  }

  public class Context {
    private final Connection writeConnection;

    public Context(Connection writeConnection) {
      this.writeConnection = writeConnection;
    }

    public void execute(String sql) throws SQLException {
      try {
        UpsertImpl.create(writeConnection, sql).execute().commit();
      } catch (Exception e) {
        throw new IllegalStateException(String.format("Fail to execute %s", sql), e);
      }
    }
  }

  public abstract void execute(Context context) throws SQLException;

  public static class AddAlterTableBuilder {

    private final Dialect dialect;
    private final String tableName;
    private List<ColumnDef> columnDefs = newArrayList();

    public AddAlterTableBuilder(String tableName, Dialect dialect) {
      this.tableName = tableName;
      this.dialect = dialect;
    }

    public AddAlterTableBuilder addColumn(ColumnDef columnDef) {
      columnDefs.add(columnDef);
      return this;
    }

    public String build() {
      switch (dialect.getId()) {
        case PostgreSql.ID :
          StringBuilder sql = new StringBuilder();
          sql.append("ALTER TABLE ").append(tableName).append(" ");
          for (int i = 0; i < columnDefs.size(); i++) {
            sql.append("ADD COLUMN ");
            addColumn(sql, columnDefs.get(i));
            if (i < columnDefs.size() - 1) {
              sql.append(", ");
            }
          }
          return sql.toString();
        default:
          return defaultSql();
      }
    }

    public String defaultSql(){
      StringBuilder sql = new StringBuilder();
      sql.append("ALTER TABLE ").append(tableName).append(" ADD (");
      for (int i = 0; i < columnDefs.size(); i++) {
        addColumn(sql, columnDefs.get(i));
        if (i < columnDefs.size() - 1) {
          sql.append(", ");
        }
      }
      sql.append(")");
      return sql.toString();
    }

    private void addColumn(StringBuilder sql, ColumnDef columnDef){
      sql.append(columnDef.getName()).append(" ").append(typeToSql(columnDef));
      Integer limit = columnDef.getLimit();
      if (limit != null) {
        sql.append(" (").append(Integer.toString(limit)).append(")");
      }
      sql.append(columnDef.isNullable() ? " NULL" : " NOT NULL");
    }

    private static String typeToSql(ColumnDef columnDef) {
      switch (columnDef.getType()) {
        case STRING:
          return "VARCHAR";
        case BIG_INTEGER:
          return "BIGINT";
        default:
          throw new IllegalArgumentException("Unsupported type : " + columnDef.getType());
      }
    }

  }

  public static class ColumnDef {
    private String name;
    private Type type;
    private boolean isNullable;
    private Integer limit;

    public enum Type {
      STRING, BIG_INTEGER
    }

    public ColumnDef setNullable(boolean isNullable) {
      this.isNullable = isNullable;
      return this;
    }

    public ColumnDef setLimit(@Nullable Integer limit) {
      this.limit = limit;
      return this;
    }

    public ColumnDef setName(String name) {
      this.name = name;
      return this;
    }

    public ColumnDef setType(Type type) {
      this.type = type;
      return this;
    }

    public boolean isNullable() {
      return isNullable;
    }

    @CheckForNull
    public Integer getLimit() {
      return limit;
    }

    public String getName() {
      return name;
    }

    public Type getType() {
      return type;
    }

  }

}
