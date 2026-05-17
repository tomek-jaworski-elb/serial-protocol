/*
 * Copyright 2026 Adtran Networks SE. All rights reserved.
 *
 * Owner: tomaszja
 */
package com.jaworski.serialprotocol.dto.backup;

import com.jaworski.serialprotocol.dto.custom.CourseCounterDTO;
import com.jaworski.serialprotocol.dto.custom.CoursesDTO;
import com.jaworski.serialprotocol.dto.custom.CourseTypeDTO;
import com.jaworski.serialprotocol.dto.custom.LecturerDTO;
import com.jaworski.serialprotocol.dto.custom.ParticipantDTO;
import com.jaworski.serialprotocol.dto.custom.TechnicianDTO;
import com.jaworski.serialprotocol.dto.custom.TrainerDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Root DTO for the complete database backup.
 * Format: GZIP-compressed JSON (.json.gz).
 *
 * <p>Restore strategy:
 * <ul>
 *   <li>UUID-pk entities (Image, CourseCounter, Trainer, Lecturer, Technician,
 *       Participant, Courses) – original UUIDs are preserved.</li>
 *   <li>IDENTITY-pk entity (CourseType with Long id) – the database generates
 *       new IDs on restore; an old→new ID mapping is built on the fly and
 *       applied when re-inserting Courses rows.</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatabaseBackupDTO {

    /** Schema version used to validate compatibility on restore. */
    private String schemaVersion;

    /** When the backup was taken. */
    private LocalDateTime timestamp;

    private List<ImageBackupDTO> images;
    private List<CourseTypeDTO> courseTypes;
    private List<CourseCounterDTO> courseCounters;
    private List<TrainerDTO> trainers;
    private List<LecturerDTO> lecturers;
    private List<TechnicianDTO> technicians;
    private List<ParticipantDTO> participants;
    private List<CoursesDTO> courses;
    private List<StudentBackupDTO> students;
    private List<InstructorBackupDTO> instructors;
}

