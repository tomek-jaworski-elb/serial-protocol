package com.jaworski.serialprotocol.dto.custom;

import java.util.UUID;

public record CourseCounterDTO(
        UUID uuid,
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
