package com.jaworski.serialprotocol.service.db.sync;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** The names of sync and backup files: {@code <node>-<yyyyMMdd-HHmm>.sync}, {@code <node>-<yyyyMMdd-HHmmss>-<reason>.bak}. */
public final class SyncFileNames {

  public static final String SYNC_EXTENSION = ".sync";
  public static final String BACKUP_EXTENSION = ".bak";

  private static final DateTimeFormatter EXPORT_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.ROOT);
  private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

  public static String exportName(String nodeName, Instant at, ZoneId zone) {
    return safe(nodeName) + "-" + EXPORT_STAMP.format(at.atZone(zone)) + SYNC_EXTENSION;
  }

  public static String backupName(String nodeName, Instant at, ZoneId zone, String reason) {
    return safe(nodeName) + "-" + BACKUP_STAMP.format(at.atZone(zone)) + "-" + safe(reason) + BACKUP_EXTENSION;
  }

  /** Letters, digits, dot, underscore and dash only; anything else becomes a dash. */
  static String safe(String name) {
    String cleaned = name == null ? "" : name.strip().replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
    return cleaned.isEmpty() ? "node" : cleaned;
  }

  private SyncFileNames() {
  }
}
