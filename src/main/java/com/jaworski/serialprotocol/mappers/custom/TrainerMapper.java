package com.jaworski.serialprotocol.mappers.custom;

import com.jaworski.serialprotocol.dto.custom.TrainerDTO;
import com.jaworski.serialprotocol.entity.custom.Trainer;

public class TrainerMapper {

  private TrainerMapper() {
  }

  public static TrainerDTO mapToDTO(Trainer trainer) {
    if (trainer == null) {
      return null;
    }
    TrainerDTO dto = new TrainerDTO();
    dto.setId(trainer.getId());
    dto.setName(trainer.getName());
    dto.setSurname(trainer.getSurname());
    dto.setEmail(trainer.getEmail());
    dto.setPhoto(trainer.getPhoto());
    return dto;
  }

  public static Trainer mapToEntity(TrainerDTO dto) {
    if (dto == null) {
      return null;
    }
    Trainer trainer = new Trainer();
    if (dto.getId() != null) {
      trainer.setId(dto.getId());
    }
    trainer.setName(dto.getName());
    trainer.setSurname(dto.getSurname());
    trainer.setEmail(dto.getEmail());
    trainer.setPhoto(dto.getPhoto());
    return trainer;
  }
}

