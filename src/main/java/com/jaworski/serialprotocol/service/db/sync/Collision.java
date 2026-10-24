package com.jaworski.serialprotocol.service.db.sync;

import java.util.List;

/**
 * One blocking finding of the analysis (docs/sync-spec.md § The report), worded for the
 * person who has to decide, with the resolutions the apply understands.
 *
 * @param id          stable across re-analyses of the same file: {@code type-table-key}; the
 *                    form posts the chosen resolution under this name
 * @param type        C1–C6
 * @param table       the table name
 * @param tableLabel  the table in the user's words
 * @param record      the record as a person would name it
 * @param explanation one sentence on what went wrong
 * @param fields      the columns that differ, when both sides have the row
 * @param local       this computer's side, {@code null} when it has no row
 * @param file        the file's side, {@code null} when it has no row
 * @param resolutions what the user may choose, in display order
 */
public record Collision(
    String id,
    Type type,
    String table,
    String tableLabel,
    String record,
    String explanation,
    List<FieldDiff> fields,
    Side local,
    Side file,
    List<Resolution> resolutions) {

  public enum Type {
    C1("created on a read-only computer"),
    C2("edited on both computers"),
    C3("deleted on one, edited on the other"),
    C4("number used twice"),
    C5("course type code used twice"),
    C6("dangling reference");

    private final String title;

    Type(String title) {
      this.title = title;
    }

    public String title() {
      return title;
    }
  }

  /**
   * One side of a collision: who last touched the row and when, in the words the page shows
   * ("changed on biuro-2 on 24/10/2026 15:02", "deleted on biuro-1 on ...").
   */
  public record Side(String nodeName, String at, boolean deleted) {
    public String describe() {
      return (deleted ? "deleted on " : "changed on ") + nodeName + (at == null ? "" : " on " + at);
    }
  }

  public record FieldDiff(String field, String localValue, String fileValue) {
  }

  /** @param code what the form posts; @param label what the radio button says */
  public record Resolution(String code, String label) {
  }
}
