package com.jaworski.serialprotocol.service.db.sync;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

/**
 * How a column value is written into a {@code tables/*.jsonl} line (docs/sync-spec.md).
 *
 * <p>Decided by the JDBC type, not by the Java class the driver happens to return, so H2
 * and MariaDB produce the same text: UUIDs, dates and timestamps are strings, numbers are
 * numbers, binary columns (the legacy photos) are Base64, NULL is {@code null}. A UUID column
 * is recognised by its type name, because H2 reports the type as BINARY(16) and MariaDB as CHAR. Timestamps
 * are read through a UTC calendar and written with six fraction digits and a {@code Z}: the
 * audit columns hold UTC instants on both databases, and the legacy {@code student} dates
 * come back through the same calendar on import, so they survive the round trip unchanged.</p>
 */
public final class SyncValues {

  public static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.ROOT).withZone(ZoneOffset.UTC);

  private static final Calendar UTC = Calendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC));

  public static String columnName(ResultSetMetaData meta, int column) throws SQLException {
    return meta.getColumnLabel(column).toLowerCase(Locale.ROOT);
  }

  public static Object read(ResultSet rs, ResultSetMetaData meta, int column) throws SQLException {
    switch (meta.getColumnType(column)) {
      case Types.TIMESTAMP_WITH_TIMEZONE -> {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : TIMESTAMP.format(value.toInstant());
      }
      case Types.TIMESTAMP -> {
        Timestamp value = rs.getTimestamp(column, (Calendar) UTC.clone());
        return value == null ? null : TIMESTAMP.format(value.toInstant());
      }
      case Types.DATE -> {
        LocalDate value = rs.getObject(column, LocalDate.class);
        return value == null ? null : value.toString();
      }
      case Types.BLOB, Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> {
        if (isUuid(meta, column)) {
          UUID value = rs.getObject(column, UUID.class);
          return value == null ? null : value.toString();
        }
        byte[] value = rs.getBytes(column);
        return value == null ? null : Base64.getEncoder().encodeToString(value);
      }
      default -> {
        Object value = rs.getObject(column);
        if (value == null || value instanceof Number || value instanceof String || value instanceof Boolean) {
          return value;
        }
        if (value instanceof UUID uuid) {
          return uuid.toString();
        }
        return value.toString();
      }
    }
  }

  /** H2 reports its native {@code UUID} type as BINARY(16); MariaDB as CHAR. Both name it UUID. */
  private static boolean isUuid(ResultSetMetaData meta, int column) throws SQLException {
    return "UUID".equalsIgnoreCase(meta.getColumnTypeName(column));
  }

  /**
   * Binds a file value to a column, the inverse of {@link #read}: again decided by the JDBC
   * type, so the text written by one database is accepted by the other. Numbers may arrive as
   * any {@code Number} (Jackson picks the smallest that fits) or as text (a key parsed out of
   * a collision id).
   */
  public static void bind(PreparedStatement ps, int index, Object value, int jdbcType, String typeName)
      throws SQLException {
    if (value == null) {
      ps.setNull(index, jdbcType);
      return;
    }
    switch (jdbcType) {
      case Types.TIMESTAMP_WITH_TIMEZONE ->
          ps.setObject(index, OffsetDateTime.ofInstant(parseTimestamp(value.toString()), ZoneOffset.UTC));
      case Types.TIMESTAMP ->
          ps.setTimestamp(index, Timestamp.from(parseTimestamp(value.toString())), (Calendar) UTC.clone());
      case Types.DATE -> ps.setObject(index, LocalDate.parse(value.toString()));
      case Types.BLOB, Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> {
        if ("UUID".equalsIgnoreCase(typeName)) {
          ps.setObject(index, UUID.fromString(value.toString()));
        } else {
          ps.setBytes(index, Base64.getDecoder().decode(value.toString()));
        }
      }
      case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT ->
          ps.setLong(index, value instanceof Number number ? number.longValue() : Long.parseLong(value.toString()));
      default -> {
        if ("UUID".equalsIgnoreCase(typeName)) {
          ps.setObject(index, UUID.fromString(value.toString()));
        } else {
          ps.setObject(index, value);
        }
      }
    }
  }

  /** Parses what {@link #TIMESTAMP} wrote. */
  public static Instant parseTimestamp(String text) {
    return Instant.from(TIMESTAMP.parse(text));
  }

  private SyncValues() {
  }
}
