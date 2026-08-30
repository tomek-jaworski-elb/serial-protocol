package com.jaworski.serialprotocol.dto.custom;

import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

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
}
