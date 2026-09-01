package com.jaworski.serialprotocol.dto.custom;

import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Decides which photo represents a person.
 *
 * <p>Lives next to the DTOs so both layers can share one implementation: the services call
 * {@link #resolve} when persisting, and the DTOs call {@link #minByString} when rendering.
 * Services already depend on this package, so nothing is inverted.</p>
 */
public final class PrimaryImage {

  private PrimaryImage() {
  }

  /**
   * Smallest uuid by its text form.
   *
   * <p>Not {@code Collections.min}: {@link UUID#compareTo} compares both halves as
   * <em>signed</em> longs, so {@code ffffffff-…} sorts before {@code 00000001-…}. Anyone
   * reading "the smallest uuid" — including a future JavaScript implementation sorting
   * strings — expects the lexicographic order, so that is the order written down here.</p>
   */
  public static UUID minByString(Set<UUID> candidates) {
    if (candidates == null || candidates.isEmpty()) {
      return null;
    }
    return candidates.stream().min(Comparator.comparing(UUID::toString)).orElse(null);
  }

  /**
   * The pointer a person should end up with after their image set changed.
   *
   * <p>Resolution order, and the reason for each step:</p>
   * <ol>
   *   <li>An explicit choice from the form wins.</li>
   *   <li>Otherwise the pointer already stored — a request that says nothing about the primary
   *       photo must not change it. Same rule as {@code removeImageUuids}: absent means
   *       "leave it alone", never "clear it".</li>
   *   <li>If that pointer is gone (the photo was just removed, or the record never had one),
   *       fall back — but <strong>prefer photos that were already there</strong>. Falling back
   *       over the merged set would let a photo uploaded in this very request become the
   *       avatar, so adding a fourth photo would silently change the face shown in the table
   *       for records that never had a pointer, and leave records that did have one alone.
   *       The same user action must not have two different outcomes depending on invisible
   *       history.</li>
   * </ol>
   *
   * @param requested       the pointer sent with the request, or {@code null} if none was sent
   * @param storedPointer   the pointer currently held by the entity
   * @param previousImages  the image ids the entity held before this request
   * @param mergedImages    the image ids it will hold after it
   */
  public static UUID resolve(UUID requested, UUID storedPointer,
                             Set<UUID> previousImages, Set<UUID> mergedImages) {
    if (mergedImages == null || mergedImages.isEmpty()) {
      return null;
    }

    UUID candidate = requested != null ? requested : storedPointer;
    if (candidate != null && mergedImages.contains(candidate)) {
      return candidate;
    }

    Set<UUID> carriedOver = previousImages == null ? Set.of()
        : previousImages.stream().filter(mergedImages::contains).collect(java.util.stream.Collectors.toSet());
    return minByString(carriedOver.isEmpty() ? mergedImages : carriedOver);
  }

  /**
   * The photo to show as the avatar for a person with multiple images: the stored pointer
   * when it still belongs to the set, otherwise the deterministic fallback.
   *
   * <p>Shared by {@code LecturerDTO}/{@code TrainerDTO}/{@code TechnicianDTO} — see their
   * {@code getAvatarImageUuid()} javadoc for the full rationale.</p>
   */
  public static UUID avatarImageUuid(Set<UUID> imagesUuid, UUID primaryImageUuid) {
    if (imagesUuid == null || imagesUuid.isEmpty()) {
      return null;
    }
    if (primaryImageUuid != null && imagesUuid.contains(primaryImageUuid)) {
      return primaryImageUuid;
    }
    return minByString(imagesUuid);
  }

  /**
   * Initials for the avatar placeholder shown when there is no photo.
   *
   * <p>Null- and blank-safe on both parts: the columns are NOT NULL in the database, but a
   * record can still reach here through a restore or a hand-made request, and an exception
   * raised during Thymeleaf rendering would take down the whole page rather than one avatar.</p>
   */
  public static String initials(String name, String surname) {
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
   * consumer then had to strip brackets from and split. Mirrors
   * {@code CoursesDTO.getTrainerIdsString()}.</p>
   */
  public static String imagesUuidString(Set<UUID> imagesUuid) {
    return imagesUuid == null ? ""
        : imagesUuid.stream().map(String::valueOf).collect(Collectors.joining(","));
  }
}
