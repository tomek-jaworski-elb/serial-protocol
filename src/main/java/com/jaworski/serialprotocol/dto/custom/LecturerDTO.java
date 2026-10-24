package com.jaworski.serialprotocol.dto.custom;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LecturerDTO {

  private UUID id;
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

  @ToString.Exclude
  @EqualsAndHashCode.Exclude
  private Set<UUID> imagesUuid = new HashSet<>();

  /**
   * Which photo represents this person; advisory, see {@link #getAvatarImageUuid()}.
   */
  private UUID primaryImageUuid;

  /**
   * The photo to show as the avatar, or {@code null} when there is none.
   *
   * <p>The stored pointer is only a preference: it is honoured when it still belongs to the
   * set, and otherwise a deterministic fallback takes over. That keeps a record whose pointer
   * was never set — every record right after the column was added, and every record after a
   * restore — showing a real photo rather than initials.</p>
   */
  public UUID getAvatarImageUuid() {
    return PrimaryImage.avatarImageUuid(imagesUuid, primaryImageUuid);
  }

  /**
   * Initials for the avatar placeholder shown when there is no photo. See
   * {@link PrimaryImage#initials(String, String)} for null-/blank-safety rationale.
   */
  public String getInitials() {
    return PrimaryImage.initials(name, surname);
  }

  /**
   * The image ids as a plain comma-separated list, for templates to hand to JavaScript.
   * See {@link PrimaryImage#imagesUuidString(Set)}.
   */
  public String getImagesUuidString() {
    return PrimaryImage.imagesUuidString(imagesUuid);
  }

}

