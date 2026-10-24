package com.jaworski.serialprotocol.service.db.sync;

import com.jaworski.serialprotocol.service.db.sync.SyncAnalysis.RowPlan;
import com.jaworski.serialprotocol.service.db.sync.SyncAnalyzer.JoinTable;
import com.jaworski.serialprotocol.service.db.sync.SyncJdbcWriter.Bind;
import com.jaworski.serialprotocol.service.db.sync.SyncJdbcWriter.Column;
import com.jaworski.serialprotocol.service.db.sync.SyncTables.SyncTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipFile;

/**
 * Writes an analysed sync file into the database in one JDBC transaction
 * (docs/sync-spec.md § Apply).
 *
 * <p>Row by row and through JDBC, never through the entities: {@code version}, the audit
 * columns and {@code origin_node} are written as the file carries them, so the optimistic
 * checks on every node keep working and the next analysis still knows who changed what.
 * The order is: the local rows the resolutions change (tombstones, renumberings, revived
 * records), then the file rows table by table in foreign-key order with join-table rows
 * replaced per written owner, then the photo bytes that are missing here, then the sequences,
 * then {@code sync_node}. Any failure rolls all of it back; the backup written just before
 * stays.</p>
 */
@Service
public class SyncApplyService {

  private static final Logger LOG = LoggerFactory.getLogger(SyncApplyService.class);

  /** The {@code id_sequence} row behind each numbered table. */
  static final Map<String, String> SEQUENCES = Map.of(
      "participants", "participant",
      "courses", "courses",
      "course_counter", "course_counter");

  /** What one transaction wrote. */
  public record Outcome(int inserted, int updated, int blobs, List<String> changes) {
  }

  private final JdbcTemplate jdbc;
  private final TransactionTemplate transaction;
  private final SyncNodeService nodeService;

  public SyncApplyService(DataSource dataSource, PlatformTransactionManager transactionManager,
      SyncNodeService nodeService) {
    this.jdbc = new JdbcTemplate(dataSource);
    this.transaction = new TransactionTemplate(transactionManager);
    this.nodeService = nodeService;
  }

