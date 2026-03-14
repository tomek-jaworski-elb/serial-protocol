package com.jaworski.serialprotocol.entity.custom;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = Courses.TABLE_NAME)
@Data
@NoArgsConstructor
public class Courses {

  protected static final String TABLE_NAME = "courses";
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = TABLE_NAME + "_uuid", nullable = false, updatable = false)
  private UUID uuid;

  @Column(name = TABLE_NAME + "_id", unique = false, nullable = false)
  @NotNull
  @Positive
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = TABLE_NAME + "_participant_uuid", nullable = false)
  @NotNull
  private Participant participant;

  @ManyToOne(optional = false)
  @JoinColumn(name = TABLE_NAME + "_course_type_id", nullable = false)
  @NotNull
  private CourseType courseType;

  @Column(name = TABLE_NAME + "_start_date", nullable = false)
  @NotNull
  private LocalDate startDate;

  @Column(name = TABLE_NAME + "_end_date", nullable = false)
  @NotNull
  private LocalDate endDate;
  
  @ManyToMany
  @JoinTable(
      name = TABLE_NAME + "_trainers",
      joinColumns = @JoinColumn(name = TABLE_NAME + "_uuid"),
      inverseJoinColumns = @JoinColumn(name = Trainer.TABLE_NAME + "_id")
  )
  @NotNull
  private Set<Trainer> trainers = new HashSet<>();
  
  @ManyToMany
  @JoinTable(
      name = TABLE_NAME + "_lecturers",
      joinColumns = @JoinColumn(name = TABLE_NAME + "_uuid"),
      inverseJoinColumns = @JoinColumn(name = Lecturer.TABLE_NAME + "_id")
  )
  @NotNull
  private Set<Lecturer> lecturers  = new HashSet<>();

  @AssertTrue(message = "endDate must be the same as or after startDate")
  private boolean isDateRangeValid() {
    if (startDate == null || endDate == null) {
      return true;
    }
    return !endDate.isBefore(startDate);
  }

}
