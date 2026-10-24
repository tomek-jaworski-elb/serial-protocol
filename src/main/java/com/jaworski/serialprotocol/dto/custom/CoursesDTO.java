package com.jaworski.serialprotocol.dto.custom;

import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
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
  @NotNull(message = "is required")
  private UUID participantUuid;
  @NotNull(message = "is required")
  private UUID courseTypeId;
  private UUID courseCounterUuid;
  private Long counter;
  @NotNull(message = "is required")
  @DateTimeFormat(pattern = "dd/MM/yyyy")
  private LocalDate startDate;
  @NotNull(message = "is required")
  @DateTimeFormat(pattern = "dd/MM/yyyy")
  private LocalDate endDate;
  private Set<UUID> trainerIds = new HashSet<>();
  private Set<UUID> lecturerIds = new HashSet<>();
  private Set<UUID> technicianIds = new HashSet<>();

  /** Optimistic-lock version of the stored record; posted back by the update form, null = unchecked. */
  private Long version;

  /** Reported under the property name {@code dateRange}, like a field. */
  @AssertTrue(message = "end date must be the same as or after the start date")
  public boolean isDateRange() {
    return startDate == null || endDate == null || !endDate.isBefore(startDate);
  }

  public String getTrainerIdsString() {
    return trainerIds == null ? "" : trainerIds.stream().map(String::valueOf).collect(Collectors.joining(","));
  }

  public String getLecturerIdsString() {
    return lecturerIds == null ? "" : lecturerIds.stream().map(String::valueOf).collect(Collectors.joining(","));
  }

  public String getTechnicianIdsString() {
    return technicianIds == null ? "" : technicianIds.stream().map(String::valueOf).collect(Collectors.joining(","));
  }
}
