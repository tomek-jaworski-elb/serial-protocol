package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.LecturerDTO;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.entity.custom.Lecturer;
import com.jaworski.serialprotocol.mappers.custom.LecturerMapper;
import com.jaworski.serialprotocol.repository.custom.ImageRepository;
import com.jaworski.serialprotocol.repository.custom.LecturerRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LecturerService {

  private final ImageRepository imageRepository;
  private final LecturerRepository lecturerRepository;
  private static final Logger LOGGER = LoggerFactory.getLogger(LecturerService.class);

  @Transactional(readOnly = true)
  public List<LecturerDTO> findAll() {
    return lecturerRepository.findAll().stream()
            .map(LecturerMapper::mapToDTO)
            .toList();
  }

  public LecturerDTO findById(Long id) {
    return lecturerRepository.findById(id)
            .map(LecturerMapper::mapToDTO)
            .orElse(null);
  }

  public LecturerDTO save(LecturerDTO dto) {
    Lecturer lecturer = LecturerMapper.mapToEntity(dto);
    lecturer.setImages(resolveImages(dto.getImagesUuid()));
    Lecturer savedLecturer = lecturerRepository.save(lecturer);
    return LecturerMapper.mapToDTO(savedLecturer);
  }

  public void deleteById(Long id) {
    lecturerRepository.findById(id).ifPresent(lecturer -> {
      lecturer.getImages().forEach(image -> imageRepository.deleteById(image.getId()));
    });
    lecturerRepository.deleteById(id);
  }

  public LecturerDTO updateById(LecturerDTO dto) {
    if (dto.getLecturerId() == null) {
      throw new IllegalArgumentException("Lecturer id is required for update");
    }
    Lecturer lecturer = LecturerMapper.mapToEntity(dto);
    lecturer.setImages(resolveImages(dto.getImagesUuid()));
    Optional<Lecturer> byId = lecturerRepository.findById(dto.getLecturerId());
    if (byId.isPresent() && !byId.get().getImages().equals(lecturer.getImages())) {
      byId.get().getImages().forEach(image -> {
        imageRepository.deleteById(image.getId());
      });
    }
    Lecturer updatedLecturer = lecturerRepository.save(lecturer);
    return LecturerMapper.mapToDTO(updatedLecturer);
  }

  private Set<Image> resolveImages(Set<UUID> imageIds) {
    if (imageIds == null || imageIds.isEmpty()) {
      return new HashSet<>();
    }
    List<Image> images = imageRepository.findAllById(imageIds);
    if (images.size() != imageIds.size()) {
      throw new IllegalArgumentException("One or more image ids do not exist");
    }
    return new HashSet<>(images);
  }
}
