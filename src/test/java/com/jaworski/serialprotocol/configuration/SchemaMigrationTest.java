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

import static org.assertj.core.api.Assertions.assertThat;

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
    "spring.jpa.hibernate.ddl-auto=validate"
})
class SchemaMigrationTest {

    /** Same image as docker-compose.yml, so the test and the deployment agree on the server. */
    @Container
    @ServiceConnection
    static final MariaDBContainer MARIADB = new MariaDBContainer("mariadb:11.8");

    @Autowired
    private Flyway flyway;

    @Test
    void everyMigrationAppliesAndTheMappingsValidateAgainstTheResult() {
        // The context has started, so ddl-auto=validate already accepted the migrated schema.
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(applied).extracting(MigrationInfo::getVersion)
            .extracting(Object::toString)
            .as("every script in db/migration ran, in order")
            .containsExactly("1", "2");
        assertThat(applied).extracting(MigrationInfo::getState)
            .containsOnly(MigrationState.SUCCESS);
        assertThat(flyway.info().pending()).as("nothing left to apply").isEmpty();
    }
}
