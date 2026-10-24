package com.jaworski.serialprotocol.dto.custom;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseTypeDTO {

  private UUID id;
  @NotBlank(message = "is required")
  @Size(max = 32)
  private String code;
  @NotBlank(message = "is required")
  @Size(max = 255)
  private String description;
  @Size(max = 5000)
  private String longDescription;

  /** Optimistic-lock version of the stored record; posted back by the update form, null = unchecked. */
  private Long version;

}

