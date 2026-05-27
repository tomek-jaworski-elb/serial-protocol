package com.jaworski.serialprotocol.service.pdf;

import com.jaworski.serialprotocol.dto.custom.CourseCounterDTO;
import com.jaworski.serialprotocol.dto.custom.CourseTypeDTO;
import com.jaworski.serialprotocol.dto.custom.CoursesDTO;
import com.jaworski.serialprotocol.dto.custom.LecturerDTO;
import com.jaworski.serialprotocol.dto.custom.ParticipantDTO;
import com.jaworski.serialprotocol.dto.custom.TechnicianDTO;
import com.jaworski.serialprotocol.dto.custom.TrainerDTO;

/**
 * Determines the optimal layout strategy (TABLE_VIEW or CARD_VIEW) for PDF reports
 * based on data complexity and available space.
 *
 * <p>Strategy:
 * <ul>
 *   <li>TABLE_VIEW: for simple entities with few columns (Trainer, Lecturer, etc.)
 *   <li>CARD_VIEW: for complex entities with many fields/relations (Participant, Courses, etc.)
 * </ul>
 */
public final class PdfPageLayout {

    private PdfPageLayout() {
        // Utility class, no instantiation
    }

    /** Enumeration of available layout strategies */
    public enum LayoutStrategy {
        TABLE_VIEW,
        CARD_VIEW
    }

    // ── Layout Decision for Each Entity Type ────────────────────────────────

    /**
     * Decides layout for Trainer entities.
     * Trainers have relatively few fields (UUID, Name, Surname, Nickname, Email, Phone, Address, Notes).
     * Use TABLE_VIEW for compact presentation.
     */
    public static LayoutStrategy decideTrainerLayout(java.util.List<TrainerDTO> trainers) {
        return shouldUseTableView(trainers.size(), 6) ? LayoutStrategy.TABLE_VIEW : LayoutStrategy.CARD_VIEW;
    }

    /**
     * Decides layout for Lecturer entities.
     * Same as Trainer: few fields → TABLE_VIEW
     */
    public static LayoutStrategy decideLecturerLayout(java.util.List<LecturerDTO> lecturers) {
        return shouldUseTableView(lecturers.size(), 6) ? LayoutStrategy.TABLE_VIEW : LayoutStrategy.CARD_VIEW;
    }

    /**
     * Decides layout for Technician entities.
     * Same as Trainer: few fields → TABLE_VIEW
     */
    public static LayoutStrategy decideTechnicianLayout(java.util.List<TechnicianDTO> technicians) {
        return shouldUseTableView(technicians.size(), 6) ? LayoutStrategy.TABLE_VIEW : LayoutStrategy.CARD_VIEW;
    }

    /**
     * Decides layout for Participant entities.
     * Participants have moderate fields (Name, Surname, Email, Phone, BirthDate, Nickname).
     * Use TABLE_VIEW for compact lists, CARD_VIEW when record count is high.
     */
    public static LayoutStrategy decideParticipantLayout(java.util.List<ParticipantDTO> participants) {
        return shouldUseTableView(participants.size(), 6) ? LayoutStrategy.TABLE_VIEW : LayoutStrategy.CARD_VIEW;
    }

    /**
     * Decides layout for Course entities.
     * Courses have moderate key fields (ID, Participant, CourseType, Start, End, Counter).
     * Use TABLE_VIEW for compact lists, CARD_VIEW when record count is high.
     */
    public static LayoutStrategy decideCourseLayout(java.util.List<CoursesDTO> courses) {
        return shouldUseTableView(courses.size(), 6) ? LayoutStrategy.TABLE_VIEW : LayoutStrategy.CARD_VIEW;
    }

    /**
     * Decides layout for CourseType entities.
     * CourseTypes have few fields (ID, Code, Description, LongDescription).
     * Use TABLE_VIEW for compact presentation.
     */
    public static LayoutStrategy decideCourseTypeLayout(java.util.List<CourseTypeDTO> courseTypes) {
        return shouldUseTableView(courseTypes.size(), 4) ? LayoutStrategy.TABLE_VIEW : LayoutStrategy.CARD_VIEW;
    }

    /**
     * Decides layout for CourseCounter entities.
     * CourseCounters have very few fields (UUID, Counter ID).
     * Always use TABLE_VIEW.
     */
    public static LayoutStrategy decideCourseCounterLayout(java.util.List<CourseCounterDTO> counters) {
        return LayoutStrategy.TABLE_VIEW;
    }

    // ── Core Decision Logic ──────────────────────────────────────────────────

    /**
     * Core logic for deciding whether to use TABLE_VIEW.
     *
     * @param recordCount number of records to display
     * @param approximateColumnCount estimated number of columns
     * @return true if TABLE_VIEW should be used, false if CARD_VIEW is preferred
     */
    private static boolean shouldUseTableView(int recordCount, int approximateColumnCount) {
        // Never use table view if we have very few columns but many records
        // that would require many pages anyway
        if (recordCount > 20 && approximateColumnCount > 5) {
            return false;
        }

        // Use table view if data is compact enough
        // Table threshold: up to 6 columns and reasonable number of records
        return approximateColumnCount <= 6;
    }
}
