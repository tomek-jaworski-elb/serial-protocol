package com.jaworski.serialprotocol.entity.custom;

import org.hibernate.annotations.IdGeneratorType;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.EventTypeSets;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.EnumSet;
import java.util.UUID;

/**
 * A uuid primary key that is generated when absent and kept when the application assigned one.
 *
 * <p>{@code @GeneratedValue(strategy = UUID)} treats any entity that already carries an id as detached, so
 * {@code persist} refuses it ("detached entity passed to persist"). A backup restore has to insert rows under
 * the uuids they had, because courses point at participants, trainers and counters by those uuids. This
 * generator declares that assigned identifiers are allowed; Hibernate then decides transient-or-detached
 * from the version column (or a lookup for {@link Image}, which has none) instead of from the id. A row that
 * already exists under the assigned uuid still fails on the primary key, never silently.</p>
 */
@IdGeneratorType(AssignedOrGeneratedUuid.Generator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface AssignedOrGeneratedUuid {

  class Generator implements BeforeExecutionGenerator {

    @Override
    public Object generate(SharedSessionContractImplementor session, Object owner, Object currentValue,
                           EventType eventType) {
      return currentValue != null ? currentValue : UUID.randomUUID();
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
      return EventTypeSets.INSERT_ONLY;
    }

    @Override
    public boolean allowAssignedIdentifiers() {
      return true;
    }
  }
}
