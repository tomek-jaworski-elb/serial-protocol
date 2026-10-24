package com.jaworski.serialprotocol.service.db.sync;

import com.jaworski.serialprotocol.service.db.sync.SyncTables.SyncTable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.ResultSetMetaData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the local tables in the shape the file has, so {@link SyncAnalyzer} compares like with
 * like: the same columns, the same {@link SyncValues} encoding, tombstones included.
 */
@Service
public class SyncLocalReader {

  private final JdbcTemplate jdbc;

  public SyncLocalReader(DataSource dataSource) {
    this.jdbc = new JdbcTemplate(dataSource);
  }

  @Transactional(readOnly = true)
  public Map<String, List<SyncRow>> read() {
    return readInTransaction();
  }

  /** The same read inside a transaction the caller owns ({@code SyncApplyService} checks nothing moved). */
  Map<String, List<SyncRow>> readInTransaction() {
    Map<String, List<SyncRow>> tables = new LinkedHashMap<>();
    for (SyncTable table : SyncTables.TABLES) {
      tables.put(table.name(), jdbc.query("SELECT * FROM " + table.name() + " ORDER BY " + table.orderBy(),
          (rs, i) -> {
            ResultSetMetaData meta = rs.getMetaData();
            Map<String, Object> values = new LinkedHashMap<>();
            for (int c = 1; c <= meta.getColumnCount(); c++) {
              values.put(SyncValues.columnName(meta, c), SyncValues.read(rs, meta, c));
            }
            return new SyncRow(values);
          }));
    }
    return tables;
  }
}
