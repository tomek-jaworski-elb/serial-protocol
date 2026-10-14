package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.LecturerDTO;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.entity.custom.Lecturer;
import com.jaworski.serialprotocol.mappers.custom.LecturerMapper;
import com.jaworski.serialprotocol.repository.custom.CoursesRepository;
import com.jaworski.serialprotocol.repository.custom.LecturerRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class LecturerService {

  private final ImageService imageService;
  private final LecturerRepository lecturerRepository;
  private final CoursesRepository coursesRepository;
  private final SoftDeleteSupport softDeleteSupport;
  private static final Logger LOGGER = LoggerFactory.getLogger(LecturerService.class);

  @Transactional(readOnly = true)
  public List<LecturerDTO> findAll() {
    return lecturerRepository.findAll().stream()
            .map(LecturerMapper::mapToDTO)
            .toList();
  }

  @Transactional(readOnly = true)
  public Page<LecturerDTO> findAll(Pageable pageable) {
    return lecturerRepository.findAll(pageable).map(LecturerMapper::mapToDTO);
  }

  public LecturerDTO findById(UUID id) {
    return lecturerRepository.findById(id)
            .map(LecturerMapper::mapToDTO)
            .orElse(null);
  }

  public LecturerDTO save(LecturerDTO dto) {
    Lecturer lecturer = LecturerMapper.mapToEntity(dto);
    lecturer.setImages(imageService.resolveImages(dto.getImagesUuid()));
    // A new record gets a pointer too, and a value arriving from the form is validated
    // here rather than trusted — the add endpoint binds the whole DTO.
    lecturer.setPrimaryImageUuid(
        imageService.resolveNewPrimaryImage(dto.getPrimaryImageUuid(), dto.getImagesUuid()));
    Lecturer savedLecturer = lecturerRepository.save(lecturer);
    return LecturerMapper.mapToDTO(savedLecturer);
  }

  public void deleteById(UUID id) {
    if (coursesRepository.existsByLecturers_Uuid(id)) {
      throw new IllegalStateException("This lecturer is used in existing courses and cannot be deleted.");
    }
    // Marked, not removed: the photos stay with the row so an administrator can restore it.
    lecturerRepository.findById(id).ifPresent(lecturer -> {
      softDeleteSupport.softDelete(lecturer, lecturerRepository);
    });
  }

  public LecturerDTO updateById(LecturerDTO dto) {
    if (dto.getId() == null) {
      throw new IllegalArgumentException("Lecturer id is required for update");
    }
    Lecturer existingLecturer = lecturerRepository.findById(dto.getId())
        .orElseThrow(() -> softDeleteSupport.notFound("Lecturer", dto.getId()));
    VersionGuard.check(Lecturer.class, dto.getId(), existingLecturer.getVersion(), dto.getVersion());

    Set<Image> previousImages = new HashSet<>(existingLecturer.getImages());
    Set<Image> requestedImages = imageService.resolveImages(dto.getImagesUuid());

    existingLecturer.setName(dto.getName());
    existingLecturer.setSurname(dto.getSurname());
    existingLecturer.setNotes(dto.getNotes());
    existingLecturer.setNickname(dto.getNickname());
    existingLecturer.setEmail(dto.getEmail());
    existingLecturer.setPhoneNumber(dto.getPhoneNumber());
    existingLecturer.setAddress(dto.getAddress());
    existingLecturer.setImages(requestedImages);
    // Resolved after the merge: the pointer may have just been removed, and the
    // fallback has to prefer photos that were already here (see PrimaryImage.resolve).
    existingLecturer.setPrimaryImageUuid(imageService.resolveUpdatedPrimaryImage(
        dto.getPrimaryImageUuid(), existingLecturer.getPrimaryImageUuid(),
        previousImages, requestedImages));

    Lecturer updatedLecturer = lecturerRepository.saveAndFlush(existingLecturer);

    imageService.deleteRemovedImages(previousImages, requestedImages);
    return LecturerMapper.mapToDTO(updatedLecturer);
  }
}
