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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class TrainerService {

  private final ImageRepository imageRepository;
  private final TrainerRepository trainerRepository;
  private final CoursesRepository coursesRepository;
  private final static Logger LOGGER = LoggerFactory.getLogger(TrainerService.class);

  public List<TrainerDTO> findAll() {
    return trainerRepository.findAll().stream()
            .map(TrainerMapper::mapToDTO)
            .toList();
  }

  @Transactional(readOnly = true)
  public Page<TrainerDTO> findAll(Pageable pageable) {
    return trainerRepository.findAll(pageable).map(TrainerMapper::mapToDTO);
  }

  public TrainerDTO findById(UUID id) {
    return trainerRepository.findById(id)
            .map(TrainerMapper::mapToDTO)
            .orElse(null);
  }

  public TrainerDTO save(TrainerDTO trainerDTO) {
    Trainer entityToSave = TrainerMapper.mapToEntity(trainerDTO);
    entityToSave.setImages(resolveImages(trainerDTO.getImagesUuid()));
    Trainer trainer = trainerRepository.save(entityToSave);
    return TrainerMapper.mapToDTO(trainer);
  }

  public void deleteById(UUID id) {
    if (coursesRepository.existsByTrainers_Uuid(id)) {
      throw new IllegalStateException("Cannot delete trainer with id " + id + " because it is referenced by existing courses.");
    }
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
    Set<Image> requestedImages = resolveImages(trainerDTO.getImagesUuid());

    existingTrainer.setName(trainerDTO.getName());
    existingTrainer.setSurname(trainerDTO.getSurname());
    existingTrainer.setNotes(trainerDTO.getNotes());
    existingTrainer.setNickname(trainerDTO.getNickname());
    existingTrainer.setEmail(trainerDTO.getEmail());
    existingTrainer.setPhoneNumber(trainerDTO.getPhoneNumber());
    existingTrainer.setAddress(trainerDTO.getAddress());
    existingTrainer.setImages(requestedImages);

    Trainer updatedTrainer = trainerRepository.save(existingTrainer);

    Set<Image> imagesToDelete = previousImages.stream()
        .filter(image -> !requestedImages.contains(image))
        .collect(java.util.stream.Collectors.toSet());
    if (!imagesToDelete.isEmpty()) {
      imageRepository.deleteAll(imagesToDelete);
    }
    return TrainerMapper.mapToDTO(updatedTrainer);
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
