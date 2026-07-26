package com.jilali.platform.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Tiny helper for SQLite schema migrations that lack native {@code IF NOT EXISTS} support on
 * certain operations — most notably {@code ALTER TABLE … ADD COLUMN}.
 *
 * <p>SQLite's {@code PRAGMA table_info(table-name)} returns one row per column in the named
 * table. We use it to ask "does this column already exist?" before issuing an
 * {@code ALTER TABLE ADD COLUMN}, which would otherwise throw a duplicate-column-name error
 * on re-runs. The check is single-statement, runs against the same connection as the
 * migration, and is safe against concurrent first-runners because {@code ADD COLUMN} takes
 * an EXCLUSIVE lock on the table.
 */
public final class SchemaGuard {

    private SchemaGuard() {}

    /** Returns true if a column with the given name exists on the given table in the current
     *  schema. Empty-string column names are treated as "always absent" — the caller probably
     *  passed a bad name. */
    public static boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        if (columnName == null || columnName.isEmpty()) return false;
        // PRAGMA table_info is a table-valued function in modern SQLite — SELECT from it
        // rather than running the PRAGMA as a statement, which JDBC drivers don't all
        // surface as a ResultSet.
        String sql = "SELECT COUNT(*) FROM pragma_table_info(?) WHERE name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setString(2, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /** Returns true if a table with the given name exists in the current schema. Uses the
     *  same pragma_table_info introspection as columnExists but checks for the special
     *  "row that names this table" pattern. */
    public static boolean tableExists(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }
}
