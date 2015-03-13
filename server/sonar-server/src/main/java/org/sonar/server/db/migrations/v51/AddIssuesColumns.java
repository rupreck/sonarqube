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

package org.sonar.server.db.migrations.v51;

import com.google.common.annotations.VisibleForTesting;
import org.sonar.core.persistence.Database;
import org.sonar.server.db.migrations.DdlChange;

import java.sql.SQLException;

public class AddIssuesColumns extends DdlChange {

  private final Database db;

  public AddIssuesColumns(Database db) {
    super(db);
    this.db = db;
  }

  @Override
  public void execute(DdlChange.Context context) throws SQLException {
    context.execute(generateSql());
  }

  @VisibleForTesting
  String generateSql(){
    return new DdlChange.AddAlterTableBuilder("issues", db.getDialect())
      .addColumn(
        new ColumnDef()
          .setName("issue_creation_date_ms")
          .setType(ColumnDef.Type.BIG_INTEGER)
          .setNullable(true)
      )
      .addColumn(
        new ColumnDef()
          .setName("issue_update_date_ms")
          .setType(ColumnDef.Type.BIG_INTEGER)
          .setNullable(true)
      )
      .addColumn(
        new ColumnDef()
          .setName("issue_close_date_ms")
          .setType(ColumnDef.Type.BIG_INTEGER)
          .setNullable(true)
      ).build();
  }

}
