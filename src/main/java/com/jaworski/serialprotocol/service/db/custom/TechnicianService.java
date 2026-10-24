package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.TechnicianDTO;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.entity.custom.Technician;
import com.jaworski.serialprotocol.mappers.custom.TechnicianMapper;
import com.jaworski.serialprotocol.repository.custom.CoursesRepository;
import com.jaworski.serialprotocol.repository.custom.ImageRepository;
import com.jaworski.serialprotocol.repository.custom.TechnicianRepository;
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
public class TechnicianService {

  private final ImageRepository imageRepository;
  private final ImageService imageService;
  private final TechnicianRepository technicianRepository;
  private final CoursesRepository coursesRepository;
  private static final Logger LOGGER = LoggerFactory.getLogger(TechnicianService.class);

  @Transactional(readOnly = true)
  public List<TechnicianDTO> findAll() {
    return toDTOs(technicianRepository.findAll());
  }

  @Transactional(readOnly = true)
  public Page<TechnicianDTO> findAll(Pageable pageable) {
    Page<Technician> page = technicianRepository.findAll(pageable);
    Map<UUID, Set<UUID>> images = imageIdsOf(page.getContent());
    return page.map(p -> TechnicianMapper.mapToDTO(p, images.get(p.getUuid())));
  }

  @Transactional(readOnly = true)
  public TechnicianDTO findById(UUID id) {
    return technicianRepository.findById(id)
        .map(p -> TechnicianMapper.mapToDTO(p, imageIdsOf(List.of(p)).get(id)))
        .orElse(null);
  }

  public TechnicianDTO save(TechnicianDTO dto) {
    Technician technician = TechnicianMapper.mapToEntity(dto);
    technician.setImages(imageService.resolveImages(dto.getImagesUuid()));
    // A new record gets a pointer too, and a value arriving from the form is validated
    // here rather than trusted — the add endpoint binds the whole DTO.
    technician.setPrimaryImageUuid(
        imageService.resolveNewPrimaryImage(dto.getPrimaryImageUuid(), dto.getImagesUuid()));
    Technician saved = technicianRepository.save(technician);
    return TechnicianMapper.mapToDTO(saved, dto.getImagesUuid());
  }

  public void deleteById(UUID id) {
    if (coursesRepository.existsByTechnicians_Uuid(id)) {
      throw new IllegalStateException("Cannot delete technician with id " + id + " because it is referenced by existing courses.");
    }
    technicianRepository.findById(id).ifPresent(technician -> {
      if (!technician.getImages().isEmpty()) {
        imageRepository.deleteAll(technician.getImages());
      }
    });
    technicianRepository.deleteById(id);
  }

  public TechnicianDTO updateById(TechnicianDTO dto) {
    if (dto.getId() == null) {
      throw new IllegalArgumentException("Technician id is required for update");
    }
    Technician existing = technicianRepository.findById(dto.getId())
        .orElseThrow(() -> new IllegalArgumentException("Technician with id " + dto.getId() + " not found"));

    Set<Image> previousImages = new HashSet<>(existing.getImages());
    Set<Image> requestedImages = imageService.resolveImages(dto.getImagesUuid());

    existing.setName(dto.getName());
    existing.setSurname(dto.getSurname());
    existing.setNotes(dto.getNotes());
    existing.setNickname(dto.getNickname());
    existing.setEmail(dto.getEmail());
    existing.setPhoneNumber(dto.getPhoneNumber());
    existing.setAddress(dto.getAddress());
    existing.setImages(requestedImages);
    // Resolved after the merge: the pointer may have just been removed, and the
    // fallback has to prefer photos that were already here (see PrimaryImage.resolve).
    existing.setPrimaryImageUuid(imageService.resolveUpdatedPrimaryImage(
        dto.getPrimaryImageUuid(), existing.getPrimaryImageUuid(),
        previousImages, requestedImages));

    Technician updated = technicianRepository.save(existing);

    imageService.deleteRemovedImages(previousImages, requestedImages);
    return TechnicianMapper.mapToDTO(updated, idsOf(requestedImages));
  }

  private List<TechnicianDTO> toDTOs(List<Technician> people) {
    Map<UUID, Set<UUID>> images = imageIdsOf(people);
    return people.stream().map(p -> TechnicianMapper.mapToDTO(p, images.get(p.getUuid()))).toList();
  }

  /**
   * Image ids for all given people in one query. Never {@code getImages()}: that would load
   * every photo's blob (see {@link ImageLink}).
   */
  private Map<UUID, Set<UUID>> imageIdsOf(List<Technician> people) {
    if (people.isEmpty()) {
      return Map.of();
    }
    return ImageLink.groupByOwner(technicianRepository.findImageLinks(people.stream().map(Technician::getUuid).toList()));
  }

  private static Set<UUID> idsOf(Set<Image> images) {
    return images.stream().map(Image::getId).collect(Collectors.toSet());
  }
}

