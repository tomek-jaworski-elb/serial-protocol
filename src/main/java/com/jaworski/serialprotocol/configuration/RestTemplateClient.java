package com.jaworski.serialprotocol.configuration;

import com.jaworski.serialprotocol.authorization.UserService;
import com.jaworski.serialprotocol.dto.User;
import com.jaworski.serialprotocol.exception.CustomRestException;
import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Configuration
@RequiredArgsConstructor
public class RestTemplateClient {

    private final UserService userService;

    private HttpComponentsClientHttpRequestFactory getRequestFactory() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        final TrustStrategy acceptingTrustStrategy = (cert, authType) -> true;
        final SSLContext sslContext = SSLContexts.custom()
                .loadTrustMaterial(null, acceptingTrustStrategy)
                .build();
        final SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
        final Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("https", sslConnectionSocketFactory)
                .register("http", new PlainConnectionSocketFactory())
                .build();

        final BasicHttpClientConnectionManager connectionManager =
                new BasicHttpClientConnectionManager(socketFactoryRegistry);
        final CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .build();
        HttpComponentsClientHttpRequestFactory httpRequestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        httpRequestFactory.setConnectTimeout(Duration.of(20, ChronoUnit.SECONDS));
        httpRequestFactory.setConnectionRequestTimeout(Duration.of(20, ChronoUnit.SECONDS));
        return httpRequestFactory;
    }

    @Bean
    public RestTemplate restClient() throws KeyStoreException, CustomRestException, NoSuchAlgorithmException, KeyManagementException {
        HttpComponentsClientHttpRequestFactory requestFactory = getRequestFactory();

        User user = userService.getUser();
        return new RestTemplateBuilder()
                .basicAuthentication(user.getName(), user.getPassword())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(() -> requestFactory)
                .build();
    }

}
