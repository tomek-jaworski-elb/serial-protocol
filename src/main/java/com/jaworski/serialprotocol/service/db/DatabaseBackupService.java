/*
 * Copyright 2026 Adtran Networks SE. All rights reserved.
 *
 * Owner: tomaszja
 */
package com.jaworski.serialprotocol.service.db;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.ObjectMapper;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
 * <p>Backup format: GZIP-compressed JSON produced by Jackson streaming API.
 *
 * <p>Large-database support:
 * <ul>
 *   <li>Backup uses Jackson {@link JsonGenerator} + JPA pagination ({@value #BATCH_SIZE} rows/page)
 *       to write JSON directly to the output stream without loading all entities into heap.</li>
 *   <li>Restore uses Jackson {@link JsonParser} streaming to read one element at a time and
 *       commits inserts in batches of {@value #BATCH_SIZE} rows using {@link TransactionTemplate},
 *       so no single transaction ever grows unbounded.</li>
 * </ul>
 *
 * <p>Restore strategy:
 * <ul>
 *   <li>UUID-pk entities – original UUIDs are preserved via {@code entityManager.persist()}.</li>
 *   <li>CourseType (IDENTITY pk) – DB generates new IDs; an old→new mapping is built and applied
 *       when re-inserting Courses rows.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DatabaseBackupService {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseBackupService.class);
    public static final String SCHEMA_VERSION = "1.0";

    /** Number of rows read/written per page during backup, and flushed per transaction during restore. */
    static final int BATCH_SIZE = 200;

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
    private final PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    // -------------------------------------------------------------------------
    // Backup – streaming (large-database safe)
    // -------------------------------------------------------------------------

    /**
     * Streams a full GZIP-compressed JSON backup directly to {@code outputStream}.
     *
     * <p>Memory footprint is bounded by {@value #BATCH_SIZE} entities per entity type at any
     * time, regardless of total database size.
     *
     * @param outputStream destination stream (e.g. {@code HttpServletResponse.getOutputStream()}).
     *                     The stream is NOT closed by this method.
     */
    @Transactional(readOnly = true)
    public void createBackup(OutputStream outputStream) throws IOException {
        try (GZIPOutputStream gzos = new GZIPOutputStream(outputStream);
             JsonGenerator gen = objectMapper.createGenerator(gzos)) {

            gen.writeStartObject();
            gen.writeStringProperty("schemaVersion", SCHEMA_VERSION);
            gen.writeStringProperty("timestamp", LocalDateTime.now().toString());

            streamWriteImages(gen);
            streamWriteCourseTypes(gen);
            streamWriteCourseCounters(gen);
            streamWriteTrainers(gen);
            streamWriteLecturers(gen);
            streamWriteTechnicians(gen);
            streamWriteParticipants(gen);
            streamWriteCourses(gen);
            streamWriteStudents(gen);
            streamWriteInstructors(gen);

            gen.writeEndObject();
        }
        LOG.info("Streaming backup completed.");
    }

    /**
     * Convenience overload for callers that need the data as a {@code byte[]}.
     * Internally delegates to {@link #createBackup(OutputStream)}.
     * For databases larger than a few hundred MB prefer the {@link OutputStream} variant.
     */
    @Transactional(readOnly = true)
    public byte[] createBackup() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        createBackup(bos);
        return bos.toByteArray();
    }

    // -- per-type streaming writers -------------------------------------------

    private void streamWriteImages(JsonGenerator gen) throws IOException {
        gen.writeName("images");
        gen.writeStartArray();
        long total = 0;
        int page = 0;
        Page<Image> chunk;
        do {
            chunk = imageRepository.findAll(PageRequest.of(page++, BATCH_SIZE));
            for (Image img : chunk) {
                gen.writePOJO(new ImageBackupDTO(img.getId(), img.getData(), img.getContentType()));
                entityManager.detach(img);
                total++;
            }
        } while (chunk.hasNext());
        gen.writeEndArray();
        LOG.info("Backup: wrote {} images.", total);
    }

    private void streamWriteCourseTypes(JsonGenerator gen) throws IOException {
        gen.writeName("courseTypes");
        gen.writeStartArray();
        long total = 0;
        int page = 0;
        Page<CourseType> chunk;
        do {
            chunk = courseTypeRepository.findAll(PageRequest.of(page++, BATCH_SIZE));
            for (CourseType ct : chunk) {
                gen.writePOJO(new CourseTypeDTO(ct.getId(), ct.getCode(), ct.getDescription(), ct.getLongDescription()));
                entityManager.detach(ct);
                total++;
            }
        } while (chunk.hasNext());
        gen.writeEndArray();
        LOG.info("Backup: wrote {} courseTypes.", total);
    }

    private void streamWriteCourseCounters(JsonGenerator gen) throws IOException {
        gen.writeName("courseCounters");
        gen.writeStartArray();
        long total = 0;
        int page = 0;
        Page<CourseCounter> chunk;
        do {
            chunk = courseCounterRepository.findAll(PageRequest.of(page++, BATCH_SIZE));
            for (CourseCounter cc : chunk) {
                gen.writePOJO(new CourseCounterDTO(
                        cc.getUuid(),
                        cc.getCounter(),
                        cc.getImage() == null ? null : cc.getImage().getId()));
                entityManager.detach(cc);
                total++;
            }
        } while (chunk.hasNext());
        gen.writeEndArray();
        LOG.info("Backup: wrote {} courseCounters.", total);
    }

    private void streamWriteTrainers(JsonGenerator gen) throws IOException {
        gen.writeName("trainers");
        gen.writeStartArray();
        long total = 0;
        int page = 0;
        Page<Trainer> chunk;
        do {
            chunk = trainerRepository.findAll(PageRequest.of(page++, BATCH_SIZE));
            for (Trainer t : chunk) {
                gen.writePOJO(TrainerMapper.mapToDTO(t));
                entityManager.detach(t);
                total++;
            }
        } while (chunk.hasNext());
        gen.writeEndArray();
        LOG.info("Backup: wrote {} trainers.", total);
    }

    private void streamWriteLecturers(JsonGenerator gen) throws IOException {
        gen.writeName("lecturers");
        gen.writeStartArray();
        long total = 0;
        int page = 0;
        Page<Lecturer> chunk;
        do {
            chunk = lecturerRepository.findAll(PageRequest.of(page++, BATCH_SIZE));
            for (Lecturer l : chunk) {
                gen.writePOJO(LecturerMapper.mapToDTO(l));
                entityManager.detach(l);
                total++;
            }
        } while (chunk.hasNext());
        gen.writeEndArray();
        LOG.info("Backup: wrote {} lecturers.", total);
    }

    private void streamWriteTechnicians(JsonGenerator gen) throws IOException {
        gen.writeName("technicians");
        gen.writeStartArray();
        long total = 0;
        int page = 0;
        Page<Technician> chunk;
        do {
            chunk = technicianRepository.findAll(PageRequest.of(page++, BATCH_SIZE));
            for (Technician t : chunk) {
                gen.writePOJO(TechnicianMapper.mapToDTO(t));
                entityManager.detach(t);
                total++;
            }
        } while (chunk.hasNext());
        gen.writeEndArray();
        LOG.info("Backup: wrote {} technicians.", total);
    }

    private void streamWriteParticipants(JsonGenerator gen) throws IOException {
        gen.writeName("participants");
        gen.writeStartArray();
        long total = 0;
        int page = 0;
        Page<Participant> chunk;
        do {
            chunk = participantRepository.findAll(PageRequest.of(page++, BATCH_SIZE));
            for (Participant p : chunk) {
                gen.writePOJO(ParticipantMapper.mapToDTO(p));
                entityManager.detach(p);
                total++;
            }
        } while (chunk.hasNext());
        gen.writeEndArray();
        LOG.info("Backup: wrote {} participants.", total);
    }

    private void streamWriteCourses(JsonGenerator gen) throws IOException {
        gen.writeName("courses");
        gen.writeStartArray();
        long total = 0;
        int page = 0;
        Page<Courses> chunk;
        do {
            chunk = coursesRepository.findAll(PageRequest.of(page++, BATCH_SIZE));
            for (Courses c : chunk) {
                gen.writePOJO(CoursesMapper.mapToDTO(c));
                entityManager.detach(c);
                total++;
            }
        } while (chunk.hasNext());
        gen.writeEndArray();
        LOG.info("Backup: wrote {} courses.", total);
    }

    private void streamWriteStudents(JsonGenerator gen) throws IOException {
        gen.writeName("students");
        gen.writeStartArray();
        long total = 0;
        int page = 0;
        Page<Student> chunk;
        do {
            chunk = studentRepository.findAll(PageRequest.of(page++, BATCH_SIZE));
            for (Student s : chunk) {
                gen.writePOJO(new StudentBackupDTO(
                        s.getId(), s.getName(), s.getLastName(), s.getCourseNo(),
                        s.getDateBegine(), s.getDateEnd(), s.getMrMs(), s.getCertType(), s.getPhoto()));
                entityManager.detach(s);
                total++;
            }
        } while (chunk.hasNext());
        gen.writeEndArray();
        LOG.info("Backup: wrote {} students.", total);
    }

    private void streamWriteInstructors(JsonGenerator gen) throws IOException {
        gen.writeName("instructors");
        gen.writeStartArray();
        long total = 0;
        int page = 0;
        Page<Instructor> chunk;
        do {
            chunk = instructorRepository.findAll(PageRequest.of(page++, BATCH_SIZE));
            for (Instructor i : chunk) {
                gen.writePOJO(new InstructorBackupDTO(
                        i.getNo(), i.getName(), i.getSurname(), i.getEmail(),
                        i.getPhone(), i.getMobile(), i.getCity(), i.getAddress(), i.getPostcode(),
                        i.getPhoto1(), i.getPhoto2(), i.getPhoto3(), i.getPhoto4(),
                        i.getNotes(), i.getOtherNotes(), i.getCertNo(), i.getSpecialization(),
                        i.getDiploma(), i.getBirthDate(), i.getBirthPlace(), i.getMrMs(),
                        i.getNick(), i.getNoCertificate(), i.getExpirationDate()));
                entityManager.detach(i);
                total++;
            }
        } while (chunk.hasNext());
        gen.writeEndArray();
        LOG.info("Backup: wrote {} instructors.", total);
    }

    // -------------------------------------------------------------------------
    // Restore – streaming (large-database safe)
    // -------------------------------------------------------------------------

    /**
     * Restores the full database from a GZIP-compressed JSON backup stream.
     *
     * <p>The JSON is parsed element-by-element using {@link JsonParser}.  Inserts are
     * committed in independent transactions of at most {@value #BATCH_SIZE} rows so heap
     * and transaction-log growth are bounded regardless of database size.
     *
     * <p>If any batch fails the method attempts a best-effort cleanup by clearing all
     * tables, then re-throws the original exception.
     *
     * @param inputStream source stream (e.g. {@code MultipartFile.getInputStream()}).
     *                    The stream is NOT closed by this method.
     */
    public void restoreFromBackup(InputStream inputStream) throws IOException {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        try (GZIPInputStream gzis = new GZIPInputStream(inputStream);
             JsonParser parser = objectMapper.createParser(gzis)) {

            // expect START_OBJECT
            parser.nextToken();

            String schemaVersion = null;
            Map<Long, Long> courseTypeIdMap = new HashMap<>();

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (parser.currentToken() != JsonToken.PROPERTY_NAME) {
                    break;
                }
                String fieldName = parser.currentName();
                parser.nextToken(); // move to value token

                switch (fieldName) {
                    case "schemaVersion":
                        schemaVersion = parser.getString();
                        if (!SCHEMA_VERSION.equals(schemaVersion)) {
                            throw new IllegalArgumentException(
                                    "Incompatible schema version: expected=" + SCHEMA_VERSION
                                            + ", found=" + schemaVersion);
                        }
                        break;
                    case "timestamp":
                        LOG.info("Starting DB restore from backup timestamp={}", parser.getText());
                        // Clear all tables once we've validated the schema version
                        if (schemaVersion != null) {
                            txTemplate.execute(status -> {
                                clearAllTables();
                                return null;
                            });
                        }
                        break;
                    case "images":
                        streamRestoreImages(parser, txTemplate);
                        break;
                    case "courseTypes":
                        courseTypeIdMap = streamRestoreCourseTypes(parser, txTemplate);
                        break;
                    case "courseCounters":
                        streamRestoreCourseCounters(parser, txTemplate);
                        break;
                    case "trainers":
                        streamRestoreTrainers(parser, txTemplate);
                        break;
                    case "lecturers":
                        streamRestoreLecturers(parser, txTemplate);
                        break;
                    case "technicians":
                        streamRestoreTechnicians(parser, txTemplate);
                        break;
                    case "participants":
                        streamRestoreParticipants(parser, txTemplate);
                        break;
                    case "courses":
                        streamRestoreCourses(parser, txTemplate, courseTypeIdMap);
                        break;
                    case "students":
                        streamRestoreStudents(parser, txTemplate);
                        break;
                    case "instructors":
                        streamRestoreInstructors(parser, txTemplate);
                        break;
                    default:
                        LOG.warn("Restore: unknown JSON field '{}', skipping.", fieldName);
                        parser.skipChildren();
                        break;
                }
            }
        } catch (RuntimeException | IOException e) {
            LOG.error("Restore failed – attempting cleanup of partial data", e);
            try {
                TransactionTemplate cleanup = new TransactionTemplate(transactionManager);
                cleanup.execute(status -> {
                    clearAllTables();
                    return null;
                });
            } catch (Exception cleanupEx) {
                LOG.error("Cleanup after failed restore also failed", cleanupEx);
            }
            // In Jackson 3.x JacksonException extends RuntimeException (not IOException).
            // Wrap it so the public API consistently throws IOException for parse errors.
            if (e instanceof JacksonException je) {
                throw new IOException("JSON processing error during restore: " + je.getMessage(), je);
            }
            throw e;
        }
        LOG.info("DB restore completed successfully.");
    }

    /**
     * Convenience overload for callers that pass a {@code byte[]}.
     * Internally delegates to {@link #restoreFromBackup(InputStream)}.
     */
    public void restoreFromBackup(byte[] compressedData) throws IOException {
        restoreFromBackup(new ByteArrayInputStream(compressedData));
    }

    // -- streaming restore helpers --------------------------------------------

    private void streamRestoreImages(JsonParser parser, TransactionTemplate txTemplate) throws IOException {
        // parser is positioned at START_ARRAY
        List<ImageBackupDTO> batch = new ArrayList<>(BATCH_SIZE);
        long total = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            batch.add(objectMapper.readValue(parser, ImageBackupDTO.class));
            if (batch.size() >= BATCH_SIZE) {
                List<ImageBackupDTO> toFlush = new ArrayList<>(batch);
                txTemplate.execute(status -> { persistImages(toFlush); return null; });
                total += batch.size();
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            List<ImageBackupDTO> toFlush = batch;
            txTemplate.execute(status -> { persistImages(toFlush); return null; });
            total += toFlush.size();
        }
        LOG.info("Restored {} images.", total);
    }

    private void persistImages(List<ImageBackupDTO> dtos) {
        for (ImageBackupDTO dto : dtos) {
            Image image = new Image();
            image.setId(dto.getUuid());
            image.setData(dto.getData());
            image.setContentType(dto.getContentType());
            entityManager.persist(image);
        }
        entityManager.flush();
        entityManager.clear();
    }

    /**
     * Restores CourseTypes and returns old-id → new-id mapping.
     * CourseType rows are config data; loading the full list into memory for the mapping is safe.
     */
    private Map<Long, Long> streamRestoreCourseTypes(JsonParser parser, TransactionTemplate txTemplate) throws IOException {
        Map<Long, Long> idMap = new HashMap<>();
        List<CourseTypeDTO> batch = new ArrayList<>(BATCH_SIZE);
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            batch.add(objectMapper.readValue(parser, CourseTypeDTO.class));
            if (batch.size() >= BATCH_SIZE) {
                List<CourseTypeDTO> toFlush = new ArrayList<>(batch);
                Map<Long, Long> partial = txTemplate.execute(status -> persistCourseTypes(toFlush));
                if (partial != null) {
                    idMap.putAll(partial);
                }
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            Map<Long, Long> partial = txTemplate.execute(status -> persistCourseTypes(batch));
            if (partial != null) {
                idMap.putAll(partial);
            }
        }
        LOG.info("Restored {} course types.", idMap.size());
        return idMap;
    }

    private Map<Long, Long> persistCourseTypes(List<CourseTypeDTO> dtos) {
        Map<Long, Long> idMap = new HashMap<>();
        for (CourseTypeDTO dto : dtos) {
            Long originalId = dto.getId();
            CourseType ct = CourseTypeMapper.mapToEntity(dto);
            ct.setId(null); // let DB generate new IDENTITY id
            CourseType saved = courseTypeRepository.save(ct);
            idMap.put(originalId, saved.getId());
        }
        entityManager.flush();
        entityManager.clear();
        return idMap;
    }

    private void streamRestoreCourseCounters(JsonParser parser, TransactionTemplate txTemplate) throws IOException {
        List<CourseCounterDTO> batch = new ArrayList<>(BATCH_SIZE);
        long total = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            batch.add(objectMapper.readValue(parser, CourseCounterDTO.class));
            if (batch.size() >= BATCH_SIZE) {
                List<CourseCounterDTO> toFlush = new ArrayList<>(batch);
                txTemplate.execute(status -> { persistCourseCounters(toFlush); return null; });
                total += batch.size();
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            List<CourseCounterDTO> toFlush = batch;
            txTemplate.execute(status -> { persistCourseCounters(toFlush); return null; });
            total += toFlush.size();
        }
        LOG.info("Restored {} course counters.", total);
    }

    private void persistCourseCounters(List<CourseCounterDTO> dtos) {
        for (CourseCounterDTO dto : dtos) {
            CourseCounter cc = new CourseCounter();
            cc.setUuid(dto.uuid());
            cc.setCounter(dto.counter());
            if (dto.imageUuid() != null) {
                cc.setImage(entityManager.getReference(Image.class, dto.imageUuid()));
            }
            entityManager.persist(cc);
        }
        entityManager.flush();
        entityManager.clear();
    }

    private void streamRestoreTrainers(JsonParser parser, TransactionTemplate txTemplate) throws IOException {
        List<TrainerDTO> batch = new ArrayList<>(BATCH_SIZE);
        long total = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            batch.add(objectMapper.readValue(parser, TrainerDTO.class));
            if (batch.size() >= BATCH_SIZE) {
                List<TrainerDTO> toFlush = new ArrayList<>(batch);
                txTemplate.execute(status -> { persistTrainers(toFlush); return null; });
                total += batch.size();
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            List<TrainerDTO> toFlush = batch;
            txTemplate.execute(status -> { persistTrainers(toFlush); return null; });
            total += toFlush.size();
        }
        LOG.info("Restored {} trainers.", total);
    }

    private void persistTrainers(List<TrainerDTO> dtos) {
        for (TrainerDTO dto : dtos) {
            Trainer trainer = new Trainer();
            trainer.setUuid(dto.getId());
            trainer.setName(dto.getName());
            trainer.setSurname(dto.getSurname());
            trainer.setNotes(dto.getNotes());
            trainer.setNickname(dto.getNickname());
            trainer.setEmail(dto.getEmail());
            trainer.setPhoneNumber(dto.getPhoneNumber());
            trainer.setAddress(dto.getAddress());
            trainer.setImages(resolveImageRefs(dto.getImagesUuid()));
            entityManager.persist(trainer);
        }
        entityManager.flush();
        entityManager.clear();
    }

    private void streamRestoreLecturers(JsonParser parser, TransactionTemplate txTemplate) throws IOException {
        List<LecturerDTO> batch = new ArrayList<>(BATCH_SIZE);
        long total = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            batch.add(objectMapper.readValue(parser, LecturerDTO.class));
            if (batch.size() >= BATCH_SIZE) {
                List<LecturerDTO> toFlush = new ArrayList<>(batch);
                txTemplate.execute(status -> { persistLecturers(toFlush); return null; });
                total += batch.size();
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            List<LecturerDTO> toFlush = batch;
            txTemplate.execute(status -> { persistLecturers(toFlush); return null; });
            total += toFlush.size();
        }
        LOG.info("Restored {} lecturers.", total);
    }

    private void persistLecturers(List<LecturerDTO> dtos) {
        for (LecturerDTO dto : dtos) {
            Lecturer lecturer = new Lecturer();
            lecturer.setUuid(dto.getId());
            lecturer.setName(dto.getName());
            lecturer.setSurname(dto.getSurname());
            lecturer.setNotes(dto.getNotes());
            lecturer.setNickname(dto.getNickname());
            lecturer.setEmail(dto.getEmail());
            lecturer.setPhoneNumber(dto.getPhoneNumber());
            lecturer.setAddress(dto.getAddress());
            lecturer.setImages(resolveImageRefs(dto.getImagesUuid()));
            entityManager.persist(lecturer);
        }
        entityManager.flush();
        entityManager.clear();
    }

    private void streamRestoreTechnicians(JsonParser parser, TransactionTemplate txTemplate) throws IOException {
        List<TechnicianDTO> batch = new ArrayList<>(BATCH_SIZE);
        long total = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            batch.add(objectMapper.readValue(parser, TechnicianDTO.class));
            if (batch.size() >= BATCH_SIZE) {
                List<TechnicianDTO> toFlush = new ArrayList<>(batch);
                txTemplate.execute(status -> { persistTechnicians(toFlush); return null; });
                total += batch.size();
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            List<TechnicianDTO> toFlush = batch;
            txTemplate.execute(status -> { persistTechnicians(toFlush); return null; });
            total += toFlush.size();
        }
        LOG.info("Restored {} technicians.", total);
    }

    private void persistTechnicians(List<TechnicianDTO> dtos) {
        for (TechnicianDTO dto : dtos) {
            Technician technician = new Technician();
            technician.setUuid(dto.getId());
            technician.setName(dto.getName());
            technician.setSurname(dto.getSurname());
            technician.setNotes(dto.getNotes());
            technician.setNickname(dto.getNickname());
            technician.setEmail(dto.getEmail());
            technician.setPhoneNumber(dto.getPhoneNumber());
            technician.setAddress(dto.getAddress());
            technician.setImages(resolveImageRefs(dto.getImagesUuid()));
            entityManager.persist(technician);
        }
        entityManager.flush();
        entityManager.clear();
    }

    private void streamRestoreParticipants(JsonParser parser, TransactionTemplate txTemplate) throws IOException {
        List<ParticipantDTO> batch = new ArrayList<>(BATCH_SIZE);
        long total = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            batch.add(objectMapper.readValue(parser, ParticipantDTO.class));
            if (batch.size() >= BATCH_SIZE) {
                List<ParticipantDTO> toFlush = new ArrayList<>(batch);
                txTemplate.execute(status -> { persistParticipants(toFlush); return null; });
                total += batch.size();
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            List<ParticipantDTO> toFlush = batch;
            txTemplate.execute(status -> { persistParticipants(toFlush); return null; });
            total += toFlush.size();
        }
        LOG.info("Restored {} participants.", total);
    }

    private void persistParticipants(List<ParticipantDTO> dtos) {
        for (ParticipantDTO dto : dtos) {
            Participant p = new Participant();
            p.setUuid(dto.getParticipantUuid());
            p.setId(dto.getId());
            p.setName(dto.getName());
            p.setSurname(dto.getSurname());
            p.setNotes(dto.getNotes());
            p.setNickname(dto.getNickname());
            p.setEmail(dto.getEmail());
            p.setPhoneNumber(dto.getPhoneNumber());
            p.setAddress(dto.getAddress());
            p.setBirthDate(dto.getBirthDate());
            if (dto.getImage() != null) {
                p.setImage(entityManager.getReference(Image.class, dto.getImage()));
            }
            entityManager.persist(p);
        }
        entityManager.flush();
        entityManager.clear();
    }

    private void streamRestoreCourses(JsonParser parser, TransactionTemplate txTemplate,
                                      Map<Long, Long> courseTypeIdMap) throws IOException {
        List<CoursesDTO> batch = new ArrayList<>(BATCH_SIZE);
        long total = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            batch.add(objectMapper.readValue(parser, CoursesDTO.class));
            if (batch.size() >= BATCH_SIZE) {
                List<CoursesDTO> toFlush = new ArrayList<>(batch);
                txTemplate.execute(status -> { persistCourses(toFlush, courseTypeIdMap); return null; });
                total += batch.size();
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            List<CoursesDTO> toFlush = batch;
            txTemplate.execute(status -> { persistCourses(toFlush, courseTypeIdMap); return null; });
            total += toFlush.size();
        }
        LOG.info("Restored {} courses.", total);
    }

    private void persistCourses(List<CoursesDTO> dtos, Map<Long, Long> courseTypeIdMap) {
        for (CoursesDTO dto : dtos) {
            Courses course = new Courses();
            course.setUuid(dto.getUuid());
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
    }

    private void streamRestoreStudents(JsonParser parser, TransactionTemplate txTemplate) throws IOException {
        List<StudentBackupDTO> batch = new ArrayList<>(BATCH_SIZE);
        long total = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            batch.add(objectMapper.readValue(parser, StudentBackupDTO.class));
            if (batch.size() >= BATCH_SIZE) {
                List<StudentBackupDTO> toFlush = new ArrayList<>(batch);
                txTemplate.execute(status -> { persistStudents(toFlush); return null; });
                total += batch.size();
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            List<StudentBackupDTO> toFlush = batch;
            txTemplate.execute(status -> { persistStudents(toFlush); return null; });
            total += toFlush.size();
        }
        LOG.info("Restored {} students.", total);
    }

    private void persistStudents(List<StudentBackupDTO> dtos) {
        for (StudentBackupDTO dto : dtos) {
            Student student = new Student(
                    dto.getId(), dto.getName(), dto.getLastName(), dto.getCourseNo(),
                    dto.getDateBegine(), dto.getDateEnd(), dto.getMrMs(), dto.getCertType(), dto.getPhoto());
            entityManager.persist(student);
        }
        entityManager.flush();
        entityManager.clear();
    }

    private void streamRestoreInstructors(JsonParser parser, TransactionTemplate txTemplate) throws IOException {
        List<InstructorBackupDTO> batch = new ArrayList<>(BATCH_SIZE);
        long total = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            batch.add(objectMapper.readValue(parser, InstructorBackupDTO.class));
            if (batch.size() >= BATCH_SIZE) {
                List<InstructorBackupDTO> toFlush = new ArrayList<>(batch);
                txTemplate.execute(status -> { persistInstructors(toFlush); return null; });
                total += batch.size();
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            List<InstructorBackupDTO> toFlush = batch;
            txTemplate.execute(status -> { persistInstructors(toFlush); return null; });
            total += toFlush.size();
        }
        LOG.info("Restored {} instructors.", total);
    }

    private void persistInstructors(List<InstructorBackupDTO> dtos) {
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
    }

    // -------------------------------------------------------------------------
    // Shared – clear all tables (reverse FK order)
    // -------------------------------------------------------------------------

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
}

