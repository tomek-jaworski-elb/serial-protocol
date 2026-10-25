package com.jaworski.serialprotocol.service.db.sync;

import java.nio.file.Path;

/** An applied import: the report, where it was written, and the backup taken before it. */
public record SyncImportResult(SyncImportReport report, Path backup, Path reportFile, int thumbnailsRebuilt) {
}
