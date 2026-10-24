package com.jaworski.serialprotocol.entity.custom;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

/**
 * A photo's identity and metadata. The bytes live in {@link ImageBlob} under the same uuid.
 *
 * <p>Split on purpose: field-level laziness is inactive in this build, so any load of a row
 * that held the LONGBLOBs read them too. With the bytes in their own table every write path
 * (resolving a person's photo set, deleting a person) and every list is cheap, and the two
 * LONGBLOBs are read solely by {@code ImageService} when bytes are actually served or scaled.
 * A soft-deleted image keeps its blob row; purging is a separate, later decision.</p>
 */
@Entity
@SQLRestriction("deleted_at is null")
@SQLDelete(sql = "UPDATE image SET deleted_at = CURRENT_TIMESTAMP(6) WHERE image_uuid = ? AND version = ?")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@Table(name = Image.TABLE_NAME)
public class Image extends Auditable {

  public static final String TABLE_NAME = "image";

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = TABLE_NAME + "_uuid", nullable = false, updatable = false)
  private UUID id;

  @Column(name = TABLE_NAME + "_content_type", nullable = false, length = 100)
  @Size(max = 100)
  private String contentType;

  /** Content type of the stored thumbnail, null when there is none (see thumbVersion). */
  @Column(name = TABLE_NAME + "_thumb_content_type", length = 100)
  @Size(max = 100)
  private String thumbContentType;

  /**
   * Which generator version produced the current thumbnail state.
   *
   * <p>Its own column rather than a meaning piggy-backed onto {@code thumbContentType}:
   * it has to record "already tried and it cannot be done" for formats ImageIO cannot
   * read, which is a state with no content type of its own. It also makes a version
   * bump actually take effect — otherwise raising the target size would change every
   * ETag, make every client refetch, and hand them back the identical old bytes.</p>
   */
  @Column(name = TABLE_NAME + "_thumb_version", length = 16)
  @Size(max = 16)
  private String thumbVersion;

}
