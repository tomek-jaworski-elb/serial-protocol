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
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@Table(name = Image.TABLE_NAME)
public class Image {

  public static final String TABLE_NAME = "image";

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = TABLE_NAME + "_uuid", nullable = false, updatable = false)
  private UUID id;

  @Lob
  @Column(name = TABLE_NAME + "_data", columnDefinition = "LONGBLOB", nullable = false)
  @Basic(fetch = FetchType.LAZY)
  @Size(max = 10_000_000)
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  private byte[] data;

  @Column(name = TABLE_NAME + "_content_type", nullable = false, length = 100)
  @Size(max = 100)
  private String contentType;

}
