package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.repository.custom.ImageRepository;
import lombok.RequiredArgsConstructor;
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
    if (stored.getThumbData() != null && stored.getThumbData().length > 0) {
      return new ImageContent(stored.getThumbData(), stored.getThumbContentType());
    }

    Image image = imageRepository.findById(id).orElse(null);
    if (image == null || image.getData() == null || image.getData().length == 0) {
      return null;
    }

    byte[] scaled = ThumbnailGenerator.scale(image.getData(), image.getContentType());
    if (scaled == null) {
      return new ImageContent(image.getData(), image.getContentType());
    }

    String thumbType = ThumbnailGenerator.outputContentType(image.getContentType());
    image.setThumbData(scaled);
    image.setThumbContentType(thumbType);
    // Concurrent requests may both generate; the bytes are equivalent, so the second
    // write simply overwrites the first with the same result.
    imageRepository.save(image);
    return new ImageContent(scaled, thumbType);
  }
}
