package com.jaworski.serialprotocol.service.db.sync;

import com.jaworski.serialprotocol.service.db.sync.SyncTables.SyncTable;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.JacksonException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Reads a sync or backup file into {@link SyncFile}: the manifest, the rows of every table
 * and the blob ids. Rows are small and all held in memory; blob bytes stay in the ZIP.
 *
 * <p>Refuses, with a message for the user, anything that is not a sync file of a format this
 * version understands. Older formats stay readable (docs/sync-spec.md); there is only one so far.</p>
 */
public final class SyncReader {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  public static SyncFile read(Path path) {
    try (ZipFile zip = new ZipFile(path.toFile(), StandardCharsets.UTF_8)) {
      ZipEntry manifestEntry = zip.getEntry(SyncTables.MANIFEST_ENTRY);
      if (manifestEntry == null) {
        throw new SyncRefusedException("This is not a sync file: it has no manifest.json.");
      }
      SyncManifest manifest;
      try (var in = zip.getInputStream(manifestEntry)) {
        manifest = MAPPER.readValue(in, SyncManifest.class);
      } catch (JacksonException e) {
        throw new SyncRefusedException("This is not a sync file: the manifest cannot be read.", e);
      }
      if (manifest.format() > SyncManifest.CURRENT_FORMAT || manifest.format() < 1) {
        throw new SyncRefusedException("The file was written in format " + manifest.format()
            + " by a newer version of the program; this version reads format " + SyncManifest.CURRENT_FORMAT + ".");
      }
      if (manifest.node() == null || manifest.node().id() == null) {
        throw new SyncRefusedException("This is not a sync file: the manifest names no node.");
      }
      Map<String, List<SyncRow>> tables = new LinkedHashMap<>();
      for (SyncTable table : SyncTables.TABLES) {
        tables.put(table.name(), readRows(zip, table));
      }
      Set<String> blobs = new LinkedHashSet<>();
      Set<String> thumbs = new LinkedHashSet<>();
      for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
        String name = entries.nextElement().getName();
        if (name.startsWith(SyncTables.BLOBS_DIR) && name.endsWith(".bin")) {
          blobs.add(name.substring(SyncTables.BLOBS_DIR.length(), name.length() - 4));
        } else if (name.startsWith(SyncTables.THUMBS_DIR) && name.endsWith(".bin")) {
          thumbs.add(name.substring(SyncTables.THUMBS_DIR.length(), name.length() - 4));
        }
      }
      return new SyncFile(manifest, tables, blobs, thumbs);
    } catch (ZipException e) {
      throw new SyncRefusedException("This is not a sync file: it is not a ZIP archive.", e);
    } catch (IOException e) {
      throw new SyncRefusedException("The file cannot be read: " + e.getMessage(), e);
    }
  }

  private static List<SyncRow> readRows(ZipFile zip, SyncTable table) throws IOException {
    ZipEntry entry = zip.getEntry(SyncTables.tableEntry(table.name()));
    List<SyncRow> rows = new ArrayList<>();
    if (entry == null) {
      return rows;
    }
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8))) {
      String line;
      int number = 0;
      while ((line = reader.readLine()) != null) {
        number++;
        if (line.isBlank()) {
          continue;
        }
        try {
          Map<String, Object> values = MAPPER.readValue(line, new tools.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() { });
          rows.add(new SyncRow(values));
        } catch (JacksonException e) {
          throw new SyncRefusedException("Line " + number + " of " + SyncTables.tableEntry(table.name())
              + " is not a JSON row.", e);
        }
      }
    }
    return rows;
  }

  private SyncReader() {
  }
}
