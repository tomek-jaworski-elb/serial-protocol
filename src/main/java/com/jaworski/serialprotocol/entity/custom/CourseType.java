package com.jaworski.serialprotocol.entity.custom;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Data
@Table(name = CourseType.TABLE_NAME)
@NoArgsConstructor
public class CourseType {

  public static final String TABLE_NAME = "course_type";

  public CourseType(String code, String description, String longDescription) {
    this.code = code;
    this.description = description;
    this.longDescription = longDescription;
  }

  // UUID like every other table: an AUTO_INCREMENT key cannot be generated independently by
  // two databases (V6__course_type_uuid.sql moved existing rows over).
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = CourseType.TABLE_NAME + "_uuid", nullable = false, updatable = false)
  private UUID id;

  @Column(name = CourseType.TABLE_NAME + "_code", nullable = false, unique = true, length = 32)
  @NotBlank
  @Size(max = 32)
  private String code;

  @Column(name = CourseType.TABLE_NAME + "_description", nullable = false, length = 255)
  @NotBlank
  @Size(max = 255)
  private String description;

  @Column(name = CourseType.TABLE_NAME + "_long_description", nullable = true, length = 5_000)
  @Size(max = 5_000)
  private String longDescription;
}
