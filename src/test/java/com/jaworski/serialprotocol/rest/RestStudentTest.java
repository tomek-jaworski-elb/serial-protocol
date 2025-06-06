package com.jaworski.serialprotocol.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaworski.serialprotocol.dto.InstructorDto;
import com.jaworski.serialprotocol.dto.StudentDTO;
import com.jaworski.serialprotocol.rest.paths.Paths;
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
    void saveStudent() throws Exception {

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
        MvcResult mvcResult = mockMvc.perform(post(Paths.ROOT + Paths.STUDENT).header(HttpHeaders.AUTHORIZATION, getAuthorizationHeaderValue("user", "user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writeValueAsString)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        Assertions.assertEquals(HttpStatus.OK.value(), mvcResult.getResponse().getStatus());
        String valueAsString = objectMapper.writeValueAsString(List.of(1));
        Assertions.assertEquals(valueAsString, mvcResult.getResponse().getContentAsString());
    }

    @Test
    void saveInstructor() throws Exception {

        int id = 1;

        InstructorDto instructorDto = new InstructorDto();
        instructorDto.setName("name");
        instructorDto.setNo(id);
        instructorDto.setSurname("lastName");
        instructorDto.setEmail("email");
        instructorDto.setPhone("phone");
        instructorDto.setMobile("mobile");
        List<InstructorDto> instructorDtos = List.of(instructorDto);

        String writeValueAsString = objectMapper.writeValueAsString(instructorDtos);
        MvcResult mvcResult = mockMvc.perform(post(Paths.ROOT + Paths.INSTRUCTOR).header(HttpHeaders.AUTHORIZATION, getAuthorizationHeaderValue("user", "user"))
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