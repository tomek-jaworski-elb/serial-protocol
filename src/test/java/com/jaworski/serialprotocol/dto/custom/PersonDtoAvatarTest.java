package com.jaworski.serialprotocol.dto.custom;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The two derived getters the templates read: which photo to show, and what to show instead. */
class PersonDtoAvatarTest {

    private static final UUID LOW = UUID.fromString("00000001-0000-0000-0000-000000000000");
    private static final UUID MID = UUID.fromString("88888888-0000-0000-0000-000000000000");

    // --- getAvatarImageUuid() ---

    @Test
    void aValidPointerIsHonoured() {
        TrainerDTO dto = trainerWith(Set.of(LOW, MID));
        dto.setPrimaryImageUuid(MID);

        assertThat(dto.getAvatarImageUuid()).isEqualTo(MID);
        assertThat(dto.getAvatarImageUuid())
                .as("MID must not coincide with the fallback, or this proves nothing")
                .isNotEqualTo(PrimaryImage.minByString(dto.getImagesUuid()));
    }

    /**
     * The state of every record right after the column was added, and of every record after a
     * restore. It has to show a photo, not initials.
     */
    @Test
    void withoutAPointer_theFallbackPicksAPhoto() {
        TrainerDTO dto = trainerWith(Set.of(LOW, MID));

        assertThat(dto.getAvatarImageUuid()).isEqualTo(LOW);
    }

    @Test
    void aPointerThatIsNoLongerInTheSet_fallsBack() {
        TrainerDTO dto = trainerWith(Set.of(LOW, MID));
        dto.setPrimaryImageUuid(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000"));

        assertThat(dto.getAvatarImageUuid()).isEqualTo(LOW);
    }

    @Test
    void noImagesAtAll_meansNoAvatar() {
        assertThat(trainerWith(Set.of()).getAvatarImageUuid()).isNull();

        TrainerDTO nullSet = trainerWith(Set.of());
        nullSet.setImagesUuid(null);
        assertThat(nullSet.getAvatarImageUuid())
                .as("a null collection must not throw during rendering")
                .isNull();
    }

    @Test
    void participantNeedsNoPointer_itHoldsExactlyOneImage() {
        ParticipantDTO dto = new ParticipantDTO();
        dto.setImage(MID);

        assertThat(dto.getAvatarImageUuid()).isEqualTo(MID);

        dto.setImage(null);
        assertThat(dto.getAvatarImageUuid()).isNull();
    }

    // --- getInitials() ---

    @Test
    void initialsAreTheTwoFirstLettersUppercased() {
        assertThat(named("zofia", "dabrowska").getInitials()).isEqualTo("ZD");
        assertThat(named("A", "B").getInitials()).isEqualTo("AB");
    }

    /**
     * Both columns are NOT NULL, but a record can still arrive through a restore or a hand-made
     * request. An exception raised here would surface as HTTP 500 on the whole page rather than
     * one missing avatar, so absence degrades instead of throwing.
     */
    @Test
    void missingNameOrSurnameDegradesInsteadOfThrowing() {
        assertThat(named(null, "Nowak").getInitials()).isEqualTo("N");
        assertThat(named("Anna", null).getInitials()).isEqualTo("A");
        assertThat(named("", "Nowak").getInitials()).isEqualTo("N");
        assertThat(named("  ", "Nowak").getInitials()).isEqualTo("N");
    }

    @Test
    void withNoNameAtAll_aPlaceholderKeepsTheDiscFromLookingBroken() {
        assertThat(named(null, null).getInitials())
                .as("an empty disc is indistinguishable from an image that failed to load")
                .isEqualTo("·");
        assertThat(named("", "").getInitials()).isEqualTo("·");
    }

    @Test
    void participantInitialsFollowTheSameContract() {
        ParticipantDTO dto = new ParticipantDTO();
        dto.setName("Marek");
        dto.setSurname("Lewandowski");

        assertThat(dto.getInitials()).isEqualTo("ML");
    }

    private static TrainerDTO trainerWith(Set<UUID> images) {
        TrainerDTO dto = new TrainerDTO();
        dto.setName("Test");
        dto.setSurname("Person");
        dto.setImagesUuid(new HashSet<>(images));
        return dto;
    }

    private static TrainerDTO named(String name, String surname) {
        TrainerDTO dto = new TrainerDTO();
        dto.setName(name);
        dto.setSurname(surname);
        return dto;
    }
}
