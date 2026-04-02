package com.jaworski.serialprotocol.dto.custom;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LecturerDTO {

  private UUID lecturerId;
  private String name;
  private String surname;
  private String email;
  private String nickname;
  private Set<UUID> imagesUuid = new HashSet<>();

}

