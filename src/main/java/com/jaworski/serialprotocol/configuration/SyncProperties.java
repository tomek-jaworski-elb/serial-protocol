package com.jaworski.serialprotocol.configuration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.time.Duration;

/**
 * The {@code sync.*} properties of the USB-stick synchronisation (docs/sync-spec.md).
 *
 * <p>The mode is read once at start and never changed from the UI: an installation is the
 * one that edits ({@link Mode#ACTIVE}, the default) or one that only browses until the next
 * sync file arrives ({@link Mode#READ_ONLY}). A read-only computer still accepts edits, but
 * every page carries a warning bar and the next import reports them as collisions.</p>
 */
@Component
@ConfigurationProperties(prefix = "sync")
@Validated
public class SyncProperties {

  public enum Mode { ACTIVE, READ_ONLY }

  @NotNull
  private Mode mode = Mode.ACTIVE;

  /** Human-readable name of this installation; blank means the host name. */
  private String nodeName = "";

  /** Where {@code .bak} files are written before an import and on request. */
  @NotNull
  private Path backupDir = Path.of("sync-backups");

  /** How many backups to keep; 0 keeps them all. */
  @Min(0)
  private int backupKeep = 20;

  /** Where uploaded sync files wait between the report and the apply. */
  @NotNull
  private Path workDir = Path.of(System.getProperty("java.io.tmpdir"), "serial-protocol-sync");

  /** Staged uploads older than this are removed. */
  @NotNull
  private Duration workTtl = Duration.ofHours(2);

  /** The largest sync file the upload accepts; also the servlet multipart limit. */
  @NotNull
  private DataSize maxFileSize = DataSize.ofGigabytes(2);

  public Mode getMode() { return mode; }
  public void setMode(Mode mode) { this.mode = mode; }
  public String getNodeName() { return nodeName; }
  public void setNodeName(String nodeName) { this.nodeName = nodeName; }
  public Path getBackupDir() { return backupDir; }
  public void setBackupDir(Path backupDir) { this.backupDir = backupDir; }
  public int getBackupKeep() { return backupKeep; }
  public void setBackupKeep(int backupKeep) { this.backupKeep = backupKeep; }
  public Path getWorkDir() { return workDir; }
  public void setWorkDir(Path workDir) { this.workDir = workDir; }
  public Duration getWorkTtl() { return workTtl; }
  public void setWorkTtl(Duration workTtl) { this.workTtl = workTtl; }
  public DataSize getMaxFileSize() { return maxFileSize; }
  public void setMaxFileSize(DataSize maxFileSize) { this.maxFileSize = maxFileSize; }

  public boolean isReadOnly() {
    return mode == Mode.READ_ONLY;
  }
}
