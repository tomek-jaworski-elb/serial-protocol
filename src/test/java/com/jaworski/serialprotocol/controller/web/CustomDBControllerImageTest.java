package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.dto.custom.ParticipantDTO;
import com.jaworski.serialprotocol.dto.custom.TrainerDTO;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.repository.custom.ImageRepository;
import com.jaworski.serialprotocol.service.db.custom.ImageService;
import com.jaworski.serialprotocol.service.db.custom.ParticipantService;
import com.jaworski.serialprotocol.service.db.custom.TrainerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Covers image merge semantics on the update endpoints.
 *
 * <p>The requests carry the images to <em>remove</em>, never the ones to keep, so
 * most of these tests are really about what happens when the client says nothing:
 * a silent request must leave the images alone rather than wipe them.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class CustomDBControllerImageTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TrainerService trainerService;
    @Autowired
    private ParticipantService participantService;
    @Autowired
    private ImageService imageService;
    @Autowired
    private ImageRepository imageRepository;

    // --- multi-image entities (trainer stands in for lecturer/technician: same helper) ---

    @Test
    void update_withoutRemoveParam_keepsEveryImage() throws Exception {
        TrainerDTO trainer = trainerWithImages(3);

        mockMvc.perform(updateTrainer(trainer)).andReturn();

        assertThat(imagesOf(trainer)).hasSize(3);
    }

    @Test
    void update_removingOne_keepsTheRestAndDeletesTheOrphan() throws Exception {
        TrainerDTO trainer = trainerWithImages(3);
        UUID doomed = imagesOf(trainer).iterator().next();

        mockMvc.perform(updateTrainer(trainer).param("removeImageUuids", doomed.toString()));

        assertThat(imagesOf(trainer)).hasSize(2).doesNotContain(doomed);
        assertThat(imageRepository.existsById(doomed))
                .as("the removed image row must not be left behind")
                .isFalse();
    }

    @Test
    void update_removingAll_leavesNoImages() throws Exception {
        TrainerDTO trainer = trainerWithImages(3);
        String[] all = imagesOf(trainer).stream().map(UUID::toString).toArray(String[]::new);

        mockMvc.perform(updateTrainer(trainer).param("removeImageUuids", all));

        assertThat(imagesOf(trainer)).isEmpty();
    }

    /**
     * The client can only subtract from the entity's own set, so a uuid belonging to
     * someone else does nothing — it cannot detach their photo and cannot attach it here.
     */
    @Test
    void update_withForeignImageUuid_changesNothing() throws Exception {
        TrainerDTO victim = trainerWithImages(2);
        TrainerDTO attacker = trainerWithImages(1);
        UUID foreign = imagesOf(victim).iterator().next();

        mockMvc.perform(updateTrainer(attacker).param("removeImageUuids", foreign.toString()));

        assertThat(imagesOf(victim)).as("other trainer untouched").hasSize(2).contains(foreign);
        assertThat(imagesOf(attacker)).as("no foreign image attached").hasSize(1).doesNotContain(foreign);
    }

    /**
     * The cap is checked before anything is written. Controllers here are not
     * transactional and ImageService commits on its own, so a check that ran after
     * the upload would leave orphaned rows nothing ever cleans up.
     */
    @Test
    void update_exceedingTheCap_persistsNoOrphanedImages() throws Exception {
        TrainerDTO trainer = trainerWithImages(6);
        long imagesBefore = imageRepository.count();

        mockMvc.perform(multipartUpdateTrainer(trainer).file(jpeg("seventh.jpg")));

        assertThat(imagesOf(trainer)).as("still at the cap").hasSize(6);
        assertThat(imageRepository.count())
                .as("the rejected upload must not have been stored")
                .isEqualTo(imagesBefore);
    }

    /**
     * Guards the {@code max(0, ...)} clamp: a record holding more images than the cap
     * (data predating the limit) must still be repairable, and removal is the only
     * way back under it.
     */
    @Test
    void update_onRecordOverTheCap_stillAllowsRemoval() throws Exception {
        TrainerDTO trainer = trainerWithImages(7);
        String[] two = imagesOf(trainer).stream().limit(2).map(UUID::toString).toArray(String[]::new);

        mockMvc.perform(updateTrainer(trainer).param("removeImageUuids", two));

        assertThat(imagesOf(trainer)).hasSize(5);
    }

    // --- single-image entities ---

    @Test
    void updateParticipant_withRemoveFlag_clearsTheImage() throws Exception {
        ParticipantDTO participant = participantWithImage();
        UUID image = participant.getImage();

        mockMvc.perform(updateParticipant(participant).param("removeImage", "true"));

        assertThat(reload(participant).getImage()).isNull();
        assertThat(imageRepository.existsById(image)).isFalse();
    }

    @Test
    void updateParticipant_withoutRemoveFlag_keepsTheImage() throws Exception {
        ParticipantDTO participant = participantWithImage();

        mockMvc.perform(updateParticipant(participant));

        assertThat(reload(participant).getImage()).isEqualTo(participant.getImage());
    }

    /** An uploaded file is the more explicit intent, so it outranks the checkbox. */
    @Test
    void updateParticipant_uploadWinsOverRemoveFlag() throws Exception {
        ParticipantDTO participant = participantWithImage();
        UUID original = participant.getImage();

        mockMvc.perform(multipart("/participant-service/update")
                .file(new MockMultipartFile("imageFile", "new.jpg", "image/jpeg", new byte[]{4, 5, 6}))
                .param("participantUuid", participant.getParticipantUuid().toString())
                .param("id", String.valueOf(participant.getId()))
                .param("name", participant.getName())
                .param("surname", participant.getSurname())
                .param("removeImage", "true")
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, auth()));

        UUID after = reload(participant).getImage();
        assertThat(after).isNotNull().isNotEqualTo(original);
    }

    // --- fixtures ---

    private TrainerDTO trainerWithImages(int count) {
        Set<UUID> images = new HashSet<>();
        for (int i = 0; i < count; i++) {
            images.add(imageService.saveImage(new byte[]{1, 2, 3}, "image/jpeg").getId());
        }
        TrainerDTO dto = new TrainerDTO();
        dto.setName("Img");
        dto.setSurname("Trainer" + UUID.randomUUID());
        dto.setImagesUuid(images);
        return trainerService.save(dto);
    }

    private ParticipantDTO participantWithImage() {
        Image image = imageService.saveImage(new byte[]{1, 2, 3}, "image/jpeg");
        ParticipantDTO dto = new ParticipantDTO();
        dto.setId(participantService.nextId());
        dto.setName("Img");
        dto.setSurname("Participant" + UUID.randomUUID());
        dto.setImage(image.getId());
        return participantService.save(dto);
    }

    private Set<UUID> imagesOf(TrainerDTO trainer) {
        return trainerService.findById(trainer.getId()).getImagesUuid();
    }

    private ParticipantDTO reload(ParticipantDTO participant) {
        return participantService.findByUuid(participant.getParticipantUuid());
    }

    private MockHttpServletRequestBuilder updateTrainer(TrainerDTO trainer) {
        return post("/trainer-service/update")
                .param("id", trainer.getId().toString())
                .param("name", trainer.getName())
                .param("surname", trainer.getSurname())
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, auth());
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder multipartUpdateTrainer(
            TrainerDTO trainer) {
        var builder = multipart("/trainer-service/update");
        builder.param("id", trainer.getId().toString());
        builder.param("name", trainer.getName());
        builder.param("surname", trainer.getSurname());
        builder.with(csrf());
        builder.header(HttpHeaders.AUTHORIZATION, auth());
        return builder;
    }

    private MockHttpServletRequestBuilder updateParticipant(ParticipantDTO participant) {
        return post("/participant-service/update")
                .param("participantUuid", participant.getParticipantUuid().toString())
                .param("id", String.valueOf(participant.getId()))
                .param("name", participant.getName())
                .param("surname", participant.getSurname())
                .with(csrf())
                .header(HttpHeaders.AUTHORIZATION, auth());
    }

    private static MockMultipartFile jpeg(String name) {
        return new MockMultipartFile("imageFiles", name, "image/jpeg", new byte[]{7, 8, 9});
    }

    private String auth() {
        return "Basic " + Base64.getEncoder().encodeToString("user:user".getBytes());
    }
}
