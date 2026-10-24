package com.jaworski.serialprotocol.entity.custom;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Writes the id of this installation into {@code origin_node} on every insert and update.
 *
 * <p>A Spring bean, not a plain listener: Boot hands Hibernate a {@code SpringBeanContainer},
 * so {@code @EntityListeners(OriginStamper.class)} on {@link Auditable} resolves to this
 * instance. The identity is an {@link ObjectProvider} because the listener may be created
 * while the entity manager factory, which the identity's repository needs, is still being
 * built. A soft delete is an {@code @SQLDelete} UPDATE and never passes through here, so a
 * tombstone keeps the origin of its last edit.</p>
 */
@Component
public class OriginStamper {

  private final ObjectProvider<NodeIdentity> identity;

  public OriginStamper(ObjectProvider<NodeIdentity> identity) {
    this.identity = identity;
  }

  @PrePersist
  @PreUpdate
  void stamp(Auditable row) {
    NodeIdentity node = identity.getIfAvailable();
    if (node != null && node.currentNodeId() != null) {
      row.setOriginNode(node.currentNodeId());
    }
  }
}
