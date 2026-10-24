package com.jaworski.serialprotocol.service.db.sync;

import com.jaworski.serialprotocol.configuration.SyncProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Keeps an uploaded sync file on disk between the report and the apply step.
 *
 * <p>A file can be gigabytes, so it is never held in the session. Each upload gets a random
 * token and its own directory under {@code sync.work-dir}; the token is the only handle the
 * browser gets back. Directories older than {@code sync.work-ttl} are purged on the next upload,
 * and {@link #discard} removes one as soon as it is applied or refused.</p>
 */
@Service
public class SyncStaging {

  private static final Logger LOG = LoggerFactory.getLogger(SyncStaging.class);
  private static final Pattern TOKEN = Pattern.compile("[a-f0-9]{32}");
  private static final SecureRandom RANDOM = new SecureRandom();

  /** One staged upload. */
  public record Staged(String token, Path file, String originalName, Instant uploadedAt) {
  }

  private final SyncProperties properties;
  private final Clock clock;

  public SyncStaging(SyncProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  public Staged stage(InputStream in, String originalName) throws IOException {
    purgeExpired();
    byte[] bytes = new byte[16];
    RANDOM.nextBytes(bytes);
    String token = HexFormat.of().formatHex(bytes);
    Path dir = Files.createDirectories(properties.getWorkDir().resolve(token));
    Path file = dir.resolve(safeName(originalName));
    Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
    return new Staged(token, file, file.getFileName().toString(), Instant.now(clock));
  }

  /** The staged file for a token, empty when the token is malformed, unknown or already purged. */
  public Optional<Staged> find(String token) {
    if (token == null || !TOKEN.matcher(token).matches()) {
      return Optional.empty();
    }
    Path dir = properties.getWorkDir().resolve(token);
    if (!Files.isDirectory(dir)) {
      return Optional.empty();
    }
    try (Stream<Path> files = Files.list(dir)) {
      return files.filter(Files::isRegularFile).findFirst()
          .map(file -> new Staged(token, file, file.getFileName().toString(), modified(dir)));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public void discard(String token) {
    find(token).ifPresent(staged -> deleteTree(staged.file().getParent()));
  }

  /** @return how many stale uploads were removed */
  public int purgeExpired() throws IOException {
    Path work = properties.getWorkDir();
    if (!Files.isDirectory(work)) {
      return 0;
    }
    Instant cutoff = Instant.now(clock).minus(properties.getWorkTtl());
    int purged = 0;
    try (Stream<Path> dirs = Files.list(work)) {
      for (Path dir : dirs.filter(Files::isDirectory).toList()) {
        if (TOKEN.matcher(dir.getFileName().toString()).matches() && modified(dir).isBefore(cutoff)) {
          deleteTree(dir);
          purged++;
        }
      }
    }
    return purged;
  }

  private static Instant modified(Path dir) {
    try {
      return Files.getLastModifiedTime(dir).toInstant();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void deleteTree(Path dir) {
    try (Stream<Path> walk = Files.walk(dir)) {
      for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    } catch (IOException e) {
      LOG.warn("Could not remove staged upload {}", dir, e);
    }
  }

  private static String safeName(String originalName) {
    String name = originalName == null ? "" : Path.of(originalName.replace('\\', '/')).getFileName().toString();
    name = name.replaceAll("[^A-Za-z0-9._-]", "_");
    return name.isBlank() || name.startsWith(".") ? "upload.zip" : name;
  }
}
