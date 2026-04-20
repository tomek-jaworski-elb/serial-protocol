package com.jaworski.serialprotocol.entity.custom;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;

import static com.jaworski.serialprotocol.entity.custom.Participant.TABLE_NAME;

@Entity
@Data
@Table(name = TABLE_NAME)
@NoArgsConstructor
@AttributeOverrides({
    @AttributeOverride(name = "uuid",        column = @Column(name = TABLE_NAME + "_uuid",         nullable = false, updatable = false)),
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
public class Participant extends PersonBase {

  public static final String TABLE_NAME = "participants";

  @NotNull
  @Positive
  @Column(name = TABLE_NAME + "_id", nullable = false, unique = true)
  private Long id;

  @Past
  @Column(name = TABLE_NAME + "_birth_date", nullable = true)
  private LocalDate birthDate;

  @OneToOne
  @JoinColumn(name = "image_uuid")
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private Image image;

}
