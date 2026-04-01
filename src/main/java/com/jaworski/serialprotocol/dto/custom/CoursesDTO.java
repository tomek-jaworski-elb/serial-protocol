package com.jaworski.serialprotocol.dto.custom;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
public class CoursesDTO {

  private UUID uuid;
  private Long id;
  private UUID participantUuid;
  private Long courseTypeId;
  @DateTimeFormat(pattern = "dd/MM/yyyy")
  private LocalDate startDate;
  @DateTimeFormat(pattern = "dd/MM/yyyy")
  private LocalDate endDate;
  private Set<Long> trainerIds = new HashSet<>();
  private Set<Long> lecturerIds = new HashSet<>();

  public String getTrainerIdsString() {
    return trainerIds == null ? "" : trainerIds.stream().map(String::valueOf).collect(Collectors.joining(","));
  }

  public String getLecturerIdsString() {
    return lecturerIds == null ? "" : lecturerIds.stream().map(String::valueOf).collect(Collectors.joining(","));
  }
}

