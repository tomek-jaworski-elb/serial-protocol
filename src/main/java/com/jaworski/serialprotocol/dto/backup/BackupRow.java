package com.jaworski.serialprotocol.dto.backup;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.jaworski.serialprotocol.entity.custom.SoftDeletable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One record of a soft-deletable kind in a backup: the record's own fields, plus when and by whom it was
 * deleted ({@code null} for an active one).
 *
 * <p>The record is written unwrapped, so the JSON of a 1.1 backup is the 1.0 shape with two extra properties
 * per element. A 1.0 file therefore still reads: its rows simply come back active. {@code version} is not
 * part of a backup; restored rows start again at 0.</p>
 *
 * @param <T> the record's form DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BackupRow<T> {

  @JsonUnwrapped
  private T record;

  private LocalDateTime deletedAt;

  private String deletedBy;

  public static <T> BackupRow<T> of(T record, SoftDeletable entity) {
    return new BackupRow<>(record, entity.getDeletedAt(), entity.getDeletedBy());
  }

  /** Carries the deletion state over to the entity being restored. */
  public void applyTo(SoftDeletable entity) {
    entity.setDeletedAt(deletedAt);
    entity.setDeletedBy(deletedBy);
  }
}
