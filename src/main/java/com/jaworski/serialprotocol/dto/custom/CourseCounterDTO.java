package com.jaworski.serialprotocol.dto.custom;

import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CourseCounterDTO(
        UUID uuid,
        /** Empty on add means "allocate the next free one". */
        @Positive(message = "must be a positive number")
        Long counter,
        UUID imageUuid,
        /** Optimistic-lock version of the stored record; posted back by the update form, null = unchecked. */
        Long version
) {
  public CourseCounterDTO(Long counter, UUID imageUuid) {
    this(null, counter, imageUuid, null);
  }

  public CourseCounterDTO(UUID uuid, Long counter, UUID imageUuid) {
    this(uuid, counter, imageUuid, null);
  }
}
