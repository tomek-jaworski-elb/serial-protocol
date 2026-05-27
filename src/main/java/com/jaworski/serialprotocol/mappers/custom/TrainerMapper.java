package com.jaworski.serialprotocol.mappers.custom;

import com.jaworski.serialprotocol.dto.custom.TrainerDTO;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.entity.custom.Trainer;

import java.util.HashSet;
import java.util.stream.Collectors;

public class TrainerMapper {

  private TrainerMapper() {
  }

  public static TrainerDTO mapToDTO(Trainer trainer) {
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
    dto.setImagesUuid(trainer.getImages() == null
            ? new HashSet<>()
            : trainer.getImages().stream().map(Image::getId).collect(Collectors.toSet()));
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
    // Images are resolved and set by the service layer — not mapped here
    return trainer;
  }
}

