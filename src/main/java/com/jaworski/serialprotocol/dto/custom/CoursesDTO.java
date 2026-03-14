package com.jaworski.serialprotocol.dto.custom;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CoursesDTO {

  private UUID uuid;
  private Long id;
  private UUID participantUuid;
  private Long courseTypeId;
  private LocalDate startDate;
  private LocalDate endDate;
  private Set<Long> trainerIds = new HashSet<>();
  private Set<Long> lecturerIds = new HashSet<>();

}

