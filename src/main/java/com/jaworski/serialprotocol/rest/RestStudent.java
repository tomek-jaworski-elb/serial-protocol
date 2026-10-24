package com.jaworski.serialprotocol.rest;

import com.jaworski.serialprotocol.dto.InstructorDto;
import com.jaworski.serialprotocol.dto.StudentDTO;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Legacy import endpoints. Access is governed by the {@code /api/**} rule in {@code SecurityConfig};
 * the class used to carry a {@code @PreAuthorize} whose expression was malformed and only ever
 * harmless because method security is not enabled anywhere.
 */
@RestController
@RequestMapping(Paths.ROOT)
@RequiredArgsConstructor
public class RestStudent {

    private static final Logger LOG = LoggerFactory.getLogger(RestStudent.class);
    private final StudentService studentService;
    private final InstructorService instructorService;

    @PostMapping(path = Paths.STUDENT, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Integer> saveStudent(@RequestBody(required = true) List<StudentDTO> studentDTOS) {
        LOG.info("Received student collection size {}", studentDTOS.size());
        List<Integer> ids = studentService.saveNew(studentDTOS.stream().map(StudentMapper::mapToEntity).toList());
        LOG.info("Saved students {}", ids);
        return ids;
    }

    @PostMapping(path = Paths.INSTRUCTOR, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Integer> saveInstructors(@RequestBody(required = true) List<InstructorDto> instructorsDTO) {
        LOG.info("Received instructor collection size {}", instructorsDTO.size());
        List<Integer> result = instructorService.saveNew(instructorsDTO.stream().map(InstructorMapper::mapToEntity).toList());
        LOG.info("Saved instructors {}", result);
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
