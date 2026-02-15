package com.jaworski.serialprotocol.controller.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaworski.serialprotocol.rest.paths.Paths;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class MapControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void test_AboutEndpoint() throws Exception {
        this.mockMvc.perform(get("/about"))
                .andExpect(result -> assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus()))
                .andExpect(result -> assertEquals("text/html;charset=UTF-8", result.getResponse().getHeaderValue(HttpHeaders.CONTENT_TYPE)));
    }

    @Test
    void test_TerminalEndpoint() throws Exception {
        this.mockMvc.perform(get("/terminal"))
                .andExpect(result -> assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus()))
                .andExpect(result -> assertEquals("text/html;charset=UTF-8", result.getResponse().getHeaderValue(HttpHeaders.CONTENT_TYPE)));
    }

    @Test
    void test_IndexEndpoint() throws Exception {
        this.mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals("text/html;charset=UTF-8", result.getResponse().getHeaderValue(HttpHeaders.CONTENT_TYPE)));
    }

    @Test
    void test_ChartEndpoint() throws Exception {
        this.mockMvc.perform(get("/chart"))
                .andExpect(result -> assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus()))
                .andExpect(result -> assertEquals("text/html;charset=UTF-8", result.getResponse().getHeaderValue(HttpHeaders.CONTENT_TYPE)));
    }

    @Test
    void test_NotValidEndpoint() throws Exception {
        this.mockMvc.perform(get("/xxx"))
                .andExpect(result -> assertEquals(HttpStatus.NOT_FOUND.value(), result.getResponse().getStatus()))
                .andExpect(result -> assertNull(result.getResponse().getHeaderValue(HttpHeaders.CONTENT_TYPE)));
    }

    @Test
    void test_NameServicePostEndpoint() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(Collections.emptyList());
        this.mockMvc.perform(post(Paths.ROOT + Paths.STUDENT).contentType(MediaType.APPLICATION_JSON).content(json)
                        .header(HttpHeaders.AUTHORIZATION, getAuthorizationHeaderValue("user", "user")))
                .andExpect(result -> assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus()))
                .andExpect(result -> assertEquals("application/json", result.getResponse().getHeaderValue(HttpHeaders.CONTENT_TYPE)));
    }

    @Test
    void test_InstructorsPostEndpoint() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(Collections.emptyList());
        this.mockMvc.perform(post(Paths.ROOT + Paths.INSTRUCTOR).contentType(MediaType.APPLICATION_JSON).content(json)
                        .header(HttpHeaders.AUTHORIZATION, getAuthorizationHeaderValue("user", "user")))
                .andExpect(result -> assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus()))
                .andExpect(result -> assertEquals("application/json", result.getResponse().getHeaderValue(HttpHeaders.CONTENT_TYPE)));
    }

    @Test
    void test_NameServiceGetEndpoint() throws Exception {
        this.mockMvc.perform(get("/name-service")
                        .header(HttpHeaders.AUTHORIZATION, getAuthorizationHeaderValue("user", "user")))
                .andExpect(result -> assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus()))
                .andExpect(result -> assertEquals("text/html;charset=UTF-8", result.getResponse().getHeaderValue(HttpHeaders.CONTENT_TYPE)));
    }

    @Test
    void test_NameServiceGetEndpoint_withPagination() throws Exception {
        this.mockMvc.perform(get("/name-service")
                        .param("page", "0")
                        .param("size", "10")
                        .header(HttpHeaders.AUTHORIZATION, getAuthorizationHeaderValue("user", "user")))
                .andExpect(result -> assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus()))
                .andExpect(result -> assertEquals("text/html;charset=UTF-8", result.getResponse().getHeaderValue(HttpHeaders.CONTENT_TYPE)));
    }

    @Test
    void test_NameServiceGetEndpoint_withCustomPageSize() throws Exception {
        this.mockMvc.perform(get("/name-service")
                        .param("page", "0")
                        .param("size", "50")
                        .header(HttpHeaders.AUTHORIZATION, getAuthorizationHeaderValue("user", "user")))
                .andExpect(result -> assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus()))
                .andExpect(result -> assertEquals("text/html;charset=UTF-8", result.getResponse().getHeaderValue(HttpHeaders.CONTENT_TYPE)));
    }

    @Test
    void test_InstructorServiceGetEndpoint() throws Exception {
        this.mockMvc.perform(get("/instructor-service")
                        .header(HttpHeaders.AUTHORIZATION, getAuthorizationHeaderValue("user", "user")))
                .andExpect(result -> assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus()))
                .andExpect(result -> assertEquals("text/html;charset=UTF-8", result.getResponse().getHeaderValue(HttpHeaders.CONTENT_TYPE)));
    }

    @Test
    void test_InstructorServiceGetEndpoint_withPagination() throws Exception {
        this.mockMvc.perform(get("/instructor-service")
                        .param("page", "0")
                        .param("size", "10")
                        .header(HttpHeaders.AUTHORIZATION, getAuthorizationHeaderValue("user", "user")))
                .andExpect(result -> assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus()))
                .andExpect(result -> assertEquals("text/html;charset=UTF-8", result.getResponse().getHeaderValue(HttpHeaders.CONTENT_TYPE)));
    }

    @Test
    void test_InstructorServiceGetEndpoint_withCustomPageSize() throws Exception {
        this.mockMvc.perform(get("/instructor-service")
                        .param("page", "0")
                        .param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, getAuthorizationHeaderValue("user", "user")))
                .andExpect(result -> assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus()))
                .andExpect(result -> assertEquals("text/html;charset=UTF-8", result.getResponse().getHeaderValue(HttpHeaders.CONTENT_TYPE)));
    }

    @Test
    void test_InstructorServiceGetEndpoint_withSecondPage() throws Exception {
        this.mockMvc.perform(get("/instructor-service")
                        .param("page", "1")
                        .param("size", "20")
                        .header(HttpHeaders.AUTHORIZATION, getAuthorizationHeaderValue("user", "user")))
                .andExpect(result -> assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus()))
                .andExpect(result -> assertEquals("text/html;charset=UTF-8", result.getResponse().getHeaderValue(HttpHeaders.CONTENT_TYPE)));
    }

    @Test
    void test_InstructorServiceGetEndpoint_unauthorized() throws Exception {
        this.mockMvc.perform(get("/instructor-service"))
                .andExpect(result -> assertEquals(HttpStatus.UNAUTHORIZED.value(), result.getResponse().getStatus()));
    }

    @Test
    void test_NameServiceGetEndpoint_unauthorized() throws Exception {
        this.mockMvc.perform(get("/name-service"))
                .andExpect(result -> assertEquals(HttpStatus.UNAUTHORIZED.value(), result.getResponse().getStatus()));
    }

    private String getAuthorizationHeaderValue(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
    }
}