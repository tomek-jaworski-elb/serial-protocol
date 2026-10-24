package com.jaworski.serialprotocol.entity.custom;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.domain.Persistable;

/**
 * The next free value of one human-visible business number (participant id, course id,
 * course-counter number). One row per sequence, read with a row lock by {@code IdAllocator}.
 *
 * <p>Replaces {@code COALESCE(MAX(id),0)+1} computed at page render, which handed the same
 * number to every form that was open at the same time. A locked row serialises the
 * allocations of one database; what it does not do is make numbers unique across several
 * databases — that needs per-node ranges or one allocating node, a decision deferred to the
 * synchronisation work, and this table is where either variant will be implemented.</p>
 */
@Entity
@Table(name = IdSequence.TABLE_NAME)
@Data
@NoArgsConstructor
public class IdSequence implements Persistable<String> {

  public static final String TABLE_NAME = "id_sequence";

  @Id
  @Column(name = TABLE_NAME + "_name", nullable = false, updatable = false, length = 32)
  private String name;

  @Column(name = TABLE_NAME + "_next", nullable = false)
  private long next;

  public IdSequence(String name, long next) {
    this.name = name;
    this.next = next;
  }

  /**
   * Spring Data cannot tell a new row from a detached one when the id is assigned and there is
   * no version, so save() would merge: a SELECT that never matches plus a copy of the whole
   * state. Saying "new until persisted or loaded" turns that into a plain INSERT.
   */
  @Transient
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private boolean persisted;

  /** The name is the key; Persistable asks for it under this name. */
  @Override
  public String getId() {
    return name;
  }

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
