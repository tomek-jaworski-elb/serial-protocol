package com.jaworski.serialprotocol.service.db.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Regenerates {@code src/test/resources/golden/sync/} from the fixture:
 * {@code bash mvnw -Dtest=SyncGoldenWriter -Dgolden.write=true test}. Review the diff before
 * committing it: a changed golden file is a changed sync format.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
@EnabledIfSystemProperty(named = "golden.write", matches = "true")
class SyncGoldenWriter {

  @Autowired
  private JdbcTemplate jdbc;
  @Autowired
  private SyncExporter exporter;

  @Test
  void writeGoldenFiles() throws Exception {
    SyncFixture.seed(jdbc);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    exporter.export(out, false);
    Path dir = SyncFormatTest.GOLDEN_DIR;
    Files.createDirectories(dir);
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
      for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
        Path target = dir.resolve(entry.getName());
        Files.createDirectories(target.getParent());
        Files.write(target, zip.readAllBytes());
      }
    }
  }
}
