package com.jaworski.serialprotocol.dto.custom;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrainerDTO {

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
    if (imagesUuid == null || imagesUuid.isEmpty()) {
      return null;
    }
    if (primaryImageUuid != null && imagesUuid.contains(primaryImageUuid)) {
      return primaryImageUuid;
    }
    return PrimaryImage.minByString(imagesUuid);
  }

  /**
   * Initials for the avatar placeholder shown when there is no photo.
   *
   * <p>Null- and blank-safe on both parts: the columns are NOT NULL in the database, but a
   * record can still reach here through a restore or a hand-made request, and an exception
   * raised during Thymeleaf rendering would take down the whole page rather than one avatar.</p>
   */
  public String getInitials() {
    String initials = firstLetter(name) + firstLetter(surname);
    // A blank disc is indistinguishable from an image that failed to load.
    return initials.isEmpty() ? "·" : initials;
  }

  private static String firstLetter(String value) {
    return value == null || value.isBlank() ? "" : value.trim().substring(0, 1).toUpperCase();
  }

  /**
   * The image ids as a plain comma-separated list, for templates to hand to JavaScript.
   *
   * <p>Rendering the Set itself yields its toString form, {@code [a, b]}, which every
   * consumer then had to strip brackets from and split — the same two-line helper ended
   * up copied into three templates and again into the photo editor. Mirrors
   * {@code CoursesDTO.getTrainerIdsString()}.</p>
   */
  public String getImagesUuidString() {
    return imagesUuid == null ? ""
        : imagesUuid.stream().map(String::valueOf).collect(Collectors.joining(","));
  }

}

