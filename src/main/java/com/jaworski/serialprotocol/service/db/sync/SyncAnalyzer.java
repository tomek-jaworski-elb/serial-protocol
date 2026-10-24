package com.jaworski.serialprotocol.service.db.sync;

import com.jaworski.serialprotocol.service.db.sync.Collision.FieldDiff;
import com.jaworski.serialprotocol.service.db.sync.Collision.Resolution;
import com.jaworski.serialprotocol.service.db.sync.Collision.Side;
import com.jaworski.serialprotocol.service.db.sync.Collision.Type;
import com.jaworski.serialprotocol.service.db.sync.SyncAnalysis.Action;
import com.jaworski.serialprotocol.service.db.sync.SyncAnalysis.Notice;
import com.jaworski.serialprotocol.service.db.sync.SyncAnalysis.RowPlan;
import com.jaworski.serialprotocol.service.db.sync.SyncAnalysis.TableSummary;
import com.jaworski.serialprotocol.service.db.sync.SyncTables.SyncTable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The decision table of docs/sync-spec.md § Analysis, on rows already in memory.
 *
 * <p>Pure: no database, no clock, no Spring, so {@code SyncCollisionTest} can walk every cell
 * with hand-built rows. {@link SyncImportService} feeds it the local tables read through JDBC
 * and the file read by {@link SyncReader}. Rows are matched by primary key; two facts drive
 * every decision, "changed here" (the local row's audit columns after {@code since}, or any
 * row when the node never exchanged data) and "changed there" (the file row comes from another
 * node and differs). Join tables follow their owning record and {@code id_sequence} takes the
 * higher value, so neither is decided row by row.</p>
 */
public final class SyncAnalyzer {

  /**
   * @param self     this node's id
   * @param selfName this node's name, for the report
   * @param since    {@code sync_node.last_sync_at}; {@code null} reads as "every local row changed here"
   * @param readOnly whether this installation runs in READ_ONLY mode
   * @param fileNode the exporting node, from the manifest
   * @param seen     {@code exportedAt} of the last file imported from {@code fileNode}, or
   *                 {@code null} when the last import came from another node (or there was none);
   *                 a file row not touched since then was already merged here
   * @param local    the local tables in file shape
   * @param file     the file's tables
   * @param zone     the zone the report shows timestamps in
   */
  public record Input(UUID self, String selfName, Instant since, boolean readOnly, SyncManifest.Node fileNode,
      Instant seen, Map<String, List<SyncRow>> local, Map<String, List<SyncRow>> file, ZoneId zone) {
  }

  static final Map<String, String> TABLE_LABELS = Map.ofEntries(
      Map.entry("course_type", "Course type"),
      Map.entry("image", "Photo"),
      Map.entry("participants", "Participant"),
      Map.entry("course_counter", "Course counter"),
      Map.entry("trainer", "Trainer"),
      Map.entry("lecturer", "Lecturer"),
      Map.entry("technician", "Technician"),
      Map.entry("trainer_image", "Trainer photos"),
      Map.entry("lecturer_image", "Lecturer photos"),
      Map.entry("technician_image", "Technician photos"),
      Map.entry("courses", "Course"),
      Map.entry("courses_trainers", "Course trainers"),
      Map.entry("courses_lecturers", "Course lecturers"),
      Map.entry("courses_technicians", "Course technicians"),
      Map.entry("student", "Student (legacy)"),
      Map.entry("instructor", "Instructor (legacy)"),
      Map.entry("id_sequence", "Number sequences"));

  /** Tables whose business number must be unique (C4): table to number column. */
  static final Map<String, String> NUMBER_COLUMNS = Map.of(
      "participants", "participants_id",
      "courses", "courses_id",
      "course_counter", "course_counter_counter");

  /** Foreign keys of the row-by-row tables (C6): table to (column to referenced table). */
  static final Map<String, Map<String, String>> REFERENCES = Map.of(
      "participants", Map.of("image_uuid", "image"),
      "course_counter", Map.of("image_uuid", "image"),
      "trainer", Map.of("trainer_primary_image_uuid", "image"),
      "lecturer", Map.of("lecturer_primary_image_uuid", "image"),
      "technician", Map.of("technician_primary_image_uuid", "image"),
      "courses", Map.of("courses_course_type_uuid", "course_type", "courses_participant_id", "participants",
          "course_counter_uuid", "course_counter"));

