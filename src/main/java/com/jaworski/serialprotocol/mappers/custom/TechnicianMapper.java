package com.jaworski.serialprotocol.mappers.custom;

import com.jaworski.serialprotocol.dto.custom.TechnicianDTO;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.entity.custom.Technician;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class TechnicianMapper {

  private TechnicianMapper() {
  }

  public static TechnicianDTO mapToDTO(Technician technician) {
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
    dto.setImagesUuid(technician.getImages() == null
            ? new HashSet<>()
            : technician.getImages().stream().map(Image::getId).collect(Collectors.toSet()));
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
    Set<Image> images = dto.getImagesUuid() == null
            ? new HashSet<>()
            : dto.getImagesUuid().stream()
            .map(id -> {
              Image image = new Image();
              image.setId(id);
              return image;
            })
            .collect(Collectors.toSet());
    technician.setImages(images);
    return technician;
  }
}

