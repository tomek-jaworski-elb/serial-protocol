package com.jaworski.serialprotocol.entity.custom;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * What every synchronisable row has to carry: a version, two timestamps and a tombstone.
 *
 * <ul>
 *   <li>{@link #version} is the optimistic lock. Update forms post it back and the services
 *       compare it with the stored value before applying a change, so two people editing the
 *       same record no longer overwrite each other silently; Hibernate checks it again at
 *       flush. A request without a version is applied unchecked (older clients, tests).</li>
 *   <li>{@link #createdAt} / {@link #updatedAt} are set by Hibernate on insert and on every
 *       dirty flush; they are the "which copy is newer" input of any merge.</li>
 *   <li>Deletes are {@code UPDATE … SET deleted_at = now()} ({@code @SQLDelete} on each entity
 *       class, together with {@code @SQLRestriction("deleted_at is null")}). Hibernate then hides
 *       deleted rows from every JPQL, derived and collection query; only native queries see them,
 *       which is exactly what the uniqueness pre-checks and {@code MAX()} queries want, because a
 *       number or code that belongs to a deleted row is still taken in the unique index. Not
 *       Hibernate's own {@code @SoftDelete}: that one forbids LAZY to-one associations towards
 *       the entity and soft-deletes join-table rows too, which made re-adding a removed photo or
 *       trainer collide with its own tombstone.</li>
 * </ul>
 */
@MappedSuperclass
@Getter
@Setter
public abstract class Auditable {

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /**
   * The tombstone. Written by each entity's {@code @SQLDelete} statement, never by Java code,
   * and read by nothing but a future merge: {@code @SQLRestriction("deleted_at is null")} on
   * every entity keeps such rows out of all queries and collections.
   */
  @Column(name = "deleted_at", insertable = false, updatable = false)
  private Instant deletedAt;
}
