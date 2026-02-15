package com.jaworski.serialprotocol.rest;

import com.jaworski.serialprotocol.dto.InstructorDto;
import com.jaworski.serialprotocol.dto.StudentDTO;
import com.jaworski.serialprotocol.entity.Instructor;
import com.jaworski.serialprotocol.entity.Student;
import com.jaworski.serialprotocol.mappers.InstructorMapper;
import com.jaworski.serialprotocol.mappers.StudentMapper;
import com.jaworski.serialprotocol.rest.paths.Paths;
import com.jaworski.serialprotocol.service.db.InstructorService;
import com.jaworski.serialprotocol.service.db.StudentService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping(Paths.ROOT)
@RequiredArgsConstructor
@PreAuthorize(value = "hasRole(hasRole(T(com.jaworski.serialprotocol.authorization.SecurityRoles).ROLE_USER.getRole()) or" +
        " hasRole(T(com.jaworski.serialprotocol.authorization.SecurityRoles).ROLE_USER.getName() + '_T')")
public class RestStudent {

    private static final Logger LOG = LoggerFactory.getLogger(RestStudent.class);
    private final StudentService studentService;
    private final InstructorService instructorService;

    @PostMapping(path = Paths.STUDENT, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Integer> saveStudent(@RequestBody(required = true) List<StudentDTO> studentDTOS) {
        LOG.info("Received student collection size {}", studentDTOS.size());
        List<Integer> ids = new ArrayList<>();
        studentDTOS
                .forEach(studentDTO -> {
                    Student studentById = studentService.getStudentById(studentDTO.getId());
                    if (studentById == null) {
                        Student student = StudentMapper.mapToEntity(studentDTO);
                        Student save = studentService.save(student);
                        ids.add(save.getId());
                        LOG.info("Saved student {}", student);
                    }
                });
        return ids;
    }

    @PostMapping(path = Paths.INSTRUCTOR, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Integer> saveInstructors(@RequestBody(required = true) List<InstructorDto> instructorsDTO) {
        LOG.info("Received instructor collection size {}", instructorsDTO.size());
        List<Integer> result = new ArrayList<>();
        instructorsDTO.stream()
                .map(InstructorMapper::mapToEntity)
                .filter(instructor -> instructorService.findByNameAndSurname(instructor).isEmpty())
                .forEach(instructor -> {
                    try {
                        Instructor save = instructorService.save(instructor);
                        result.add(save.getNo());
                        LOG.info("Saved instructor {}", save.getNo());
                    } catch (Exception e) {
                        LOG.error("Error saving instructor {}", instructor.toString(), e);
                    }
                });
        return result;
    }

    @GetMapping(path = Paths.INSTRUCTOR, produces = MediaType.APPLICATION_JSON_VALUE)
    public Page<InstructorDto> getInstructors(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        LOG.info("Getting instructors page {} with size {}", page, size);
        return instructorService.findAllPaginated(page, size)
                .map(InstructorMapper::mapToDto);
    }
}
