package com.jaworski.serialprotocol.restclient;

import com.jaworski.serialprotocol.configuration.RestTemplateClient;
import com.jaworski.serialprotocol.dto.Student;
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
public class RestNameService {

    private final RestTemplateClient restTemplateClient;
    private static final Logger LOG = LogManager.getLogger(RestNameService.class);
    private final Resources resources;

    public void checkConnection() {
        URI uri;
        try {
            uri = getUri("hello");
        } catch (CustomRestException e) {
            LOG.error("Failed to create URI for {}", resources.getDbClientIp(), e);
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

    public Collection<Student> getNames() throws CustomRestException {
        URI uri = getUri("names");
        try {
            ResponseEntity<List<Student>> forEntity = restTemplateClient.restClient()
                    .exchange(uri, HttpMethod.GET, null, new ParameterizedTypeReference<List<Student>>() {
                    });
            return forEntity.getBody();
        } catch (KeyStoreException | RestClientException e) {
            throw new CustomRestException(e.getMessage());
        }
    }

    private URI getUri(String url) throws CustomRestException {
        URI uri;
        String dbClientIp = resources.getDbClientIp();
        try {
            uri = URI.create("http://" + dbClientIp + "/api/" + url);
        } catch (IllegalArgumentException e) {
            LOG.error("Failed to create URI for {}", dbClientIp, e);
            throw new CustomRestException(e.getMessage());
        }
        return uri;
    }

}
