package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.PrimaryImage;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.repository.custom.ImageRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
@Transactional
public class ImageService {

  private static final Logger LOG = LoggerFactory.getLogger(ImageService.class);

  private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
      "image/jpeg", "image/png", "image/gif", "image/webp", "image/svg+xml"
  );

  private final ImageRepository imageRepository;

  /**
   * Normalizes an uploaded or stored content type to one of the allowed image types,
   * or {@code application/octet-stream} otherwise.
   *
   * <p>Shared by the upload path (what a new photo is allowed to claim to be) and the
   * serving path (defensive re-check of what is already stored) — both need the same
   * allowlist, or a value written under one rule could be served under a looser one.</p>
   */
  public static String sanitizeContentType(String rawContentType) {
    if (rawContentType == null) {
      return "application/octet-stream";
    }
    String normalized = rawContentType.trim().toLowerCase();
    // strip parameters (e.g. "image/jpeg; charset=utf-8")
    int semicolon = normalized.indexOf(';');
    String base = semicolon >= 0 ? normalized.substring(0, semicolon).trim() : normalized;
    return ALLOWED_IMAGE_TYPES.contains(base) ? base : "application/octet-stream";
  }

  public Image getImageById(UUID id) {
    return imageRepository.findById(id).orElse(null);
  }

  public Image saveImage(byte[] data, String contentType) {
    Image image = new Image();
    image.setData(data);
    image.setContentType(contentType);
    return imageRepository.save(image);
  }

  public Set<UUID> saveAllImages(List<Image> images) {
    return imageRepository.saveAll(images).stream()
        .map(Image::getId)
        .collect(Collectors.toSet());
  }

  public int getAllImages() {
    return (int) imageRepository.count();
  }

  public void delete(UUID id) {
    imageRepository.deleteById(id);
  }

  /** Primary-key lookup only — deliberately does not touch the blob columns. */
  public boolean exists(UUID id) {
    return imageRepository.existsById(id);
  }

  /** Bytes plus the content type they should be served with. */
  public record ImageContent(byte[] data, String contentType) {
  }

  /**
   * Fetches images by id, validating every id exists.
   *
   * <p>Shared by {@code LecturerService}/{@code TrainerService}/{@code TechnicianService}
   * when resolving the image set a create/update request asked for.</p>
   *
   * @throws IllegalArgumentException if any id has no matching image
   */
  public Set<Image> resolveImages(Set<UUID> imageIds) {
    if (imageIds == null || imageIds.isEmpty()) {
      return new HashSet<>();
    }
    List<Image> images = imageRepository.findAllById(imageIds);
    if (images.size() != imageIds.size()) {
      throw new IllegalArgumentException("One or more image ids do not exist");
    }
    return new HashSet<>(images);
  }

  /** Primary-image pointer for a person being created for the first time. */
  public UUID resolveNewPrimaryImage(UUID requestedPrimaryImage, Set<UUID> imageIds) {
    return PrimaryImage.resolve(requestedPrimaryImage, null, Set.of(), imageIds);
  }

  /**
   * Primary-image pointer for a person whose image set just changed.
   *
   * <p>Resolved from the before/after {@link Image} sets rather than raw ids so callers
   * can pass what they already have on hand after {@link #resolveImages}.</p>
   */
  public UUID resolveUpdatedPrimaryImage(UUID requestedPrimaryImage, UUID storedPrimaryImage,
      Set<Image> previousImages, Set<Image> requestedImages) {
    return PrimaryImage.resolve(requestedPrimaryImage, storedPrimaryImage,
        toIds(previousImages), toIds(requestedImages));
  }

  /** Deletes images that were dropped from the set (present before the update, absent after). */
  public void deleteRemovedImages(Set<Image> previousImages, Set<Image> requestedImages) {
    Set<Image> imagesToDelete = previousImages.stream()
        .filter(image -> !requestedImages.contains(image))
        .collect(Collectors.toSet());
    if (!imagesToDelete.isEmpty()) {
      imageRepository.deleteAll(imagesToDelete);
    }
  }

  private static Set<UUID> toIds(Set<Image> images) {
    return images.stream().map(Image::getId).collect(Collectors.toSet());
  }

  /**
   * Returns the downscaled copy of an image, generating and storing it on first use.
   *
   * <p>Falls back to the original whenever a thumbnail cannot or need not be made:
   * an unreadable format (webp, svg), an image already smaller than the target, or a
   * generation failure. A thumbnail is a convenience, never a reason to fail a
   * request, so this never throws for image-processing reasons.</p>
   */
  public ImageContent getThumbnail(UUID id) {
    ImageRepository.ThumbnailView stored = imageRepository.findThumbnailById(id).orElse(null);
    if (stored == null) {
      return null;
    }
    // Only a state produced by the CURRENT generator can be reused. Anything older is
    // rebuilt, which is what makes bumping ThumbnailGenerator.VERSION mean something:
    // without this the bump would change every ETag, make every client refetch, and
    // return the identical old bytes.
    boolean current = ThumbnailGenerator.VERSION.equals(stored.getThumbVersion());
    if (current && stored.getThumbData() != null && stored.getThumbData().length > 0) {
      return new ImageContent(stored.getThumbData(), stored.getThumbContentType());
    }

    Image image = imageRepository.findById(id).orElse(null);
    if (image == null || image.getData() == null || image.getData().length == 0) {
      return null;
    }

    // A current version with no data records "tried, and it cannot be done" — webp and
    // svg have no ImageIO reader, and an image already under the target size gains
    // nothing. Without it every request would re-run a conversion known to fail.
    byte[] scaled = null;
    boolean verdict = current;   // whether the outcome is worth recording
    if (!current) {
      try {
        scaled = ThumbnailGenerator.scale(image.getData(), image.getContentType());
        verdict = true;
      } catch (ThumbnailGenerator.ThumbnailFailedException e) {
        // Serve the original this time and try again next time. Recording the version here
        // would pin a perfectly good photo to the full-size path until someone bumps VERSION,
        // which rebuilds every thumbnail in the table.
        LOG.warn("Thumbnail generation failed for image {}, leaving it unrecorded", id);
      }
    }
    if (scaled == null) {
      if (!current && verdict) {
        // Record the attempt, not a copy of the original: duplicating a 10 MB blob to
        // avoid a cheap failed read would be a poor trade.
        image.setThumbData(null);
        image.setThumbContentType(null);
        image.setThumbVersion(ThumbnailGenerator.VERSION);
        imageRepository.save(image);
      }
      return new ImageContent(image.getData(), image.getContentType());
    }

    String thumbType = ThumbnailGenerator.outputContentType(image.getContentType());
    image.setThumbData(scaled);
    image.setThumbContentType(thumbType);
    image.setThumbVersion(ThumbnailGenerator.VERSION);
    // Concurrent requests may both generate; the bytes are equivalent, so the second
    // write simply overwrites the first with the same result.
    imageRepository.save(image);
    return new ImageContent(scaled, thumbType);
  }
}
