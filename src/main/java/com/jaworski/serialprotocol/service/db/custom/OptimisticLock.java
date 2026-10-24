package com.jaworski.serialprotocol.service.db.custom;

import org.springframework.dao.OptimisticLockingFailureException;

/**
 * The version check every update runs before touching a record.
 *
 * <p>Done explicitly on the loaded entity rather than by merging a detached one that carries
 * the version: the services load the record and copy the request onto it, so Hibernate's own
 * check at flush would only fire for a concurrent change between load and commit. This one
 * fires when the form was rendered from an older copy, which is the common case.</p>
 */
public final class OptimisticLock {

  private OptimisticLock() {
  }

  /**
   * @param requested the version the request carries, or {@code null} to skip the check
   * @param current   the version the stored record has now
   * @throws OptimisticLockingFailureException when the two differ
   */
  public static void check(Long requested, long current, String what) {
    if (requested != null && requested != current) {
      throw new OptimisticLockingFailureException(
          what + " was changed by someone else in the meantime (version " + requested + " was expected, "
              + current + " is stored). Reload the page and apply the change again.");
    }
  }
}
