package com.jaworski.serialprotocol.service.db.sync;

import com.jaworski.serialprotocol.configuration.SyncProperties;
import com.jaworski.serialprotocol.entity.custom.NodeIdentity;
import com.jaworski.serialprotocol.entity.custom.SyncNode;
import com.jaworski.serialprotocol.repository.custom.SyncNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.UUID;

/**
 * Owns the single {@code sync_node} row: this installation's id, name and exchange timestamps.
 *
 * <p>The id is cached after start-up so {@code OriginStamper} can read it inside a flush
 * without a query. The name follows {@code sync.node-name} on every start, or the host name
 * when the property is blank, so renaming a computer is a property change.</p>
 */
@Service
@Transactional
public class SyncNodeService implements NodeIdentity {

  private static final Logger LOG = LoggerFactory.getLogger(SyncNodeService.class);

  private final SyncNodeRepository repository;
  private final SyncProperties properties;
  private volatile UUID nodeId;

  public SyncNodeService(SyncNodeRepository repository, SyncProperties properties) {
    this.repository = repository;
    this.properties = properties;
  }

  /**
   * Creates the row when it is missing (the H2 profile runs without Flyway; V10 seeds MariaDB)
   * and applies the configured name. Runs before the first request, like
   * {@code IdAllocator.ensureSequences}.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void ensureNode() {
    SyncNode node = repository.findFirstByOrderByIdAsc()
        .orElseGet(() -> repository.save(new SyncNode(UUID.randomUUID(), configuredName())));
    String name = configuredName();
    if (!name.equals(node.getName())) {
      node.setName(name);
      node = repository.save(node);
    }
    nodeId = node.getId();
    LOG.info("Sync node {} ({}), mode {}", node.getName(), node.getId(), properties.getMode());
  }

  @Transactional(readOnly = true)
  public SyncNode current() {
    SyncNode node = repository.findFirstByOrderByIdAsc()
        .orElseThrow(() -> new IllegalStateException("sync_node row is missing; ensureNode() has not run"));
    // Keeps the cache honest when the row was replaced underneath (a new node id, or a test
    // context that recreated the shared in-memory schema).
    nodeId = node.getId();
    return node;
  }

  /** Records a completed export: {@code last_export_at} and, since it is the newer exchange, {@code last_sync_at}. */
  public void markExported(Instant at) {
    SyncNode node = current();
    node.setLastExportAt(at);
    node.setLastSyncAt(at);
    repository.save(node);
  }

  /** Records a completed import: what was loaded and when, and {@code last_sync_at} with it. */
  public void markImported(Instant at, String fileName) {
    SyncNode node = current();
    node.setLastImportAt(at);
    node.setLastImportFile(fileName);
    node.setLastSyncAt(at);
    repository.save(node);
  }

  @Override
  public UUID currentNodeId() {
    return nodeId;
  }

  private String configuredName() {
    String configured = properties.getNodeName();
    if (configured != null && !configured.isBlank()) {
      return configured.strip();
    }
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      return "node";
    }
  }
}
