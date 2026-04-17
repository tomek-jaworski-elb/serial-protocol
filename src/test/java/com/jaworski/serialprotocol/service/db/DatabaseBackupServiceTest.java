/*
 * Copyright 2026 Adtran Networks SE. All rights reserved.
 *
 * Owner: tomaszja
 */
package com.jaworski.serialprotocol.service.db;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jaworski.serialprotocol.dto.backup.DatabaseBackupDTO;
import com.jaworski.serialprotocol.entity.Instructor;
import com.jaworski.serialprotocol.entity.Student;
import com.jaworski.serialprotocol.entity.custom.CourseType;
import com.jaworski.serialprotocol.entity.custom.Courses;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.entity.custom.Lecturer;
import com.jaworski.serialprotocol.entity.custom.Participant;
import com.jaworski.serialprotocol.entity.custom.Technician;
import com.jaworski.serialprotocol.entity.custom.Trainer;
import com.jaworski.serialprotocol.repository.InstructorRepository;
import com.jaworski.serialprotocol.repository.StudentRepository;
import com.jaworski.serialprotocol.repository.custom.CoursesRepository;
import com.jaworski.serialprotocol.repository.custom.CourseTypeRepository;
import com.jaworski.serialprotocol.repository.custom.ImageRepository;
import com.jaworski.serialprotocol.repository.custom.LecturerRepository;
import com.jaworski.serialprotocol.repository.custom.ParticipantRepository;
import com.jaworski.serialprotocol.repository.custom.TechnicianRepository;
import com.jaworski.serialprotocol.repository.custom.TrainerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit / slice tests for {@link DatabaseBackupService} against an in-memory H2 database.
 *
 * <p><strong>Scope:</strong>
 * <ul>
 *   <li>{@code createBackup()} – serialisation, schema version, timestamp, entity inclusion.</li>
 *   <li>{@code restoreFromBackup()} – validation-only paths (wrong schema, invalid GZIP, bad JSON).</li>
 * </ul>
 *
 * <p><strong>Why no round-trip tests here?</strong>
 * {@code @DataJpaTest} wraps each test in a transaction and keeps the entities managed inside that
 * transaction's persistence context (PC).  {@code restoreFromBackup()} participates in the same
 * transaction (REQUIRED propagation) and calls {@code entityManager.clear()} to wipe the PC.
 * When it then tries to re-persist entities with the same UUIDs, Hibernate detects them as
 * "detached" (seen in this session) and throws {@code EntityExistsException}.
 * This is a Hibernate-level interaction specific to the in-process {@code @DataJpaTest} context;
 * it does <em>not</em> occur in production (MariaDB) where restore runs in its own transaction.
 * Full backup → restore → verify round-trips are covered by
 * {@link com.jaworski.serialprotocol.controller.web.DbUtilsControllerTest}, which uses
 * {@code @SpringBootTest} and therefore a proper isolated transaction per request.
 */
@DataJpaTest
@Import(DatabaseBackupService.class)
class DatabaseBackupServiceTest {

    // ---- Test-only ObjectMapper (JavaTimeModule for LocalDate/LocalDateTime) ----

