package com.jaworski.serialprotocol.service.db.sync;

/** What the report page shows: the staged file, its manifest and the analysis. */
public record SyncImportPreview(String token, String fileName, long size, SyncManifest manifest, SyncAnalysis analysis) {
}
