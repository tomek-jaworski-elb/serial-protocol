package com.jaworski.serialprotocol.service.db.sync;

import com.jaworski.serialprotocol.configuration.SyncProperties;
import com.jaworski.serialprotocol.entity.custom.SyncNode;
import com.jaworski.serialprotocol.service.db.custom.ThumbnailBackfill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The import in two steps: {@link #analyze} turns a staged upload into the report (reads the
 * file, refuses what cannot be imported, reads the local tables and runs {@link SyncAnalyzer})
 * and {@link #apply} loads it once the user has confirmed the report and resolved every collision.
 */
@Service
public class SyncImportService {

  private static final Logger LOG = LoggerFactory.getLogger(SyncImportService.class);

  private final SyncStaging staging;
  private final SyncLocalReader localReader;
  private final SyncNodeService nodeService;
  private final SyncExporter exporter;
  private final SyncProperties properties;
  private final SyncBackupService backups;
  private final SyncApplyService applyService;
  private final ThumbnailBackfill thumbnails;
  private final Clock clock;
  private final JsonMapper mapper = JsonMapper.builder().build();

  public SyncImportService(SyncStaging staging, SyncLocalReader localReader, SyncNodeService nodeService,
      SyncExporter exporter, SyncProperties properties, SyncBackupService backups, SyncApplyService applyService,
      ThumbnailBackfill thumbnails, Clock clock) {
    this.staging = staging;
    this.localReader = localReader;
    this.nodeService = nodeService;
    this.exporter = exporter;
    this.properties = properties;
    this.backups = backups;
    this.applyService = applyService;
    this.thumbnails = thumbnails;
    this.clock = clock;
  }

  /** @throws SyncRefusedException when the file cannot be imported, with the reason for the user */
  public SyncImportPreview analyze(SyncStaging.Staged staged) {
    SyncFile file = SyncReader.read(staged.file());
    SyncNode self = nodeService.current();
    SyncAnalyzer.checkAccepted(file.manifest(), self.getId(), exporter.schemaVersion());
    SyncAnalysis analysis = analyze(file, self, localReader.read());
    try {
      return new SyncImportPreview(staged.token(), staged.originalName(), Files.size(staged.file()),
          file.manifest(), analysis);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * A row whose {@code origin_node} is not a UUID or whose timestamp does not parse is a
   * damaged file, not a server fault: the reader accepts any JSON object as a row and the
   * values are first interpreted here, so the failure becomes a refusal with the reason.
   */
  private SyncAnalysis analyze(SyncFile file, SyncNode self, Map<String, List<SyncRow>> local) {
    // Every audited row's timestamps and origin are parsed here, so a damaged one is refused
    // before the report is shown rather than failing halfway through the apply. Only this
    // check is a refusal: a failure inside the analyzer itself is a fault, not a damaged file.
    try {
      file.tables().values().forEach(rows -> rows.forEach(SyncRow::checkAudit));
    } catch (IllegalArgumentException | DateTimeException e) {
      throw new SyncRefusedException("The file has a damaged row and cannot be imported: " + e.getMessage(), e);
    }
    return SyncAnalyzer.analyze(new SyncAnalyzer.Input(self.getId(), self.getName(),
        self.getLastSyncAt(), properties.isReadOnly(), file.manifest().node(), local, file.tables(),
        ZoneId.systemDefault()));
  }

  /**
   * Step two: a backup, then the file in one transaction with the user's resolutions, then the
   * report next to the backup. The upload is discarded afterwards, and the thumbnails of rows
   * that came without one are rebuilt.
   *
   * <p>The file is analysed again against the current database, so a decision the user made
   * on a stale report still applies to the rows as they are now, or is refused when the
   * collision it concerned is gone.</p>
   *
   * @param resolutions collision id to the chosen resolution code, from the report form
   * @throws SyncRefusedException when the file cannot be imported or a collision has no valid resolution
   */
  public SyncImportResult apply(SyncStaging.Staged staged, Map<String, String> resolutions) throws IOException {
    SyncFile file = SyncReader.read(staged.file());
    SyncNode self = nodeService.current();
    SyncAnalyzer.checkAccepted(file.manifest(), self.getId(), exporter.schemaVersion());
    Map<String, List<SyncRow>> local = localReader.read();
    SyncAnalysis analysis = analyze(file, self, local);
    for (Collision collision : analysis.collisions()) {
      collision.chosen(resolutions);
    }
    Path backup = backups.writeBackup("before-import");
    Instant now = Instant.now(clock);
    SyncApplyService.Outcome outcome = applyService.apply(staged.file(), file, analysis, resolutions, local,
        self.getId(), now, staged.originalName());

    List<SyncImportReport.ResolvedCollision> resolved = new ArrayList<>();
    for (Collision c : analysis.collisions()) {
      resolved.add(new SyncImportReport.ResolvedCollision(c.id(), c.type(), c.table(), c.record(), c.explanation(),
          resolutions.get(c.id())));
    }
    SyncImportReport report = new SyncImportReport(staged.originalName(), file.manifest().node(),
        SyncValues.TIMESTAMP.format(now), backup.getFileName().toString(), outcome.inserted(), outcome.updated(),
        outcome.blobs(), analysis.tables(), analysis.notices(), resolved, outcome.changes());
    Path reportFile = SyncBackupService.reportFor(backup);
    Files.write(reportFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(report));
    staging.discard(staged.token());
    int rebuilt = SyncHousekeeping.afterCommit(LOG, "import", backups, thumbnails);
    LOG.info("Sync import report written: {}", reportFile.getFileName());
    return new SyncImportResult(report, backup, reportFile, rebuilt);
  }

  public SyncStaging staging() {
    return staging;
  }
}
