package com.jaworski.serialprotocol.dto.custom;

import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseTypeDTO {

  private Long id;
  private String code;
  private String description;
  private String longDescription;

  /** Optimistic-locking version of the record the form was opened on (a hidden field). Not part of a
   * backup: restored rows start again at 0. */
  @JsonIgnore
  private Long version;

}

