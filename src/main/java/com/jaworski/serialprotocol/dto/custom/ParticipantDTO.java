package com.jaworski.serialprotocol.dto.custom;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantDTO {

  private UUID participantUuid;
  /** The participant number; empty on add means "allocate the next free one". */
  @Positive(message = "must be a positive number")
  private Long id;
  @NotBlank(message = "is required")
  @Size(max = 100)
  private String name;
  @NotBlank(message = "is required")
  @Size(max = 100)
  private String surname;
  @Size(max = 1000)
  private String notes;
  @Size(max = 100)
  private String nickname;
  @Email(message = "must be a valid e-mail address")
  @Size(max = 100)
  private String email;
  @Size(max = 26)
  private String phoneNumber;
  @Size(max = 300)
  private String address;

  /** Optimistic-lock version of the stored record; posted back by the update form, null = unchecked. */
  private Long version;
  @DateTimeFormat(pattern = "dd/MM/yyyy")
  @Past(message = "must be in the past")
  private LocalDate birthDate;

  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  private UUID image;

  /**
   * The photo to show as the avatar. A participant holds exactly one image ({@code @OneToOne}),
   * so unlike the multi-image people there is nothing to choose between and no pointer to keep.
   */
  public UUID getAvatarImageUuid() {
    return image;
  }

  /** See {@link PrimaryImage#initials(String, String)} — same contract, same null-/blank-safety. */
  public String getInitials() {
    return PrimaryImage.initials(name, surname);
  }

}

