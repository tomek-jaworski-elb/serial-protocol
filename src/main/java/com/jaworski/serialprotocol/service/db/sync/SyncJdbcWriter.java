package com.jaworski.serialprotocol.service.db.sync;

import com.jaworski.serialprotocol.service.db.sync.SyncTables.SyncTable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The JDBC side shared by the import apply and the restore: binds a file row to a statement
 * with the column types the database reports, and streams a photo's bytes from the ZIP into
 * {@code image_blob}. Not a bean: each run makes one over its own transaction's
 * {@link JdbcTemplate} and the column metadata is cached per table for that run.
 */
final class SyncJdbcWriter {

  /** The JDBC type of one column, as {@code select * from t where 1 = 0} reports it. */
  record Column(int type, String typeName) {
  }

  /** One bound value. */
  record Bind(String column, Object value) {
  }

  private final JdbcTemplate jdbc;
  private final Map<String, Map<String, Column>> columns = new HashMap<>();

  SyncJdbcWriter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** The row's values that match a column of the table, in the row's order. */
  List<Bind> binds(SyncTable table, SyncRow row) {
    Map<String, Column> cols = columns(table.name());
    List<Bind> binds = new ArrayList<>();
    for (Map.Entry<String, Object> value : row.values().entrySet()) {
      if (cols.containsKey(value.getKey())) {
        binds.add(new Bind(value.getKey(), value.getValue()));
      }
    }
    return binds;
  }

  void insert(SyncTable table, SyncRow row) {
    insert(table, binds(table, row));
  }

  void insert(SyncTable table, List<Bind> binds) {
    StringBuilder names = new StringBuilder();
    StringBuilder marks = new StringBuilder();
    for (Bind bind : binds) {
      names.append(names.isEmpty() ? "" : ", ").append(bind.column());
      marks.append(marks.isEmpty() ? "" : ", ").append('?');
    }
    execute(table.name(), "insert into " + table.name() + " (" + names + ") values (" + marks + ")", binds);
  }

  /** Streams {@code blobs/<id>.bin} and, when the file has one, {@code thumbs/<id>.bin} into a new blob row. */
  void insertBlob(ZipFile zip, String id, boolean hasThumb) {
    ZipEntry data = zip.getEntry(SyncTables.BLOBS_DIR + id + ".bin");
    ZipEntry thumb = hasThumb ? zip.getEntry(SyncTables.THUMBS_DIR + id + ".bin") : null;
    try (InputStream dataIn = zip.getInputStream(data);
         InputStream thumbIn = thumb == null ? null : zip.getInputStream(thumb)) {
      jdbc.update("insert into " + SyncTables.BLOB_TABLE + " (image_uuid, image_data, image_thumb_data)"
          + " values (?, ?, ?)", ps -> {
        ps.setObject(1, UUID.fromString(id));
        ps.setBinaryStream(2, dataIn, data.getSize());
        if (thumbIn == null) {
          ps.setNull(3, Types.LONGVARBINARY);
        } else {
          ps.setBinaryStream(3, thumbIn, thumb.getSize());
        }
      });
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  int execute(String table, String sql, List<Bind> binds) {
    Map<String, Column> cols = columns(table);
    return jdbc.update(sql, ps -> {
      for (int i = 0; i < binds.size(); i++) {
        Bind bind = binds.get(i);
        Column column = cols.get(bind.column());
        if (column == null) {
          throw new SQLException("Unknown column " + table + "." + bind.column());
        }
        SyncValues.bind(ps, i + 1, bind.value(), column.type(), column.typeName());
      }
    });
  }

  Map<String, Column> columns(String table) {
    return columns.computeIfAbsent(table, t -> jdbc.query("select * from " + t + " where 1 = 0",
        (ResultSetExtractor<Map<String, Column>>) rs -> {
          ResultSetMetaData meta = rs.getMetaData();
          Map<String, Column> result = new LinkedHashMap<>();
          for (int c = 1; c <= meta.getColumnCount(); c++) {
            result.put(SyncValues.columnName(meta, c), new Column(meta.getColumnType(c), meta.getColumnTypeName(c)));
          }
          return result;
        }));
  }
}
