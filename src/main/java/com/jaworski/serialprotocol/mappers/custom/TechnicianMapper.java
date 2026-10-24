package com.jaworski.serialprotocol.mappers.custom;

import com.jaworski.serialprotocol.dto.custom.TechnicianDTO;
import com.jaworski.serialprotocol.entity.custom.Technician;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TechnicianMapper {

  private TechnicianMapper() {
  }

  /**
   * @param imageIds the ids of the photos, fetched separately (see {@code ImageLink}). The mapper
   *   never reads {@code getImages()}: initialising that collection loads every photo's LONGBLOB,
   *   and the list pages need only the ids.
   */
  public static TechnicianDTO mapToDTO(Technician technician, Set<UUID> imageIds) {
    if (technician == null) {
      return null;
    }
    TechnicianDTO dto = new TechnicianDTO();
    dto.setId(technician.getUuid());
    dto.setName(technician.getName());
    dto.setSurname(technician.getSurname());
    dto.setNotes(technician.getNotes());
    dto.setNickname(technician.getNickname());
    dto.setEmail(technician.getEmail());
    dto.setPhoneNumber(technician.getPhoneNumber());
    dto.setAddress(technician.getAddress());
    dto.setPrimaryImageUuid(technician.getPrimaryImageUuid());
    dto.setImagesUuid(imageIds == null ? new HashSet<>() : new HashSet<>(imageIds));
    return dto;
  }

  public static Technician mapToEntity(TechnicianDTO dto) {
    if (dto == null) {
      return null;
    }
    Technician technician = new Technician();
    if (dto.getId() != null) {
      technician.setUuid(dto.getId());
    }
    technician.setName(dto.getName());
    technician.setSurname(dto.getSurname());
    technician.setNotes(dto.getNotes());
    technician.setNickname(dto.getNickname());
    technician.setEmail(dto.getEmail());
    technician.setPhoneNumber(dto.getPhoneNumber());
    technician.setAddress(dto.getAddress());
    technician.setPrimaryImageUuid(dto.getPrimaryImageUuid());
    // Images are resolved and set by the service layer — not mapped here
    return technician;
  }
}

