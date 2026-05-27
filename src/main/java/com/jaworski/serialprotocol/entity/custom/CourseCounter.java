package com.jaworski.serialprotocol.entity.custom;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.UUID;

@Entity
@Table(name = CourseCounter.TABLE_NAME)
@Data
@NoArgsConstructor
public class CourseCounter {

  public static final String TABLE_NAME = "course_counter";

  @Id
  @Column(name = CourseCounter.TABLE_NAME + "_uuid")
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID uuid;

  @NotNull
  @Positive
  @Column(name = CourseCounter.TABLE_NAME + "_counter", unique = true, nullable = false)
  private Long counter;

  @OneToOne
  @JoinColumn(name = "image_uuid", unique = true)
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private Image image;

}
