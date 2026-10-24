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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.jaworski.serialprotocol.repository.custom.ImageLink;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class LecturerService {

  private final ImageRepository imageRepository;
  private final ImageService imageService;
  private final LecturerRepository lecturerRepository;
  private final CoursesRepository coursesRepository;
  private static final Logger LOGGER = LoggerFactory.getLogger(LecturerService.class);

  @Transactional(readOnly = true)
  public List<LecturerDTO> findAll() {
    return toDTOs(lecturerRepository.findAll());
  }

  @Transactional(readOnly = true)
  public Page<LecturerDTO> findAll(Pageable pageable) {
    Page<Lecturer> page = lecturerRepository.findAll(pageable);
    Map<UUID, Set<UUID>> images = imageIdsOf(page.getContent());
    return page.map(p -> LecturerMapper.mapToDTO(p, images.get(p.getUuid())));
  }

  @Transactional(readOnly = true)
  public LecturerDTO findById(UUID id) {
    return lecturerRepository.findById(id)
        .map(p -> LecturerMapper.mapToDTO(p, imageIdsOf(List.of(p)).get(id)))
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
    return LecturerMapper.mapToDTO(savedLecturer, dto.getImagesUuid());
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
    if (dto.getId() == null) {
      throw new IllegalArgumentException("Lecturer id is required for update");
    }
    Lecturer existingLecturer = lecturerRepository.findById(dto.getId())
        .orElseThrow(() -> new IllegalArgumentException("Lecturer with id " + dto.getId() + " not found"));

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

    Lecturer updatedLecturer = lecturerRepository.save(existingLecturer);

    imageService.deleteRemovedImages(previousImages, requestedImages);
    return LecturerMapper.mapToDTO(updatedLecturer, idsOf(requestedImages));
  }

  private List<LecturerDTO> toDTOs(List<Lecturer> people) {
    Map<UUID, Set<UUID>> images = imageIdsOf(people);
    return people.stream().map(p -> LecturerMapper.mapToDTO(p, images.get(p.getUuid()))).toList();
  }

  /**
   * Image ids for all given people in one query. Never {@code getImages()}: that would load
   * every photo's blob (see {@link ImageLink}).
   */
  private Map<UUID, Set<UUID>> imageIdsOf(List<Lecturer> people) {
    if (people.isEmpty()) {
      return Map.of();
    }
    return ImageLink.groupByOwner(lecturerRepository.findImageLinks(people.stream().map(Lecturer::getUuid).toList()));
  }

  private static Set<UUID> idsOf(Set<Image> images) {
    return images.stream().map(Image::getId).collect(Collectors.toSet());
  }
}
