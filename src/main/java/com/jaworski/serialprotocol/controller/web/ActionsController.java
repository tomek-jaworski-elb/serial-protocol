package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.entity.Student;
import com.jaworski.serialprotocol.service.db.StudentService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class ActionsController {

    private static final Logger LOG = LoggerFactory.getLogger(ActionsController.class);
    private final StudentService studentService;

    @PreAuthorize("hasRole(T(com.jaworski.serialprotocol.authorization.SecurityRoles).ROLE_ADMIN.getRole()) or " +
            "hasRole(T(com.jaworski.serialprotocol.authorization.SecurityRoles).ROLE_ADMIN.getName() + '_T')")
    @PostMapping("/name-service/{id}/show")
    public String show(@PathVariable int id) {
        LOG.info("Show {}", id);
        Student student = studentService.setStudentShow(id);
        LOG.info("Show student {}", student);
        return "redirect:/name-service";
    }

    @PreAuthorize("hasRole(T(com.jaworski.serialprotocol.authorization.SecurityRoles).ROLE_ADMIN.getRole()) or " +
            "hasRole(T(com.jaworski.serialprotocol.authorization.SecurityRoles).ROLE_ADMIN.getName() + '_T')")
    @PostMapping("/name-service/{id}/hide")
    public String hide(@PathVariable int id) {
        LOG.info("Hide {}", id);
        Student student = studentService.setStudentHide(id);
        LOG.info("Hide student {}", student);
        return "redirect:/name-service";
    }
}
