package com.jaworski.serialprotocol.dto.custom;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantDTO {

  private UUID uuid;
  private Long id;
  private String name;
  private String surname;
  private LocalDate birthDate;
  private byte[] photo;

}

