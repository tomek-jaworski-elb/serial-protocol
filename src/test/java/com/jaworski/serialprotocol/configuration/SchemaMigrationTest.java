package com.jaworski.serialprotocol.configuration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The Flyway migrations build a MariaDB schema that the Hibernate mappings accept.
 *
 * <p>The ordinary suite runs on H2 with the schema generated from the mappings and Flyway
 * switched off, so nothing there ever executes {@code db/migration}. This test starts a real
 * MariaDB (the version pinned in docker-compose), lets Flyway run every script on an empty
 * database and then has Hibernate validate the result, exactly as a fresh installation
 * does on its first start. A mapping change that forgets its migration, or a migration that
 * drifts from the mappings, fails here.</p>
 *
 * <p>Skipped when Docker is not available, so the H2 suite still runs everywhere; CI has
 * Docker (see docker-compose.ci.yml).</p>
 */
@DataJpaTest
// The JPA slice does not include Flyway; without this the schema would simply be missing.
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    // Same as src/main/resources/application.properties, which the H2 test profile replaces.
    "spring.datasource.hikari.connection-init-sql=SET time_zone = '+00:00'"
})
class SchemaMigrationTest {

    /** Same image as docker-compose.yml, so the test and the deployment agree on the server. */
    @Container
    @ServiceConnection
    static final MariaDBContainer MARIADB = new MariaDBContainer("mariadb:11.8");

    @Autowired
    private Flyway flyway;
    @Autowired
    private DataSource dataSource;

    @Test
    void everyMigrationAppliesAndTheMappingsValidateAgainstTheResult() {
        // The context has started, so ddl-auto=validate already accepted the migrated schema.
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(applied).extracting(MigrationInfo::getVersion)
            .extracting(Object::toString)
            .as("every script in db/migration ran, in order")
            .startsWith("1", "2")
            .hasSize(flyway.info().all().length);
        assertThat(applied).extracting(MigrationInfo::getState)
            .containsOnly(MigrationState.SUCCESS);
        assertThat(flyway.info().pending()).as("nothing left to apply").isEmpty();
    }

    /**
     * Audit timestamps are UTC wall time in {@code DATETIME(6)} columns. {@code @SQLDelete} and
     * the column defaults write {@code CURRENT_TIMESTAMP(6)}, which follows the session time
     * zone, so the pool pins every connection to UTC; otherwise a soft delete made on a server
     * running in another zone would land hours away from the edits around it.
     */
    @Test
    void everyConnectionRunsInUtcSoDatabaseSideTimestampsMatchTheOnesHibernateWrites() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC));

        assertThat(jdbc.queryForObject("select @@session.time_zone", String.class)).isEqualTo("+00:00");
        Instant fromDatabase = jdbc.query("select current_timestamp(6)",
            rs -> rs.next() ? rs.getTimestamp(1, utc).toInstant() : null);
        assertThat(fromDatabase).isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
    }
}
