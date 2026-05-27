package com.jaworski.serialprotocol.mappers.custom;

import com.jaworski.serialprotocol.dto.custom.LecturerDTO;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.entity.custom.Lecturer;

import java.util.HashSet;
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
    dto.setNotes(lecturer.getNotes());
    dto.setNickname(lecturer.getNickname());
    dto.setEmail(lecturer.getEmail());
    dto.setPhoneNumber(lecturer.getPhoneNumber());
    dto.setAddress(lecturer.getAddress());
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
    lecturer.setNotes(dto.getNotes());
    lecturer.setNickname(dto.getNickname());
    lecturer.setEmail(dto.getEmail());
    lecturer.setPhoneNumber(dto.getPhoneNumber());
    lecturer.setAddress(dto.getAddress());
    // Images are resolved and set by the service layer — not mapped here
    return lecturer;
  }
}

