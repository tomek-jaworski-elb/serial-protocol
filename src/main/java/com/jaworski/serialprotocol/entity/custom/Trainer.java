package com.jaworski.serialprotocol.entity.custom;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Data
@NoArgsConstructor
@Table(name = Trainer.TABLE_NAME)
public class Trainer {

  protected static final String TABLE_NAME = "trainer";

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  @Column(name = TABLE_NAME + "_id", nullable = false, updatable = false)
  private long id;

  @Column(name = TABLE_NAME + "_name", nullable = false, length = 100)
  @NotBlank
  @Size(max = 100)
  private String name;

  @Column(name = TABLE_NAME + "_surname", nullable = false, length = 100)
  @NotBlank
  @Size(max = 100)
  private String surname;

  @Column(name = TABLE_NAME + "_email", nullable = false, length = 100)
  @NotBlank
  @Email
  @Size(max = 100)
  private String email;

  @Lob
  @Basic(fetch = FetchType.LAZY)
  @Column(name = Trainer.TABLE_NAME + "_photo", columnDefinition = "LONGBLOB")
  @Size(max = 10_000_000)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  private byte[] photo;

}
