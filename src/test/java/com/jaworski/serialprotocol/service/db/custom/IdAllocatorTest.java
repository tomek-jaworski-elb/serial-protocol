package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.ParticipantDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Business numbers come from a locked sequence row, consulted together with what is already
 * in use, so neither two concurrent forms nor a hand-typed number can produce a duplicate.
 */
@DataJpaTest
@Import({IdAllocator.class, ParticipantService.class, ImageService.class})
class IdAllocatorTest {

    @Autowired
    private IdAllocator idAllocator;
    @Autowired
    private ParticipantService participantService;

    @Test
    void handsOutConsecutiveValues_andPeekDoesNotConsume() {
        assertEquals(1L, idAllocator.peek("probe", () -> 0));
        assertEquals(1L, idAllocator.peek("probe", () -> 0));

        assertEquals(1L, idAllocator.next("probe", () -> 0));
        assertEquals(2L, idAllocator.next("probe", () -> 0));
        assertEquals(3L, idAllocator.peek("probe", () -> 0));
    }

    @Test
    void sequencesAreIndependent() {
        idAllocator.next("one", () -> 0);
        idAllocator.next("one", () -> 0);

        assertEquals(1L, idAllocator.next("two", () -> 0));
    }

    @Test
    void stepsOverANumberThatWasTypedByHand() {
        ParticipantDTO byHand = participant("Hand");
        byHand.setId(100L);
        participantService.save(byHand);

        ParticipantDTO allocated = participantService.save(participant("Next"));

        assertEquals(101L, allocated.getId());
        assertEquals(102L, participantService.nextId());
    }

    @Test
    void startsFromWhatIsAlreadyInUse_whenTheSequenceRowDoesNotExistYet() {
        // 41 is reported as in use, so the first value is 42, not 1 ...
        assertEquals(42L, idAllocator.next("fresh", () -> 41));
        // ... and the sequence continues from there, consulting what is in use every time.
        assertEquals(43L, idAllocator.next("fresh", () -> 42));
        assertEquals(44L, idAllocator.next("fresh", () -> 0));
    }

    private static ParticipantDTO participant(String name) {
        ParticipantDTO dto = new ParticipantDTO();
        dto.setName(name);
        dto.setSurname("Allocator");
        return dto;
    }
}
