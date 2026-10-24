package com.jaworski.serialprotocol.service.db.custom;

import com.jaworski.serialprotocol.dto.custom.CourseTypeDTO;
import com.jaworski.serialprotocol.dto.custom.CoursesDTO;
import com.jaworski.serialprotocol.dto.custom.ParticipantDTO;
import com.jaworski.serialprotocol.entity.custom.Participant;
import com.jaworski.serialprotocol.repository.custom.ParticipantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every custom-domain row carries a version and two timestamps, and a delete is a tombstone.
 * These are the properties a later synchronisation relies on, so they are pinned here rather
 * than left to the individual service tests.
 */
@DataJpaTest
@Import({IdAllocator.class, ParticipantService.class, CoursesService.class, CourseTypeService.class,
    TrainerService.class, LecturerService.class, ImageService.class})
class AuditAndSoftDeleteTest {

    @Autowired private ParticipantService participantService;
    @Autowired private CoursesService coursesService;
    @Autowired private CourseTypeService courseTypeService;
    @Autowired private ParticipantRepository participantRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void aNewRecordStartsAtVersionZeroWithBothTimestamps() {
        ParticipantDTO saved = participantService.save(participant("Anna"));
        entityManager.flush();

        Participant stored = participantRepository.findById(saved.getParticipantUuid()).orElseThrow();
        assertEquals(0L, saved.getVersion());
        assertNotNull(stored.getCreatedAt());
        assertNotNull(stored.getUpdatedAt());
        assertFalse(stored.getUpdatedAt().isBefore(stored.getCreatedAt()));
    }

    @Test
    void anUpdateBumpsTheVersionAndMovesUpdatedAt() throws InterruptedException {
        ParticipantDTO saved = participantService.save(participant("Anna"));
        entityManager.flush();
        Instant createdAt = participantRepository.findById(saved.getParticipantUuid()).orElseThrow().getCreatedAt();
        Thread.sleep(5);

        saved.setNotes("changed");
        ParticipantDTO updated = participantService.updateByUuid(saved);
        entityManager.flush();

        Participant stored = participantRepository.findById(saved.getParticipantUuid()).orElseThrow();
        assertEquals(1L, updated.getVersion());
        assertEquals(createdAt, stored.getCreatedAt());
        assertTrue(stored.getUpdatedAt().isAfter(createdAt));
    }

    @Test
    void aFormRenderedFromAnOlderCopyIsRejected() {
        ParticipantDTO saved = participantService.save(participant("Anna"));
        entityManager.flush();

        ParticipantDTO firstEditor = participantService.findByUuid(saved.getParticipantUuid());
        firstEditor.setNotes("first");
        participantService.updateByUuid(firstEditor);                 // version 0 -> 1

        ParticipantDTO secondEditor = saved;                           // still carries version 0
        secondEditor.setNotes("second");
        OptimisticLockingFailureException e = assertThrows(OptimisticLockingFailureException.class,
            () -> participantService.updateByUuid(secondEditor));
        assertTrue(e.getMessage().contains("changed by someone else"));
        assertEquals("first", participantService.findByUuid(saved.getParticipantUuid()).getNotes());
    }

    @Test
    void aRequestWithoutAVersionIsAppliedUnchecked() {
        ParticipantDTO saved = participantService.save(participant("Anna"));
        saved.setVersion(null);

        saved.setNotes("one");
        participantService.updateByUuid(saved);
        saved.setNotes("two");
        participantService.updateByUuid(saved);

        assertEquals("two", participantService.findByUuid(saved.getParticipantUuid()).getNotes());
    }

    @Test
    void theSameCheckGuardsCourses() {
        ParticipantDTO participant = participantService.save(participant("Anna"));
        CourseTypeDTO type = new CourseTypeDTO();
        type.setCode("AUD-" + UUID.randomUUID().toString().substring(0, 6));
        type.setDescription("audit");
        type = courseTypeService.save(type);
        CoursesDTO course = new CoursesDTO();
        course.setParticipantUuid(participant.getParticipantUuid());
        course.setCourseTypeId(type.getId());
        course.setStartDate(LocalDate.of(2026, 3, 1));
        course.setEndDate(LocalDate.of(2026, 3, 2));
        CoursesDTO saved = coursesService.save(course);
        entityManager.flush();

        CoursesDTO fresh = coursesService.findByUuid(saved.getUuid());
        fresh.setEndDate(LocalDate.of(2026, 3, 3));
        coursesService.update(fresh);
        entityManager.flush();

        saved.setEndDate(LocalDate.of(2026, 3, 4));
        assertThrows(OptimisticLockingFailureException.class, () -> coursesService.update(saved));
    }

    @Test
    void aDeleteLeavesATombstoneAndTheNumberStaysTaken() {
        ParticipantDTO saved = participantService.save(participant("Anna"));
        UUID uuid = saved.getParticipantUuid();
        Long number = saved.getId();
        entityManager.flush();

        participantService.deleteByUuid(uuid);
        entityManager.flush();
        entityManager.clear();

        assertNull(participantService.findByUuid(uuid), "hidden from every ordinary query");
        Number rows = (Number) entityManager
            .createNativeQuery("select count(*) from participants where participants_uuid = ?1")
            .setParameter(1, uuid).getSingleResult();
        assertEquals(1L, rows.longValue(), "but the row is still there");
        assertNotNull(entityManager
            .createNativeQuery("select deleted_at from participants where participants_uuid = ?1")
            .setParameter(1, uuid).getSingleResult(), "with the time of the delete");

        assertTrue(participantService.isIdTaken(number), "its number is still in the unique index");
        ParticipantDTO reuse = participant("Beata");
        reuse.setId(number);
        assertThrows(IllegalArgumentException.class, () -> participantService.save(reuse));
        assertTrue(participantService.nextId() > number, "and the allocator never goes back below it");
    }

    private static ParticipantDTO participant(String name) {
        ParticipantDTO dto = new ParticipantDTO();
        dto.setName(name);
        dto.setSurname("Audit");
        return dto;
    }
}
