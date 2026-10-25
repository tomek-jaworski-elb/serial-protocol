package com.jaworski.serialprotocol.service.db.sync;

import java.nio.file.Path;

/**
 * What a restore did: the backup written first, how much the restored file carried, and how
 * many thumbnails were rebuilt afterwards.
 */
public record SyncRestoreResult(String restored, Path beforeRestore, int rows, int blobs, int thumbnailsRebuilt) {
}
