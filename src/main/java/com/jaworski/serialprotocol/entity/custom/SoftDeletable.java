package com.jaworski.serialprotocol.entity.custom;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;

import java.time.LocalDateTime;

/**
 * Shared base of every entity that is hidden instead of removed when a user deletes it.
 *
 * <p>A row is active while {@code deleted_at} is {@code NULL}. The filter below is switched on for every
 * session automatically and also applies to lookups by id, so ordinary queries, {@code findById},
 * {@code existsById} and the "referenced by courses" guards never see a deleted row. Code that has to see them
 * (admin view, restore, id and unique-value checks) goes through {@code SoftDeleteSupport#withDeleted}.</p>
 *
 * <p>Column names are shared by all tables on purpose, so one filter condition fits every entity. The fields stay
 * out of {@code equals}/{@code hashCode} (no {@code @Data} here): entities live in {@code Set}s, and marking a row
 * deleted or bumping its version must not change its hash.</p>
 *
 * <p>Each subclass declares {@code @SQLDelete} as a safety net: a stray {@code repository.delete(...)} then marks
 * the row instead of removing it. It covers the row only — Hibernate still clears the join tables the entity
 * owns ({@code trainer_image}, {@code courses_trainers}, ...) before it runs that statement, so a stray delete
 * keeps the record but loses its photo and staff links. No production path calls {@code delete} on these
 * entities; the real soft delete is {@code SoftDeleteSupport#softDelete}, which goes through
 * {@link #markDeleted(String)} because {@code @SQLDelete} has no way to record who deleted the row.</p>
 */
@MappedSuperclass
@FilterDef(name = SoftDeletable.ACTIVE_ONLY, autoEnabled = true, applyToLoadByKey = true,
    defaultCondition = "deleted_at IS NULL")
@Filter(name = SoftDeletable.ACTIVE_ONLY)
@Getter
@Setter
public abstract class SoftDeletable {

  public static final String ACTIVE_ONLY = "activeOnly";

  @Column(name = "deleted_at", nullable = true)
  private LocalDateTime deletedAt;

  @Column(name = "deleted_by", nullable = true, length = 100)
  private String deletedBy;

  /**
   * Optimistic-locking version. Every write bumps it, so a form opened before someone else's save carries a
   * stale value and is rejected instead of silently overwriting their change ({@code VersionGuard} in the
   * services, and Hibernate's own check on flush). {@code DEFAULT 0} lets {@code ddl-auto=update} fill the
   * column for rows that predate it. Delete, restore and purge bump it too but never check it.
   */
  @Version
  @Column(name = "version", columnDefinition = "BIGINT DEFAULT 0")
  private Long version;

  public boolean isDeleted() {
    return deletedAt != null;
  }

  public void markDeleted(String user) {
    this.deletedAt = LocalDateTime.now();
    this.deletedBy = user;
  }

  public void restore() {
    this.deletedAt = null;
    this.deletedBy = null;
  }
}
