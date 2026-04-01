package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.repository.custom.ImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@RequiredArgsConstructor
@Service
public class ImageService {

  private final ImageRepository imageRepository;

  public Image getImageById(UUID id) {
    return imageRepository.findById(id).orElse(null);
  }

}
