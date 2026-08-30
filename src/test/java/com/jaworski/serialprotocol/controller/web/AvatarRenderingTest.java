package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.dto.custom.LecturerDTO;
import com.jaworski.serialprotocol.dto.custom.ParticipantDTO;
import com.jaworski.serialprotocol.dto.custom.TechnicianDTO;
import com.jaworski.serialprotocol.dto.custom.TrainerDTO;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.repository.custom.ImageRepository;
import com.jaworski.serialprotocol.service.db.custom.LecturerService;
import com.jaworski.serialprotocol.service.db.custom.ParticipantService;
import com.jaworski.serialprotocol.service.db.custom.TechnicianService;
import com.jaworski.serialprotocol.service.db.custom.TrainerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The avatar as it actually reaches the browser.
 *
 * <p>Every request asks for a large page and asserts the record is present before asserting
 * anything about it. Without that these tests would be quietly empty: the listing endpoints call
 * {@code findAll(PageRequest.of(page, size))} with <strong>no Sort</strong> and a default size of
 * 10, and this suite leaves records behind between classes, so a freshly created person is
 * unlikely to land on page one. An existing counter test survives that only by accident — that
 * listing sorts DESC and its fixture uses {@code nanoTime()}.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AvatarRenderingTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TrainerService trainerService;
    @Autowired
    private LecturerService lecturerService;
    @Autowired
    private TechnicianService technicianService;
    @Autowired
    private ParticipantService participantService;
    @Autowired
    private ImageRepository imageRepository;

    /**
     * Every person template, because the block is copied between them. A copy that kept
     * {@code ${trainer.…}} inside lecturer-service.html throws at render time — HTTP 500 — and
     * the existing smoke tests miss it, because they render an empty table where the th:each
     * body never executes. That is the same mechanism that let a broken alt attribute ship.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/trainer-service", "/lecturer-service", "/technician-service", "/participant-service"})
    void everyPersonTableRendersWithoutError(String path) throws Exception {
        String surname = createPersonWithPhoto(path);

        String html = page(path);

        assertThat(html).as("the record must be on the page at all").contains(surname);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/trainer-service", "/lecturer-service", "/technician-service", "/participant-service"})
    void aPersonWithAPhotoGetsAnImageAvatar(String path) throws Exception {
        String surname = createPersonWithPhoto(path);

        String html = page(path);
        assertThat(html).contains(surname);

        // One pattern over a single tag: three independent contains() would pass even if alt=""
        // belonged to a different element and this avatar repeated the name to screen readers.
        assertThat(html).containsPattern(Pattern.compile(
                "<img[^>]*src=\"[^\"]*/custom/image/[0-9a-f-]+\\?size=thumb\"[^>]*"
                        + "width=\"32\"[^>]*height=\"32\"[^>]*loading=\"lazy\"[^>]*alt=\"\""));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/trainer-service", "/lecturer-service", "/technician-service", "/participant-service"})
    void aPersonWithoutAPhotoGetsInitialsThatStayOutOfTheText(String path) throws Exception {
        // "ZD" is not a substring of "Zofia": if it were, a match could come from the name and
        // the test would prove nothing about the initials.
        String surname = createPersonWithoutPhoto(path);

        String html = page(path);
        assertThat(html).contains(surname);

        String cell = nameCell(html, surname);
        assertThat(cell).contains("data-initials=\"ZD\"").contains("aria-hidden=\"true\"");
        assertThat(textOf(cell))
                .as("initials must not become a text node — they would break sorting and the filter")
                .isEqualTo("Zofia");
    }

    /** The condition the aria-hidden on the initials depends on. */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/trainer-service", "/lecturer-service", "/technician-service", "/participant-service"})
    void aPersonWithoutAPhotoStillReportsItInTheImagesColumn(String path) throws Exception {
        String surname = createPersonWithoutPhoto(path);

        String html = page(path);
        assertThat(html).contains(surname);

        assertThat(html)
                .as("hiding the initials from screen readers is only acceptable while this badge exists")
                .contains(">Missing<");
    }

    /**
     * Without it the editor would always mark the first tile as primary, the user would
     * "confirm" what they see, and a correct pointer would be overwritten by a wrong one.
     */
    @Test
    void theUpdateButtonCarriesTheCurrentPrimaryPhoto() throws Exception {
        Set<UUID> images = twoImages();
        TrainerDTO dto = new TrainerDTO();
        dto.setName("Foto");
        dto.setSurname("Primary" + UUID.randomUUID());
        dto.setImagesUuid(new HashSet<>(images));
        TrainerDTO saved = trainerService.save(dto);
        UUID pointer = trainerService.findById(saved.getId()).getPrimaryImageUuid();

        String html = page("/trainer-service");
        assertThat(html).contains(dto.getSurname());

        assertThat(html).containsPattern(Pattern.compile(
                "class=\"dropdown-item update-trainer-btn\"[^>]*data-primary-image=\"" + pointer + "\"",
                Pattern.DOTALL));
    }

    /** Guards the assumption that lets the avatar live inside the Name cell. */
    @Test
    void theTrainerTableKeepsItsColumnLayout() throws Exception {
        createPersonWithPhoto("/trainer-service");

        String head = page("/trainer-service").split("</thead>")[0];
        Matcher headers = Pattern.compile("<th scope=\"col\"").matcher(head);
        int count = 0;
        while (headers.find()) {
            count++;
        }

        assertThat(count)
                .as("a new column would shift cells[cellIndex] used by the sort")
                // checkbox, #, Name, Surname, Nickname, Email, Phone, Images, Actions
                .isEqualTo(9);
    }

    // --- helpers ---

    private String page(String path) throws Exception {
        return mockMvc.perform(get(path)
                        .param("size", "500")
                        .header(HttpHeaders.AUTHORIZATION, auth()))
                .andReturn().getResponse().getContentAsString();
    }

    private String createPersonWithPhoto(String path) {
        return createPerson(path, "Foto", "Photo" + UUID.randomUUID(), twoImages());
    }

    private String createPersonWithoutPhoto(String path) {
        return createPerson(path, "Zofia", "Dabrowska" + UUID.randomUUID(), Set.of());
    }

    private String createPerson(String path, String name, String surname, Set<UUID> images) {
        switch (path) {
            case "/trainer-service" -> {
                TrainerDTO dto = new TrainerDTO();
                dto.setName(name);
                dto.setSurname(surname);
                dto.setImagesUuid(new HashSet<>(images));
                trainerService.save(dto);
            }
            case "/lecturer-service" -> {
                LecturerDTO dto = new LecturerDTO();
                dto.setName(name);
                dto.setSurname(surname);
                dto.setImagesUuid(new HashSet<>(images));
                lecturerService.save(dto);
            }
            case "/technician-service" -> {
                TechnicianDTO dto = new TechnicianDTO();
                dto.setName(name);
                dto.setSurname(surname);
                dto.setImagesUuid(new HashSet<>(images));
                technicianService.save(dto);
            }
            case "/participant-service" -> {
                ParticipantDTO dto = new ParticipantDTO();
                dto.setId(participantService.nextId());
                dto.setName(name);
                dto.setSurname(surname);
                dto.setImage(images.isEmpty() ? null : images.iterator().next());
                participantService.save(dto);
            }
            default -> throw new IllegalArgumentException(path);
        }
        return surname;
    }

    private Set<UUID> twoImages() {
        Set<UUID> images = new HashSet<>();
        for (int i = 0; i < 2; i++) {
            Image image = new Image();
            image.setData(new byte[]{1, 2, 3});
            image.setContentType("image/jpeg");
            images.add(imageRepository.save(image).getId());
        }
        return images;
    }

    /**
     * The Name cell of the row carrying this surname — located via the row, not via the surname
     * itself: the surname lives in its own column, so walking back from it lands one cell too
     * far right.
     */
    private static String nameCell(String html, String surname) {
        int pos = html.indexOf(surname);
        assertThat(pos).as("row for " + surname).isGreaterThan(0);
        String row = html.substring(html.lastIndexOf("<tr", pos), html.indexOf("</tr>", pos));
        int marker = row.indexOf("avatar-wrap");
        assertThat(marker).as("Name cell of " + surname).isGreaterThan(0);
        String rest = row.substring(row.lastIndexOf("<td", marker));
        return rest.substring(0, rest.indexOf("</td>") + 5);
    }

    private static String textOf(String fragment) {
        return fragment.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private String auth() {
        return "Basic " + Base64.getEncoder().encodeToString("user:user".getBytes());
    }
}
