package com.jaworski.serialprotocol.mappers.custom;

import com.jaworski.serialprotocol.dto.custom.LecturerDTO;
import com.jaworski.serialprotocol.entity.custom.Lecturer;

public class LecturerMapper {

  private LecturerMapper() {
  }

  public static LecturerDTO mapToDTO(Lecturer lecturer) {
    if (lecturer == null) {
      return null;
    }
    LecturerDTO dto = new LecturerDTO();
    dto.setLecturerId(lecturer.getLecturerId());
    dto.setName(lecturer.getName());
    dto.setSurname(lecturer.getSurname());
    dto.setPhoto(lecturer.getPhoto());
    return dto;
  }

  public static Lecturer mapToEntity(LecturerDTO dto) {
    if (dto == null) {
      return null;
    }
    Lecturer lecturer = new Lecturer();
    if (dto.getLecturerId() != null) {
      lecturer.setLecturerId(dto.getLecturerId());
    }
    lecturer.setName(dto.getName());
    lecturer.setSurname(dto.getSurname());
    lecturer.setPhoto(dto.getPhoto());
    return lecturer;
  }
}

