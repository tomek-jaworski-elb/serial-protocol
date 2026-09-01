package com.jaworski.serialprotocol.dto.custom;

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
  private String name;
  private String surname;
  private String notes;
  private String nickname;
  private String email;
  private String phoneNumber;
  private String address;

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

