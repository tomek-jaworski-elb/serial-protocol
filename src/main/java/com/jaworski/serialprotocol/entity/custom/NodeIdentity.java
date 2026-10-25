package com.jaworski.serialprotocol.entity.custom;

import java.util.UUID;

/**
 * Where {@link OriginStamper} gets the id of this installation from.
 *
 * <p>An interface so the entity package does not depend on the service that owns the
 * {@code sync_node} row. The id must come from memory, not from a query: the stamper runs
 * inside Hibernate's flush, where a query would trigger another flush.</p>
 */
public interface NodeIdentity {

  /** The id of this installation, or {@code null} until it has been loaded after start-up. */
  UUID currentNodeId();
}
