package com.jaworski.serialprotocol.rest;

import com.jaworski.serialprotocol.dto.StudentDTO;
import com.jaworski.serialprotocol.entity.Student;
import com.jaworski.serialprotocol.service.db.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@RequestMapping("/api")
@RequiredArgsConstructor
public class RestStudent {

    private final StudentService studentService;

    @PostMapping(path = "/student", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<String> getStudent(@RequestBody(required = false) List<StudentDTO> studentDTOS) {
        return List.of("student");
    }

    @GetMapping(path = "/student", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public String getStudents() {
        Student student = new Student();
        student.setName("Janusz");
        student.setLastName("Kowalski");
        student.setId(1);
        studentService.save(student);
        List<Student> allStudents = studentService.getAllStudents();
        return allStudents.toString();
    }
}
