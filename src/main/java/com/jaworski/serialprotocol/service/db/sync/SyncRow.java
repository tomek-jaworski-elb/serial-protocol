package com.jaworski.serialprotocol.service.db.sync;

import com.jaworski.serialprotocol.service.db.sync.SyncTables.SyncTable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One row of a synchronised table in file shape: column name to JSON value.
 *
 * <p>The same shape whether the row came out of a {@code tables/*.jsonl} line or straight
 * from the local database through {@link SyncValues}, so the analysis compares the two with
 * one rule: {@link #canonical(Object)} turns every value into text (a JSON {@code 1001} may
 * arrive as an {@code Integer}, the same column through JDBC as a {@code Long}) and the
 * audit columns are read through the accessors below.</p>
 */
public record SyncRow(Map<String, Object> values) {

  public static final String VERSION = "version";
  public static final String CREATED_AT = "created_at";
  public static final String UPDATED_AT = "updated_at";
  public static final String DELETED_AT = "deleted_at";
  public static final String ORIGIN_NODE = "origin_node";

  public Object get(String column) {
    return values.get(column);
  }

  /** The value as the analysis compares it; {@code null} stays {@code null}. */
  public String text(String column) {
    return canonical(values.get(column));
  }

  public Long version() {
    Object value = values.get(VERSION);
    return value instanceof Number number ? number.longValue() : null;
  }

  public Instant createdAt() {
    return instant(CREATED_AT);
  }

  public Instant updatedAt() {
    return instant(UPDATED_AT);
  }

  public Instant deletedAt() {
    return instant(DELETED_AT);
  }

  public boolean isDeleted() {
    return values.get(DELETED_AT) != null;
  }

  public UUID originNode() {
    Object value = values.get(ORIGIN_NODE);
    return value == null ? null : UUID.fromString(value.toString());
  }

  /** Whether the row carries the audit columns at all (join tables and {@code id_sequence} do not). */
  public boolean isAudited() {
    return values.containsKey(VERSION) && values.containsKey(UPDATED_AT);
  }

  /** The primary key as one string, the map key the analysis matches rows by. */
  public String key(SyncTable table) {
    StringBuilder key = new StringBuilder();
    for (String column : table.keyColumns()) {
      if (!key.isEmpty()) {
        key.append('|');
      }
      key.append(text(column));
    }
    return key.toString();
  }

  /** The same four audit facts, textually; two rows with equal state need no decision. */
  public boolean sameStateAs(SyncRow other) {
    return Objects.equals(text(VERSION), other.text(VERSION))
        && Objects.equals(text(UPDATED_AT), other.text(UPDATED_AT))
        && Objects.equals(text(DELETED_AT), other.text(DELETED_AT))
        && Objects.equals(text(ORIGIN_NODE), other.text(ORIGIN_NODE));
  }

  private Instant instant(String column) {
    Object value = values.get(column);
    return value == null ? null : SyncValues.parseTimestamp(value.toString());
  }

  /** Numbers lose their Java class, everything else is its {@code toString()}. */
  public static String canonical(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
    }
    return value.toString();
  }
}
