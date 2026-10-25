package com.jaworski.serialprotocol.service.db.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sync file format is a contract between installations that may run different builds:
 * every entry the exporter writes for the fixture must match {@code golden/sync/} byte for
 * byte, apart from the manifest fields that name the exporting node and moment.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
class SyncFormatTest {

  static final Path GOLDEN_DIR = Path.of("src/test/resources/golden/sync");

  @Autowired
  private JdbcTemplate jdbc;
  @Autowired
  private SyncExporter exporter;

  private Map<String, byte[]> entries;
  private SyncManifest manifest;

  @BeforeEach
  void exportTheFixture() throws IOException {
    SyncFixture.seed(jdbc);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    manifest = exporter.export(out, false);
    entries = unzip(out.toByteArray());
  }

  @Test
  void theEntriesAreTheGoldenOnesInOrder() throws IOException {
    List<String> golden;
    try (Stream<Path> files = Files.walk(GOLDEN_DIR)) {
      golden = files.filter(Files::isRegularFile)
          .map(p -> GOLDEN_DIR.relativize(p).toString().replace('\\', '/')).sorted().toList();
    }
    assertThat(entries.keySet()).containsExactlyInAnyOrderElementsOf(golden);

    List<String> expectedOrder = new java.util.ArrayList<>();
    expectedOrder.add(SyncTables.MANIFEST_ENTRY);
    SyncTables.TABLES.forEach(t -> expectedOrder.add(SyncTables.tableEntry(t.name())));
    expectedOrder.add(SyncTables.BLOBS_DIR + SyncFixture.IMAGE + ".bin");
    expectedOrder.add(SyncTables.THUMBS_DIR + SyncFixture.IMAGE + ".bin");
    assertThat(entries.keySet()).containsExactlyElementsOf(expectedOrder);
  }

  @Test
  void everyTableMatchesItsGoldenFile() throws IOException {
    for (SyncTables.SyncTable table : SyncTables.TABLES) {
      String name = SyncTables.tableEntry(table.name());
      String actual = new String(entries.get(name), StandardCharsets.UTF_8);
      String expected = Files.readString(GOLDEN_DIR.resolve(name)).replace("\r\n", "\n");
      assertThat(actual).as(name).isEqualTo(expected);
    }
  }

  @Test
  void blobsTravelAsFiles() throws IOException {
    assertThat(entries.get(SyncTables.BLOBS_DIR + SyncFixture.IMAGE + ".bin")).isEqualTo(SyncFixture.PHOTO);
    assertThat(entries.get(SyncTables.THUMBS_DIR + SyncFixture.IMAGE + ".bin")).isEqualTo(SyncFixture.THUMB);
    assertThat(Files.readAllBytes(GOLDEN_DIR.resolve(SyncTables.BLOBS_DIR + SyncFixture.IMAGE + ".bin")))
        .isEqualTo(SyncFixture.PHOTO);
  }

  @Test
  void theManifestDescribesTheFile() throws IOException {
    JsonNode written = new JsonMapper().readTree(entries.get(SyncTables.MANIFEST_ENTRY));
    JsonNode golden = new JsonMapper().readTree(Files.readAllBytes(GOLDEN_DIR.resolve(SyncTables.MANIFEST_ENTRY)));

    assertThat(written.get("format").asInt()).isEqualTo(SyncManifest.CURRENT_FORMAT);
    assertThat(written.get("backup").asBoolean()).isFalse();
    assertThat(written.get("mode").asString()).isEqualTo("ACTIVE");
    assertThat(written.get("node").get("id").asString()).isEqualTo(manifest.node().id().toString());
    assertThat(written.get("node").get("name").asString()).isNotBlank();
    assertThat(SyncValues.parseTimestamp(written.get("exportedAt").asString())).isNotNull();
    assertThat(written.get("rows")).isEqualTo(golden.get("rows"));
    assertThat(written.get("blobs").asLong()).isEqualTo(1);
    assertThat(fieldNames(written)).containsExactlyElementsOf(fieldNames(golden));
    assertThat(golden.get("rows").propertyNames())
        .containsExactlyElementsOf(SyncTables.TABLES.stream().map(SyncTables.SyncTable::name).toList());
  }

  private static List<String> fieldNames(JsonNode node) {
    List<String> names = new java.util.ArrayList<>();
    node.propertyNames().forEach(names::add);
    return names;
  }

  static Map<String, byte[]> unzip(byte[] bytes) throws IOException {
    Map<String, byte[]> result = new LinkedHashMap<>();
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
        result.put(entry.getName(), zip.readAllBytes());
      }
    }
    return result;
  }
}