  /** Join tables: owning column to owner table, and member column to referenced table. */
  record JoinTable(String ownerColumn, String ownerTable, String memberColumn, String memberTable) {
  }

  static final Map<String, JoinTable> JOIN_TABLES = Map.of(
      "trainer_image", new JoinTable("trainer_uuid", "trainer", "image_uuid", "image"),
      "lecturer_image", new JoinTable("lecturer_uuid", "lecturer", "image_uuid", "image"),
      "technician_image", new JoinTable("technician_uuid", "technician", "image_uuid", "image"),
      "courses_trainers", new JoinTable("courses_uuid", "courses", "trainer_uuid", "trainer"),
      "courses_lecturers", new JoinTable("courses_uuid", "courses", "lecturer_uuid", "lecturer"),
      "courses_technicians", new JoinTable("courses_uuid", "courses", "technician_uuid", "technician"));

  private static final Set<String> AUDIT_COLUMNS = Set.of(SyncRow.VERSION, SyncRow.CREATED_AT, SyncRow.UPDATED_AT,
      SyncRow.DELETED_AT, SyncRow.ORIGIN_NODE);

  private static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT);

  private final Input in;
  private final List<TableSummary> summaries = new ArrayList<>();
  private final List<Notice> notices = new ArrayList<>();
  private final List<Collision> collisions = new ArrayList<>();
  private final List<RowPlan> plan = new ArrayList<>();
  /** What the database would hold after the plan: local rows overlaid with the planned file rows. */
  private final Map<String, Map<String, SyncRow>> merged = new LinkedHashMap<>();
  private final Map<String, Map<String, SyncRow>> localByKey = new HashMap<>();
  private final Map<String, Map<String, SyncRow>> fileByKey = new HashMap<>();

  private SyncAnalyzer(Input in) {
    this.in = in;
  }

  public static SyncAnalysis analyze(Input in) {
    return new SyncAnalyzer(in).run();
  }

  /** The two refusals that need no rows: our own export and a different schema. */
  public static void checkAccepted(SyncManifest manifest, UUID self, String localSchemaVersion) {
    if (self.equals(manifest.node().id())) {
      throw new SyncRefusedException("This is our own export (node " + manifest.node().name()
          + "); a backup is restored on the synchronisation page, not imported.");
    }
    if (!Objects.equals(manifest.schemaVersion(), localSchemaVersion)) {
      throw new SyncRefusedException("The file was made with database schema version "
          + String.valueOf(manifest.schemaVersion()) + " and this computer runs version "
          + String.valueOf(localSchemaVersion) + "; update the program on both computers first.");
    }
  }

  private SyncAnalysis run() {
    for (SyncTable table : SyncTables.TABLES) {
      index(table);
    }
    for (SyncTable table : SyncTables.TABLES) {
      if (JOIN_TABLES.containsKey(table.name())) {
        summaries.add(summary(table, 0, 0, 0, 0, 0, "follows the owning record"));
      } else if (table.name().equals("id_sequence")) {
        summaries.add(summary(table, 0, 0, 0, 0, 0, "takes the higher value of each sequence"));
      } else {
        decideRows(table);
      }
    }
    checkNumbers();
    checkCodes();
    checkReferences();
    return new SyncAnalysis(List.copyOf(summaries), List.copyOf(notices), List.copyOf(collisions), List.copyOf(plan));
  }

  private void index(SyncTable table) {
    Map<String, SyncRow> local = new LinkedHashMap<>();
    for (SyncRow row : in.local().getOrDefault(table.name(), List.of())) {
      local.put(row.key(table), row);
    }
    Map<String, SyncRow> file = new LinkedHashMap<>();
    for (SyncRow row : in.file().getOrDefault(table.name(), List.of())) {
      file.put(row.key(table), row);
    }
    localByKey.put(table.name(), local);
    fileByKey.put(table.name(), file);
    merged.put(table.name(), new LinkedHashMap<>(local));
  }

  // ---- the decision table -------------------------------------------------------------

  private void decideRows(SyncTable table) {
    Map<String, SyncRow> local = localByKey.get(table.name());
    Map<String, SyncRow> file = fileByKey.get(table.name());
    int inserts = 0;
    int updates = 0;
    int unchanged = 0;
    int kept = 0;
    int collided = 0;
    for (Map.Entry<String, SyncRow> entry : file.entrySet()) {
      String key = entry.getKey();
      SyncRow fileRow = entry.getValue();
      SyncRow localRow = local.get(key);
      switch (decide(table, key, localRow, fileRow)) {
        case INSERT -> inserts++;
        case UPDATE -> updates++;
        case UNCHANGED -> unchanged++;
        case KEEP -> kept++;
        case COLLISION -> collided++;
      }
    }
    for (Map.Entry<String, SyncRow> entry : local.entrySet()) {
      if (file.containsKey(entry.getKey())) {
        continue;
      }
      if (in.readOnly()) {
        collided++;
        collisions.add(createdOnReadOnly(table, entry.getKey(), entry.getValue()));
      } else {
        kept++;
        notices.add(notice(table, entry.getValue(), "not in file; created on this computer after the file was made"));
      }
    }
    summaries.add(summary(table, inserts, updates, unchanged, kept, collided, null));
  }

  private enum Outcome { INSERT, UPDATE, UNCHANGED, KEEP, COLLISION }

  private Outcome decide(SyncTable table, String key, SyncRow local, SyncRow file) {
    if (local == null) {
      plan(table, key, Action.INSERT, file);
      return Outcome.INSERT;
    }
    if (local.sameStateAs(file)) {
      return Outcome.UNCHANGED;
    }
    boolean changedHere = changedHere(local);
    boolean changedThere = changedThere(local, file);
    if (!changedHere && !changedThere) {
      return Outcome.KEEP;
    }
    if (!changedHere) {
      if (isOlder(file, local)) {
        notices.add(notice(table, local, "file is older than this computer for this row; kept"));
        return Outcome.KEEP;
      }
      plan(table, key, Action.UPDATE, file);
      return Outcome.UPDATE;
    }
    if (!changedThere) {
      return Outcome.KEEP;
    }
    if (local.isDeleted() && file.isDeleted()) {
      if (file.deletedAt().isBefore(local.deletedAt())) {
        plan(table, key, Action.UPDATE, file);
        return Outcome.UPDATE;
      }
      return Outcome.UNCHANGED;
    }
    if (local.isDeleted() || file.isDeleted()) {
      collisions.add(deletedAndEdited(table, key, local, file));
    } else {
      collisions.add(editedOnBoth(table, key, local, file));
    }
    return Outcome.COLLISION;
  }

  /**
   * A file row whose origin is not {@code self} carries the other computer's change. A
   * tombstone is the exception: a soft delete is an {@code @SQLDelete} UPDATE that never
   * passes through {@code OriginStamper}, so a row deleted there keeps the origin of its last
   * edit, which may be this computer. A tombstone in the file that this computer does not
   * have (or has with another {@code deleted_at}) is therefore a change made there whatever
   * its origin says.
   *
   * <p>Either way the file row must be newer than {@code seen}, the export time of the last
   * file taken from the same node: a row that node wrote, which this computer imported and
   * then edited, comes back unchanged in the next file (another origin, a lower version) and is
   * not a competing change. Clocks differ between computers, but here both timestamps come
   * from the exporting one.</p>
   */
  private boolean changedThere(SyncRow local, SyncRow file) {
    if (alreadySeen(file)) {
      return false;
    }
    if (!in.self().equals(file.originNode())) {
      return true;
    }
    return file.isDeleted() && !Objects.equals(file.deletedAt(), local.deletedAt());
  }

  private boolean alreadySeen(SyncRow file) {
    Instant seen = in.seen();
    if (seen == null) {
      return false;
    }
    return !after(file.createdAt(), seen) && !after(file.updatedAt(), seen) && !after(file.deletedAt(), seen);
  }

  /** Rows stamped before V10 ({@code origin_node} NULL) count as untouched here. */
  private boolean changedHere(SyncRow local) {
    if (local.originNode() == null) {
      return false;
    }
    Instant since = in.since();
    if (since == null) {
      return true;
    }
    return after(local.createdAt(), since) || after(local.updatedAt(), since) || after(local.deletedAt(), since);
  }

  private static boolean after(Instant at, Instant since) {
    return at != null && at.isAfter(since);
  }

  private static boolean isOlder(SyncRow file, SyncRow local) {
    Long fileVersion = file.version();
    Long localVersion = local.version();
    return fileVersion != null && localVersion != null && fileVersion < localVersion;
  }

  private void plan(SyncTable table, String key, Action action, SyncRow fileRow) {
    plan.add(new RowPlan(table.name(), key, action, fileRow));
    merged.get(table.name()).put(key, fileRow);
  }

  // ---- collisions C1–C3 ---------------------------------------------------------------

  private Collision createdOnReadOnly(SyncTable table, String key, SyncRow local) {
    return new Collision(id(Type.C1, table, key), Type.C1, table.name(), label(table), name(table.name(), local),
        "This record was created on this read-only computer after the last exchange of data and the file "
            + "does not have it; only the active installation should be creating records.",
        List.of(), side(local), null,
        List.of(new Resolution("keep-local", "Keep the record made here"),
            new Resolution("discard-local", "Discard the record made here")));
  }

  private Collision editedOnBoth(SyncTable table, String key, SyncRow local, SyncRow file) {
    return new Collision(id(Type.C2, table, key), Type.C2, table.name(), label(table), name(table.name(), file),
        "Both computers edited this record after the last exchange of data; only one installation should "
            + "be editing at a time.",
        diffs(local, file), side(local), side(file),
        List.of(new Resolution("take-file", "Take the file's version"),
            new Resolution("keep-local", "Keep this computer's version")));
  }

  private Collision deletedAndEdited(SyncTable table, String key, SyncRow local, SyncRow file) {
    SyncRow live = local.isDeleted() ? file : local;
    String where = local.isDeleted() ? "this computer" : nodeName(file.originNode());
    return new Collision(id(Type.C3, table, key), Type.C3, table.name(), label(table), name(table.name(), live),
        "This record was deleted on " + where + " and edited on the other computer after the last "
            + "exchange of data.",
        diffs(local, file), side(local), side(file),
        List.of(new Resolution("keep-deleted", "Keep it deleted"),
            new Resolution("keep-edited", "Keep the edited record")));
  }

  // ---- post checks C4–C6 --------------------------------------------------------------

  private void checkNumbers() {
    for (Map.Entry<String, String> entry : NUMBER_COLUMNS.entrySet()) {
      String tableName = entry.getKey();
      String column = entry.getValue();
      SyncTable table = table(tableName);
      Map<String, List<String>> keysByNumber = new LinkedHashMap<>();
      for (Map.Entry<String, SyncRow> row : merged.get(tableName).entrySet()) {
        String number = row.getValue().text(column);
        if (number != null) {
          keysByNumber.computeIfAbsent(number, n -> new ArrayList<>()).add(row.getKey());
        }
      }
      for (Map.Entry<String, List<String>> dup : keysByNumber.entrySet()) {
        if (dup.getValue().size() < 2) {
          continue;
        }
        String fileKey = dup.getValue().stream().filter(k -> isPlanned(tableName, k)).findFirst().orElse(null);
        String localKey = dup.getValue().stream().filter(k -> !isPlanned(tableName, k)).findFirst().orElse(null);
        if (fileKey == null || localKey == null) {
          continue;
        }
        SyncRow fileRow = merged.get(tableName).get(fileKey);
        SyncRow localRow = merged.get(tableName).get(localKey);
        collisions.add(new Collision(id(Type.C4, table, fileKey), Type.C4, tableName, label(table),
            name(tableName, fileRow),
            label(table) + " number " + dup.getKey() + " was given to " + name(tableName, localRow) + " on "
                + nodeName(localRow.originNode()) + " and to " + name(tableName, fileRow) + " on "
                + nodeName(fileRow.originNode()) + "; numbers are unique and are printed on certificates.",
            List.of(new FieldDiff(column, dup.getKey(), dup.getKey())), side(localRow), side(fileRow),
            List.of(new Resolution("keep-file-number", "Keep the file's number, give the record made here the next free one"),
                new Resolution("keep-local-number", "Keep this computer's number, give the file's record the next free one"))));
      }
    }
  }

  private void checkCodes() {
    SyncTable table = table("course_type");
    Map<String, List<String>> keysByCode = new LinkedHashMap<>();
    for (Map.Entry<String, SyncRow> row : merged.get("course_type").entrySet()) {
      String code = row.getValue().text("course_type_code");
      if (code != null) {
        keysByCode.computeIfAbsent(code.toLowerCase(Locale.ROOT), c -> new ArrayList<>()).add(row.getKey());
      }
    }
    for (Map.Entry<String, List<String>> dup : keysByCode.entrySet()) {
      if (dup.getValue().size() < 2) {
        continue;
      }
      String fileKey = dup.getValue().stream().filter(k -> isPlanned("course_type", k)).findFirst().orElse(null);
      String localKey = dup.getValue().stream().filter(k -> !isPlanned("course_type", k)).findFirst().orElse(null);
      if (fileKey == null || localKey == null) {
        continue;
      }
      SyncRow fileRow = merged.get("course_type").get(fileKey);
      SyncRow localRow = merged.get("course_type").get(localKey);
      collisions.add(new Collision(id(Type.C5, table, fileKey), Type.C5, "course_type", label(table),
          name("course_type", fileRow),
          "Course type code " + fileRow.text("course_type_code") + " is used by two different course types, one made on "
              + nodeName(localRow.originNode()) + " and one on " + nodeName(fileRow.originNode())
              + "; codes are unique.",
          List.of(new FieldDiff("course_type_code", localRow.text("course_type_code"), fileRow.text("course_type_code"))),
          side(localRow), side(fileRow),
          List.of(new Resolution("keep-file-code", "Keep the file's code, rename the type made here"),
              new Resolution("keep-local-code", "Keep this computer's code, rename the file's type"))));
    }
  }

  /**
   * Every file row the apply may write is checked: the planned ones, and the file side of a C2
   * or C3 collision, which a "take the file's version" or "keep edited" decision writes as well.
   */
  private void checkReferences() {
    List<RowPlan> candidates = new ArrayList<>(plan);
    Set<String> conditional = new HashSet<>();
    for (Collision c : List.copyOf(collisions)) {
      if (c.type() == Type.C2 || c.type() == Type.C3) {
        String key = c.id().substring(c.type().name().length() + 1 + c.table().length() + 1);
        candidates.add(new RowPlan(c.table(), key, Action.UPDATE, fileByKey.get(c.table()).get(key)));
        conditional.add(c.table() + "|" + key);
      }
    }
    for (RowPlan candidate : candidates) {
      SyncTable table = table(candidate.table());
      SyncRow row = candidate.fileRow();
      String condition = conditional.contains(candidate.table() + "|" + candidate.key())
          ? " This applies only if the file's version of the record is taken." : "";
      Map<String, String> refs = REFERENCES.getOrDefault(candidate.table(), Map.of());
      for (Map.Entry<String, String> ref : refs.entrySet()) {
        checkReference(table, candidate.key(), row, row.text(ref.getKey()), ref.getValue(), ref.getKey(),
            condition);
      }
      for (Map.Entry<String, JoinTable> join : JOIN_TABLES.entrySet()) {
        if (!join.getValue().ownerTable().equals(candidate.table())) {
          continue;
        }
        for (SyncRow member : fileByKey.get(join.getKey()).values()) {
          if (candidate.key().equals(member.text(join.getValue().ownerColumn()))) {
            checkReference(table, candidate.key(), row, member.text(join.getValue().memberColumn()),
                join.getValue().memberTable(), join.getKey(), condition);
          }
        }
      }
    }
  }

  private void checkReference(SyncTable table, String key, SyncRow row, String referenced, String referencedTable,
      String via, String condition) {
    if (referenced == null) {
      return;
    }
    SyncRow target = merged.get(referencedTable).get(referenced);
    boolean missing = target == null;
    boolean tombstone = !missing && target.isDeleted() && !row.isDeleted();
    if (!missing && !tombstone) {
      return;
    }
    String what = label(table(referencedTable)).toLowerCase(Locale.ROOT);
    String reason = (missing
        ? "The record from the file refers to a " + what + " that exists on neither computer."
        : "The record from the file refers to a " + what + " (" + name(referencedTable, target)
            + ") that is deleted here, and the record itself is live.") + condition;
    List<Resolution> options = new ArrayList<>();
    options.add(new Resolution("skip-row", "Skip this record from the file"));
    if (tombstone) {
      options.add(new Resolution("revive", "Bring the deleted " + what + " back and take the record"));
    }
    collisions.add(new Collision(id(Type.C6, table, key) + "-" + via + "-" + referenced, Type.C6, table.name(),
        label(table),
        name(table.name(), row), reason, List.of(new FieldDiff(via, null, referenced)), null, side(row), options));
  }

  private boolean isPlanned(String table, String key) {
    return plan.stream().anyMatch(p -> p.table().equals(table) && p.key().equals(key));
  }

  // ---- wording --------------------------------------------------------------------------

  private static SyncTable table(String name) {
    return SyncTables.TABLES.stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow();
  }

  static String label(SyncTable table) {
    return TABLE_LABELS.getOrDefault(table.name(), table.name());
  }

  private static String id(Type type, SyncTable table, String key) {
    return type.name() + "-" + table.name() + "-" + key;
  }

  private TableSummary summary(SyncTable table, int inserts, int updates, int unchanged, int kept, int collided,
      String note) {
    return new TableSummary(table.name(), label(table), fileByKey.get(table.name()).size(),
        localByKey.get(table.name()).size(), inserts, updates, unchanged, kept, collided, note);
  }

  private Notice notice(SyncTable table, SyncRow row, String text) {
    return new Notice(table.name(), label(table), name(table.name(), row), text);
  }

  private Side side(SyncRow row) {
    Instant at = row.isDeleted() ? row.deletedAt() : row.updatedAt();
    return new Side(nodeName(row.originNode()), at == null ? null : SHOWN.format(at.atZone(in.zone())),
        row.isDeleted());
  }

  String nodeName(UUID origin) {
    if (origin == null) {
      return "an older version of the program";
    }
    if (origin.equals(in.self())) {
      return in.selfName();
    }
    if (in.fileNode() != null && origin.equals(in.fileNode().id())) {
      return in.fileNode().name();
    }
    return "node " + origin.toString().substring(0, 8);
  }

  private static List<FieldDiff> diffs(SyncRow local, SyncRow file) {
    List<FieldDiff> diffs = new ArrayList<>();
    Set<String> columns = new java.util.LinkedHashSet<>(file.values().keySet());
    columns.addAll(local.values().keySet());
    for (String column : columns) {
      if (AUDIT_COLUMNS.contains(column)) {
        continue;
      }
      String mine = local.text(column);
      String theirs = file.text(column);
      if (!Objects.equals(mine, theirs)) {
        diffs.add(new FieldDiff(column, shorten(mine), shorten(theirs)));
      }
    }
    return diffs;
  }

  private static String shorten(String value) {
    return value != null && value.length() > 120 ? value.substring(0, 117) + "..." : value;
  }

  /** The record as a person would name it; falls back to the key. */
  String name(String table, SyncRow row) {
    return switch (table) {
      case "participants" -> person(row, "participants_name", "participants_surname") + number(row, "participants_id");
      case "trainer", "lecturer", "technician" -> person(row, table + "_name", table + "_surname");
      case "course_type" -> "code " + nz(row.text("course_type_code"));
      case "course_counter" -> "counter " + nz(row.text("course_counter_counter"));
      case "courses" -> course(row);
      case "image" -> "photo " + nz(row.text("image_uuid"));
      case "student" -> person(row, "student_name", "student_last_name") + number(row, "student_id");
      case "instructor" -> person(row, "instructor_name", "instructor_surname") + number(row, "instructor_no");
      default -> row.key(table(table));
    };
  }

  private String course(SyncRow row) {
    SyncRow participant = merged.get("participants").get(row.text("courses_participant_id"));
    SyncRow type = merged.get("course_type").get(row.text("courses_course_type_uuid"));
    return "course " + nz(row.text("courses_id"))
        + (participant == null ? "" : " of " + person(participant, "participants_name", "participants_surname"))
        + (type == null ? "" : ", " + nz(type.text("course_type_code")));
  }

  private static String person(SyncRow row, String name, String surname) {
    String full = (nz(row.text(name)) + " " + nz(row.text(surname))).strip();
    return full.isEmpty() ? "(no name)" : full;
  }

  private static String number(SyncRow row, String column) {
    String value = row.text(column);
    return value == null ? "" : " (no. " + value + ")";
  }

  private static String nz(String value) {
    return value == null ? "" : value;
  }
}
