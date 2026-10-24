package com.jaworski.serialprotocol.mappers.custom;

import com.jaworski.serialprotocol.dto.custom.TrainerDTO;
import com.jaworski.serialprotocol.entity.custom.Trainer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TrainerMapper {

  private TrainerMapper() {
  }

  /**
   * @param imageIds the ids of the photos, fetched separately (see {@code ImageLink}). The mapper
   *   never reads {@code getImages()}: initialising that collection loads every photo's LONGBLOB,
   *   and the list pages need only the ids.
   */
  public static TrainerDTO mapToDTO(Trainer trainer, Set<UUID> imageIds) {
    if (trainer == null) {
      return null;
    }
    TrainerDTO dto = new TrainerDTO();
    dto.setId(trainer.getUuid());
    dto.setName(trainer.getName());
    dto.setSurname(trainer.getSurname());
    dto.setNotes(trainer.getNotes());
    dto.setNickname(trainer.getNickname());
    dto.setEmail(trainer.getEmail());
    dto.setPhoneNumber(trainer.getPhoneNumber());
    dto.setAddress(trainer.getAddress());
    dto.setPrimaryImageUuid(trainer.getPrimaryImageUuid());
    dto.setVersion(trainer.getVersion());
    dto.setImagesUuid(imageIds == null ? new HashSet<>() : new HashSet<>(imageIds));
    return dto;
  }

  public static Trainer mapToEntity(TrainerDTO dto) {
    if (dto == null) {
      return null;
    }
    Trainer trainer = new Trainer();
    if (dto.getId() != null) {
      trainer.setUuid(dto.getId());
    }
    trainer.setName(dto.getName());
    trainer.setSurname(dto.getSurname());
    trainer.setNotes(dto.getNotes());
    trainer.setNickname(dto.getNickname());
    trainer.setEmail(dto.getEmail());
    trainer.setPhoneNumber(dto.getPhoneNumber());
    trainer.setAddress(dto.getAddress());
    trainer.setPrimaryImageUuid(dto.getPrimaryImageUuid());
    // Images are resolved and set by the service layer — not mapped here
    return trainer;
  }
}

