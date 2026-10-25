package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.service.db.custom.ImageService;
import com.jaworski.serialprotocol.service.db.custom.UploadedImage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns the multipart parts of a form post into {@link UploadedImage}s.
 *
 * <p>This is all the web layer does with a photo. Nothing is persisted here: the services
 * store the bytes in the same transaction as the record they belong to, so a save that fails
 * for any reason — a duplicate business id, a name over its column length, a record deleted
 * meanwhile — leaves no orphaned blob behind and needs no compensating delete.</p>
 *
 * <p>The per-photo size cap lives here ({@code images.max-upload-size}, default 10 MB) and
 * not in the servlet multipart limit, which is sized for a sync file (docs/sync-spec.md).
 * An oversized part raises the same {@link MaxUploadSizeExceededException} the container used
 * to, so {@link WebExceptionHandler} keeps showing the same toast.</p>
 */
@Component
public class MultipartImages {

  private final DataSize maxUploadSize;

  public MultipartImages(@Value("${images.max-upload-size:10MB}") DataSize maxUploadSize) {
    this.maxUploadSize = maxUploadSize;
  }

  public DataSize maxUploadSize() {
    return maxUploadSize;
  }

  /** Every non-empty part, in request order; a missing parameter means no uploads. */
  public List<UploadedImage> read(MultipartFile[] files) {
    List<UploadedImage> uploads = new ArrayList<>();
    if (files == null) {
      return uploads;
    }
    for (MultipartFile file : files) {
      UploadedImage upload = read(file);
      if (upload != null) {
        uploads.add(upload);
      }
    }
    return uploads;
  }

  /** The single part, or {@code null} when none was sent. */
  public UploadedImage read(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      return null;
    }
    if (file.getSize() > maxUploadSize.toBytes()) {
      throw new MaxUploadSizeExceededException(maxUploadSize.toBytes());
    }
    try {
      return new UploadedImage(file.getBytes(), ImageService.sanitizeContentType(file.getContentType()));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read uploaded photo", e);
    }
  }
}
