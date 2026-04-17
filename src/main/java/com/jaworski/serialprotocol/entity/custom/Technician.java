package com.jaworski.serialprotocol.entity.custom;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import static com.jaworski.serialprotocol.entity.custom.Technician.TABLE_NAME;

@Entity
@Data
@NoArgsConstructor
@Table(name = TABLE_NAME)
@AttributeOverrides({
    @AttributeOverride(name = "uuid",    column = @Column(name = TABLE_NAME +"_uuid",    nullable = false, updatable = false)),
    @AttributeOverride(name = "name",    column = @Column(name = TABLE_NAME+ "_name",    nullable = false, length = 100)),
    @AttributeOverride(name = "surname", column = @Column(name = TABLE_NAME+ "_surname", nullable = false, length = 100))
})
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Technician extends PersonBase {

  public static final String TABLE_NAME = "technician";

  @Column(name = TABLE_NAME + "_email", nullable = true, length = 100)
  @NotBlank
  @Email
  @Size(max = 100)
  private String email;

  @Column(name = TABLE_NAME + "_nickname", nullable = true, length = 100)
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
