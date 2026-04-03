package com.jaworski.serialprotocol.dto.custom;

import java.util.UUID;

public record CourseCounterDTO(
        UUID uuid,
        long counter,
        UUID imageUuid
) {
  public CourseCounterDTO(long counter, UUID imageUuid) {
    this(null, counter, imageUuid);
  }
}
