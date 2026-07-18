package com.jaworski.serialprotocol.configuration;

import jakarta.annotation.PostConstruct;
import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.servlet.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.List;

@Configuration
@ConditionalOnProperty(name = "server.http.redirect.enabled", havingValue = "true", matchIfMissing = false)
public class TomcatServerConfiguration {

    @Value("${server.port}")
    private int httpsPort;

    @Value("${server.http.ports:8080}")
    private List<Integer> httpPorts;

    @PostConstruct
    void validatePorts() {
        if (httpPorts.isEmpty()) {
            throw new IllegalStateException("No HTTP redirect ports configured (server.http.ports is empty)");
        }
        for (int port : httpPorts) {
            if (port < 1 || port > 65535) {
                throw new IllegalStateException(
                        "Invalid HTTP redirect port " + port + " — must be 1-65535 (server.http.ports=" + httpPorts + ")");
            }
        }
        if (httpPorts.size() != new HashSet<>(httpPorts).size()) {
            throw new IllegalStateException("Duplicate HTTP redirect ports configured: " + httpPorts);
        }
        if (httpPorts.contains(httpsPort)) {
            throw new IllegalStateException(
                    "HTTP redirect port collides with HTTPS port " + httpsPort + " (server.http.ports=" + httpPorts + ")");
        }
    }

    @Bean
    public ServletWebServerFactory servletContainer() {
        TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory() {
            @Override
            protected void postProcessContext(Context context) {
                SecurityConstraint securityConstraint = new SecurityConstraint();
                securityConstraint.setUserConstraint("CONFIDENTIAL");
                SecurityCollection collection = new SecurityCollection();
                collection.addPattern("/*");
                securityConstraint.addCollection(collection);
                context.addConstraint(securityConstraint);
            }
        };
        Connector[] connectors = httpPorts.stream()
                .map(this::buildRedirectConnector)
                .toArray(Connector[]::new);
        tomcat.addAdditionalConnectors(connectors);
        return tomcat;
    }

    Connector buildRedirectConnector(int port) {
        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setScheme("http");
        connector.setPort(port);
        connector.setSecure(false);
        connector.setRedirectPort(httpsPort);
        return connector;
    }
}
