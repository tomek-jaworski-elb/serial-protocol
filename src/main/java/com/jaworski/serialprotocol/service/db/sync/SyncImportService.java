package com.jaworski.serialprotocol.service.db.sync;

import com.jaworski.serialprotocol.configuration.SyncProperties;
import com.jaworski.serialprotocol.entity.custom.SyncNode;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.ZoneId;

/**
 * Turns a staged upload into the report: reads the file, refuses what cannot be imported,
 * reads the local tables and runs {@link SyncAnalyzer}. Nothing here writes; applying the
 * plan is the next stage.
 */
@Service
public class SyncImportService {

  private final SyncStaging staging;
  private final SyncLocalReader localReader;
  private final SyncNodeService nodeService;
  private final SyncExporter exporter;
  private final SyncProperties properties;

  public SyncImportService(SyncStaging staging, SyncLocalReader localReader, SyncNodeService nodeService,
      SyncExporter exporter, SyncProperties properties) {
    this.staging = staging;
    this.localReader = localReader;
    this.nodeService = nodeService;
    this.exporter = exporter;
    this.properties = properties;
  }

  /** @throws SyncRefusedException when the file cannot be imported, with the reason for the user */
  public SyncImportPreview analyze(SyncStaging.Staged staged) {
    SyncFile file = SyncReader.read(staged.file());
    SyncNode self = nodeService.current();
    SyncAnalyzer.checkAccepted(file.manifest(), self.getId(), exporter.schemaVersion());
    SyncAnalysis analysis = SyncAnalyzer.analyze(new SyncAnalyzer.Input(self.getId(), self.getName(),
        self.getLastSyncAt(), properties.isReadOnly(), file.manifest().node(), localReader.read(), file.tables(),
        ZoneId.systemDefault()));
    try {
      return new SyncImportPreview(staged.token(), staged.originalName(), Files.size(staged.file()),
          file.manifest(), analysis);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public SyncStaging staging() {
    return staging;
  }
}
