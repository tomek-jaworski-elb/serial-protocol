package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.LecturerDTO;
import com.jaworski.serialprotocol.dto.custom.PrimaryImage;
import com.jaworski.serialprotocol.dto.custom.TechnicianDTO;
import com.jaworski.serialprotocol.dto.custom.TrainerDTO;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.repository.custom.ImageRepository;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence side of the primary-image pointer, run against all three multi-image people.
 *
 * <p>The rule itself lives in one place ({@link PrimaryImage}) and is unit-tested there. What is
 * exercised here is the <em>wiring</em>: each service calls it separately, with its own arguments,
 * in both {@code save} and {@code update}. A service that forgot the call, or passed the merged
 * set where the previous one belongs, compiles cleanly and would be caught by nothing else — and
 * a test written against one service would say nothing about the other two.</p>
 */
@DataJpaTest
// PER_CLASS so the @MethodSource factory can hand out adapters built from the autowired
// services; the transaction is still rolled back per test method.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import({TrainerService.class, LecturerService.class, TechnicianService.class,
        ImageService.class, CoursesService.class, ParticipantService.class, CourseTypeService.class})
class PrimaryImagePersistenceTest {

    @Autowired
    private TrainerService trainerService;
    @Autowired
    private LecturerService lecturerService;
    @Autowired
    private TechnicianService technicianService;
    @Autowired
    private ImageRepository imageRepository;

    /**
     * Names, not adapters. The @MethodSource factory runs while JUnit resolves arguments, which
     * happens before the test instance is fully prepared — handing out objects built from the
     * autowired services there yielded nulls for whichever test ran first. Resolving inside the
     * test body puts the lookup after injection, where it is guaranteed.
     */
    private static Stream<String> people() {
        return Stream.of("trainer", "lecturer", "technician");
    }

    private PersonOps ops(String kind) {
        return switch (kind) {
            case "trainer" -> new TrainerOps(trainerService);
            case "lecturer" -> new LecturerOps(lecturerService);
            case "technician" -> new TechnicianOps(technicianService);
            default -> throw new IllegalArgumentException(kind);
        };
    }

    // --- the resolution order, per service ---

