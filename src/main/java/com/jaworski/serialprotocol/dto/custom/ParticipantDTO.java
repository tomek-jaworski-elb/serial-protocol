package com.jaworski.serialprotocol.dto.custom;

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
  private Long id;
  private String name;
  private String surname;
  private String notes;
  private String nickname;
  private String email;
  private String phoneNumber;
  private String address;
  @DateTimeFormat(pattern = "dd/MM/yyyy")
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

  /** See {@code TrainerDTO#getInitials()} — same contract, same null- and blank-safety. */
  public String getInitials() {
    String initials = firstLetter(name) + firstLetter(surname);
    return initials.isEmpty() ? "·" : initials;
  }

  private static String firstLetter(String value) {
    return value == null || value.isBlank() ? "" : value.trim().substring(0, 1).toUpperCase();
  }

}

