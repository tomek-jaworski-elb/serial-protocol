package com.jaworski.serialprotocol.dto.custom;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrainerDTO {

  private UUID id;
  private String name;
  private String surname;
  private String email;

  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  private Set<UUID> imagesUuid = new HashSet<>();

}

