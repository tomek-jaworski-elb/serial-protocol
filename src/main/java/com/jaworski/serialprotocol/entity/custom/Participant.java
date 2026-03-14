package com.jaworski.serialprotocol.entity.custom;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Basic;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Data
@Table(name = Participant.TABLE_NAME)
@NoArgsConstructor
public class Participant {

  protected static final String TABLE_NAME = "participants";

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = Participant.TABLE_NAME + "_uuid", nullable = false, updatable = false)
  private UUID uuid;

  @NotNull
  @Positive
  @Column(name = Participant.TABLE_NAME + "_id", nullable = false, unique = true)
  private Long id;

  @NotBlank
  @Size(max = 100)
  @Column(name = Participant.TABLE_NAME + "_name", nullable = false, length = 100)
  private String name;

  @NotBlank
  @Size(max = 100)
  @Column(name = Participant.TABLE_NAME + "_surname", nullable = false, length = 100)
  private String surname;

  @NotNull
  @Past
  @Column(name = Participant.TABLE_NAME + "_birth_date", nullable = false)
  private LocalDate birthDate;

  @Lob
  @Basic(fetch = FetchType.LAZY)
  @Column(name = Participant.TABLE_NAME + "_photo", columnDefinition = "LONGBLOB")
  @Size(max = 10_000_000)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  private byte[] photo;

}
