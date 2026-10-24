package com.jaworski.serialprotocol.service.db.sync;

import com.jaworski.serialprotocol.configuration.SyncProperties;
import com.jaworski.serialprotocol.entity.custom.SyncNode;
import com.jaworski.serialprotocol.service.db.sync.SyncTables.SyncTable;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes the whole synchronised database as the ZIP described in docs/sync-spec.md.
 *
 * <p>Through JDBC, never through entities: the file carries {@code version}, the audit
 * timestamps and {@code origin_node} verbatim, and an entity save would rewrite them all.
 * The rows are streamed entry by entry into whatever stream the caller hands over (the HTTP
 * response or a backup file); a blob goes from the result set straight into the ZIP, so a
 * database with hundreds of photos never has them all in memory. Everything is read inside
 * one REPEATABLE READ transaction, so a save during the download cannot leave a course in the
 * file without its participant.</p>
 */
@Service
public class SyncExporter {

  private static final int FETCH_SIZE = 200;
  /** Every fetched row of the blob query may hold two LONGBLOBs, so the driver buffers very few. */
  private static final int BLOB_FETCH_SIZE = 2;

  private final JdbcTemplate jdbc;
  private final JdbcTemplate blobJdbc;
  private final TransactionTemplate snapshot;
  private final SyncNodeService nodeService;
  private final SyncProperties properties;
  private final ObjectProvider<Flyway> flyway;
  private final String appVersion;
  private final Clock clock;
  private final ObjectWriter rowWriter;
  private final ObjectWriter manifestWriter;

  public SyncExporter(DataSource dataSource, PlatformTransactionManager transactionManager,
      SyncNodeService nodeService, SyncProperties properties, ObjectProvider<Flyway> flyway,
      @Value("${application.version:unknown}") String appVersion, Clock clock) {
    this.jdbc = new JdbcTemplate(dataSource);
    this.jdbc.setFetchSize(FETCH_SIZE);
    this.blobJdbc = new JdbcTemplate(dataSource);
    this.blobJdbc.setFetchSize(BLOB_FETCH_SIZE);
    this.snapshot = new TransactionTemplate(transactionManager);
    this.snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    this.snapshot.setReadOnly(true);
    this.nodeService = nodeService;
    this.properties = properties;
    this.flyway = flyway;
    this.appVersion = appVersion;
    this.clock = clock;
    JsonMapper mapper = JsonMapper.builder().build();
    this.rowWriter = mapper.writer();
    this.manifestWriter = mapper.writerWithDefaultPrettyPrinter();
  }

  /**
   * Streams one sync file into {@code out}, which stays open for the caller to close.
   *
   * @param backup {@code true} when the file is a {@code .bak} the application writes for itself
   * @return the manifest that went into the file
   */
  public SyncManifest export(OutputStream out, boolean backup) throws IOException {
    try {
      return snapshot.execute(status -> {
        try {
          return write(out, backup);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      });
    } catch (UncheckedIOException e) {
      throw e.getCause();
    }
  }

  private SyncManifest write(OutputStream out, boolean backup) throws IOException {
    SyncNode node = nodeService.current();
    Map<String, Long> rows = new LinkedHashMap<>();
    for (SyncTable table : SyncTables.TABLES) {
      rows.put(table.name(), count(table.name()));
    }
    long blobs = count(SyncTables.BLOB_TABLE);
    SyncManifest manifest = new SyncManifest(SyncManifest.CURRENT_FORMAT, schemaVersion(), appVersion,
        new SyncManifest.Node(node.getId(), node.getName()), SyncValues.TIMESTAMP.format(Instant.now(clock)),
        properties.getMode().name(), backup, rows, blobs);

    ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
    zip.putNextEntry(new ZipEntry(SyncTables.MANIFEST_ENTRY));
    zip.write(manifestWriter.writeValueAsBytes(manifest));
    zip.closeEntry();
    for (SyncTable table : SyncTables.TABLES) {
      zip.putNextEntry(new ZipEntry(SyncTables.tableEntry(table.name())));
      writeRows(table, zip);
      zip.closeEntry();
    }
    writeBlobs(zip);
    zip.finish();
    zip.flush();
    return manifest;
  }

  private long count(String table) {
    Long count = jdbc.queryForObject("select count(*) from " + table, Long.class);
    return count == null ? 0 : count;
  }

  private void writeRows(SyncTable table, ZipOutputStream zip) {
    String sql = "select * from " + table.name() + " order by " + table.orderBy();
    jdbc.query(sql, (RowCallbackHandler) rs -> {
      try {
        ResultSetMetaData meta = rs.getMetaData();
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
          row.put(SyncValues.columnName(meta, i), SyncValues.read(rs, meta, i));
        }
        zip.write(rowWriter.writeValueAsBytes(row));
        zip.write('\n');
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    });
  }

  private void writeBlobs(ZipOutputStream zip) {
    String sql = "select image_uuid, image_data, image_thumb_data from " + SyncTables.BLOB_TABLE
        + " order by image_uuid";
    blobJdbc.query(sql, (RowCallbackHandler) rs -> {
      try {
        String id = String.valueOf(rs.getObject(1));
        copy(rs.getBinaryStream(2), SyncTables.BLOBS_DIR + id + ".bin", zip);
        InputStream thumb = rs.getBinaryStream(3);
        if (thumb != null && !rs.wasNull()) {
          copy(thumb, SyncTables.THUMBS_DIR + id + ".bin", zip);
        }
      } catch (IOException | SQLException e) {
        throw e instanceof IOException io ? new UncheckedIOException(io) : new IllegalStateException(e);
      }
    });
  }

  private static void copy(InputStream in, String entry, ZipOutputStream zip) throws IOException {
    zip.putNextEntry(new ZipEntry(entry));
    try (in) {
      in.transferTo(zip);
    }
    zip.closeEntry();
  }

  /** The Flyway version this database is at, or {@code null} without Flyway (the H2 profile). */
  public String schemaVersion() {
    Flyway instance = flyway.getIfAvailable();
    if (instance == null) {
      return null;
    }
    MigrationInfo current = instance.info().current();
    return current == null ? null : current.getVersion().getVersion();
  }
}
