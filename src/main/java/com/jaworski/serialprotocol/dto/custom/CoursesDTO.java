package com.jaworski.serialprotocol.dto.custom;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Data
@NoArgsConstructor
public class CoursesDTO {

  public CoursesDTO(Long id, UUID participantUuid, Long courseTypeId, LocalDate startDate, LocalDate endDate, Set<Long> trainerIds, Set<Long> lecturerIds) {
    this.id = id;
    this.participantUuid = participantUuid;
    this.courseTypeId = courseTypeId;
    this.startDate = startDate;
    this.endDate = endDate;
    this.trainerIds = trainerIds;
    this.lecturerIds = lecturerIds;
    uuid = UUID.randomUUID();
  }

  private UUID uuid;
  private Long id;
  private UUID participantUuid;
  private Long courseTypeId;
  private LocalDate startDate;
  private LocalDate endDate;
  private Set<Long> trainerIds = new HashSet<>();
  private Set<Long> lecturerIds = new HashSet<>();

}

