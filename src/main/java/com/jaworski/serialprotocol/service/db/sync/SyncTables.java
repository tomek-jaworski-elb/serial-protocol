package com.jaworski.serialprotocol.service.db.sync;

import java.util.List;

/**
 * The tables a sync file carries, in foreign-key order (docs/sync-spec.md § File format).
 *
 * <p>The exporter writes them in this order and the importer inserts in it and deletes in
 * reverse. {@code image_blob} is not in the list: its bytes travel as {@code blobs/} and
 * {@code thumbs/} entries, one file per row. {@code sync_node} and
 * {@code flyway_schema_history} are never exported. The key columns give every table a
 * stable row order, which is what lets {@code SyncFormatTest} compare an export line by line.</p>
 */
public final class SyncTables {

  /** One synchronised table and the columns that order its rows in the file. */
  public record SyncTable(String name, List<String> keyColumns) {
    public String orderBy() {
      return String.join(", ", keyColumns);
    }
  }

  public static final List<SyncTable> TABLES = List.of(
      new SyncTable("course_type", List.of("course_type_uuid")),
      new SyncTable("image", List.of("image_uuid")),
      new SyncTable("participants", List.of("participants_uuid")),
      new SyncTable("course_counter", List.of("course_counter_uuid")),
      new SyncTable("trainer", List.of("trainer_uuid")),
      new SyncTable("lecturer", List.of("lecturer_uuid")),
      new SyncTable("technician", List.of("technician_uuid")),
      new SyncTable("trainer_image", List.of("trainer_uuid", "image_uuid")),
      new SyncTable("lecturer_image", List.of("lecturer_uuid", "image_uuid")),
      new SyncTable("technician_image", List.of("technician_uuid", "image_uuid")),
      new SyncTable("courses", List.of("courses_uuid")),
      new SyncTable("courses_trainers", List.of("courses_uuid", "trainer_uuid")),
      new SyncTable("courses_lecturers", List.of("courses_uuid", "lecturer_uuid")),
      new SyncTable("courses_technicians", List.of("courses_uuid", "technician_uuid")),
      new SyncTable("student", List.of("student_id")),
      new SyncTable("instructor", List.of("instructor_no")),
      new SyncTable("id_sequence", List.of("id_sequence_name")));

  public static final String BLOB_TABLE = "image_blob";

  /** The ZIP entry names, fixed by the format. */
  public static final String MANIFEST_ENTRY = "manifest.json";
  public static final String TABLES_DIR = "tables/";
  public static final String BLOBS_DIR = "blobs/";
  public static final String THUMBS_DIR = "thumbs/";

  public static String tableEntry(String table) {
    return TABLES_DIR + table + ".jsonl";
  }

  private SyncTables() {
  }
}
