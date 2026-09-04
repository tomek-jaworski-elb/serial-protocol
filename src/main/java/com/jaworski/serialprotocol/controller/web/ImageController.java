package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.service.db.custom.ImageService;
import com.jaworski.serialprotocol.service.db.custom.ThumbnailGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Serves stored images (and their thumbnails), split out of {@link CustomDBController}
 * because it is a self-contained concern — caching and thumbnail selection — unrelated
 * to any single CRUD domain that controller handles.
 */
@RequiredArgsConstructor
@Controller
public class ImageController {

  private final ImageService imageService;

  private static final String THUMB_VARIANT = "thumb";

  /**
   * Revalidate every time rather than trusting a copy for N minutes.
   *
   * <p>These are photos of people and deleting one has to mean it is gone. With
   * max-age the browser keeps serving a deleted photo from disk until the age
   * expires — the server answers 404 while the cache still hands out the image.
   * "no-cache" still caches; it just asks first, and an unchanged image comes back
   * as an empty 304, so nearly all of the bandwidth saving remains.</p>
   */
  private static final String IMAGE_CACHE_CONTROL = "private, no-cache";

  /**
   * Serves a stored image, optionally downscaled via {@code ?size=thumb}.
   *
   * <p>Caching note that looks wrong until you check it: Spring Security's
   * CacheControlHeadersWriter would normally stamp {@code no-store} on every response,
   * but it skips whenever Cache-Control is already set, and skips 304s outright. The
   * header set here therefore survives.</p>
   */
  @GetMapping("/custom/image/{uuid}")
  public ResponseEntity<byte[]> imageByUuid(
      @PathVariable UUID uuid,
      @RequestParam(required = false) String size,
      @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

    // Only one variant is offered. Accepting arbitrary values would let a single url
    // spawn unbounded resize work.
    boolean wantsThumb = THUMB_VARIANT.equals(size);
    if (size != null && !wantsThumb) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported size: " + size);
    }

    // The bytes behind a uuid never change — replacing a photo creates a new Image row —
    // so the tag needs only the uuid, the variant and the generator version.
    // The generator version belongs to the thumbnail only. Including it in the tag for the
    // original would make a thumbnail-size tweak invalidate every cached full-size photo,
    // whose bytes did not change — every details modal would refetch for nothing.
    String etag = wantsThumb
        ? "\"" + uuid + "-" + THUMB_VARIANT + "-" + ThumbnailGenerator.VERSION + "\""
        : "\"" + uuid + "-orig\"";

    // Answered without reading the blob or generating a thumbnail — but existence is
    // still checked, and that check is not optional. The tag is derived from the uuid
    // alone, so a purely tag-based 304 would keep telling browsers "unchanged" about
    // a photo that has since been deleted, and they would go on serving it from cache
    // forever. existsById is a primary-key lookup, so the saving is preserved.
    if (ifNoneMatch != null && matchesEtag(ifNoneMatch, etag)) {
      if (!imageService.exists(uuid)) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
      }
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
          .eTag(etag)
          .header(HttpHeaders.CACHE_CONTROL, IMAGE_CACHE_CONTROL)
          .build();
    }

    byte[] data;
    String contentType;
    if (wantsThumb) {
      ImageService.ImageContent thumbnail = imageService.getThumbnail(uuid);
      if (thumbnail == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
      }
      data = thumbnail.data();
      contentType = thumbnail.contentType();
    } else {
      Image image = imageService.getImageById(uuid);
      if (image == null || image.getData() == null || image.getData().length == 0) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
      }
      data = image.getData();
      contentType = image.getContentType();
    }

    MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
    if (contentType != null && !contentType.isBlank()) {
      mediaType = MediaType.parseMediaType(ImageService.sanitizeContentType(contentType));
    }

    return ResponseEntity.ok()
        .contentType(mediaType)
        .eTag(etag)
        .header(HttpHeaders.CACHE_CONTROL, IMAGE_CACHE_CONTROL)
        // Ignored by <img>, but it stops an uploaded SVG being opened as a top-level
        // document, which is the XSS vector for image/svg+xml. Do not remove.
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
        .body(data);
  }

  private static boolean matchesEtag(String ifNoneMatch, String etag) {
    for (String candidate : ifNoneMatch.split(",")) {
      String trimmed = candidate.trim();
      if ("*".equals(trimmed) || trimmed.equals(etag) || trimmed.equals("W/" + etag)) {
        return true;
      }
    }
    return false;
  }
}
