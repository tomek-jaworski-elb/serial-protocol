package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.service.db.custom.ImageService;
import com.jaworski.serialprotocol.service.db.custom.UploadedImage;
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
 */
final class MultipartImages {

  private MultipartImages() {
  }

  /** Every non-empty part, in request order; a missing parameter means no uploads. */
  static List<UploadedImage> read(MultipartFile[] files) {
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
  static UploadedImage read(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      return null;
    }
    try {
      return new UploadedImage(file.getBytes(), ImageService.sanitizeContentType(file.getContentType()));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read uploaded photo", e);
    }
  }
}
