package com.jaworski.serialprotocol.service.db.sync;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What an import would do, before anything is written (docs/sync-spec.md § Analysis).
 *
 * @param tables     one summary per synchronised table, in file order
 * @param notices    findings that need no decision
 * @param collisions findings that block the apply until resolved
 * @param plan       the row writes the apply would make for the rows without a collision
 */
public record SyncAnalysis(
    List<TableSummary> tables,
    List<Notice> notices,
    List<Collision> collisions,
    List<RowPlan> plan) {

  public enum Action { INSERT, UPDATE }

  /**
   * @param note for tables the analysis does not decide row by row (join tables follow their
   *             owning record, sequences take the higher value); {@code null} otherwise
   */
  public record TableSummary(String table, String label, int fileRows, int localRows, int inserts, int updates,
      int unchanged, int kept, int collisions, String note) {
  }

  public record Notice(String table, String tableLabel, String record, String text) {
  }

  public record RowPlan(String table, String key, Action action, SyncRow fileRow) {
  }

  public boolean hasCollisions() {
    return !collisions.isEmpty();
  }

  public int inserts() {
    return (int) plan.stream().filter(p -> p.action() == Action.INSERT).count();
  }

  public int updates() {
    return (int) plan.stream().filter(p -> p.action() == Action.UPDATE).count();
  }

  /** Collisions grouped by table label, in table order; every type is blocking. */
  public Map<String, List<Collision>> collisionsByTable() {
    Map<String, List<Collision>> grouped = new LinkedHashMap<>();
    for (TableSummary table : tables) {
      List<Collision> own = collisions.stream().filter(c -> c.table().equals(table.table())).toList();
      if (!own.isEmpty()) {
        grouped.put(table.label(), own);
      }
    }
    return grouped;
  }
}
