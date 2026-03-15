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

@Entity
@Data
@Table(name = CourseType.TABLE_NAME)
public class CourseType {

  protected static final String TABLE_NAME = "course_type";

  public CourseType() {
  //  JPA requires a no-args constructor
  }


  public CourseType(String code, String description, String longDescription) {
    this.code = code;
    this.description = description;
    this.longDescription = longDescription;
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = CourseType.TABLE_NAME + "_id", nullable = false, updatable = false)
  private Long id;

  @Column(name = CourseType.TABLE_NAME + "_code", nullable = false, length = 32)
  @NotBlank
  @Size(max = 32)
  private String code;

  @Column(name = CourseType.TABLE_NAME + "_description", nullable = false, length = 255)
  @NotBlank
  @Size(max = 255)
  private String description;

  @Column(name = CourseType.TABLE_NAME + "_long_description",nullable = false, length = 5_000)
  @Size(max = 5_000)
  @NotBlank
  private String longDescription;
}
