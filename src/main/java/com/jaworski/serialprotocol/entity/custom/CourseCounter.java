package com.jaworski.serialprotocol.entity.custom;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.UUID;

@Entity
@SQLRestriction("deleted_at is null")
@SQLDelete(sql = "UPDATE course_counter SET deleted_at = CURRENT_TIMESTAMP(6) WHERE course_counter_uuid = ? AND version = ?")
@Table(name = CourseCounter.TABLE_NAME)
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
public class CourseCounter extends Auditable {

  public static final String TABLE_NAME = "course_counter";

  @Id
  @Column(name = CourseCounter.TABLE_NAME + "_uuid")
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID uuid;

  @NotNull
  @Positive
  @Column(name = CourseCounter.TABLE_NAME + "_counter", unique = true, nullable = false)
  private Long counter;

  // LAZY for the same reason as Participant.image: the id comes off the proxy.
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "image_uuid", unique = true)
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private Image image;

}