    @TestConfiguration
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        }
    }

    // ---- Injections ----------------------------------------------------------

    @Autowired
    private DatabaseBackupService backupService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ImageRepository imageRepository;
    @Autowired
    private CourseTypeRepository courseTypeRepository;
    @Autowired
    private TrainerRepository trainerRepository;
    @Autowired
    private LecturerRepository lecturerRepository;
    @Autowired
    private TechnicianRepository technicianRepository;
    @Autowired
    private ParticipantRepository participantRepository;
    @Autowired
    private CoursesRepository coursesRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private InstructorRepository instructorRepository;

    // =========================================================================
    // createBackup – serialisation & metadata
    // =========================================================================

    @Test
    void createBackup_shouldReturnNonEmptyBytes_whenDatabaseIsEmpty() throws IOException {
        byte[] result = backupService.createBackup();
        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    @Test
    void createBackup_shouldProduceValidGzipStream() throws IOException {
        byte[] result = backupService.createBackup();
        assertDoesNotThrow(() -> {
            try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(result))) {
                assertTrue(gzis.readAllBytes().length > 0);
            }
        });
    }

    @Test
    void createBackup_shouldContainCurrentSchemaVersion() throws IOException {
        DatabaseBackupDTO dto = decompress(backupService.createBackup());
        assertEquals(DatabaseBackupService.SCHEMA_VERSION, dto.getSchemaVersion());
    }

    @Test
    void createBackup_shouldIncludeTimestampApproximatelyNow() throws IOException {
        LocalDateTime before = LocalDateTime.now().minusSeconds(5);
        byte[] result = backupService.createBackup();
        LocalDateTime after = LocalDateTime.now().plusSeconds(5);

        DatabaseBackupDTO dto = decompress(result);
        assertNotNull(dto.getTimestamp());
        assertTrue(dto.getTimestamp().isAfter(before));
        assertTrue(dto.getTimestamp().isBefore(after));
    }

    @Test
    void createBackup_shouldReturnEmptyLists_whenDatabaseIsEmpty() throws IOException {
        DatabaseBackupDTO dto = decompress(backupService.createBackup());

        assertNotNull(dto.getImages());
        assertTrue(dto.getImages().isEmpty());
        assertNotNull(dto.getCourseTypes());
        assertTrue(dto.getCourseTypes().isEmpty());
        assertNotNull(dto.getTrainers());
        assertTrue(dto.getTrainers().isEmpty());
        assertNotNull(dto.getParticipants());
        assertTrue(dto.getParticipants().isEmpty());
        assertNotNull(dto.getCourses());
        assertTrue(dto.getCourses().isEmpty());
        assertNotNull(dto.getStudents());
        assertTrue(dto.getStudents().isEmpty());
        assertNotNull(dto.getInstructors());
        assertTrue(dto.getInstructors().isEmpty());
    }

    @Test
    void createBackup_shouldIncludeAllSavedEntities() throws IOException {
        Image img = saveImage();
        CourseType ct = saveCourseType("BACKUP-CT");
        Trainer trainer = saveTrainer("Jan", "Kowalski");
        saveLecturer("Anna", "Nowak");
        saveTechnician("Piotr", "Zielinski");
        Participant participant = saveParticipant(1001L, "Maria", "Wisniewska");
        saveCourse(1L, participant, ct, trainer);

        DatabaseBackupDTO dto = decompress(backupService.createBackup());

        assertEquals(1, dto.getImages().size());
        assertEquals(1, dto.getCourseTypes().size());
        assertEquals(1, dto.getTrainers().size());
        assertEquals(1, dto.getLecturers().size());
        assertEquals(1, dto.getTechnicians().size());
        assertEquals(1, dto.getParticipants().size());
        assertEquals(1, dto.getCourses().size());

        assertEquals(ct.getCode(), dto.getCourseTypes().getFirst().getCode());
        assertEquals(trainer.getEmail(), dto.getTrainers().getFirst().getEmail());
        assertEquals(participant.getSurname(), dto.getParticipants().getFirst().getSurname());
    }

    @Test
    void createBackup_shouldIncludeStudentAndInstructor() throws IOException {
        studentRepository.save(makeStudent(77, "StudentName", "StudentSurname"));
        instructorRepository.save(makeInstructor(88, "InstructorName", "InstructorSurname"));

        DatabaseBackupDTO dto = decompress(backupService.createBackup());

        assertEquals(1, dto.getStudents().size());
        assertEquals(1, dto.getInstructors().size());
        assertEquals(77, dto.getStudents().getFirst().getId());
        assertEquals(88, dto.getInstructors().getFirst().getNo());
    }

    @Test
    void createBackup_shouldPreserveImageBinaryDataThroughSerialization() throws IOException {
        byte[] rawData = new byte[]{10, 20, 30, 40, 50};
        Image img = new Image();
        img.setData(rawData);
        img.setContentType("image/png");
        imageRepository.save(img);

        DatabaseBackupDTO dto = decompress(backupService.createBackup());

        assertNotNull(dto.getImages().getFirst().getData());
        assertArrayEquals(rawData, dto.getImages().getFirst().getData());
    }

    @Test
    void createBackup_shouldSerializeCourseWithRelations() throws IOException {
        CourseType ct = saveCourseType("COURSE-CT");
        Trainer trainer = saveTrainer("TrainerName", "TrainerSurname");
        Participant participant = saveParticipant(2001L, "P", "Q");
        Courses course = saveCourse(10L, participant, ct, trainer);

        DatabaseBackupDTO dto = decompress(backupService.createBackup());

        assertEquals(1, dto.getCourses().size());
        assertFalse(dto.getCourses().getFirst().getTrainerIds().isEmpty());
        assertEquals(course.getUuid(), dto.getCourses().getFirst().getUuid());
    }

    // =========================================================================
    // restoreFromBackup – validation (no data in DB before these tests)
    // =========================================================================

    @Test
    void restoreFromBackup_shouldThrowIllegalArgument_whenSchemaVersionMismatch() throws IOException {
        byte[] data = compress(emptyBackupWithVersion("99.0"));
        assertThrows(IllegalArgumentException.class,
                () -> backupService.restoreFromBackup(data));
    }

    @Test
    void restoreFromBackup_shouldIncludeVersionsInErrorMessage_whenSchemaMismatch() throws IOException {
        byte[] data = compress(emptyBackupWithVersion("0.1"));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> backupService.restoreFromBackup(data));
        assertTrue(ex.getMessage().contains("0.1"));
        assertTrue(ex.getMessage().contains(DatabaseBackupService.SCHEMA_VERSION));
    }

    @Test
    void restoreFromBackup_shouldThrowIOException_whenDataIsNotGzip() {
        byte[] notGzip = "plain text, not gzip".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThrows(IOException.class,
                () -> backupService.restoreFromBackup(notGzip));
    }

    @Test
    void restoreFromBackup_shouldThrowIOException_whenGzipContainsInvalidJson() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(bos)) {
            gzos.write("{ not valid json ]]]".getBytes());
        }
        assertThrows(IOException.class,
                () -> backupService.restoreFromBackup(bos.toByteArray()));
    }

    @Test
    void restoreFromBackup_emptyDatabase_shouldCompleteWithoutErrors() throws IOException {
        // Empty DB → backup → restore: no pre-existing entities → no PC conflict
        byte[] backup = backupService.createBackup();
        assertDoesNotThrow(() -> backupService.restoreFromBackup(backup));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Image saveImage() {
        Image img = new Image();
        img.setData(new byte[]{1, 2, 3});
        img.setContentType("image/png");
        return imageRepository.save(img);
    }

    private CourseType saveCourseType(String code) {
        return courseTypeRepository.save(
                new CourseType(code, "Description " + code, "Long description " + code));
    }

    private Trainer saveTrainer(String name, String surname) {
        Trainer t = new Trainer();
        t.setName(name);
        t.setSurname(surname);
        t.setEmail(name.toLowerCase() + "." + surname.toLowerCase() + "@test.com");
        t.setImages(new HashSet<>());
        return trainerRepository.save(t);
    }

    private Lecturer saveLecturer(String name, String surname) {
        Lecturer l = new Lecturer();
        l.setName(name);
        l.setSurname(surname);
        l.setEmail(name.toLowerCase() + "." + surname.toLowerCase() + "@test.com");
        l.setNickname(name.toLowerCase());
        l.setImages(new HashSet<>());
        return lecturerRepository.save(l);
    }

    private Technician saveTechnician(String name, String surname) {
        Technician t = new Technician();
        t.setName(name);
        t.setSurname(surname);
        t.setEmail(name.toLowerCase() + "." + surname.toLowerCase() + "@test.com");
        t.setNickname(name.toLowerCase());
        t.setImages(new HashSet<>());
        return technicianRepository.save(t);
    }

    private Participant saveParticipant(Long id, String name, String surname) {
        Participant p = new Participant();
        p.setId(id);
        p.setName(name);
        p.setSurname(surname);
        p.setBirthDate(LocalDate.of(1990, 6, 15));
        return participantRepository.save(p);
    }

    private Courses saveCourse(Long id, Participant participant, CourseType ct, Trainer trainer) {
        Courses c = new Courses();
        c.setId(id);
        c.setParticipant(participant);
        c.setCourseType(ct);
        c.setStartDate(LocalDate.of(2026, 3, 1));
        c.setEndDate(LocalDate.of(2026, 3, 10));
        c.setTrainers(trainer != null ? new HashSet<>(java.util.Set.of(trainer)) : new HashSet<>());
        c.setLecturers(new HashSet<>());
        c.setTechnicians(new HashSet<>());
        return coursesRepository.save(c);
    }

    private Student makeStudent(int id, String name, String lastName) {
        return new Student(id, name, lastName, "COURSE-X",
                new java.util.Date(), new java.util.Date(), "Mr", "TypeA", null);
    }

    private Instructor makeInstructor(int no, String name, String surname) {
        return new Instructor(no, name, surname, "email@test.com",
                null, null, null, null, null,
                null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null);
    }

    private DatabaseBackupDTO emptyBackupWithVersion(String version) {
        return DatabaseBackupDTO.builder()
                .schemaVersion(version)
                .timestamp(LocalDateTime.now())
                .images(List.of())
                .courseTypes(List.of())
                .courseCounters(List.of())
                .trainers(List.of())
                .lecturers(List.of())
                .technicians(List.of())
                .participants(List.of())
                .courses(List.of())
                .students(List.of())
                .instructors(List.of())
                .build();
    }

    private DatabaseBackupDTO decompress(byte[] data) throws IOException {
        try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return objectMapper.readValue(gzis, DatabaseBackupDTO.class);
        }
    }

    private byte[] compress(DatabaseBackupDTO dto) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(bos)) {
            objectMapper.writeValue(gzos, dto);
        }
        return bos.toByteArray();
    }
}

