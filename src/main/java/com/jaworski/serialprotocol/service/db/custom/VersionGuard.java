package com.jaworski.serialprotocol.service.db.custom;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * The explicit half of optimistic locking: a form says which version of the record it was opened on, and
 * an edit is applied only to that version.
 *
 * <p>Hibernate checks the version on its own when it flushes, but only for the entity it writes, and on the
 * "load, then set fields" update path the loaded entity is by definition current, so Hibernate would never
 * notice a stale form. This check closes that gap and makes a missing version a loud error rather than a
 * silent overwrite. The exception is Spring's own, so the conflict reaches the same handler whichever half
 * catches it.</p>
 */
public final class VersionGuard {

  private VersionGuard() {
  }

  /**
   * @param type the entity being edited, for the message
   * @param id its identifier, for the message
   * @param current the version in the database
   * @param submitted the version the form carried; {@code null} means the form did not say
   * @throws IllegalArgumentException when the form carried no version
   * @throws ObjectOptimisticLockingFailureException when the record changed since the form was opened
   */
  public static void check(Class<?> type, Object id, Long current, Long submitted) {
    if (submitted == null) {
      throw new IllegalArgumentException(
          "The form did not say which version of the record it was editing. Reload the page and try again.");
    }
    if (!submitted.equals(current)) {
      throw new ObjectOptimisticLockingFailureException(type, id);
    }
  }
}
