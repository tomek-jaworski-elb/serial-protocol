package com.jaworski.serialprotocol.entity.custom;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * The identity of this installation in the synchronisation: one row, never exported.
 *
 * <p>{@code V10__sync.sql} inserts it with a random id; {@code SyncNodeService} creates it on
 * H2 and renames it from {@code sync.node-name}. The mode is deliberately not here: it is a
 * property read at start. Copying a database volume copies this row too, which is why a new
 * installation must start empty and import a sync file instead (docs/sync-spec.md).</p>
 */
@Entity
@Table(name = SyncNode.TABLE_NAME)
@Data
@NoArgsConstructor
public class SyncNode implements Persistable<UUID> {

  public static final String TABLE_NAME = "sync_node";

  @Id
  @Column(name = "node_id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "node_name", nullable = false, length = 100)
  private String name;

  @Column(name = "last_export_at")
  private Instant lastExportAt;

  @Column(name = "last_import_at")
  private Instant lastImportAt;

  @Column(name = "last_import_file", length = 255)
  private String lastImportFile;

  /** The node that made the last imported file, and that file's {@code exportedAt}: the base the next file from the same node is compared against. */
  @Column(name = "last_import_node")
  private UUID lastImportNode;

  @Column(name = "last_import_exported_at")
  private Instant lastImportExportedAt;

  /** The later of the last export and the last import; local changes are measured from it. */
  @Column(name = "last_sync_at")
  private Instant lastSyncAt;

  public SyncNode(UUID id, String name) {
    this.id = id;
    this.name = name;
  }

  /** Assigned id and no version: see {@link IdSequence#isNew()} for why this is needed. */
  @Transient
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private boolean persisted;

  @Override
  public boolean isNew() {
    return !persisted;
  }

  @PostPersist
  @PostLoad
  void markPersisted() {
    persisted = true;
  }
}
