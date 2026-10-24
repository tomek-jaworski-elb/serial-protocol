package com.jaworski.serialprotocol.service.db.sync;

import com.jaworski.serialprotocol.configuration.SyncProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
 * and {@link #discard} removes one as soon as it is applied or refused. The same directory
 * holds the {@link #scratch} files an export is spooled through.</p>
 */
@Service
public class SyncStaging {

  private static final Logger LOG = LoggerFactory.getLogger(SyncStaging.class);
  private static final Pattern TOKEN = Pattern.compile("[a-f0-9]{32}");
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String SCRATCH_SUFFIX = ".part";

  /** One staged upload. */
  public record Staged(String token, Path file, String originalName, Instant uploadedAt) {
  }

  private final SyncProperties properties;
  private final Clock clock;

  public SyncStaging(SyncProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Stages an upload by moving the container's temporary file into the work directory
   * ({@link MultipartFile#transferTo}), so a gigabyte file is written to disk once. Nothing
   * is left behind when the move fails.
   */
  public Staged stage(MultipartFile upload) throws IOException {
    return stage(upload.getOriginalFilename(), file -> upload.transferTo(file));
  }

  /** Stages a stream by copying it; for callers that do not hold a multipart upload. */
  public Staged stage(InputStream in, String originalName) throws IOException {
    return stage(originalName, file -> Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING));
  }

  private Staged stage(String originalName, Writer writer) throws IOException {
    purgeExpired();
    String token = token();
    Path dir = Files.createDirectories(properties.getWorkDir().resolve(token));
    Path file = dir.resolve(safeName(originalName));
    try {
      writer.write(file);
    } catch (IOException | RuntimeException e) {
      deleteTree(dir);
      throw e;
    }
    return new Staged(token, file, file.getFileName().toString(), Instant.now(clock));
  }

  /**
   * An empty scratch file under the work directory for a short-lived intermediate (the export
   * is written here before it is streamed). The caller deletes it; one that outlives
   * {@code sync.work-ttl} is removed by {@link #purgeExpired}.
   */
  public Path scratch(String prefix) throws IOException {
    purgeExpired();
    Path work = Files.createDirectories(properties.getWorkDir());
    return Files.createTempFile(work, prefix, SCRATCH_SUFFIX);
  }

  private static String token() {
    byte[] bytes = new byte[16];
    RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  @FunctionalInterface
  private interface Writer {
    void write(Path file) throws IOException;
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
    try (Stream<Path> entries = Files.list(work)) {
      for (Path entry : entries.toList()) {
        String name = entry.getFileName().toString();
        boolean staged = Files.isDirectory(entry) && TOKEN.matcher(name).matches();
        boolean scratch = Files.isRegularFile(entry) && name.endsWith(SCRATCH_SUFFIX);
        if ((staged || scratch) && modified(entry).isBefore(cutoff)) {
          deleteTree(entry);
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
