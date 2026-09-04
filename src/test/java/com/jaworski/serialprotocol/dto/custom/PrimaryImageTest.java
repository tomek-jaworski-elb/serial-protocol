package com.jaworski.serialprotocol.dto.custom;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure logic behind the avatar: which photo represents a person, and what happens when the
 * stored choice no longer applies. No Spring — this is arithmetic on a set.
 */
class PrimaryImageTest {

    /**
     * Chosen so the two plausible orderings disagree: {@link UUID#compareTo} treats both halves
     * as signed longs, so it ranks HIGH_BITS first, while the lexicographic order ranks
     * LOW_BITS first. A test using uuids where the two agree would not tell the orderings apart.
     */
    private static final UUID HIGH_BITS = UUID.fromString("ffffffff-0000-0000-0000-000000000000");
    private static final UUID LOW_BITS = UUID.fromString("00000001-0000-0000-0000-000000000000");
    private static final UUID MID = UUID.fromString("88888888-0000-0000-0000-000000000000");

    @Test
    void minByString_usesLexicographicOrder_notSignedUuidOrder() {
        Set<UUID> set = ordered(HIGH_BITS, LOW_BITS, MID);

        assertThat(PrimaryImage.minByString(set))
                .as("lexicographic, so the 00000001 uuid wins")
                .isEqualTo(LOW_BITS);
        assertThat(HIGH_BITS.compareTo(LOW_BITS))
                .as("guards the premise: UUID.compareTo really does disagree here")
                .isNegative();
    }

    @Test
    void minByString_isStableWhateverTheInsertionOrder() {
        assertThat(PrimaryImage.minByString(ordered(MID, HIGH_BITS, LOW_BITS))).isEqualTo(LOW_BITS);
        assertThat(PrimaryImage.minByString(ordered(LOW_BITS, MID, HIGH_BITS))).isEqualTo(LOW_BITS);
        assertThat(PrimaryImage.minByString(new java.util.HashSet<>(Set.of(HIGH_BITS, MID, LOW_BITS))))
                .isEqualTo(LOW_BITS);
    }

    @Test
    void minByString_ofNothing_isNull() {
        assertThat(PrimaryImage.minByString(Set.of())).isNull();
        assertThat(PrimaryImage.minByString(null)).isNull();
    }

    // --- resolution order ---

    @Test
    void anExplicitChoiceWins_evenWhenItIsNotTheFallback() {
        Set<UUID> images = ordered(LOW_BITS, MID, HIGH_BITS);

        UUID resolved = PrimaryImage.resolve(MID, null, images, images);

        assertThat(resolved).isEqualTo(MID);
        assertThat(resolved)
                .as("if this equalled the fallback the test could not tell the two apart")
                .isNotEqualTo(PrimaryImage.minByString(images));
    }

    @Test
    void withoutAnExplicitChoice_theStoredPointerSurvives() {
        Set<UUID> images = ordered(LOW_BITS, MID, HIGH_BITS);

        UUID resolved = PrimaryImage.resolve(null, MID, images, images);

        assertThat(resolved).as("a request that says nothing must not move the pointer").isEqualTo(MID);
        assertThat(resolved).isNotEqualTo(PrimaryImage.minByString(images));
    }

    @Test
    void aPointerOutsideTheSet_fallsBackInsteadOfBeingHonoured() {
        Set<UUID> images = ordered(LOW_BITS, MID);
        UUID somebodyElses = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000");

        assertThat(PrimaryImage.resolve(somebodyElses, null, images, images)).isEqualTo(LOW_BITS);
    }

    @Test
    void removingThePointedPhoto_movesThePointerToARemainingOne() {
        Set<UUID> before = ordered(LOW_BITS, MID, HIGH_BITS);
        Set<UUID> after = ordered(MID, HIGH_BITS);

        assertThat(PrimaryImage.resolve(null, LOW_BITS, before, after))
                .as("deterministic, not whatever the set iterator yields")
                .isEqualTo(PrimaryImage.minByString(after));
    }

    @Test
    void removingEveryPhoto_clearsThePointer() {
        assertThat(PrimaryImage.resolve(null, MID, ordered(MID), Set.of())).isNull();
    }

    /**
     * The reason the fallback prefers photos that were already there. Without it, uploading a
     * photo would change the avatar of records that never had a pointer while leaving records
     * that did have one alone — the same action with two different outcomes, decided by
     * invisible history.
     */
    @Test
    void addingAPhoto_doesNotChangeTheAvatarOfAPointerlessRecord() {
        Set<UUID> before = ordered(MID, HIGH_BITS);
        Set<UUID> after = ordered(MID, HIGH_BITS, LOW_BITS);   // LOW_BITS is the new upload

        UUID resolved = PrimaryImage.resolve(null, null, before, after);

        assertThat(resolved)
                .as("the freshly uploaded photo must not take over")
                .isEqualTo(MID)
                .isNotEqualTo(LOW_BITS);
        assertThat(PrimaryImage.minByString(after))
                .as("guards the premise: a naive fallback over the merged set would pick the upload")
                .isEqualTo(LOW_BITS);
    }

    @Test
    void whenNothingCarriesOver_theFallbackUsesTheWholeSet() {
        Set<UUID> before = ordered(MID);
        Set<UUID> after = ordered(LOW_BITS, HIGH_BITS);        // every old photo replaced

        assertThat(PrimaryImage.resolve(null, MID, before, after)).isEqualTo(LOW_BITS);
    }

    private static Set<UUID> ordered(UUID... uuids) {
        return new LinkedHashSet<>(java.util.Arrays.asList(uuids));
    }
}
