package com.jaworski.serialprotocol.dto.custom;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.UUID;

/**
 * {@code version} is the optimistic-locking version of the record a form was opened on (a hidden field);
 * it is not part of a backup, restored rows start again at 0.
 */
public record CourseCounterDTO(
        UUID uuid,
        Long counter,
        UUID imageUuid,
        @JsonIgnore Long version
) {
  public CourseCounterDTO(Long counter, UUID imageUuid) {
    this(null, counter, imageUuid, null);
  }

  public CourseCounterDTO(UUID uuid, Long counter, UUID imageUuid) {
    this(uuid, counter, imageUuid, null);
  }
}