    @ParameterizedTest(name = "{0}")
    @MethodSource("people")
    void anExplicitPointerIsStored(String kind) {
        PersonOps ops = ops(kind);
        Set<UUID> images = threeImages();
        UUID chosen = notTheFallback(images);

        UUID id = ops.create(images, null);
        ops.update(id, images, chosen);

        assertThat(ops.pointerOf(id)).isEqualTo(chosen);
        assertThat(chosen)
                .as("if the choice equalled the fallback this test could not tell them apart")
                .isNotEqualTo(PrimaryImage.minByString(images));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("people")
    void aRequestThatSaysNothingLeavesThePointerAlone(String kind) {
        PersonOps ops = ops(kind);
        Set<UUID> images = threeImages();
        UUID chosen = notTheFallback(images);
        UUID id = ops.create(images, null);
        ops.update(id, images, chosen);

        ops.update(id, images, null);          // e.g. the user only edited a phone number

        assertThat(ops.pointerOf(id))
                .as("absent means 'leave it alone', never 'recompute'")
                .isEqualTo(chosen);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("people")
    void removingThePointedPhotoMovesThePointerDeterministically(String kind) {
        PersonOps ops = ops(kind);
        Set<UUID> images = threeImages();
        UUID chosen = notTheFallback(images);
        UUID id = ops.create(images, null);
        ops.update(id, images, chosen);

        Set<UUID> remaining = new LinkedHashSet<>(images);
        remaining.remove(chosen);
        ops.update(id, remaining, null);

        assertThat(ops.pointerOf(id))
                .as("not whatever the set iterator happens to yield")
                .isEqualTo(PrimaryImage.minByString(remaining));
    }

    /**
     * The uuid is a real image belonging to somebody else, never a random one. A random uuid
     * cannot tell {@code merged.contains(x)} apart from {@code imageRepository.existsById(x)} —
     * and the second would put another person's face next to this person's name.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("people")
    void aPointerToSomebodyElsesPhotoIsRefused(String kind) {
        PersonOps ops = ops(kind);
        Set<UUID> mine = threeImages();
        UUID victimId = ops.create(threeImages(), null);
        UUID somebodyElses = ops.imagesOf(victimId).iterator().next();

        UUID id = ops.create(mine, null);
        ops.update(id, mine, somebodyElses);

        assertThat(ops.pointerOf(id))
                .as("falls back to its own photo")
                .isEqualTo(PrimaryImage.minByString(mine));
        assertThat(ops.imagesOf(victimId))
                .as("and the other person keeps their photo")
                .contains(somebodyElses);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("people")
    void removingEveryPhotoClearsThePointer(String kind) {
        PersonOps ops = ops(kind);
        Set<UUID> images = threeImages();
        UUID id = ops.create(images, null);

        ops.update(id, Set.of(), null);

        assertThat(ops.pointerOf(id)).isNull();
        assertThat(ops.imagesOf(id)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("people")
    void aNewRecordGetsAPointerOfItsOwn(String kind) {
        PersonOps ops = ops(kind);
        Set<UUID> images = threeImages();

        UUID id = ops.create(images, null);

        assertThat(ops.pointerOf(id))
                .as("a record with photos must never render initials")
                .isEqualTo(PrimaryImage.minByString(images));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("people")
    void aNewRecordDoesNotTrustAPointerFromTheRequest(String kind) {
        PersonOps ops = ops(kind);
        Set<UUID> stranger = threeImages();
        ops.create(stranger, null);
        UUID somebodyElses = stranger.iterator().next();
        Set<UUID> mine = threeImages();

        UUID id = ops.create(mine, somebodyElses);

        assertThat(ops.pointerOf(id)).isEqualTo(PrimaryImage.minByString(mine));
    }

    /**
     * Why the fallback prefers photos that were already there: otherwise uploading a photo would
     * change the avatar of records that never had a pointer, and leave records that did have one
     * alone — the same action with two outcomes, decided by invisible history.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("people")
    void addingAPhotoDoesNotChangeTheAvatar(String kind) {
        PersonOps ops = ops(kind);
        Set<UUID> original = threeImages();
        UUID id = ops.create(original, null);
        UUID avatarBefore = ops.pointerOf(id);

        Set<UUID> withExtra = new LinkedHashSet<>(original);
        withExtra.add(newImage());
        ops.update(id, withExtra, null);

        assertThat(ops.pointerOf(id)).isEqualTo(avatarBefore);
    }

    /** Orphaned image rows are a named, repeat defect of this project. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("people")
    void removingThePointedPhotoAlsoDeletesItsRow(String kind) {
        PersonOps ops = ops(kind);
        Set<UUID> images = threeImages();
        UUID id = ops.create(images, null);
        UUID pointed = ops.pointerOf(id);
        long before = imageRepository.count();

        Set<UUID> remaining = new LinkedHashSet<>(images);
        remaining.remove(pointed);
        ops.update(id, remaining, null);

        assertThat(imageRepository.existsById(pointed)).isFalse();
        assertThat(imageRepository.count()).isEqualTo(before - 1);
    }

    // --- fixtures ---

    private Set<UUID> threeImages() {
        Set<UUID> images = new LinkedHashSet<>();
        for (int i = 0; i < 3; i++) {
            images.add(newImage());
        }
        return images;
    }

    private UUID newImage() {
        Image image = new Image();
        image.setData(new byte[]{1, 2, 3});
        image.setContentType("image/jpeg");
        return imageRepository.save(image).getId();
    }

    /** Any member that is not what the fallback would choose. */
    private static UUID notTheFallback(Set<UUID> images) {
        UUID fallback = PrimaryImage.minByString(images);
        return images.stream().filter(u -> !u.equals(fallback)).findFirst().orElseThrow();
    }

    // --- one small adapter per person type: same rule, three separate call sites ---

    private interface PersonOps {
        UUID create(Set<UUID> images, UUID pointer);

        void update(UUID id, Set<UUID> images, UUID pointer);

        Set<UUID> imagesOf(UUID id);

        UUID pointerOf(UUID id);
    }

    private record TrainerOps(TrainerService service) implements PersonOps {
        public UUID create(Set<UUID> images, UUID pointer) {
            TrainerDTO dto = new TrainerDTO();
            dto.setName("Foto");
            dto.setSurname("Trainer" + UUID.randomUUID());
            dto.setImagesUuid(new HashSet<>(images));
            dto.setPrimaryImageUuid(pointer);
            return service.save(dto).getId();
        }

        public void update(UUID id, Set<UUID> images, UUID pointer) {
            TrainerDTO dto = service.findById(id);
            dto.setImagesUuid(new HashSet<>(images));
            dto.setPrimaryImageUuid(pointer);
            service.update(dto);
        }

        public Set<UUID> imagesOf(UUID id) {
            return service.findById(id).getImagesUuid();
        }

        public UUID pointerOf(UUID id) {
            return service.findById(id).getPrimaryImageUuid();
        }

        public String toString() {
            return "trainer";
        }
    }

    private record LecturerOps(LecturerService service) implements PersonOps {
        public UUID create(Set<UUID> images, UUID pointer) {
            LecturerDTO dto = new LecturerDTO();
            dto.setName("Foto");
            dto.setSurname("Lecturer" + UUID.randomUUID());
            dto.setImagesUuid(new HashSet<>(images));
            dto.setPrimaryImageUuid(pointer);
            return service.save(dto).getId();
        }

        public void update(UUID id, Set<UUID> images, UUID pointer) {
            LecturerDTO dto = service.findById(id);
            dto.setImagesUuid(new HashSet<>(images));
            dto.setPrimaryImageUuid(pointer);
            service.updateById(dto);
        }

        public Set<UUID> imagesOf(UUID id) {
            return service.findById(id).getImagesUuid();
        }

        public UUID pointerOf(UUID id) {
            return service.findById(id).getPrimaryImageUuid();
        }

        public String toString() {
            return "lecturer";
        }
    }

    private record TechnicianOps(TechnicianService service) implements PersonOps {
        public UUID create(Set<UUID> images, UUID pointer) {
            TechnicianDTO dto = new TechnicianDTO();
            dto.setName("Foto");
            dto.setSurname("Technician" + UUID.randomUUID());
            dto.setImagesUuid(new HashSet<>(images));
            dto.setPrimaryImageUuid(pointer);
            return service.save(dto).getId();
        }

        public void update(UUID id, Set<UUID> images, UUID pointer) {
            TechnicianDTO dto = service.findById(id);
            dto.setImagesUuid(new HashSet<>(images));
            dto.setPrimaryImageUuid(pointer);
            service.updateById(dto);
        }

        public Set<UUID> imagesOf(UUID id) {
            return service.findById(id).getImagesUuid();
        }

        public UUID pointerOf(UUID id) {
            return service.findById(id).getPrimaryImageUuid();
        }

        public String toString() {
            return "technician";
        }
    }
}
