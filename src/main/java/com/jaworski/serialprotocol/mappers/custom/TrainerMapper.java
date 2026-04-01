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
    dto.setId(trainer.getId());
    dto.setName(trainer.getName());
    dto.setSurname(trainer.getSurname());
    dto.setEmail(trainer.getEmail());
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
      trainer.setId(dto.getId());
    }
    trainer.setName(dto.getName());
    trainer.setSurname(dto.getSurname());
    trainer.setEmail(dto.getEmail());
    trainer.setImages(dto.getImagesUuid() == null
            ? new HashSet<>()
            : dto.getImagesUuid().stream().map(id -> {
              Image image = new Image();
              image.setId(id);
              return image;
            }).collect(Collectors.toSet()));
    return trainer;
  }
}

