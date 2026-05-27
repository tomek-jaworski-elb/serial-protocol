package com.jaworski.serialprotocol.controller.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class CustomDBControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // --- Trainer Service ---

    @Test
    void trainerService_shouldReturnOkWithDefaultPagination() throws Exception {
        mockMvc.perform(get("/trainer-service")
                        .header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("trainerPage", "trainers", "currentPage", "pageSize"));
    }

    @Test
    void trainerService_shouldAcceptPageAndSizeParams() throws Exception {
        mockMvc.perform(get("/trainer-service")
                        .param("page", "0").param("size", "25")
                        .header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pageSize", 25));
    }

    // --- Lecturer Service ---

    @Test
    void lecturerService_shouldReturnOkWithDefaultPagination() throws Exception {
        mockMvc.perform(get("/lecturer-service")
                        .header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("lecturerPage", "lecturers", "currentPage", "pageSize"));
    }

    @Test
    void lecturerService_shouldAcceptPageAndSizeParams() throws Exception {
        mockMvc.perform(get("/lecturer-service")
                        .param("page", "0").param("size", "50")
                        .header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pageSize", 50));
    }

    // --- Technician Service ---

    @Test
    void technicianService_shouldReturnOkWithDefaultPagination() throws Exception {
        mockMvc.perform(get("/technician-service")
                        .header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("technicianPage", "technicians", "currentPage", "pageSize"));
    }

    @Test
    void technicianService_shouldAcceptPageAndSizeParams() throws Exception {
        mockMvc.perform(get("/technician-service")
                        .param("page", "1").param("size", "10")
                        .header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentPage", 1));
    }

    // --- Course Type Service ---

    @Test
    void courseTypeService_shouldReturnOkWithDefaultPagination() throws Exception {
        mockMvc.perform(get("/course-type-service")
                        .header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("courseTypePage", "courseTypes", "currentPage", "pageSize"));
    }

    @Test
    void courseTypeService_shouldAcceptPageAndSizeParams() throws Exception {
        mockMvc.perform(get("/course-type-service")
                        .param("page", "0").param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pageSize", 100));
    }

    // --- Participant Service ---

    @Test
    void participantService_shouldReturnOkWithDefaultPagination() throws Exception {
        mockMvc.perform(get("/participant-service")
                        .header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("participantPage", "participants", "currentPage", "pageSize"));
    }

    @Test
    void participantService_shouldAcceptPageAndSizeParams() throws Exception {
        mockMvc.perform(get("/participant-service")
                        .param("page", "0").param("size", "25")
                        .header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pageSize", 25));
    }

    // --- Courses Service ---

    @Test
    void coursesService_shouldReturnOkWithDefaultPagination() throws Exception {
        mockMvc.perform(get("/courses-service")
                        .header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("coursesPage", "courses", "currentPage", "pageSize"));
    }

    @Test
    void coursesService_shouldAcceptPageAndSizeParams() throws Exception {
        mockMvc.perform(get("/courses-service")
                        .param("page", "0").param("size", "50")
                        .header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pageSize", 50));
    }

    // --- Course Counter Service ---

    @Test
    void courseCounterService_shouldReturnOkWithDefaultPagination() throws Exception {
        mockMvc.perform(get("/course-counter-service")
                        .header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("counterPage", "courseCounters", "currentPage", "pageSize"));
    }

    @Test
    void courseCounterService_shouldAcceptPageAndSizeParams() throws Exception {
        mockMvc.perform(get("/course-counter-service")
                        .param("page", "0").param("size", "25")
                        .header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pageSize", 25));
    }

    // --- Security ---

    @Test
    void trainerService_shouldRejectUnauthorized() throws Exception {
        mockMvc.perform(get("/trainer-service"))
                .andExpect(result ->
                        org.junit.jupiter.api.Assertions.assertEquals(
                                HttpStatus.UNAUTHORIZED.value(),
                                result.getResponse().getStatus()));
    }

    @Test
    void coursesService_shouldRejectUnauthorized() throws Exception {
        mockMvc.perform(get("/courses-service"))
                .andExpect(result ->
                        org.junit.jupiter.api.Assertions.assertEquals(
                                HttpStatus.UNAUTHORIZED.value(),
                                result.getResponse().getStatus()));
    }

    private String auth() {
        return "Basic " + Base64.getEncoder().encodeToString("user:user".getBytes());
    }
}
