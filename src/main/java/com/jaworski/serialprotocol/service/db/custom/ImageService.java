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
    return imageRepository.findAll().size();
  }

  public void delete(UUID id) {
    imageRepository.deleteById(id);
  }
}
