package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.service.db.custom.CourseTypeService;
import com.jaworski.serialprotocol.service.db.custom.ParticipantService;
import com.jaworski.serialprotocol.service.db.custom.TrainerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Invalid input is rejected on the DTO, before any service runs, and the toast names the field.
 *
 * <p>Validation used to happen only when Hibernate flushed the entity, so the user saw
 * "Please verify your input" with nothing more, and the person-with-photos handlers had
 * already stored the upload by then.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class CustomDBControllerValidationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TrainerService trainerService;
    @Autowired private ParticipantService participantService;
    @Autowired private CourseTypeService courseTypeService;

    @Test
    void aBlankNameIsNamedInTheToastAndNothingIsSaved() throws Exception {
        long before = trainerService.findAll().size();

        mockMvc.perform(multipart("/trainer-service/add")
                .param("name", "   ")
                .param("surname", "Nowak")
                .with(csrf()).header(HttpHeaders.AUTHORIZATION, auth()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/trainer-service"))
            .andExpect(flash().attribute("errorMessage", containsString("name is required")))
            .andExpect(flash().attribute("errorMessage", not(containsString("Please verify"))));

        assertThat(trainerService.findAll()).hasSize((int) before);
    }

    @Test
    void anOverlongFieldReportsItsLimit() throws Exception {
        mockMvc.perform(multipart("/lecturer-service/add")
                .param("name", "x".repeat(150))
                .param("surname", "Nowak")
                .with(csrf()).header(HttpHeaders.AUTHORIZATION, auth()))
            .andExpect(flash().attribute("errorMessage", containsString("name ")))
            .andExpect(flash().attribute("errorMessage", containsString("100")));
    }

    @Test
    void aMalformedEmailIsRejectedButABlankOneIsNot() throws Exception {
        long before = participantService.findAll().size();

        mockMvc.perform(multipart("/participant-service/add")
                .param("name", "Jan").param("surname", "Kowalski")
                .param("email", "not-an-address")
                .with(csrf()).header(HttpHeaders.AUTHORIZATION, auth()))
            .andExpect(flash().attribute("errorMessage", containsString("email must be a valid e-mail address")));
        assertThat(participantService.findAll()).hasSize((int) before);

        // Blank optional fields are turned into null by the binder and must pass.
        mockMvc.perform(multipart("/participant-service/add")
                .param("name", "Jan").param("surname", "Kowalski")
                .param("email", "   ")
                .with(csrf()).header(HttpHeaders.AUTHORIZATION, auth()))
            .andExpect(flash().attributeExists("successMessage"));
        assertThat(participantService.findAll()).hasSize((int) before + 1);
    }

    @Test
    void aCourseWhoseEndPrecedesItsStartIsRejected() throws Exception {
        mockMvc.perform(post("/courses-service/add")
                .param("participantUuid", UUID.randomUUID().toString())
                .param("courseTypeId", UUID.randomUUID().toString())
                .param("startDate", "10/03/2026")
                .param("endDate", "09/03/2026")
                .with(csrf()).header(HttpHeaders.AUTHORIZATION, auth()))
            .andExpect(flash().attribute("errorMessage", containsString("end date must be the same as or after the start date")));
    }

    @Test
    void aCourseTypeCodeOverItsColumnLengthIsRejectedBeforeTheDatabaseSeesIt() throws Exception {
        long before = courseTypeService.findAll().size();

        mockMvc.perform(post("/course-type-service/add")
                .param("code", "C".repeat(33))
                .param("description", "too long a code")
                .with(csrf()).header(HttpHeaders.AUTHORIZATION, auth()))
            .andExpect(flash().attribute("errorMessage", containsString("code ")));

        assertThat(courseTypeService.findAll()).hasSize((int) before);
    }

    private static String auth() {
        return "Basic " + Base64.getEncoder().encodeToString("user:user".getBytes());
    }
}
