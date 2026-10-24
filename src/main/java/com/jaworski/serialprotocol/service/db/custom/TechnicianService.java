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

  /** Creates the technician and stores the uploaded photos in the same transaction. */
  public TechnicianDTO save(TechnicianDTO dto, List<UploadedImage> uploads) {
    Set<UUID> images = dto.getImagesUuid() == null ? new HashSet<>() : new HashSet<>(dto.getImagesUuid());
    ImageService.assertWithinLimit(images.size(), uploads.size());
    images.addAll(imageService.saveImages(uploads));
    dto.setImagesUuid(images);
    return save(dto);
  }

  /**
   * Updates the technician; the photos it ends up with are the ones it had, minus {@code removals},
   * plus {@code uploads}, all committed together with the record.
   *
   * <p>The request carries the images to <em>remove</em>, never the ones to keep. That direction
   * matters: the edit form builds its thumbnails in JavaScript, so a "keep these" list would turn
   * any rendering failure into data loss — an empty editor would submit an empty keep-list and
   * wipe every photo. With removals, a broken editor simply removes nothing, and a missing
   * parameter means "leave the images alone". Ids outside the record's own set are ignored, so a
   * forged uuid can only ever subtract from its photos, never attach someone else's.</p>
   */
  public TechnicianDTO updateById(TechnicianDTO dto, List<UploadedImage> uploads, List<UUID> removals) {
    if (dto.getId() == null) {
      throw new IllegalArgumentException("Technician id is required for update");
    }
    if (!technicianRepository.existsById(dto.getId())) {
      throw new IllegalArgumentException("Technician with id " + dto.getId() + " not found");
    }
    // Ids only: initialising the image collection would read every photo's blob.
    Set<UUID> kept = new HashSet<>(ImageLink.groupByOwner(technicianRepository.findImageLinks(List.of(dto.getId())))
        .getOrDefault(dto.getId(), Set.of()));
    if (removals != null) {
      kept.removeAll(removals);
    }
    ImageService.assertWithinLimit(kept.size(), uploads.size());
    kept.addAll(imageService.saveImages(uploads));
    dto.setImagesUuid(kept);
    return updateById(dto);
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

