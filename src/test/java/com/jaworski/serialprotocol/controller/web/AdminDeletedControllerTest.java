package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.dto.custom.CourseTypeDTO;
import com.jaworski.serialprotocol.service.db.custom.CourseTypeService;
import com.jaworski.serialprotocol.service.db.custom.DeletedKind;
import com.jaworski.serialprotocol.service.db.custom.DeletedRecordsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Access to the trash and one restore/purge round trip through HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AdminDeletedControllerTest {

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private CourseTypeService courseTypeService;
  @Autowired
  private DeletedRecordsService trash;

  @Test
  void theTrashIsForAdministratorsOnly() throws Exception {
    mockMvc.perform(get("/admin/deleted/participants"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/admin/deleted/participants").header(HttpHeaders.AUTHORIZATION, auth("user", "user")))
        .andExpect(status().isForbidden());
    mockMvc.perform(post("/admin/deleted/participants/purge/" + java.util.UUID.randomUUID())
            .with(csrf()).header(HttpHeaders.AUTHORIZATION, auth("user", "user")))
        .andExpect(status().isForbidden());
  }

  @Test
  void anAdministratorSeesTheTabsAndLandsOnParticipants() throws Exception {
    mockMvc.perform(get("/admin/deleted").header(HttpHeaders.AUTHORIZATION, admin()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/deleted/participants"));

    mockMvc.perform(get("/admin/deleted/course-types").header(HttpHeaders.AUTHORIZATION, admin()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Deleted records")))
        .andExpect(content().string(containsString("/admin/deleted/trainers")))
        .andExpect(content().string(containsString("/admin/deleted/courses")));
  }

  @Test
  void anUnknownKindIsNotFound() throws Exception {
    mockMvc.perform(get("/admin/deleted/students").header(HttpHeaders.AUTHORIZATION, admin()))
        .andExpect(status().isNotFound());
  }

  @Test
  void restoreAndPurgeRoundTrip() throws Exception {
    CourseTypeDTO type = new CourseTypeDTO();
    type.setCode("ADM-RT");
    type.setDescription("round trip");
    type = courseTypeService.save(type);
    String key = String.valueOf(type.getId());
    courseTypeService.deleteById(type.getId());

    mockMvc.perform(get("/admin/deleted/course-types").header(HttpHeaders.AUTHORIZATION, admin()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("ADM-RT")))
        .andExpect(content().string(containsString("/admin/deleted/course-types/restore/" + key)));

    mockMvc.perform(post("/admin/deleted/course-types/restore/" + key)
            .with(csrf()).header(HttpHeaders.AUTHORIZATION, admin()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/deleted/course-types"))
        .andExpect(flash().attribute("successMessage", "Course type restored."));
    assertNotNull(courseTypeService.findById(type.getId()));

    mockMvc.perform(post("/admin/deleted/course-types/purge/" + key)
            .with(csrf()).header(HttpHeaders.AUTHORIZATION, admin()))
        .andExpect(status().is3xxRedirection())
        .andExpect(flash().attributeExists("errorMessage"));   // active records cannot be purged

    courseTypeService.deleteById(type.getId());
    mockMvc.perform(post("/admin/deleted/course-types/purge/" + key)
            .with(csrf()).header(HttpHeaders.AUTHORIZATION, admin()))
        .andExpect(status().is3xxRedirection())
        .andExpect(flash().attribute("successMessage", "Course type permanently deleted."));

    long id = type.getId();
    assertThrows(IllegalArgumentException.class, () -> courseTypeService.findById(id));
    assertEquals(0, trash.list(DeletedKind.COURSE_TYPES, PageRequest.of(0, 10)).getContent().stream()
        .filter(row -> row.title().equals("ADM-RT")).count());
  }

  private static String admin() {
    return auth("admin", "admin");
  }

  private static String auth(String user, String password) {
    return "Basic " + Base64.getEncoder()
        .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
  }
}
