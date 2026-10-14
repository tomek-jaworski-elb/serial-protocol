package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.entity.custom.SoftDeletable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.function.Supplier;

/**
 * The few operations the soft delete needs from outside the entities: who is deleting, marking and
 * restoring a row, and looking at rows the "active only" filter normally hides.
 */
@Component
public class SoftDeleteSupport {

  /** Recorded as {@code deleted_by} when nobody is signed in (tests, background work). */
  public static final String SYSTEM_USER = "system";

  private static final int MAX_USER_LENGTH = 100;

  private static final Logger LOGGER = LoggerFactory.getLogger(SoftDeleteSupport.class);

  @PersistenceContext
  private EntityManager entityManager;

  public String currentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()
        || authentication instanceof AnonymousAuthenticationToken || authentication.getName() == null) {
      return SYSTEM_USER;
    }
    String name = authentication.getName();
    return name.length() <= MAX_USER_LENGTH ? name : name.substring(0, MAX_USER_LENGTH);
  }

  /**
   * Message for a value that is free among active rows but still held by a deleted one. The row keeps its
   * unique value until an administrator restores or purges it, so a new record cannot take it over.
   */
  public String heldByDeletedRecord(String what) {
    return what + " is used by a deleted record. Ask an administrator to restore or purge it.";
  }

  /**
   * The exception for a record a user tried to open, edit or delete that is gone, whether it never existed or
   * someone deleted it meanwhile. The key goes to the log only: the user sees the kind of record, not an id.
   */
  public IllegalArgumentException notFound(String what, Object key) {
    LOGGER.warn("{} {} not found: it never existed or has been deleted", what, key);
    return new IllegalArgumentException(what + " not found. It may have been deleted by someone else.");
  }

  public void markDeleted(SoftDeletable entity) {
    entity.markDeleted(currentUser());
  }

  /**
   * The soft delete a user triggers: records who and when, writes it, and drops the entity from the session.
   *
   * <p>Detaching matters. A session answers {@code findById} from its own cache before it asks the database,
   * and the "active only" filter lives in the database query. Without it, code that deletes a record and then
   * reads it again in the same transaction would still be handed the deleted one.</p>
   */
  public <T extends SoftDeletable> void softDelete(T entity, JpaRepository<T, ?> repository) {
    markDeleted(entity);
    repository.saveAndFlush(entity);
    entityManager.detach(entity);
  }

  public void restore(SoftDeletable entity) {
    entity.restore();
  }

  /**
   * Runs {@code work} with the "active only" filter switched off, then switches it back on.
   *
   * <p>The filter belongs to the session, so this only means something inside a transaction: outside one
   * every repository call opens its own session and the switch would not reach it. That is rejected rather
   * than silently doing nothing. Entities loaded here stay in the session; do not hand them to code that
   * assumes it only ever sees active rows.</p>
   */
  public <T> T withDeleted(Supplier<T> work) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("withDeleted() needs an active transaction");
    }
    Session session = entityManager.unwrap(Session.class);
    if (session.getEnabledFilter(SoftDeletable.ACTIVE_ONLY) == null) {
      // already off: an outer withDeleted owns the switch and puts it back when it returns
      return work.get();
    }
    session.disableFilter(SoftDeletable.ACTIVE_ONLY);
    try {
      return work.get();
    } finally {
      session.enableFilter(SoftDeletable.ACTIVE_ONLY);
    }
  }

  /** Work with no result; may throw a checked exception (a plain lambda infers {@code RuntimeException}). */
  @FunctionalInterface
  public interface ThrowingRunnable<E extends Exception> {
    void run() throws E;
  }

  /** Same as {@link #withDeleted(Supplier)} for work with no result (a separate name keeps lambdas unambiguous). */
  public <E extends Exception> void runWithDeleted(ThrowingRunnable<E> work) throws E {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("runWithDeleted() needs an active transaction");
    }
    Session session = entityManager.unwrap(Session.class);
    if (session.getEnabledFilter(SoftDeletable.ACTIVE_ONLY) == null) {
      work.run();
      return;
    }
    session.disableFilter(SoftDeletable.ACTIVE_ONLY);
    try {
      work.run();
    } finally {
      session.enableFilter(SoftDeletable.ACTIVE_ONLY);
    }
  }
}
