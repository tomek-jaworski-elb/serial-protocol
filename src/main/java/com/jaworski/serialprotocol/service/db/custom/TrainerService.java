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
