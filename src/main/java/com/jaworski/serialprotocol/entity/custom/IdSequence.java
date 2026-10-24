package com.jaworski.serialprotocol.entity.custom;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

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
public class IdSequence {

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
}
