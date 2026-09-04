package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.service.db.custom.ImageService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Uploads photos submitted with a person/course-counter form and reconciles them with
 * an entity's existing image set, cleaning up orphaned rows when a later step fails.
 *
 * <p>Split out of {@link CustomDBController} because every domain handler there (trainer,
 * lecturer, technician, participant, course counter) needed the same multipart-upload and
 * rollback machinery — it was copied, not reused. This is that one implementation.</p>
 */
@RequiredArgsConstructor
@Component
public class ImageUploadCoordinator {

  private static final Logger LOG = LoggerFactory.getLogger(ImageUploadCoordinator.class);

  public static final int MAX_UPLOAD_IMAGES = 6;

  private final ImageService imageService;

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

  /** The set the entity should end up with, plus the rows this request created. */
  public record MergedImages(Set<UUID> merged, Set<UUID> uploaded) {
  }

  /**
   * Builds the image set an update should end up with: what survives the user's
   * removals, plus whatever was newly uploaded.
   *
   * <p>The request carries the images to <em>remove</em>, never the ones to keep.
   * That direction matters. The edit form builds its thumbnails in JavaScript, so
   * a "keep these" list would turn any rendering failure into data loss — an empty
   * editor would submit an empty keep-list and wipe every photo. With removals, a
   * broken editor simply removes nothing. It also makes a missing parameter mean
   * "leave the images alone", which is exactly how the form behaved before.</p>
   *
   * <p>Set difference already ignores ids that are not in {@code existing}, so a
   * forged uuid is a no-op: the client can only ever subtract from the entity's
   * own set, never attach someone else's photo.</p>
   */
  public MergedImages mergeImages(Set<UUID> existing, List<UUID> removeUuids, MultipartFile[] imageFiles) {
    Set<UUID> kept = existing == null ? new HashSet<>() : new HashSet<>(existing);
    if (removeUuids != null) {
      kept.removeAll(removeUuids);
    }

    // Clamped at 0 so a record that somehow holds more than the limit (data from
    // before the cap existed) can still have images removed — that is the only
    // way back under the limit. Adding to such a record stays blocked.
    int budget = Math.max(0, MAX_UPLOAD_IMAGES - kept.size());

    // Checked before uploadImages() runs, because that method persists each image
    // through ImageService, which commits on its own. Controllers here are not
    // transactional, so throwing after the upload would leave orphaned rows in the
    // image table that nothing ever cleans up.
    if (usableFiles(imageFiles).size() > budget) {
      throw new ImageLimitExceededException(
          "Maximum " + MAX_UPLOAD_IMAGES + " images allowed (" + kept.size() + " already in use)");
    }

    Set<UUID> uploaded = uploadImages(imageFiles, budget);
    Set<UUID> merged = new HashSet<>(kept);
    merged.addAll(uploaded);
    return new MergedImages(merged, uploaded);
  }

  /**
   * Runs the persist step and removes anything this request had just uploaded if it fails.
   *
   * <p>Every upload path needs it, not only the ones that merge image sets: the upload commits
   * through ImageService in its own transaction while the controller is not transactional, so
   * any later failure — a duplicate participant id, a name over its length limit — would leave
   * the blob in the table with nothing pointing at it.</p>
   */
  public void persistOrDiscard(Set<UUID> uploaded, Runnable persist) {
    try {
      persist.run();
    } catch (RuntimeException e) {
      discardUploaded(uploaded);
      throw e;
    }
  }

  private void discardUploaded(Set<UUID> uploaded) {
    for (UUID id : uploaded) {
      try {
        imageService.delete(id);
      } catch (RuntimeException e) {
        LOG.warn("Could not discard orphaned upload. uuid={}", id, e);
      }
    }
  }

  private static List<MultipartFile> usableFiles(MultipartFile[] files) {
    if (files == null || files.length == 0) {
      return List.of();
    }
    return Arrays.stream(files)
        .filter(Objects::nonNull)
        .filter(f -> !f.isEmpty())
        .toList();
  }

  public Set<UUID> uploadImages(MultipartFile[] files, int maxFiles) {
    List<MultipartFile> nonEmpty = usableFiles(files);
    if (nonEmpty.isEmpty()) {
      return new HashSet<>();
    }

    if (nonEmpty.size() > maxFiles) {
      throw new IllegalArgumentException("Maximum " + maxFiles + " images allowed");
    }

    List<Image> images = nonEmpty.stream().map(file -> {
      try {
        Image image = new Image();
        image.setData(file.getBytes());
        image.setContentType(ImageService.sanitizeContentType(file.getContentType()));
        return image;
      } catch (IOException e) {
        throw new UncheckedIOException("Failed to read uploaded photo", e);
      }
    }).toList();
    return imageService.saveAllImages(images);
  }

  /** The single-image counterpart of MergedImages.uploaded(): empty when nothing was uploaded. */
  public static Set<UUID> justUploaded(UUID uploaded) {
    return uploaded == null ? Set.of() : Set.of(uploaded);
  }

  public UUID uploadSingleImage(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      return null;
    }
    try {
      Image image = imageService.saveImage(
          file.getBytes(),
          ImageService.sanitizeContentType(file.getContentType())
      );
      return image.getId();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read uploaded photo", e);
    }
  }
}
