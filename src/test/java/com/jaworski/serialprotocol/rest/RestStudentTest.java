package com.jaworski.serialprotocol.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaworski.serialprotocol.dto.StudentDTO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Date;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class RestStudentTest {

    @Autowired
    private MockMvc mockMvc;

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

        String writeValueAsString = new ObjectMapper().writeValueAsString(studentDTOS);
        MvcResult mvcResult = mockMvc.perform(post("/api/v1/student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeValueAsString)
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        Assertions.assertEquals(HttpStatus.OK.value(), mvcResult.getResponse().getStatus());
        Assertions.assertEquals(List.of(1).toString(), mvcResult.getResponse().getContentAsString());
    }

}