/*
 * Copyright 2026 Adtran Networks SE. All rights reserved.
 *
 * Owner: tomaszja
 */
package com.jaworski.serialprotocol.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Applies schema alterations that Hibernate ddl-auto=update cannot handle
 * (modifying existing column constraints from NOT NULL to NULL).
 * <p>
 * Runs once on application startup, only in non-test profiles.
 * Each ALTER statement is idempotent — safe to re-execute on every restart.
 * </p>
 */
@Component
@Profile("!test")
public class SchemaMigrationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(SchemaMigrationRunner.class);

    private final JdbcTemplate jdbcTemplate;

    public SchemaMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void applyMigrations() {
        LOG.info("SchemaMigrationRunner: applying nullable column migrations...");

        String product = "unknown";
        try {
            if (jdbcTemplate.getDataSource() != null) {
                try (java.sql.Connection c = jdbcTemplate.getDataSource().getConnection()) {
                    product = c.getMetaData().getDatabaseProductName();
                }
            }
        } catch (Exception e) {
            LOG.warn("SchemaMigrationRunner: unable to detect database product name: {}", e.getMessage());
        }

        // Skip MySQL-specific ALTERs on H2 (tests use in-memory H2 which does not accept `MODIFY COLUMN` syntax)
        if (product != null && product.toLowerCase().contains("h2")) {
            LOG.info("SchemaMigrationRunner: H2 detected ({}). Skipping MySQL-specific ALTER statements.", product);
            return;
        }

        // course_type — long_description was previously NOT NULL
        alter("course_type", "course_type_long_description", "VARCHAR(5000) NULL");

        // lecturer — email and nickname were previously NOT NULL
        alter("lecturer", "lecturer_email",       "VARCHAR(100)  NULL");
        alter("lecturer", "lecturer_nickname",    "VARCHAR(100)  NULL");
        alter("lecturer", "lecturer_notes",       "VARCHAR(1000) NULL");
        alter("lecturer", "lecturer_phone_number","VARCHAR(26)   NULL");
        alter("lecturer", "lecturer_address",     "VARCHAR(300)  NULL");

        // trainer — email was previously NOT NULL
        alter("trainer", "trainer_email",        "VARCHAR(100)  NULL");
        alter("trainer", "trainer_nickname",     "VARCHAR(100)  NULL");
        alter("trainer", "trainer_notes",        "VARCHAR(1000) NULL");
        alter("trainer", "trainer_phone_number", "VARCHAR(26)   NULL");
        alter("trainer", "trainer_address",      "VARCHAR(300)  NULL");

        // technician
        alter("technician", "technician_email",        "VARCHAR(100)  NULL");
        alter("technician", "technician_nickname",     "VARCHAR(100)  NULL");
        alter("technician", "technician_notes",        "VARCHAR(1000) NULL");
        alter("technician", "technician_phone_number", "VARCHAR(26)   NULL");
        alter("technician", "technician_address",      "VARCHAR(300)  NULL");

        // participants — birth_date was previously NOT NULL
        alter("participants", "participants_birth_date",   "DATE          NULL");
        alter("participants", "participants_email",        "VARCHAR(100)  NULL");
        alter("participants", "participants_nickname",     "VARCHAR(100)  NULL");
        alter("participants", "participants_notes",        "VARCHAR(1000) NULL");
        alter("participants", "participants_phone_number", "VARCHAR(26)   NULL");
        alter("participants", "participants_address",      "VARCHAR(300)  NULL");

        LOG.info("SchemaMigrationRunner: migrations applied.");
    }

    /**
     * Executes {@code ALTER TABLE `table` MODIFY COLUMN `column` definition}.
     * If the column does not yet exist (new install), the error is swallowed —
     * Hibernate will create it with the correct definition on its own DDL pass.
     * <p>
     * <strong>Security note:</strong> All arguments are hardcoded string literals from
     * {@link #applyMigrations()}. Never pass user-supplied values to this method.
     * </p>
     */
    private void alter(String table, String column, String definition) {
        String sql = String.format("ALTER TABLE `%s` MODIFY COLUMN `%s` %s", table, column, definition);
        try {
            jdbcTemplate.execute(sql);
            LOG.debug("SchemaMigrationRunner: OK — {}", sql);
        } catch (Exception e) {
            LOG.warn("SchemaMigrationRunner: skipped '{}' — {}", sql, e.getMessage());
        }
    }
}

