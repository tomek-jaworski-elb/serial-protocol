package com.jaworski.serialprotocol.service.db.sync;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A sync file as {@link SyncReader} hands it to the analysis: the manifest, every table's rows
 * and the ids of the blobs and thumbnails it carries. The bytes of the blobs are not read
 * here; the apply streams them from the ZIP when it needs them.
 *
 * @param tables rows per table name, in file order; a table absent from the file is an empty list
 */
public record SyncFile(SyncManifest manifest, Map<String, List<SyncRow>> tables, Set<String> blobIds,
    Set<String> thumbIds) {

  public List<SyncRow> rows(String table) {
    return tables.getOrDefault(table, List.of());
  }
}
