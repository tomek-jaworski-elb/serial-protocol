package com.jaworski.serialprotocol.entity.custom;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@Table(name = Lecturer.TABLE_NAME)
public class Lecturer {

  public static final String TABLE_NAME = "lecturer";

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = TABLE_NAME + "_uuid", nullable = false, updatable = false)
  private UUID uuid;

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

  @Column(name = TABLE_NAME + "_nickname", nullable = false, length = 100)
  @NotBlank
  @Size(max = 100)
  private String nickname;

  @ManyToMany()
  @JoinTable(
      name = TABLE_NAME + "_image",
      joinColumns = @JoinColumn(name = TABLE_NAME + "_uuid"),
      inverseJoinColumns = @JoinColumn(name = "image_uuid")
  )
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  private Set<Image> images = new HashSet<>();
}
