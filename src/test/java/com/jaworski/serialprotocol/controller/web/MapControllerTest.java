package com.jaworski.serialprotocol.controller.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@AutoConfigureMockMvc
class MapControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void test_AboutEndpoint() throws Exception {
        this.mockMvc.perform(get("/about"))
                .andExpect(result -> assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus()))
                .andExpect(result -> assertEquals(result.getResponse().getHeaderValue(HttpHeaders.CONTENT_TYPE), "text/html;charset=UTF-8"));
    }

    @Test
    void test_TerminalEndpoint() throws Exception {
        this.mockMvc.perform(get("/terminal"))
                .andExpect(result -> assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus()))
                .andExpect(result -> assertEquals(result.getResponse().getHeaderValue(HttpHeaders.CONTENT_TYPE), "text/html;charset=UTF-8"));
    }

    @Test
    void test_IndexEndpoint() throws Exception {
        this.mockMvc.perform(get("/"))
                .andExpect(result -> assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus()))
                .andExpect(result -> assertEquals(result.getResponse().getHeaderValue(HttpHeaders.CONTENT_TYPE), "text/html;charset=UTF-8"));
    }

    @Test
    void test_ChartEndpoint() throws Exception {
        this.mockMvc.perform(get("/chart"))
                .andExpect(result -> assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus()))
                .andExpect(result -> assertEquals(result.getResponse().getHeaderValue(HttpHeaders.CONTENT_TYPE), "text/html;charset=UTF-8"));
    }

    @Test
    void test_NotValidEndpoint() throws Exception {
        this.mockMvc.perform(get("/xxx"))
                .andExpect(result -> assertEquals(HttpStatus.NOT_FOUND.value(), result.getResponse().getStatus()))
                .andExpect(result -> assertNull(result.getResponse().getHeaderValue(HttpHeaders.CONTENT_TYPE)));
    }

    @Test
    void test_NameServicePostEndpoint() throws Exception {
        this.mockMvc.perform(post("/name-service"))
                .andExpect(result -> assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus()))
                .andExpect(result -> assertEquals(result.getResponse().getHeaderValue(HttpHeaders.CONTENT_TYPE), "text/html;charset=UTF-8"));
    }

    @Test
    void test_NameServiceGetEndpoint() throws Exception {
        this.mockMvc.perform(get("/name-service"))
                .andExpect(result -> assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus()))
                .andExpect(result -> assertEquals(result.getResponse().getHeaderValue(HttpHeaders.CONTENT_TYPE), "text/html;charset=UTF-8"));
    }
}