  /**
   * @param path        the staged file, opened again for the photo bytes
   * @param file        its rows
   * @param analysis    the plan the user confirmed, made against {@code local}
   * @param resolutions collision id to the chosen resolution code, one per collision
   * @param local       the local tables the analysis was made from
   * @param self        this node's id, stamped on every local row a resolution changes
   * @param now         the commit time written into the changed local rows and {@code sync_node}
   */
  public Outcome apply(Path path, SyncFile file, SyncAnalysis analysis, Map<String, String> resolutions,
      Map<String, List<SyncRow>> local, UUID self, Instant now, String fileName) {
    return transaction.execute(status -> {
      try (ZipFile zip = new ZipFile(path.toFile(), StandardCharsets.UTF_8)) {
        return new Run(zip, file, analysis, resolutions, local, self, now).run(fileName);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    });
  }

  /** One transaction's state: what to write, and what the resolutions change first. */
  private final class Run {

    private final ZipFile zip;
    private final SyncFile file;
    private final SyncAnalysis analysis;
    private final Map<String, String> resolutions;
    private final UUID self;
    private final String now;
    private final Map<String, Map<String, SyncRow>> localByKey = new HashMap<>();
    private final Map<String, Map<String, SyncRow>> fileByKey = new HashMap<>();
    /** The file rows to write, per table and key, after the resolutions. */
    private final Map<String, Map<String, SyncRow>> writes = new LinkedHashMap<>();
    /** Local rows the resolutions change, run before any file row is written. */
    private final List<Runnable> localChanges = new ArrayList<>();
    /** File rows a C6 "skip" left out, per table; rows referring to them are left out too. */
    private final Map<String, Set<String>> skipped = new HashMap<>();
    private final List<String> changes = new ArrayList<>();
    private final Map<String, Long> sequences = new LinkedHashMap<>();
    private final SyncJdbcWriter writer = new SyncJdbcWriter(jdbc);
    private int inserted;
    private int updated;
    private int blobs;

    Run(ZipFile zip, SyncFile file, SyncAnalysis analysis, Map<String, String> resolutions,
        Map<String, List<SyncRow>> local, UUID self, Instant now) {
      this.zip = zip;
      this.file = file;
      this.analysis = analysis;
      this.resolutions = resolutions;
      this.self = self;
      this.now = SyncValues.TIMESTAMP.format(now);
      for (SyncTable table : SyncTables.TABLES) {
        Map<String, SyncRow> l = new LinkedHashMap<>();
        for (SyncRow row : local.getOrDefault(table.name(), List.of())) {
          l.put(row.key(table), row);
        }
        Map<String, SyncRow> f = new LinkedHashMap<>();
        for (SyncRow row : file.rows(table.name())) {
          f.put(row.key(table), row);
        }
        localByKey.put(table.name(), l);
        fileByKey.put(table.name(), f);
        writes.put(table.name(), new LinkedHashMap<>());
      }
      for (RowPlan planned : analysis.plan()) {
        writes.get(planned.table()).put(planned.key(), planned.fileRow());
      }
    }

    Outcome run(String fileName) {
      prepareSequences();
      for (Collision collision : analysis.collisions()) {
        resolve(collision);
      }
      skipDependants();
      localChanges.forEach(Runnable::run);
      for (SyncTable table : SyncTables.TABLES) {
        JoinTable join = SyncAnalyzer.JOIN_TABLES.get(table.name());
        if (join != null) {
          replaceJoinRows(table, join);
        } else if (!table.name().equals("id_sequence")) {
          writeRows(table);
        }
      }
      writeBlobs();
      writeSequences();
      nodeService.markImported(SyncValues.parseTimestamp(now), fileName);
      LOG.info("Sync file {} applied: {} inserted, {} updated, {} photo(s) loaded, {} local change(s)",
          fileName, inserted, updated, blobs, changes.size());
      return new Outcome(inserted, updated, blobs, List.copyOf(changes));
    }

    // ---- resolutions ---------------------------------------------------------------------

    private void resolve(Collision c) {
      String code = resolutions.get(c.id());
      if (code == null || c.resolutions().stream().noneMatch(r -> r.code().equals(code))) {
        throw new SyncRefusedException("Choose what to do with every collision before applying the import ("
            + c.tableLabel() + " " + c.record() + ").");
      }
      SyncTable table = table(c.table());
      String key = keyOf(c);
      switch (c.type()) {
        case C1 -> {
          if (code.equals("discard-local")) {
            localChanges.add(() -> touch(table, key, List.of(new Bind(SyncRow.DELETED_AT, now)),
                "deleted " + c.tableLabel() + " " + c.record() + " (made on this read-only computer)"));
          }
        }
        case C2 -> {
          if (code.equals("take-file")) {
            writes.get(c.table()).put(key, fileByKey.get(c.table()).get(key));
          }
        }
        case C3 -> {
          SyncRow fileRow = fileByKey.get(c.table()).get(key);
          boolean takeFile = code.equals("keep-deleted") == fileRow.isDeleted();
          if (takeFile) {
            writes.get(c.table()).put(key, fileRow);
          }
        }
        case C4 -> renumber(c, table, key, code);
        case C5 -> recode(c, table, key, code);
        case C6 -> {
          if (code.equals("skip-row")) {
            skip(c.table(), key, "skipped " + c.tableLabel() + " " + c.record() + " from the file");
          } else {
            revive(c);
          }
        }
      }
    }

    /**
     * A C6 "bring back": the referenced tombstone becomes live. When the file carries that row
     * and the plan writes it, the file row is written live instead of the local tombstone being
     * touched, or the write would put the tombstone straight back; the row still counts as an
     * edit made here now. Otherwise the local tombstone is touched as any other resolution.
     */
    private void revive(Collision c) {
      String via = c.fields().getFirst().field();
      String referenced = c.fields().getFirst().fileValue();
      JoinTable join = SyncAnalyzer.JOIN_TABLES.get(via);
      String referencedTable = join != null ? join.memberTable() : SyncAnalyzer.REFERENCES.get(c.table()).get(via);
      SyncTable target = table(referencedTable);
      String change = "brought back the deleted " + SyncAnalyzer.label(target).toLowerCase(Locale.ROOT)
          + " that " + c.tableLabel() + " " + c.record() + " refers to";
      SyncRow planned = writes.get(referencedTable).get(referenced);
      if (planned != null) {
        Long version = planned.version();
        writes.get(referencedTable).put(referenced, planned.with(SyncRow.DELETED_AT, null)
            .with(SyncRow.UPDATED_AT, now).with(SyncRow.ORIGIN_NODE, self.toString())
            .with(SyncRow.VERSION, version == null ? 1L : version + 1));
        changes.add(change);
        return;
      }
      localChanges.add(() -> touch(target, referenced, List.of(new Bind(SyncRow.DELETED_AT, null)), change));
    }

    private void skip(String table, String key, String change) {
      if (writes.get(table).remove(key) != null || !localByKey.get(table).containsKey(key)) {
        skipped.computeIfAbsent(table, t -> new LinkedHashSet<>()).add(key);
      }
      changes.add(change);
    }

    /**
     * A skipped row that is not here either cannot be referred to, so every planned row pointing
     * at it is left out as well, and so on down the chain; join rows follow their owner and are
     * filtered against the members that will exist in {@link #replaceJoinRows}.
     */
    private void skipDependants() {
      boolean more = true;
      while (more) {
        more = false;
        for (Map.Entry<String, Map<String, String>> refs : SyncAnalyzer.REFERENCES.entrySet()) {
          for (Map.Entry<String, SyncRow> entry : List.copyOf(writes.get(refs.getKey()).entrySet())) {
            for (Map.Entry<String, String> ref : refs.getValue().entrySet()) {
              String referenced = entry.getValue().text(ref.getKey());
              if (referenced != null && !exists(ref.getValue(), referenced)) {
                SyncTable table = table(refs.getKey());
                skip(refs.getKey(), entry.getKey(), "skipped " + SyncAnalyzer.label(table).toLowerCase(Locale.ROOT)
                    + " " + entry.getKey() + " from the file: the "
                    + SyncAnalyzer.label(table(ref.getValue())).toLowerCase(Locale.ROOT) + " it refers to was skipped");
                more = true;
                break;
              }
            }
          }
        }
      }
    }

    /** Whether the row will be in the database after the import: here already, or written from the file. */
    private boolean exists(String table, String key) {
      return (localByKey.get(table).containsKey(key) && !skipped.getOrDefault(table, Set.of()).contains(key))
          || writes.get(table).containsKey(key);
    }

    private void renumber(Collision c, SyncTable table, String fileKey, String code) {
      String column = SyncAnalyzer.NUMBER_COLUMNS.get(c.table());
      String sequence = SEQUENCES.get(c.table());
      String number = c.fields().getFirst().fileValue();
      SyncRow fileRow = writes.get(c.table()).get(fileKey);
      if (code.equals("keep-local-number")) {
        long fresh = allocate(sequence);
        writes.get(c.table()).put(fileKey, fileRow.with(column, fresh));
        changes.add(c.tableLabel() + " " + c.record() + " from the file was given number " + fresh
            + " instead of " + number);
        return;
      }
      String localKey = localByKey.get(c.table()).entrySet().stream()
          .filter(e -> !e.getKey().equals(fileKey) && !writes.get(c.table()).containsKey(e.getKey()))
          .filter(e -> number.equals(e.getValue().text(column)))
          .map(Map.Entry::getKey).findFirst()
          .orElseThrow(() -> new SyncRefusedException("The record with number " + number
              + " is no longer here; read the file again."));
      long fresh = allocate(sequence);
      localChanges.add(() -> touch(table, localKey, List.of(new Bind(column, fresh)),
          c.tableLabel() + " made here was given number " + fresh + " instead of " + number));
    }

    private void recode(Collision c, SyncTable table, String fileKey, String code) {
      String fileCode = c.fields().getFirst().fileValue();
      if (code.equals("keep-local-code")) {
        String fresh = freeCode(fileCode);
        writes.get(c.table()).put(fileKey, writes.get(c.table()).get(fileKey).with("course_type_code", fresh));
        changes.add("course type " + c.record() + " from the file was given code " + fresh + " instead of " + fileCode);
        return;
      }
      String localKey = localByKey.get(c.table()).entrySet().stream()
          .filter(e -> !e.getKey().equals(fileKey) && !writes.get(c.table()).containsKey(e.getKey()))
          .filter(e -> fileCode.equalsIgnoreCase(e.getValue().text("course_type_code")))
          .map(Map.Entry::getKey).findFirst()
          .orElseThrow(() -> new SyncRefusedException("The course type with code " + fileCode
              + " is no longer here; read the file again."));
      String fresh = freeCode(fileCode);
      localChanges.add(() -> touch(table, localKey, List.of(new Bind("course_type_code", fresh)),
          "course type made here was given code " + fresh + " instead of " + fileCode));
    }

    /** {@code CODE-2}, {@code CODE-3}, ... the first not used on either side, case-insensitively. */
    private String freeCode(String code) {
      Set<String> taken = new LinkedHashSet<>();
      for (SyncRow row : localByKey.get("course_type").values()) {
        taken.add(String.valueOf(row.text("course_type_code")).toLowerCase(Locale.ROOT));
      }
      for (SyncRow row : fileByKey.get("course_type").values()) {
        taken.add(String.valueOf(row.text("course_type_code")).toLowerCase(Locale.ROOT));
      }
      for (SyncRow row : writes.get("course_type").values()) {
        taken.add(String.valueOf(row.text("course_type_code")).toLowerCase(Locale.ROOT));
      }
      for (int n = 2; ; n++) {
        String candidate = code + "-" + n;
        if (taken.add(candidate.toLowerCase(Locale.ROOT))) {
          return candidate;
        }
      }
    }

    /**
     * The key inside a collision id: {@code TYPE-table-key}, with {@code -via-referenced}
     * appended for C6 so that two dangling references of one record get two decisions.
     */
    private static String keyOf(Collision c) {
      String rest = c.id().substring(c.type().name().length() + 1 + c.table().length() + 1);
      if (c.type() == Collision.Type.C6) {
        String suffix = "-" + c.fields().getFirst().field() + "-" + c.fields().getFirst().fileValue();
        rest = rest.substring(0, rest.length() - suffix.length());
      }
      return rest;
    }

    // ---- sequences -----------------------------------------------------------------------

    /**
     * Each sequence starts from the highest of the file's value, the local value and the
     * highest number in use on either side plus one; renumberings then take from here.
     */
    private void prepareSequences() {
      Map<String, SyncRow> fileRows = fileByKey.get("id_sequence");
      Map<String, SyncRow> localRows = localByKey.get("id_sequence");
      Set<String> names = new LinkedHashSet<>(fileRows.keySet());
      names.addAll(localRows.keySet());
      for (String name : names) {
        long value = Math.max(next(fileRows.get(name)), next(localRows.get(name)));
        for (Map.Entry<String, String> entry : SEQUENCES.entrySet()) {
          if (entry.getValue().equals(name)) {
            value = Math.max(value, highestInUse(entry.getKey()) + 1);
          }
        }
        sequences.put(name, value);
      }
    }

    private static long next(SyncRow row) {
      if (row == null) {
        return 1;
      }
      Object value = row.get("id_sequence_next");
      return value instanceof Number number ? number.longValue() : 1;
    }

    private long highestInUse(String table) {
      String column = SyncAnalyzer.NUMBER_COLUMNS.get(table);
      Long local = jdbc.queryForObject("select max(" + column + ") from " + table, Long.class);
      long highest = local == null ? 0 : local;
      for (SyncRow row : fileByKey.get(table).values()) {
        Object value = row.get(column);
        if (value instanceof Number number) {
          highest = Math.max(highest, number.longValue());
        }
      }
      return highest;
    }

    private long allocate(String sequence) {
      long value = sequences.getOrDefault(sequence, 1L);
      sequences.put(sequence, value + 1);
      return value;
    }

    private void writeSequences() {
      for (Map.Entry<String, Long> entry : sequences.entrySet()) {
        int changed = jdbc.update("update id_sequence set id_sequence_next = ? where id_sequence_name = ?",
            entry.getValue(), entry.getKey());
        if (changed == 0) {
          jdbc.update("insert into id_sequence (id_sequence_name, id_sequence_next) values (?, ?)",
              entry.getKey(), entry.getValue());
        }
      }
    }

    // ---- rows ------------------------------------------------------------------------------

    private void writeRows(SyncTable table) {
      Map<String, Column> cols = columns(table.name());
      for (Map.Entry<String, SyncRow> entry : writes.get(table.name()).entrySet()) {
        List<Bind> binds = new ArrayList<>();
        for (Map.Entry<String, Object> value : entry.getValue().values().entrySet()) {
          if (cols.containsKey(value.getKey())) {
            binds.add(new Bind(value.getKey(), value.getValue()));
          }
        }
        if (localByKey.get(table.name()).containsKey(entry.getKey())) {
          update(table, binds);
          updated++;
        } else {
          insert(table, binds);
          inserted++;
        }
      }
    }

    private void insert(SyncTable table, List<Bind> binds) {
      writer.insert(table, binds);
    }

    private void update(SyncTable table, List<Bind> binds) {
      StringBuilder set = new StringBuilder();
      List<Bind> all = new ArrayList<>();
      for (Bind bind : binds) {
        if (table.keyColumns().contains(bind.column())) {
          continue;
        }
        set.append(set.isEmpty() ? "" : ", ").append(bind.column()).append(" = ?");
        all.add(bind);
      }
      StringBuilder where = new StringBuilder();
      for (String key : table.keyColumns()) {
        where.append(where.isEmpty() ? "" : " and ").append(key).append(" = ?");
        all.add(binds.stream().filter(b -> b.column().equals(key)).findFirst().orElseThrow());
      }
      execute(table.name(), "update " + table.name() + " set " + set + " where " + where, all);
    }

    /**
     * Changes a local row the way a resolution asks and marks it as edited here now, so the
     * change travels with the next export and the next analysis sees it as this computer's.
     */
    private void touch(SyncTable table, String key, List<Bind> set, String change) {
      List<Bind> all = new ArrayList<>(set);
      all.add(new Bind(SyncRow.UPDATED_AT, now));
      all.add(new Bind(SyncRow.ORIGIN_NODE, self.toString()));
      StringBuilder sql = new StringBuilder("update " + table.name() + " set ");
      for (Bind bind : all) {
        sql.append(bind.column()).append(" = ?, ");
      }
      sql.append("version = version + 1 where ");
      String[] parts = key.split("\\|", -1);
      for (int i = 0; i < table.keyColumns().size(); i++) {
        sql.append(i == 0 ? "" : " and ").append(table.keyColumns().get(i)).append(" = ?");
        all.add(new Bind(table.keyColumns().get(i), parts[i]));
      }
      int changed = execute(table.name(), sql.toString(), all);
      if (changed != 1) {
        throw new SyncRefusedException("The record this decision concerns is no longer here; read the file again.");
      }
      changes.add(change);
    }

    private void replaceJoinRows(SyncTable table, JoinTable join) {
      Map<String, Column> cols = columns(table.name());
      for (String owner : writes.get(join.ownerTable()).keySet()) {
        execute(table.name(), "delete from " + table.name() + " where " + join.ownerColumn() + " = ?",
            List.of(new Bind(join.ownerColumn(), owner)));
        for (SyncRow row : fileByKey.get(table.name()).values()) {
          if (!owner.equals(row.text(join.ownerColumn()))
              || !exists(join.memberTable(), row.text(join.memberColumn()))) {
            continue;
          }
          List<Bind> binds = new ArrayList<>();
          for (Map.Entry<String, Object> value : row.values().entrySet()) {
            if (cols.containsKey(value.getKey())) {
              binds.add(new Bind(value.getKey(), value.getValue()));
            }
          }
          insert(table, binds);
        }
      }
    }

    // ---- blobs -----------------------------------------------------------------------------

    /** A photo's bytes are loaded only for an {@code image} row that exists and has no bytes yet. */
    private void writeBlobs() {
      for (String id : file.blobIds()) {
        boolean imageKnown = localByKey.get("image").containsKey(id) || writes.get("image").containsKey(id);
        if (!imageKnown) {
          continue;
        }
        Integer present = jdbc.queryForObject("select count(*) from " + SyncTables.BLOB_TABLE
            + " where image_uuid = ?", Integer.class, UUID.fromString(id));
        if (present != null && present > 0) {
          continue;
        }
        writer.insertBlob(zip, id, file.thumbIds().contains(id));
        blobs++;
      }
    }

    // ---- JDBC ------------------------------------------------------------------------------

    private int execute(String table, String sql, List<Bind> binds) {
      return writer.execute(table, sql, binds);
    }

    private Map<String, Column> columns(String table) {
      return writer.columns(table);
    }

    private static SyncTable table(String name) {
      return SyncTables.TABLES.stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow();
    }
  }
}
