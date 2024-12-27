package com.jaworski.serialprotocol.restclient;

import com.jaworski.serialprotocol.configuration.RestTemplateClient;
import com.jaworski.serialprotocol.dto.StudentDTO;
import com.jaworski.serialprotocol.exception.CustomRestException;
import com.jaworski.serialprotocol.resources.Resources;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.net.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.jaworski.serialprotocol.restclient.ServiceUri.API_PATH;
import static com.jaworski.serialprotocol.restclient.ServiceUri.HELLO_PATH;
import static com.jaworski.serialprotocol.restclient.ServiceUri.NAMES_LATEST_PATH;
import static com.jaworski.serialprotocol.restclient.ServiceUri.NAMES_PATH;
import static com.jaworski.serialprotocol.restclient.ServiceUri.URI_SCHEME;

@RequiredArgsConstructor
@Component
public class RestNameService {

    private final RestTemplateClient restTemplateClient;
    private static final Logger LOG = LoggerFactory.getLogger(RestNameService.class);
    private final Resources resources;

    public void checkConnection() {
        URI uri;
        try {
            uri = getUri(HELLO_PATH);
        } catch (CustomRestException e) {
            LOG.error("Failed to create URI for {}", resources.getDbClientIp(), e);
            return;
        }
        LOG.info("Checking connection to {}", uri);
        try {
            ResponseEntity<String> forEntity = restTemplateClient.restClient().getForEntity(uri, String.class);
            LOG.info("Response: {}, status: {}", forEntity.getBody(), forEntity.getStatusCode());
        } catch (CustomRestException e) {
            LOG.error("Failed to send request to {}", uri, e);
        } catch (KeyStoreException | NoSuchAlgorithmException | KeyManagementException e) {
            LOG.error("Failed Https connection", e);
        }
    }

    public Collection<StudentDTO> getNames() throws CustomRestException {
        URI uri = getUri(NAMES_PATH);
        return getStudents(uri);
    }

    private URI getUri(String url) throws CustomRestException {
        URI uri;
        String dbClientIp = resources.getDbClientIp();
        try {
            URIBuilder builder = new URIBuilder()
                    .setScheme(URI_SCHEME)
                    .setPort(Integer.parseInt(StringUtils.split(dbClientIp, ":")[1]))
                    .setHost(StringUtils.split(dbClientIp, ":")[0])
                    .setPath(API_PATH).appendPath(url);
            uri = builder.build();
        } catch (URISyntaxException | NumberFormatException e) {
            LOG.error("Failed to create URI for {}", resources.getDbClientIp(), e);
            throw new CustomRestException(e.getMessage());
        }
        return uri;
    }

    public Collection<StudentDTO> getNamesLatest() throws CustomRestException {
        URI uri = getUri(NAMES_LATEST_PATH);
        return getStudents(uri);
    }

    private Collection<StudentDTO> getStudents(URI uri) throws CustomRestException {
        try {
            ResponseEntity<List<StudentDTO>> forEntity = restTemplateClient.restClient()
                    .exchange(uri, HttpMethod.GET, null, new ParameterizedTypeReference<List<StudentDTO>>() {
                    });
            LOG.info("Response: {}, status: {} {}", forEntity.getBody(), forEntity.getStatusCode(), HttpStatus.valueOf(forEntity.getStatusCode().value()));
            return forEntity.getBody() == null ?
                    Collections.emptyList() :
                    forEntity.getBody();
        } catch (KeyStoreException | RestClientException e) {
            LOG.error("Rest error: ", e);
            throw new CustomRestException(e.getMessage(), e);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            LOG.error("Failed Https connection", e);
            throw new RuntimeException(e);
        }
    }
}
