package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.dto.custom.CourseTypeDTO;
import com.jaworski.serialprotocol.service.db.custom.CourseTypeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** What a user sees when the form they submit is older than the record: one message, back where they were. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class StaleFormTest {

  private static final String LIST_PAGE = "/course-type-service?page=0&size=10";

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private CourseTypeService courseTypeService;

  @Test
  void aStaleFormIsBouncedBackToThePageItCameFrom() throws Exception {
    CourseTypeDTO opened = courseTypeService.save(courseType("STALE-1"));
    CourseTypeDTO byOther = courseTypeService.findById(opened.getId());
    byOther.setDescription("changed by someone else");
    courseTypeService.update(byOther);

    mockMvc.perform(post("/course-type-service/update")
            .param("id", String.valueOf(opened.getId()))
            .param("code", "STALE-1")
            .param("description", "my stale edit")
            .param("version", String.valueOf(opened.getVersion()))
            .header(HttpHeaders.REFERER, LIST_PAGE)
            .header(HttpHeaders.AUTHORIZATION, user())
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl(LIST_PAGE))
        .andExpect(flash().attribute("errorMessage", containsString("changed by someone else")));

    assertEquals("changed by someone else", courseTypeService.findById(opened.getId()).getDescription());
  }

  @Test
  void aFormWithoutAVersionIsRefused() throws Exception {
    CourseTypeDTO opened = courseTypeService.save(courseType("STALE-2"));

    mockMvc.perform(post("/course-type-service/update")
            .param("id", String.valueOf(opened.getId()))
            .param("code", "STALE-2")
            .param("description", "edit without a version")
            .header(HttpHeaders.AUTHORIZATION, user())
            .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(flash().attribute("errorMessage", containsString("version")));

    assertEquals("Description STALE-2", courseTypeService.findById(opened.getId()).getDescription());
  }

  private static CourseTypeDTO courseType(String code) {
    CourseTypeDTO dto = new CourseTypeDTO();
    dto.setCode(code);
    dto.setDescription("Description " + code);
    return dto;
  }

  private static String user() {
    return "Basic " + Base64.getEncoder().encodeToString("user:user".getBytes(StandardCharsets.UTF_8));
  }
}
