package com.jaworski.serialprotocol.configuration;

import org.apache.catalina.connector.Connector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class TomcatServerConfigurationTest {

    private TomcatServerConfiguration config;

    @BeforeEach
    void setUp() {
        config = new TomcatServerConfiguration();
        ReflectionTestUtils.setField(config, "httpsPort", 443);
        ReflectionTestUtils.setField(config, "httpPorts", List.of(80, 8080));
    }

    @Test
    void buildRedirectConnector_schemeIsHttp() {
        Connector connector = config.buildRedirectConnector(80);
        assertEquals("http", connector.getScheme());
    }

    @Test
    void buildRedirectConnector_usesGivenPort() {
        assertEquals(80, config.buildRedirectConnector(80).getPort());
        assertEquals(8080, config.buildRedirectConnector(8080).getPort());
    }

    @Test
    void buildRedirectConnector_isNotSecure() {
        Connector connector = config.buildRedirectConnector(80);
        assertFalse(connector.getSecure());
    }

    @Test
    void buildRedirectConnector_redirectsToHttpsPort() {
        Connector connector = config.buildRedirectConnector(8080);
        assertEquals(443, connector.getRedirectPort());
    }

    @Test
    void buildRedirectConnector_customHttpsPort() {
        ReflectionTestUtils.setField(config, "httpsPort", 8443);
        Connector connector = config.buildRedirectConnector(8080);
        assertEquals(8080, connector.getPort());
        assertEquals(8443, connector.getRedirectPort());
    }

    @Test
    void validatePorts_passesForDistinctPorts() {
        assertDoesNotThrow(config::validatePorts);
    }

    @Test
    void validatePorts_rejectsDuplicateHttpPorts() {
        ReflectionTestUtils.setField(config, "httpPorts", List.of(80, 80));
        IllegalStateException ex = assertThrows(IllegalStateException.class, config::validatePorts);
        assertTrue(ex.getMessage().contains("Duplicate"));
    }

    @Test
    void validatePorts_rejectsHttpPortCollidingWithHttpsPort() {
        ReflectionTestUtils.setField(config, "httpPorts", List.of(80, 443));
        IllegalStateException ex = assertThrows(IllegalStateException.class, config::validatePorts);
        assertTrue(ex.getMessage().contains("collides"));
    }

    @Test
    void validatePorts_rejectsEmptyPortList() {
        ReflectionTestUtils.setField(config, "httpPorts", List.of());
        IllegalStateException ex = assertThrows(IllegalStateException.class, config::validatePorts);
        assertTrue(ex.getMessage().contains("empty"));
    }

    @Test
    void validatePorts_rejectsPortBelowRange() {
        ReflectionTestUtils.setField(config, "httpPorts", List.of(0));
        IllegalStateException ex = assertThrows(IllegalStateException.class, config::validatePorts);
        assertTrue(ex.getMessage().contains("1-65535"));
    }

    @Test
    void validatePorts_rejectsPortAboveRange() {
        ReflectionTestUtils.setField(config, "httpPorts", List.of(65536));
        IllegalStateException ex = assertThrows(IllegalStateException.class, config::validatePorts);
        assertTrue(ex.getMessage().contains("1-65535"));
    }
}
