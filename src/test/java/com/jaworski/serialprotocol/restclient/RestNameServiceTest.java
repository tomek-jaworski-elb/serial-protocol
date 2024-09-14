package com.jaworski.serialprotocol.restclient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaworski.serialprotocol.configuration.RestTemplateClient;
import com.jaworski.serialprotocol.dto.Student;
import com.jaworski.serialprotocol.exception.CustomRestException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.List;

import static org.hamcrest.Matchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

//@SpringBootTest(classes = {SpringBootTest.class, RestNameService.class, RestTemplateClient.class, UserService.class, Resources.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RestNameServiceTest {

    @Autowired
    private RestNameService nameService;
    @Autowired
    private RestTemplateClient restTemplateClient;

    private MockRestServiceServer mockServer;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    public void init() throws CustomRestException, KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
        mockServer = MockRestServiceServer.createServer(restTemplateClient.restClient());
    }

    @Test
    void testGetNames_HappyPath() throws URISyntaxException, CustomRestException, JsonProcessingException {
        Student student = new Student();
        student.setName("name");
        student.setId(1);
        List<Student> studentsSet = List.of(student);
        mockServer.expect(ExpectedCount.once(),
                        requestTo(new URI("https://127.0.0.1:8085/api/names")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, anything()))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(mapper.writeValueAsString(studentsSet)));

        Collection<Student> students = nameService.getNames();

        mockServer.verify();

        Assertions.assertNotNull(students);
        Assertions.assertEquals(1, students.size());
        Assertions.assertEquals("name", students.iterator().next().getName());
        Assertions.assertEquals(1, students.iterator().next().getId());
    }

    @Test
    void testGetNames_HappyPath_2() throws URISyntaxException, CustomRestException, JsonProcessingException {
        Student student = new Student();
        student.setName("name");
        List<Student> studentsSet = List.of(student);
        mockServer.expect(ExpectedCount.once(),
                        requestTo(new URI("https://127.0.0.1:8085/api/names/4")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, anything()))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(mapper.writeValueAsString(studentsSet)));

        Collection<Student> students = nameService.getNamesLatest();

        mockServer.verify();

        Assertions.assertNotNull(students);
        Assertions.assertEquals(1, students.size());
        Assertions.assertEquals("name", students.iterator().next().getName());
    }

    @Test
    void testCheckConnection_UnHappyPath() throws URISyntaxException {
        mockServer.expect(ExpectedCount.once(),
                        requestTo(new URI("https://127.0.0.1:8085/api/hello")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON));

        Assertions.assertThrows(HttpServerErrorException.InternalServerError.class, () -> nameService.checkConnection());

        mockServer.verify();

    }

    @Test
    void testCheckConnection_HappyPath() throws URISyntaxException {
        mockServer.expect(ExpectedCount.once(),
                        requestTo(new URI("https://127.0.0.1:8085/api/hello")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, anything()))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON));

        nameService.checkConnection();

        mockServer.verify();

    }

    @Test
    void testCheckConnection_whenInternalServerError() throws URISyntaxException {
        mockServer.expect(ExpectedCount.once(),
                        requestTo(new URI("https://127.0.0.1:8085/api/hello")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        Assertions.assertThrows(HttpServerErrorException.InternalServerError.class, () -> nameService.checkConnection());

        mockServer.verify();

    }

    @Test
    void testCheckConnection_whenUnauthorized() throws URISyntaxException {
        mockServer.expect(ExpectedCount.once(),
                        requestTo(new URI("https://127.0.0.1:8085/api/hello")))
                .andExpect(header(HttpHeaders.AUTHORIZATION, anything("Basic dXNlcjpwYXNzd29yZA==")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        Assertions.assertThrows(HttpClientErrorException.Unauthorized.class, () -> nameService.checkConnection());

        mockServer.verify();

    }
}