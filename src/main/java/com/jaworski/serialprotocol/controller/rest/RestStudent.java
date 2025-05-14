package com.jaworski.serialprotocol.controller.rest;

import com.jaworski.serialprotocol.dto.StudentDTO;
import com.jaworski.serialprotocol.entity.Student;
import com.jaworski.serialprotocol.mappers.StudentMapper;
import com.jaworski.serialprotocol.service.db.StudentService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize(value =
        "hasRole(T(com.jaworski.serialprotocol.authorization.SecurityRoles).ROLE_ADMIN.getRole()) or " +
                "hasRole(T(com.jaworski.serialprotocol.authorization.SecurityRoles).ROLE_USER.getRole()) or " +
                "hasRole(T(com.jaworski.serialprotocol.authorization.SecurityRoles).ROLE_ADMIN.getName() + '_T') or " +
                "hasRole(T(com.jaworski.serialprotocol.authorization.SecurityRoles).ROLE_USER.getName() + '_T')")
public class RestStudent {

    private static final Logger LOG = LoggerFactory.getLogger(RestStudent.class);
    public static final String STUDENT = "/student";

    private final StudentService studentService;

    @PostMapping(path = STUDENT, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Integer> getStudent(@RequestBody(required = true) List<StudentDTO> studentDTOS) {
        LOG.info("Received dtos size {}", studentDTOS.size());
        List<Integer> ids = new ArrayList<>();
        studentDTOS
                .forEach(studentDTO -> {
                    Student studentById = studentService.findStudentById(studentDTO.getId());
                    if (studentById == null) {
                        Student student = StudentMapper.mapToEntity(studentDTO);
                        Student save = studentService.save(student);
                        ids.add(save.getId());
                        LOG.info("Saved student {}", student);
                    }
                });
        return ids;
    }
}
