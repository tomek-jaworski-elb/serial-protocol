package com.jaworski.serialprotocol.entity.custom;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.DynamicUpdate;

import java.util.UUID;

/**
 * The bytes of an {@link Image}, keyed by the same uuid and loaded only on purpose.
 *
 * <p>Deliberately not an association: a to-one from {@code Image} to this row would have to
 * be LAZY to keep lists cheap, and Hibernate cannot proxy the inverse side of a shared-key
 * one-to-one without bytecode enhancement. {@code ImageService} reads this table by id when a
 * request wants the bytes, and nothing else ever does; {@code ListPagesLoadNoImageBlobsTest}
 * fails if a list page loads one. Not versioned and not soft-deleted: the {@code Image} row is
 * the record, this is its payload, immutable apart from the thumbnail columns.</p>
 */
@Entity
@Data
@NoArgsConstructor
/*
 * Storing a thumbnail must not rewrite the full-size LONGBLOB in the same UPDATE.
 */
@DynamicUpdate
@Table(name = ImageBlob.TABLE_NAME)
public class ImageBlob {

  public static final String TABLE_NAME = "image_blob";

  /** The {@link Image#getId()} this payload belongs to; assigned, never generated. */
  @Id
  @Column(name = "image_uuid", nullable = false, updatable = false)
  private UUID id;

  @Lob
  @Column(name = "image_data", columnDefinition = "LONGBLOB", nullable = false)
  @Size(max = 10_000_000)
  @ToString.Exclude
  private byte[] data;

  /**
   * Downscaled copy, made at upload and kept so it is produced once. Null for rows whose
   * format ImageIO cannot read or that are already small; {@link Image#getThumbVersion()}
   * says whether that verdict is current.
   */
  @Lob
  @Column(name = "image_thumb_data", columnDefinition = "LONGBLOB")
  @ToString.Exclude
  private byte[] thumbData;

  public ImageBlob(UUID id, byte[] data) {
    this.id = id;
    this.data = data;
  }
}
