package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.dto.custom.CourseCounterDTO;
import com.jaworski.serialprotocol.dto.custom.CourseTypeDTO;
import com.jaworski.serialprotocol.dto.custom.CoursesDTO;
import com.jaworski.serialprotocol.dto.custom.LecturerDTO;
import com.jaworski.serialprotocol.dto.custom.ParticipantDTO;
import com.jaworski.serialprotocol.dto.custom.TechnicianDTO;
import com.jaworski.serialprotocol.dto.custom.TrainerDTO;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.service.db.custom.CourseCounterService;
import com.jaworski.serialprotocol.service.db.custom.CourseTypeService;
import com.jaworski.serialprotocol.service.db.custom.CoursesService;
import com.jaworski.serialprotocol.service.db.custom.ImageService;
import com.jaworski.serialprotocol.service.db.custom.LecturerService;
import com.jaworski.serialprotocol.service.db.custom.ParticipantService;
import com.jaworski.serialprotocol.service.db.custom.TechnicianService;
import com.jaworski.serialprotocol.service.db.custom.TrainerService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rendering a list page must never load an {@link Image} entity.
 *
 * <p>Field-level laziness on {@code Image.data} is inactive in this build (no
 * hibernate-enhance-maven-plugin), so every {@code Image} that Hibernate loads carries its
 * LONGBLOB columns with it. Before this test existed the trainer page initialised each
 * person's image collection just to collect ids, and the participant and course pages
 * reached their photos through eager to-one associations: one render read every photo in
 * the database. The pages now get image ids from id-only projections and the associations
 * are lazy; this test is what keeps it that way.</p>
 *
 * <p>Measured with Hibernate statistics rather than by timing, so the assertion is exact and
 * independent of the machine.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ListPagesLoadNoImageBlobsTest {

    private static final List<String> LIST_PAGES = List.of(
        "/trainer-service", "/lecturer-service", "/technician-service",
        "/participant-service", "/course-counter-service", "/courses-service");

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private ImageService imageService;
    @Autowired private TrainerService trainerService;
    @Autowired private LecturerService lecturerService;
    @Autowired private TechnicianService technicianService;
    @Autowired private ParticipantService participantService;
    @Autowired private CourseCounterService courseCounterService;
    @Autowired private CourseTypeService courseTypeService;
    @Autowired private CoursesService coursesService;

    private Statistics statistics;

    @BeforeEach
    void enableStatistics() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    @Test
    void noListPageLoadsAnImageEntity() throws Exception {
        seedOnePersonOfEachKindWithPhotosAndACourse();

        for (String page : LIST_PAGES) {
            statistics.clear();
            mockMvc.perform(get(page).param("size", "50").header(HttpHeaders.AUTHORIZATION, auth()))
                .andExpect(status().isOk());
            assertThat(statistics.getEntityStatistics(Image.class.getName()).getLoadCount())
                .as("Image entities loaded while rendering %s", page)
                .isZero();
        }
    }

    /**
     * The participant page used to issue one courses query per row. The number of statements
     * must not depend on how many participants the page shows.
     */
    @Test
    void participantPageQueryCountDoesNotGrowWithRows() throws Exception {
        seedOnePersonOfEachKindWithPhotosAndACourse();
        long withOne = statementsFor("/participant-service");

        for (int i = 0; i < 4; i++) {
            seedOnePersonOfEachKindWithPhotosAndACourse();
        }
        long withFive = statementsFor("/participant-service");

        assertThat(withFive).as("statements for the page with more rows").isEqualTo(withOne);
    }

    private long statementsFor(String page) throws Exception {
        statistics.clear();
        mockMvc.perform(get(page).param("size", "50").header(HttpHeaders.AUTHORIZATION, auth()))
            .andExpect(status().isOk());
        return statistics.getPrepareStatementCount();
    }

    // --- seeding through the services, exactly as the controllers do ---

    private void seedOnePersonOfEachKindWithPhotosAndACourse() {
        TrainerDTO trainer = new TrainerDTO();
        trainer.setName("Anna");
        trainer.setSurname("Trainer");
        trainer.setImagesUuid(Set.of(photo(), photo()));
        trainer = trainerService.save(trainer);

        LecturerDTO lecturer = new LecturerDTO();
        lecturer.setName("Ewa");
        lecturer.setSurname("Lecturer");
        lecturer.setImagesUuid(Set.of(photo()));
        lecturer = lecturerService.save(lecturer);

        TechnicianDTO technician = new TechnicianDTO();
        technician.setName("Jan");
        technician.setSurname("Technician");
        technician.setImagesUuid(Set.of(photo()));
        technician = technicianService.save(technician);

        ParticipantDTO participant = new ParticipantDTO();
        participant.setName("Piotr");
        participant.setSurname("Participant");
        participant.setImage(photo());
        participant = participantService.save(participant);

        CourseCounterDTO counter = courseCounterService.save(
            new CourseCounterDTO(null, ThreadLocalRandom.current().nextLong(1_000_000, Long.MAX_VALUE), photo()));

        CourseTypeDTO courseType = new CourseTypeDTO();
        courseType.setCode("T" + UUID.randomUUID().toString().substring(0, 8));
        courseType.setDescription("Type");
        courseType = courseTypeService.save(courseType);

        CoursesDTO course = new CoursesDTO();
        course.setParticipantUuid(participant.getParticipantUuid());
        course.setCourseTypeId(courseType.getId());
        course.setCourseCounterUuid(counter.uuid());
        course.setStartDate(LocalDate.of(2026, 1, 10));
        course.setEndDate(LocalDate.of(2026, 1, 12));
        course.setTrainerIds(Set.of(trainer.getId()));
        course.setLecturerIds(Set.of(lecturer.getId()));
        course.setTechnicianIds(Set.of(technician.getId()));
        coursesService.save(course);
    }

    private UUID photo() {
        return imageService.saveImage(new byte[]{1, 2, 3, 4}, "image/png").getId();
    }

    private static String auth() {
        return "Basic " + Base64.getEncoder().encodeToString("user:user".getBytes());
    }
}
