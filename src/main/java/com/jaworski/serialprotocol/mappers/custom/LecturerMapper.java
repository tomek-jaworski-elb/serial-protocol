package com.jaworski.serialprotocol.mappers.custom;

import com.jaworski.serialprotocol.dto.custom.LecturerDTO;
import com.jaworski.serialprotocol.entity.custom.Lecturer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class LecturerMapper {

  private LecturerMapper() {
  }

  /**
   * @param imageIds the ids of the photos, fetched separately (see {@code ImageLink}). The mapper
   *   never reads {@code getImages()}: initialising that collection loads every photo's LONGBLOB,
   *   and the list pages need only the ids.
   */
  public static LecturerDTO mapToDTO(Lecturer lecturer, Set<UUID> imageIds) {
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
    dto.setPrimaryImageUuid(lecturer.getPrimaryImageUuid());
    dto.setImagesUuid(imageIds == null ? new HashSet<>() : new HashSet<>(imageIds));
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
    lecturer.setPrimaryImageUuid(dto.getPrimaryImageUuid());
    // Images are resolved and set by the service layer — not mapped here
    return lecturer;
  }
}

