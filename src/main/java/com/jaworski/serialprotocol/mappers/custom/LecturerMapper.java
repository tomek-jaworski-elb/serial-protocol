package com.jaworski.serialprotocol.mappers.custom;

import com.jaworski.serialprotocol.dto.custom.LecturerDTO;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.entity.custom.Lecturer;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class LecturerMapper {

  private LecturerMapper() {
  }

  public static LecturerDTO mapToDTO(Lecturer lecturer) {
    if (lecturer == null) {
      return null;
    }
    LecturerDTO dto = new LecturerDTO();
    dto.setId(lecturer.getUuid());
    dto.setName(lecturer.getName());
    dto.setSurname(lecturer.getSurname());
    dto.setEmail(lecturer.getEmail());
    dto.setNickname(lecturer.getNickname());
    dto.setImagesUuid(lecturer.getImages() == null
            ? new HashSet<>()
            : lecturer.getImages().stream().map(Image::getId).collect(Collectors.toSet()));
    return dto;
  }

  public static Lecturer mapToEntity(LecturerDTO dto) {
    if (dto == null) {
      return null;
    }
    Lecturer lecturer = new Lecturer();
    if (dto.getId() != null) {
      lecturer.setUuid(dto.getId());
    }
    lecturer.setName(dto.getName());
    lecturer.setSurname(dto.getSurname());
    lecturer.setEmail(dto.getEmail());
    lecturer.setNickname(dto.getNickname());
    Set<Image> images = dto.getImagesUuid() == null
            ? new HashSet<>()
            : dto.getImagesUuid().stream()
            .map(id -> {
              Image image = new Image();
              image.setId(id);
              return image;
            })
            .collect(Collectors.toSet());
    lecturer.setImages(images);
    return lecturer;
  }
}

