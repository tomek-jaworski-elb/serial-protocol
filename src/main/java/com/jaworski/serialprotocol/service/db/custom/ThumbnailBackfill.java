package com.jaworski.serialprotocol.service.db.custom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Rebuilds thumbnails that predate the current {@link ThumbnailGenerator#VERSION}.
 *
 * <p>Thumbnails are produced at upload and serving them never writes, so a version bump,
 * or rows uploaded before thumbnails existed, need a deliberate pass to catch up. This runs
 * once after start-up when {@code images.thumbnail.backfill-on-start} is {@code true}
 * (the default) and costs one id query when nothing is stale. Each image is refreshed in
 * its own transaction so a single bad row cannot roll back the rest, and a transient
 * failure is simply retried on the next start.</p>
 */
@Component
public class ThumbnailBackfill {

  private static final Logger LOG = LoggerFactory.getLogger(ThumbnailBackfill.class);

  private final ImageService imageService;
  private final boolean onStart;

  public ThumbnailBackfill(ImageService imageService,
      @org.springframework.beans.factory.annotation.Value("${images.thumbnail.backfill-on-start:true}") boolean onStart) {
    this.imageService = imageService;
    this.onStart = onStart;
  }

  /**
   * Off the start-up thread: SpringApplication.run() waits for this listener, and the serial
   * ports are opened only after it returns, so a long rebuild would delay frame ingestion.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    if (onStart) {
      Thread thread = new Thread(this::refreshStale, "thumbnail-backfill");
      thread.setDaemon(true);
      thread.start();
    }
  }

  /** @return how many images now carry a current thumbnail state */
  public int refreshStale() {
    List<UUID> stale = imageService.findStaleThumbnailIds();
    if (stale.isEmpty()) {
      return 0;
    }
    LOG.info("Rebuilding {} thumbnail(s) for generator version {}", stale.size(), ThumbnailGenerator.VERSION);
    int done = 0;
    for (UUID id : stale) {
      try {
        if (imageService.refreshThumbnail(id)) {
          done++;
        }
      } catch (RuntimeException e) {
        // One bad row (a blob missing after a partial migration, say) must not stop the rest.
        LOG.warn("Thumbnail rebuild failed for image {}", id, e);
      }
    }
    LOG.info("Thumbnail backfill finished: {} of {} rebuilt", done, stale.size());
    return done;
  }
}
