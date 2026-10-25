package com.jaworski.serialprotocol.service.db.sync;

import com.jaworski.serialprotocol.service.db.sync.SyncAnalysis.Notice;
import com.jaworski.serialprotocol.service.db.sync.SyncAnalysis.TableSummary;

import java.util.List;

/**
 * What an applied import did, written as {@code <backup>.report.json} next to the backup
 * taken just before it (docs/sync-spec.md § Apply), so the decisions stay on record.
 *
 * @param file        the uploaded file's name
 * @param from        the node that wrote the file
 * @param importedAt  ISO-8601 UTC instant of the commit
 * @param backup      the backup file written before the import
 * @param inserted    rows added
 * @param updated     rows overwritten with the file's version
 * @param blobs       photos whose bytes were loaded from the file
 * @param tables      the per-table figures of the report the user confirmed
 * @param notices     the report's notices
 * @param collisions  every collision with the resolution the user chose
 * @param changes     the local rows the resolutions changed, in plain words
 */
public record SyncImportReport(
    String file,
    SyncManifest.Node from,
    String importedAt,
    String backup,
    int inserted,
    int updated,
    int blobs,
    List<TableSummary> tables,
    List<Notice> notices,
    List<ResolvedCollision> collisions,
    List<String> changes) {

  public record ResolvedCollision(String id, Collision.Type type, String table, String record, String explanation,
      String resolution) {
  }
}
