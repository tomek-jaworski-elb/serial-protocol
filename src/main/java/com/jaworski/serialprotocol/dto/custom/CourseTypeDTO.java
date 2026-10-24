package com.jaworski.serialprotocol.dto.custom;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseTypeDTO {

  private UUID id;
  private String code;
  private String description;
  private String longDescription;

  /** Optimistic-lock version of the stored record; posted back by the update form, null = unchecked. */
  private Long version;

}

