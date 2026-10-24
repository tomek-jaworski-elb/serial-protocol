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
import java.util.LinkedHashSet;
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

  /** How many photos a lecturer, trainer or technician may carry. */
  public static final int MAX_IMAGES_PER_PERSON = 6;

  /**
   * Raised when a request would push a record past its photo limit.
   *
   * <p>Its own type so the handlers can surface its message verbatim without also leaking
   * internal ones — {@code "Trainer with id <uuid> not found"} or {@code "One or more image
   * ids do not exist"} come out of the service layer as plain IllegalArgumentException and
   * are not written for the person reading the toast.</p>
   */
  public static class ImageLimitExceededException extends IllegalArgumentException {
    public ImageLimitExceededException(String message) {
      super(message);
    }
  }

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

  /**
   * Fails when {@code kept} photos plus {@code added} new ones would exceed the limit.
   *
   * <p>Clamped at zero on the kept side so a record that somehow holds more than the limit
   * (data from before the cap existed) can still have images removed — that is the only
   * way back under the limit. Adding to such a record stays blocked.</p>
   */
  public static void assertWithinLimit(int kept, int added) {
    int budget = Math.max(0, MAX_IMAGES_PER_PERSON - kept);
    if (added > budget) {
      throw new ImageLimitExceededException(
          "Maximum " + MAX_IMAGES_PER_PERSON + " images allowed (" + kept + " already in use)");
    }
  }

  @Transactional(readOnly = true)
  public Image getImageById(UUID id) {
    return imageRepository.findById(id).orElse(null);
  }

  /**
   * Stores a photo together with its thumbnail.
   *
   * <p>The thumbnail is produced here, at upload, so that serving one is a pure read. It
   * used to be generated on the first GET and written back then, which made a GET a write
   * and, under replication, would have every node emit change traffic for derived data.</p>
   */
  public Image saveImage(byte[] data, String contentType) {
    Image image = new Image();
    image.setData(data);
    image.setContentType(contentType);
    applyThumbnail(image);
    return imageRepository.save(image);
  }

  /** {@link #saveImage} for every upload of a request; ids in upload order. */
  public Set<UUID> saveImages(List<UploadedImage> uploads) {
    Set<UUID> ids = new LinkedHashSet<>();
    for (UploadedImage upload : uploads) {
      ids.add(saveImage(upload.data(), upload.contentType()).getId());
    }
    return ids;
  }

  @Transactional(readOnly = true)
  public int getAllImages() {
    return (int) imageRepository.count();
  }

  public void delete(UUID id) {
    imageRepository.deleteById(id);
  }

  /** Primary-key lookup only — deliberately does not touch the blob columns. */
  @Transactional(readOnly = true)
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
   * Returns the downscaled copy of an image, or the original whenever there is none.
   *
   * <p>A pure read: the thumbnail was stored at upload. A row whose thumbnail predates the
   * current {@link ThumbnailGenerator#VERSION} (or was never produced, for rows older than
   * the column) is scaled for this response only and left as it is; rebuilding and storing
   * it is {@link ThumbnailBackfill}'s job. Nothing in this method ever writes, so serving an
   * image cannot fail on a write and never generates change traffic.</p>
   *
   * <p>Falls back to the original whenever a thumbnail cannot or need not be made:
   * an unreadable format (webp, svg), an image already smaller than the target, or a
   * generation failure. A thumbnail is a convenience, never a reason to fail a request.</p>
   */
  @Transactional(readOnly = true)
  public ImageContent getThumbnail(UUID id) {
    ImageRepository.ThumbnailView stored = imageRepository.findThumbnailById(id).orElse(null);
    if (stored == null) {
      return null;
    }
    if (ThumbnailGenerator.VERSION.equals(stored.getThumbVersion())) {
      // A current verdict: either real thumbnail bytes, or "cannot be done", in which case
      // the original is the answer and is read below.
      if (stored.getThumbData() != null && stored.getThumbData().length > 0) {
        return new ImageContent(stored.getThumbData(), stored.getThumbContentType());
      }
    }

    Image image = imageRepository.findById(id).orElse(null);
    if (image == null || image.getData() == null || image.getData().length == 0) {
      return null;
    }
    if (ThumbnailGenerator.VERSION.equals(image.getThumbVersion())) {
      return new ImageContent(image.getData(), image.getContentType());
    }

    // Stale or absent: serve a freshly scaled copy without recording it.
    byte[] scaled = scaleQuietly(image);
    return scaled == null
        ? new ImageContent(image.getData(), image.getContentType())
        : new ImageContent(scaled, ThumbnailGenerator.outputContentType(image.getContentType()));
  }

  /**
   * Rebuilds the stored thumbnail of one image against the current generator version.
   *
   * @return whether the row now carries a current thumbnail state (a transient failure
   *     leaves it untouched so a later run can retry)
   */
  public boolean refreshThumbnail(UUID id) {
    Image image = imageRepository.findById(id).orElse(null);
    if (image == null) {
      return false;
    }
    boolean recorded = applyThumbnail(image);
    if (recorded) {
      imageRepository.save(image);
    }
    return recorded;
  }

  /** Ids of images whose thumbnail state was produced by another generator version, or never. */
  @Transactional(readOnly = true)
  public List<UUID> findStaleThumbnailIds() {
    return imageRepository.findIdsWithStaleThumbnail(ThumbnailGenerator.VERSION);
  }

  /**
   * Fills the thumbnail columns from the image's own bytes.
   *
   * <p>A {@code null} from the generator is a permanent verdict — unreadable format, bytes
   * that do not match their content type, an image already under the target size — and is
   * recorded as the current version with no data, so nobody retries a conversion known to
   * fail. Recording the version and not a copy of the original matters: duplicating a 10 MB
   * blob to avoid a cheap failed read would be a poor trade. An unexpected failure is not a
   * verdict about the image and leaves the columns alone so the backfill retries it.</p>
   *
   * @return whether a verdict was recorded
   */
  private boolean applyThumbnail(Image image) {
    byte[] scaled;
    try {
      scaled = ThumbnailGenerator.scale(image.getData(), image.getContentType());
    } catch (ThumbnailGenerator.ThumbnailFailedException e) {
      LOG.warn("Thumbnail generation failed for image {}, leaving it unrecorded", image.getId());
      return false;
    }
    image.setThumbData(scaled);
    image.setThumbContentType(scaled == null ? null : ThumbnailGenerator.outputContentType(image.getContentType()));
    image.setThumbVersion(ThumbnailGenerator.VERSION);
    return true;
  }

  private static byte[] scaleQuietly(Image image) {
    try {
      return ThumbnailGenerator.scale(image.getData(), image.getContentType());
    } catch (ThumbnailGenerator.ThumbnailFailedException e) {
      LOG.warn("Thumbnail generation failed for image {}, serving the original", image.getId());
      return null;
    }
  }
}
