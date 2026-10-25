package com.jaworski.serialprotocol.service.db.sync;

import java.util.Map;
import java.util.UUID;

/**
 * {@code manifest.json} of a sync or backup file (docs/sync-spec.md § File format).
 *
 * @param format        the file format number; a reader keeps accepting older ones
 * @param schemaVersion the highest applied Flyway version, {@code null} where Flyway is off (H2)
 * @param appVersion    {@code application.version} of the exporter
 * @param node          the exporting installation
 * @param exportedAt    ISO-8601 UTC instant of the export
 * @param mode          the exporter's {@code sync.mode} at export time, informational
 * @param backup        {@code true} in a {@code .bak} written by the application itself
 * @param rows          row count per exported table, in file order
 * @param blobs         how many {@code image_blob} rows travel as files
 */
public record SyncManifest(
    int format,
    String schemaVersion,
    String appVersion,
    Node node,
    String exportedAt,
    String mode,
    boolean backup,
    Map<String, Long> rows,
    long blobs) {

  public static final int CURRENT_FORMAT = 1;

  public record Node(UUID id, String name) {
  }
}
