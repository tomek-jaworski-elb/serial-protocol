package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.LecturerDTO;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.entity.custom.Lecturer;
import com.jaworski.serialprotocol.mappers.custom.LecturerMapper;
import com.jaworski.serialprotocol.repository.custom.CoursesRepository;
import com.jaworski.serialprotocol.repository.custom.ImageRepository;
import com.jaworski.serialprotocol.repository.custom.LecturerRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class LecturerService {

  private final ImageRepository imageRepository;
  private final LecturerRepository lecturerRepository;
  private final CoursesRepository coursesRepository;
  private static final Logger LOGGER = LoggerFactory.getLogger(LecturerService.class);

  @Transactional(readOnly = true)
  public List<LecturerDTO> findAll() {
    return lecturerRepository.findAll().stream()
            .map(LecturerMapper::mapToDTO)
            .toList();
  }

  public LecturerDTO findById(UUID id) {
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

  public void deleteById(UUID id) {
    if (coursesRepository.existsByLecturers_Uuid(id)) {
      throw new IllegalStateException("Cannot delete lecturer with id " + id + " because it is referenced by existing courses.");
    }
    lecturerRepository.findById(id).ifPresent(lecturer -> {
      if (!lecturer.getImages().isEmpty()) {
        imageRepository.deleteAll(lecturer.getImages());
      }
    });
    lecturerRepository.deleteById(id);
  }

  public LecturerDTO updateById(LecturerDTO dto) {
    if (dto.getLecturerId() == null) {
      throw new IllegalArgumentException("Lecturer id is required for update");
    }
    Lecturer existingLecturer = lecturerRepository.findById(dto.getLecturerId())
        .orElseThrow(() -> new IllegalArgumentException("Lecturer with id " + dto.getLecturerId() + " not found"));

    Set<Image> previousImages = new HashSet<>(existingLecturer.getImages());
    Set<Image> requestedImages = resolveImages(dto.getImagesUuid());

    existingLecturer.setName(dto.getName());
    existingLecturer.setSurname(dto.getSurname());
    existingLecturer.setEmail(dto.getEmail());
    existingLecturer.setNickname(dto.getNickname());
    existingLecturer.setImages(requestedImages);

    Lecturer updatedLecturer = lecturerRepository.save(existingLecturer);

    Set<Image> imagesToDelete = previousImages.stream()
        .filter(image -> !requestedImages.contains(image))
        .collect(java.util.stream.Collectors.toSet());
    if (!imagesToDelete.isEmpty()) {
      imageRepository.deleteAll(imagesToDelete);
    }
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
