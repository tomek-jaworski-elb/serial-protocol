package com.jaworski.serialprotocol.mappers.custom;

import com.jaworski.serialprotocol.dto.custom.ParticipantDTO;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.entity.custom.Participant;

public class ParticipantMapper {

  private ParticipantMapper() {
  }

  public static ParticipantDTO mapToDTO(Participant participant) {
    if (participant == null) {
      return null;
    }
    ParticipantDTO dto = new ParticipantDTO();
    dto.setParticipantUuid(participant.getUuid());
    dto.setId(participant.getId());
    dto.setName(participant.getName());
    dto.setSurname(participant.getSurname());
    dto.setNotes(participant.getNotes());
    dto.setNickname(participant.getNickname());
    dto.setEmail(participant.getEmail());
    dto.setPhoneNumber(participant.getPhoneNumber());
    dto.setAddress(participant.getAddress());
    dto.setBirthDate(participant.getBirthDate());
    dto.setImage(participant.getImage() == null ? null : participant.getImage().getId());
    return dto;
  }

  public static Participant mapToEntity(ParticipantDTO dto) {
    if (dto == null) {
      return null;
    }
    Participant participant = new Participant();
    participant.setUuid(dto.getParticipantUuid());
    participant.setId(dto.getId());
    participant.setName(dto.getName());
    participant.setSurname(dto.getSurname());
    participant.setNotes(dto.getNotes());
    participant.setNickname(dto.getNickname());
    participant.setEmail(dto.getEmail());
    participant.setPhoneNumber(dto.getPhoneNumber());
    participant.setAddress(dto.getAddress());
    participant.setBirthDate(dto.getBirthDate());
    if (dto.getImage() != null) {
      Image image = new Image();
      image.setId(dto.getImage());
      participant.setImage(image);
    }
    return participant;
  }
}

