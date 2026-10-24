package com.jaworski.serialprotocol.service.db.sync;

import com.jaworski.serialprotocol.service.db.custom.ThumbnailBackfill;
import org.slf4j.Logger;

import java.io.IOException;

/**
 * What an import or a restore does once its transaction has committed: prune the backups and
 * rebuild stale thumbnails. The data has changed by then, so a failure here is logged and the
 * operation still reports success; letting it propagate would tell the user the import failed
 * and invite a second one, or a restore, on top of rows that were written.
 */
final class SyncHousekeeping {

  private SyncHousekeeping() {
  }

  /** @return how many images carry a current thumbnail, or 0 when the rebuild failed */
  static int afterCommit(Logger log, String operation, SyncBackupService backups, ThumbnailBackfill thumbnails) {
    try {
      backups.prune();
    } catch (IOException | RuntimeException e) {
      log.warn("The backups could not be pruned after the {}; the data was written", operation, e);
    }
    try {
      return thumbnails.refreshStale();
    } catch (RuntimeException e) {
      log.warn("The thumbnails could not be rebuilt after the {}; the data was written", operation, e);
      return 0;
    }
  }
}
