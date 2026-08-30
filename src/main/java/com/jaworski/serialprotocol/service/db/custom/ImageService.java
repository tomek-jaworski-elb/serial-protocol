package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.repository.custom.ImageRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
@Transactional
public class ImageService {

  private static final Logger LOG = LoggerFactory.getLogger(ImageService.class);

  private final ImageRepository imageRepository;

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
