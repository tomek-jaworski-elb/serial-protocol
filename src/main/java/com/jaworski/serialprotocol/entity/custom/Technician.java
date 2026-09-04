package com.jaworski.serialprotocol.entity.custom;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static com.jaworski.serialprotocol.entity.custom.Technician.TABLE_NAME;

@Entity
@Data
@NoArgsConstructor
@Table(name = TABLE_NAME)
@AttributeOverrides({
    @AttributeOverride(name = "uuid",        column = @Column(name = TABLE_NAME + "_uuid",         nullable = false, updatable = false, unique = true)),
    @AttributeOverride(name = "name",        column = @Column(name = TABLE_NAME + "_name",         nullable = false, length = 100)),
    @AttributeOverride(name = "surname",     column = @Column(name = TABLE_NAME + "_surname",      nullable = false, length = 100)),
    @AttributeOverride(name = "notes",       column = @Column(name = TABLE_NAME + "_notes",        nullable = true, length = 1000)),
    @AttributeOverride(name = "nickname",    column = @Column(name = TABLE_NAME + "_nickname",     nullable = true, length = 100)),
    @AttributeOverride(name = "email",       column = @Column(name = TABLE_NAME + "_email",        nullable = true, length = 100)),
    @AttributeOverride(name = "phoneNumber", column = @Column(name = TABLE_NAME + "_phone_number", nullable = true, length = 26)),
    @AttributeOverride(name = "address",     column = @Column(name = TABLE_NAME + "_address",      nullable = true, length = 300))
})
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Technician extends PersonBase {

  public static final String TABLE_NAME = "technician";


  @ManyToMany()
  @JoinTable(
      name = TABLE_NAME + "_image",
      joinColumns = @JoinColumn(name = TABLE_NAME + "_uuid", referencedColumnName = TABLE_NAME + "_uuid"),
      inverseJoinColumns = @JoinColumn(name = "image_uuid", referencedColumnName = "image_uuid")
  )
  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  private Set<Image> images = new HashSet<>();

  /**
   * Which of {@link #images} represents this person in table listings.
   *
   * <p>A plain uuid, deliberately not a second {@code @OneToOne} to {@code Image}: a real
   * association would force the orphan-image cleanup in the services to clear this FK first,
   * in the right order, in three separate places — and that cleanup is exactly where the last
   * two review rounds found bugs. Readers fall back when the value no longer belongs to the
   * set, so a stale uuid degrades to another photo instead of breaking the page.</p>
   */
  @Column(name = TABLE_NAME + "_primary_image_uuid")
  private UUID primaryImageUuid;
}
