package com.jaworski.serialprotocol.dto.custom;

import java.util.UUID;

public record CourseCounterDTO(
        UUID uuid,
        Long counter,
        UUID imageUuid
) {
  public CourseCounterDTO(Long counter, UUID imageUuid) {
    this(null, counter, imageUuid);
  }
}
