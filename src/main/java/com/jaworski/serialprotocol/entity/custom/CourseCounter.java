package com.jaworski.serialprotocol.entity.custom;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.SQLDelete;

import java.util.UUID;

@Entity
@Table(name = CourseCounter.TABLE_NAME)
@Data
@NoArgsConstructor
@SQLDelete(sql = "UPDATE " + CourseCounter.TABLE_NAME + " SET deleted_at = CURRENT_TIMESTAMP, deleted_by = 'system', version = version + 1 WHERE "
    + CourseCounter.TABLE_NAME + "_uuid = ? AND version = ?")
@EqualsAndHashCode(callSuper = false)
public class CourseCounter extends SoftDeletable {

  public static final String TABLE_NAME = "course_counter";

  @Id
  @Column(name = CourseCounter.TABLE_NAME + "_uuid")
  @AssignedOrGeneratedUuid
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
