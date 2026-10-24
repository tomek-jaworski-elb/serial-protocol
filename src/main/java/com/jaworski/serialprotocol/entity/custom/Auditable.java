package com.jaworski.serialprotocol.entity.custom;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

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
 *   <li>Deletes are {@code UPDATE … SET deleted_at = CURRENT_TIMESTAMP(6)} ({@code @SQLDelete} on
 *       each entity class, together with {@code @SQLRestriction("deleted_at is null")}). The
 *       database fills that one in its session time zone, which the MariaDB pool pins to UTC
 *       ({@code connection-init-sql}) so it lines up with the UTC wall time Hibernate writes for
 *       the other two. Hibernate then hides
 *       deleted rows from every JPQL, derived and collection query; only native queries see them,
 *       which is exactly what the uniqueness pre-checks and {@code MAX()} queries want, because a
 *       number or code that belongs to a deleted row is still taken in the unique index. Not
 *       Hibernate's own {@code @SoftDelete}: that one forbids LAZY to-one associations towards
 *       the entity and soft-deletes join-table rows too, which made re-adding a removed photo or
 *       trainer collide with its own tombstone.</li>
 *   <li>{@link #originNode} names the installation that wrote the row last, stamped by
 *       {@link OriginStamper}. The sync import uses it to tell this node's own earlier state
 *       coming back on the stick from a competing edit made elsewhere.</li>
 * </ul>
 */
@MappedSuperclass
@EntityListeners(OriginStamper.class)
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

  /**
   * The {@code sync_node.node_id} of the installation that inserted or last updated the row.
   * NULL for rows written before V10, which an import treats as "origin unknown".
   */
  @Column(name = "origin_node")
  private UUID originNode;
}
