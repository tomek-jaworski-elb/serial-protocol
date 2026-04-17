/*
 * Copyright 2026 Adtran Networks SE. All rights reserved.
 *
 * Owner: tomaszja
 */
package com.jaworski.serialprotocol.service.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaworski.serialprotocol.dto.backup.DatabaseBackupDTO;
import com.jaworski.serialprotocol.dto.backup.ImageBackupDTO;
import com.jaworski.serialprotocol.dto.backup.InstructorBackupDTO;
import com.jaworski.serialprotocol.dto.backup.StudentBackupDTO;
import com.jaworski.serialprotocol.dto.custom.CourseCounterDTO;
import com.jaworski.serialprotocol.dto.custom.CoursesDTO;
import com.jaworski.serialprotocol.dto.custom.CourseTypeDTO;
import com.jaworski.serialprotocol.dto.custom.LecturerDTO;
import com.jaworski.serialprotocol.dto.custom.ParticipantDTO;
import com.jaworski.serialprotocol.dto.custom.TechnicianDTO;
import com.jaworski.serialprotocol.dto.custom.TrainerDTO;
import com.jaworski.serialprotocol.entity.Instructor;
import com.jaworski.serialprotocol.entity.Student;
import com.jaworski.serialprotocol.entity.custom.CourseCounter;
import com.jaworski.serialprotocol.entity.custom.CourseType;
import com.jaworski.serialprotocol.entity.custom.Courses;
import com.jaworski.serialprotocol.entity.custom.Image;
import com.jaworski.serialprotocol.entity.custom.Lecturer;
import com.jaworski.serialprotocol.entity.custom.Participant;
import com.jaworski.serialprotocol.entity.custom.Technician;
import com.jaworski.serialprotocol.entity.custom.Trainer;
import com.jaworski.serialprotocol.mappers.custom.CoursesMapper;
import com.jaworski.serialprotocol.mappers.custom.CourseTypeMapper;
import com.jaworski.serialprotocol.mappers.custom.LecturerMapper;
import com.jaworski.serialprotocol.mappers.custom.ParticipantMapper;
import com.jaworski.serialprotocol.mappers.custom.TechnicianMapper;
import com.jaworski.serialprotocol.mappers.custom.TrainerMapper;
import com.jaworski.serialprotocol.repository.InstructorRepository;
import com.jaworski.serialprotocol.repository.StudentRepository;
import com.jaworski.serialprotocol.repository.custom.CourseCounterRepository;
import com.jaworski.serialprotocol.repository.custom.CoursesRepository;
import com.jaworski.serialprotocol.repository.custom.CourseTypeRepository;
import com.jaworski.serialprotocol.repository.custom.ImageRepository;
import com.jaworski.serialprotocol.repository.custom.LecturerRepository;
import com.jaworski.serialprotocol.repository.custom.ParticipantRepository;
import com.jaworski.serialprotocol.repository.custom.TechnicianRepository;
import com.jaworski.serialprotocol.repository.custom.TrainerRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Service responsible for creating and restoring full database backups.
 *
 * <p>Backup format: GZIP-compressed JSON produced by Jackson.
 * <p>Restore strategy:
 * <ul>
 *   <li>UUID-pk entities – original UUIDs are preserved via JPA merge.</li>
 *   <li>CourseType (IDENTITY pk) – DB generates new IDs; an old→new mapping
 *       is applied when re-inserting Courses rows.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DatabaseBackupService {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseBackupService.class);
    public static final String SCHEMA_VERSION = "1.0";

    private final ImageRepository imageRepository;
    private final CourseTypeRepository courseTypeRepository;
    private final CourseCounterRepository courseCounterRepository;
    private final TrainerRepository trainerRepository;
    private final LecturerRepository lecturerRepository;
    private final TechnicianRepository technicianRepository;
    private final ParticipantRepository participantRepository;
    private final CoursesRepository coursesRepository;
    private final StudentRepository studentRepository;
    private final InstructorRepository instructorRepository;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    // -------------------------------------------------------------------------
    // Backup
    // -------------------------------------------------------------------------

    /**
     * Collects all entities from every table and serializes them to a
     * GZIP-compressed JSON byte array ready to be sent as a file download.
     */
    @Transactional(readOnly = true)
    public byte[] createBackup() throws IOException {

        DatabaseBackupDTO backup = DatabaseBackupDTO.builder()
                .schemaVersion(SCHEMA_VERSION)
                .timestamp(LocalDateTime.now())
                .images(collectImages())
                .courseTypes(collectCourseTypes())
                .courseCounters(collectCourseCounters())
                .trainers(collectTrainers())
                .lecturers(collectLecturers())
                .technicians(collectTechnicians())
                .participants(collectParticipants())
                .courses(collectCourses())
                .students(collectStudents())
                .instructors(collectInstructors())
                .build();

        LOG.info("Backup created: images={}, courseTypes={}, trainers={}, lecturers={}, " +
                        "technicians={}, participants={}, courses={}, students={}, instructors={}",
                backup.getImages().size(), backup.getCourseTypes().size(),
                backup.getTrainers().size(), backup.getLecturers().size(),
                backup.getTechnicians().size(), backup.getParticipants().size(),
                backup.getCourses().size(), backup.getStudents().size(),
                backup.getInstructors().size());

        return toGzip(backup);
    }

    private List<ImageBackupDTO> collectImages() {
        return imageRepository.findAll().stream()
                .map(img -> new ImageBackupDTO(img.getId(), img.getData(), img.getContentType()))
                .collect(Collectors.toList());
    }

    private List<CourseTypeDTO> collectCourseTypes() {
        return courseTypeRepository.findAll().stream()
                .map(ct -> new CourseTypeDTO(ct.getId(), ct.getCode(), ct.getDescription(), ct.getLongDescription()))
                .collect(Collectors.toList());
    }

    private List<CourseCounterDTO> collectCourseCounters() {
        return courseCounterRepository.findAll().stream()
                .map(cc -> new CourseCounterDTO(
                        cc.getUuid(),
                        cc.getCounter(),
                        cc.getImage() == null ? null : cc.getImage().getId()))
                .collect(Collectors.toList());
    }

    private List<TrainerDTO> collectTrainers() {
        return trainerRepository.findAll().stream()
                .map(TrainerMapper::mapToDTO)
                .collect(Collectors.toList());
    }

    private List<LecturerDTO> collectLecturers() {
        return lecturerRepository.findAll().stream()
                .map(LecturerMapper::mapToDTO)
                .collect(Collectors.toList());
    }

    private List<TechnicianDTO> collectTechnicians() {
        return technicianRepository.findAll().stream()
                .map(TechnicianMapper::mapToDTO)
                .collect(Collectors.toList());
    }

    private List<ParticipantDTO> collectParticipants() {
        return participantRepository.findAll().stream()
                .map(ParticipantMapper::mapToDTO)
                .collect(Collectors.toList());
    }

    private List<CoursesDTO> collectCourses() {
        return coursesRepository.findAll().stream()
                .map(CoursesMapper::mapToDTO)
                .collect(Collectors.toList());
    }

    private List<StudentBackupDTO> collectStudents() {
        return studentRepository.findAll().stream()
                .map(s -> new StudentBackupDTO(
                        s.getId(), s.getName(), s.getLastName(), s.getCourseNo(),
                        s.getDateBegine(), s.getDateEnd(), s.getMrMs(), s.getCertType(), s.getPhoto()))
                .collect(Collectors.toList());
    }

    private List<InstructorBackupDTO> collectInstructors() {
        return instructorRepository.findAll().stream()
                .map(i -> new InstructorBackupDTO(
                        i.getNo(), i.getName(), i.getSurname(), i.getEmail(),
                        i.getPhone(), i.getMobile(), i.getCity(), i.getAddress(), i.getPostcode(),
                        i.getPhoto1(), i.getPhoto2(), i.getPhoto3(), i.getPhoto4(),
                        i.getNotes(), i.getOtherNotes(), i.getCertNo(), i.getSpecialization(),
                        i.getDiploma(), i.getBirthDate(), i.getBirthPlace(), i.getMrMs(),
                        i.getNick(), i.getNoCertificate(), i.getExpirationDate()))
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Restore
    // -------------------------------------------------------------------------

    /**
     * Restores the full database from a GZIP-compressed JSON backup file.
     *
     * <p>Steps:
     * <ol>
     *   <li>Decompress and deserialize JSON → {@link DatabaseBackupDTO}</li>
     *   <li>Verify schema version</li>
     *   <li>Clear all tables in reverse FK order</li>
     *   <li>Re-insert entities in forward FK order, preserving original UUIDs</li>
     * </ol>
     */
    @Transactional
    public void restoreFromBackup(byte[] compressedData) throws IOException {

        // 1. Decompress and parse
        DatabaseBackupDTO backup = fromGzip(compressedData);

        // 2. Schema validation
        if (!SCHEMA_VERSION.equals(backup.getSchemaVersion())) {
            throw new IllegalArgumentException(
                    "Incompatible schema version: expected=" + SCHEMA_VERSION
                            + ", found=" + backup.getSchemaVersion());
        }

        LOG.info("Starting DB restore from backup timestamp={}", backup.getTimestamp());

        // 3. Clear all tables (reverse FK order)
        clearAllTables();

        // 4. Re-insert in forward FK order
        restoreImages(backup.getImages());
        Map<Long, Long> courseTypeIdMap = restoreCourseTypes(backup.getCourseTypes());
        restoreCourseCounters(backup.getCourseCounters());
        restoreTrainers(backup.getTrainers());
        restoreLecturers(backup.getLecturers());
        restoreTechnicians(backup.getTechnicians());
        restoreParticipants(backup.getParticipants());
        restoreCourses(backup.getCourses(), courseTypeIdMap);
        restoreStudents(backup.getStudents());
        restoreInstructors(backup.getInstructors());

        entityManager.flush();
        LOG.info("DB restore completed successfully. timestamp={}", backup.getTimestamp());
    }

    // -- clear phase ----------------------------------------------------------

    private void clearAllTables() {
        // Courses owns all ManyToMany join tables → deleteAll() removes join rows first
        coursesRepository.deleteAll();
        entityManager.flush();

        participantRepository.deleteAllInBatch();
        entityManager.flush();

        // Trainer/Lecturer/Technician own their image join tables
        trainerRepository.deleteAll();
        lecturerRepository.deleteAll();
        technicianRepository.deleteAll();
        entityManager.flush();

        courseCounterRepository.deleteAllInBatch();
        courseTypeRepository.deleteAllInBatch();
        imageRepository.deleteAllInBatch();
        studentRepository.deleteAllInBatch();
        instructorRepository.deleteAllInBatch();
        entityManager.flush();
        entityManager.clear();

        LOG.info("All tables cleared.");
    }

    // -- restore phase --------------------------------------------------------

    private void restoreImages(List<ImageBackupDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return;
        }
        for (ImageBackupDTO dto : dtos) {
            Image image = new Image();
            image.setId(dto.getUuid());          // preserve original UUID
            image.setData(dto.getData());
            image.setContentType(dto.getContentType());
            // Use persist() NOT save(): save() calls merge() for non-null UUID, and merge()
            // throws StaleObjectStateException when the same UUID was deleted earlier in the
            // same transaction. persist() treats the object as transient and always INSERTs.
            entityManager.persist(image);
        }
        entityManager.flush();
        entityManager.clear();
        LOG.info("Restored {} images.", dtos.size());
    }

    /**
     * Inserts CourseTypes without explicit IDs (IDENTITY strategy) and
     * returns a mapping old-id → new-id for use when restoring Courses.
     */
    private Map<Long, Long> restoreCourseTypes(List<CourseTypeDTO> dtos) {
        Map<Long, Long> idMap = new HashMap<>();
        if (dtos == null || dtos.isEmpty()) {
            return idMap;
        }
        for (CourseTypeDTO dto : dtos) {
            Long originalId = dto.getId();
            CourseType ct = CourseTypeMapper.mapToEntity(dto);
            ct.setId(null);           // let DB generate new IDENTITY id
            CourseType saved = courseTypeRepository.save(ct);
            idMap.put(originalId, saved.getId());
        }
        entityManager.flush();
        entityManager.clear();
        LOG.info("Restored {} course types.", dtos.size());
        return idMap;
    }

    private void restoreCourseCounters(List<CourseCounterDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return;
        }
        for (CourseCounterDTO dto : dtos) {
            CourseCounter cc = new CourseCounter();
            cc.setUuid(dto.uuid());         // preserve original UUID
            cc.setCounter(dto.counter());
            if (dto.imageUuid() != null) {
                cc.setImage(entityManager.getReference(Image.class, dto.imageUuid()));
            }
            entityManager.persist(cc);      // persist(), not save() – avoids merge() StaleObjectState
        }
        entityManager.flush();
        entityManager.clear();
        LOG.info("Restored {} course counters.", dtos.size());
    }

    private void restoreTrainers(List<TrainerDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return;
        }
        for (TrainerDTO dto : dtos) {
            Trainer trainer = new Trainer();
            trainer.setUuid(dto.getId());   // preserve original UUID
            trainer.setName(dto.getName());
            trainer.setSurname(dto.getSurname());
            trainer.setEmail(dto.getEmail());
            trainer.setImages(resolveImageRefs(dto.getImagesUuid()));
            entityManager.persist(trainer);
        }
        entityManager.flush();
        entityManager.clear();
        LOG.info("Restored {} trainers.", dtos.size());
    }

    private void restoreLecturers(List<LecturerDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return;
        }
        for (LecturerDTO dto : dtos) {
            Lecturer lecturer = new Lecturer();
            lecturer.setUuid(dto.getId());  // preserve original UUID
            lecturer.setName(dto.getName());
            lecturer.setSurname(dto.getSurname());
            lecturer.setEmail(dto.getEmail());
            lecturer.setNickname(dto.getNickname());
            lecturer.setImages(resolveImageRefs(dto.getImagesUuid()));
            entityManager.persist(lecturer);
        }
        entityManager.flush();
        entityManager.clear();
        LOG.info("Restored {} lecturers.", dtos.size());
    }

    private void restoreTechnicians(List<TechnicianDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return;
        }
        for (TechnicianDTO dto : dtos) {
            Technician technician = new Technician();
            technician.setUuid(dto.getId()); // preserve original UUID
            technician.setName(dto.getName());
            technician.setSurname(dto.getSurname());
            technician.setEmail(dto.getEmail());
            technician.setNickname(dto.getNickname());
            technician.setImages(resolveImageRefs(dto.getImagesUuid()));
            entityManager.persist(technician);
        }
        entityManager.flush();
        entityManager.clear();
        LOG.info("Restored {} technicians.", dtos.size());
    }

    private void restoreParticipants(List<ParticipantDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return;
        }
        for (ParticipantDTO dto : dtos) {
            Participant p = new Participant();
            p.setUuid(dto.getParticipantUuid());       // preserve original UUID
            p.setId(dto.getId());
            p.setName(dto.getName());
            p.setSurname(dto.getSurname());
            p.setBirthDate(dto.getBirthDate());
            if (dto.getImage() != null) {
                p.setImage(entityManager.getReference(Image.class, dto.getImage()));
            }
            entityManager.persist(p);
        }
        entityManager.flush();
        entityManager.clear();
        LOG.info("Restored {} participants.", dtos.size());
    }

    private void restoreCourses(List<CoursesDTO> dtos, Map<Long, Long> courseTypeIdMap) {
        if (dtos == null || dtos.isEmpty()) {
            return;
        }
        for (CoursesDTO dto : dtos) {
            Courses course = new Courses();
            course.setUuid(dto.getUuid());  // preserve original UUID
            course.setId(dto.getId());
            course.setStartDate(dto.getStartDate());
            course.setEndDate(dto.getEndDate());

            if (dto.getParticipantUuid() != null) {
                course.setParticipant(entityManager.getReference(Participant.class, dto.getParticipantUuid()));
            }

            if (dto.getCourseTypeId() != null) {
                Long mappedId = courseTypeIdMap.get(dto.getCourseTypeId());
                if (mappedId != null) {
                    course.setCourseType(entityManager.getReference(CourseType.class, mappedId));
                } else {
                    LOG.warn("CourseType id mapping not found for originalId={}, skipping.", dto.getCourseTypeId());
                }
            }

            if (dto.getCourseCounterUuid() != null) {
                course.setCourseCounter(entityManager.getReference(CourseCounter.class, dto.getCourseCounterUuid()));
            }

            Set<Trainer> trainers = dto.getTrainerIds() == null ? new HashSet<>()
                    : dto.getTrainerIds().stream()
                    .map(id -> entityManager.getReference(Trainer.class, id))
                    .collect(Collectors.toSet());
            course.setTrainers(trainers);

            Set<Lecturer> lecturers = dto.getLecturerIds() == null ? new HashSet<>()
                    : dto.getLecturerIds().stream()
                    .map(id -> entityManager.getReference(Lecturer.class, id))
                    .collect(Collectors.toSet());
            course.setLecturers(lecturers);

            Set<Technician> technicians = dto.getTechnicianIds() == null ? new HashSet<>()
                    : dto.getTechnicianIds().stream()
                    .map(id -> entityManager.getReference(Technician.class, id))
                    .collect(Collectors.toSet());
            course.setTechnicians(technicians);

            entityManager.persist(course);
        }
        entityManager.flush();
        entityManager.clear();
        LOG.info("Restored {} courses.", dtos.size());
    }

    private void restoreStudents(List<StudentBackupDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return;
        }
        for (StudentBackupDTO dto : dtos) {
            Student student = new Student(
                    dto.getId(), dto.getName(), dto.getLastName(), dto.getCourseNo(),
                    dto.getDateBegine(), dto.getDateEnd(), dto.getMrMs(), dto.getCertType(), dto.getPhoto());
            // Student.id is int (primitive) → isNew() returns false for id != 0 → save() calls merge()
            // which may cause StaleObjectState. Use persist() to force INSERT.
            entityManager.persist(student);
        }
        entityManager.flush();
        entityManager.clear();
        LOG.info("Restored {} students.", dtos.size());
    }

    private void restoreInstructors(List<InstructorBackupDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return;
        }
        for (InstructorBackupDTO dto : dtos) {
            Instructor instructor = new Instructor(
                    dto.getNo(), dto.getName(), dto.getSurname(), dto.getEmail(),
                    dto.getPhone(), dto.getMobile(), dto.getCity(), dto.getAddress(), dto.getPostcode(),
                    dto.getPhoto1(), dto.getPhoto2(), dto.getPhoto3(), dto.getPhoto4(),
                    dto.getNotes(), dto.getOtherNotes(), dto.getCertNo(), dto.getSpecialization(),
                    dto.getDiploma(), dto.getBirthDate(), dto.getBirthPlace(), dto.getMrMs(),
                    dto.getNick(), dto.getNoCertificate(), dto.getExpirationDate());
            entityManager.persist(instructor);
        }
        entityManager.flush();
        entityManager.clear();
        LOG.info("Restored {} instructors.", dtos.size());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Set<Image> resolveImageRefs(Set<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            return new HashSet<>();
        }
        return uuids.stream()
                .map(id -> entityManager.getReference(Image.class, id))
                .collect(Collectors.toSet());
    }

    private byte[] toGzip(DatabaseBackupDTO backup) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(bos)) {
            objectMapper.writeValue(gzos, backup);
        }
        return bos.toByteArray();
    }

    private DatabaseBackupDTO fromGzip(byte[] data) throws IOException {
        try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return objectMapper.readValue(gzis, DatabaseBackupDTO.class);
        }
    }
}

