package com.jaworski.serialprotocol.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaworski.serialprotocol.dto.StudentDTO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class RestStudentTest {

    @Autowired
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String getAuthorizationHeaderValue(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
    }

    @Test
    void getStudent() throws Exception {

        int id = 1;

        StudentDTO studentDTO = new StudentDTO();
        studentDTO.setName("name");
        studentDTO.setId(id);
        studentDTO.setLastName("lastName");
        studentDTO.setCourseNo("courseNo");
        studentDTO.setDateBegine(new Date());
        studentDTO.setDateEnd(new Date());
        studentDTO.setMrMs("mrMs");
        studentDTO.setCertType("certType");
        List<StudentDTO> studentDTOS = List.of(studentDTO);

        String writeValueAsString = objectMapper.writeValueAsString(studentDTOS);
        MvcResult mvcResult = mockMvc.perform(post("/api/v1/student").header(HttpHeaders.AUTHORIZATION, getAuthorizationHeaderValue("user", "user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeValueAsString)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        Assertions.assertEquals(HttpStatus.OK.value(), mvcResult.getResponse().getStatus());
        String valueAsString = objectMapper.writeValueAsString(List.of(1));
        Assertions.assertEquals(valueAsString, mvcResult.getResponse().getContentAsString());
    }

}