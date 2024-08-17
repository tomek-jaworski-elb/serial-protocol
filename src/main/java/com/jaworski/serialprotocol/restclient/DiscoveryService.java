package com.jaworski.serialprotocol.restclient;

import com.jaworski.serialprotocol.configuration.RestTemplateClient;
import com.jaworski.serialprotocol.dto.Personel;
import com.jaworski.serialprotocol.exception.CustomRestException;
import com.jaworski.serialprotocol.resources.Resources;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.security.KeyStoreException;
import java.util.Collection;
import java.util.List;

@RequiredArgsConstructor
@Component
public class DiscoveryService {

    private final RestTemplateClient restTemplateClient;
    private static final Logger LOG = LogManager.getLogger(DiscoveryService.class);
    private final Resources resources;

    public void checkConnection() {
        URI uri;
        String dbClientIp = resources.getDbClientIp();
        try {
            uri = URI.create("http://" + dbClientIp + "/api/hello");
        } catch (IllegalArgumentException e) {
            LOG.error("Failed to create URI for {}", dbClientIp, e);
            return;
        }

        LOG.info("Checking connection to {}", uri);
        try {
            ResponseEntity<String> forEntity = restTemplateClient.restClient().getForEntity(uri, String.class);
            LOG.info("Response: {}, status: {}", forEntity.getBody(), forEntity.getStatusCode());
        } catch (RestClientException e) {
            LOG.error("Failed to send request to {}", uri, e);
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        }
    }

    public Collection<Personel> getNames() throws CustomRestException {
        URI uri;
        String dbClientIp = resources.getDbClientIp();
        try {
            uri = URI.create("http://" + dbClientIp + "/api/names");
        } catch (IllegalArgumentException e) {
            LOG.error("Failed to create URI for {}", dbClientIp, e);
            throw new CustomRestException(e.getMessage());
        }

        try {
            ResponseEntity<List<Personel>> forEntity = restTemplateClient.restClient()
                    .exchange(uri, HttpMethod.GET, null, new ParameterizedTypeReference<List<Personel>>() {
                    });
            return forEntity.getBody();
        } catch (KeyStoreException | RestClientException e) {
            throw new CustomRestException(e.getMessage());
        }
    }

}
