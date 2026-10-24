package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.TrainerDTO;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.entity.custom.Trainer;
import com.jaworski.serialprotocol.mappers.custom.TrainerMapper;
import com.jaworski.serialprotocol.repository.custom.CoursesRepository;
import com.jaworski.serialprotocol.repository.custom.ImageRepository;
import com.jaworski.serialprotocol.repository.custom.TrainerRepository;
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
public class TrainerService {

  private final ImageRepository imageRepository;
  private final ImageService imageService;
  private final TrainerRepository trainerRepository;
  private final CoursesRepository coursesRepository;
  private final static Logger LOGGER = LoggerFactory.getLogger(TrainerService.class);

  @Transactional(readOnly = true)
  public List<TrainerDTO> findAll() {
    return toDTOs(trainerRepository.findAll());
  }

  @Transactional(readOnly = true)
  public Page<TrainerDTO> findAll(Pageable pageable) {
    Page<Trainer> page = trainerRepository.findAll(pageable);
    Map<UUID, Set<UUID>> images = imageIdsOf(page.getContent());
    return page.map(p -> TrainerMapper.mapToDTO(p, images.get(p.getUuid())));
  }

  @Transactional(readOnly = true)
  public TrainerDTO findById(UUID id) {
    return trainerRepository.findById(id)
        .map(p -> TrainerMapper.mapToDTO(p, imageIdsOf(List.of(p)).get(id)))
        .orElse(null);
  }

  public TrainerDTO save(TrainerDTO trainerDTO) {
    Trainer entityToSave = TrainerMapper.mapToEntity(trainerDTO);
    entityToSave.setImages(imageService.resolveImages(trainerDTO.getImagesUuid()));
    // A new record gets a pointer too, and a value arriving from the form is validated
    // here rather than trusted — the add endpoint binds the whole DTO.
    entityToSave.setPrimaryImageUuid(imageService.resolveNewPrimaryImage(
        trainerDTO.getPrimaryImageUuid(), trainerDTO.getImagesUuid()));
    Trainer trainer = trainerRepository.save(entityToSave);
    return TrainerMapper.mapToDTO(trainer, trainerDTO.getImagesUuid());
  }

  public void deleteById(UUID id) {
    if (coursesRepository.existsByTrainers_Uuid(id)) {
      throw new IllegalStateException("Cannot delete trainer with id " + id + " because it is referenced by existing courses.");
    }
    trainerRepository.findById(id).ifPresent(trainer -> {
      if (!trainer.getImages().isEmpty()) {
        imageRepository.deleteAll(trainer.getImages());
      }
    });
    trainerRepository.deleteById(id);
  }

  public TrainerDTO update(TrainerDTO trainerDTO) {
    if (trainerDTO.getId() == null) {
      throw new IllegalArgumentException("Trainer id is required for update");
    }
    if (!trainerRepository.existsById(trainerDTO.getId())) {
      throw new IllegalArgumentException("Trainer with id " + trainerDTO.getId() + " not found");
    }
    Trainer existingTrainer = trainerRepository.findById(trainerDTO.getId())
        .orElseThrow(() -> new IllegalArgumentException("Trainer with id " + trainerDTO.getId() + " not found"));

    Set<Image> previousImages = new HashSet<>(existingTrainer.getImages());
    Set<Image> requestedImages = imageService.resolveImages(trainerDTO.getImagesUuid());

    existingTrainer.setName(trainerDTO.getName());
    existingTrainer.setSurname(trainerDTO.getSurname());
    existingTrainer.setNotes(trainerDTO.getNotes());
    existingTrainer.setNickname(trainerDTO.getNickname());
    existingTrainer.setEmail(trainerDTO.getEmail());
    existingTrainer.setPhoneNumber(trainerDTO.getPhoneNumber());
    existingTrainer.setAddress(trainerDTO.getAddress());
    existingTrainer.setImages(requestedImages);
    // Resolved after the merge: the pointer may have just been removed, and the
    // fallback has to prefer photos that were already here (see PrimaryImage.resolve).
    existingTrainer.setPrimaryImageUuid(imageService.resolveUpdatedPrimaryImage(
        trainerDTO.getPrimaryImageUuid(), existingTrainer.getPrimaryImageUuid(),
        previousImages, requestedImages));

    Trainer updatedTrainer = trainerRepository.save(existingTrainer);

    imageService.deleteRemovedImages(previousImages, requestedImages);
    return TrainerMapper.mapToDTO(updatedTrainer, idsOf(requestedImages));
  }

  /** Creates the trainer and stores the uploaded photos in the same transaction. */
  public TrainerDTO save(TrainerDTO dto, List<UploadedImage> uploads) {
    Set<UUID> images = dto.getImagesUuid() == null ? new HashSet<>() : new HashSet<>(dto.getImagesUuid());
    ImageService.assertWithinLimit(images.size(), uploads.size());
    images.addAll(imageService.saveImages(uploads));
    dto.setImagesUuid(images);
    return save(dto);
  }

  /**
   * Updates the trainer; the photos it ends up with are the ones it had, minus {@code removals},
   * plus {@code uploads}, all committed together with the record.
   *
   * <p>The request carries the images to <em>remove</em>, never the ones to keep. That direction
   * matters: the edit form builds its thumbnails in JavaScript, so a "keep these" list would turn
   * any rendering failure into data loss — an empty editor would submit an empty keep-list and
   * wipe every photo. With removals, a broken editor simply removes nothing, and a missing
   * parameter means "leave the images alone". Ids outside the record's own set are ignored, so a
   * forged uuid can only ever subtract from its photos, never attach someone else's.</p>
   */
  public TrainerDTO update(TrainerDTO dto, List<UploadedImage> uploads, List<UUID> removals) {
    if (dto.getId() == null) {
      throw new IllegalArgumentException("Trainer id is required for update");
    }
    if (!trainerRepository.existsById(dto.getId())) {
      throw new IllegalArgumentException("Trainer with id " + dto.getId() + " not found");
    }
    // Ids only: initialising the image collection would read every photo's blob.
    Set<UUID> kept = new HashSet<>(ImageLink.groupByOwner(trainerRepository.findImageLinks(List.of(dto.getId())))
        .getOrDefault(dto.getId(), Set.of()));
    if (removals != null) {
      kept.removeAll(removals);
    }
    ImageService.assertWithinLimit(kept.size(), uploads.size());
    kept.addAll(imageService.saveImages(uploads));
    dto.setImagesUuid(kept);
    return update(dto);
  }

  private List<TrainerDTO> toDTOs(List<Trainer> people) {
    Map<UUID, Set<UUID>> images = imageIdsOf(people);
    return people.stream().map(p -> TrainerMapper.mapToDTO(p, images.get(p.getUuid()))).toList();
  }

  /**
   * Image ids for all given people in one query. Never {@code getImages()}: that would load
   * every photo's blob (see {@link ImageLink}).
   */
  private Map<UUID, Set<UUID>> imageIdsOf(List<Trainer> people) {
    if (people.isEmpty()) {
      return Map.of();
    }
    return ImageLink.groupByOwner(trainerRepository.findImageLinks(people.stream().map(Trainer::getUuid).toList()));
  }

  private static Set<UUID> idsOf(Set<Image> images) {
    return images.stream().map(Image::getId).collect(Collectors.toSet());
  }
}
