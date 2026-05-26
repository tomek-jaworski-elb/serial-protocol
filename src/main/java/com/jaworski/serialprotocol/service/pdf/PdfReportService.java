package com.jaworski.serialprotocol.service.pdf;

import com.jaworski.serialprotocol.configuration.PdfReportProperties;
import com.jaworski.serialprotocol.dto.custom.CourseCounterDTO;
import com.jaworski.serialprotocol.dto.custom.CourseTypeDTO;
import com.jaworski.serialprotocol.dto.custom.CoursesDTO;
import com.jaworski.serialprotocol.dto.custom.LecturerDTO;
import com.jaworski.serialprotocol.dto.custom.ParticipantDTO;
import com.jaworski.serialprotocol.dto.custom.TechnicianDTO;
import com.jaworski.serialprotocol.dto.custom.TrainerDTO;
import com.jaworski.serialprotocol.service.db.custom.CourseTypeService;
import com.jaworski.serialprotocol.service.db.custom.LecturerService;
import com.jaworski.serialprotocol.service.db.custom.ParticipantService;
import com.jaworski.serialprotocol.service.db.custom.TechnicianService;
import com.jaworski.serialprotocol.service.db.custom.TrainerService;
import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.openpdf.text.pdf.draw.LineSeparator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service responsible for generating PDF reports for all domain entities.
 *
 * <p>Fonts are loaded once at startup via {@link PostConstruct} from
 * {@code classpath:/fonts/}. Both a regular ({@code {name}.ttf}) and a bold
 * ({@code {name}-Bold.ttf}) variant are required.
 *
 * <p><strong>Runtime dependency:</strong> TTF font files must be present in
 * {@code src/main/resources/fonts/}. DejaVuSans is used as the built-in
 * fallback. Download from <a href="https://dejavu-fonts.github.io/">
 * https://dejavu-fonts.github.io/</a>.
 *
 * <p><strong>Note on OpenPDF:</strong> This implementation uses the
 * {@code com.github.librepdf:openpdf} artifact (latest stable 2.x) whose
 * runtime packages are {@code com.lowagie.text.*}. The artifact is
 * backward-compatible with the iText 2 API.
 */
@Service
@RequiredArgsConstructor
public class PdfReportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfReportService.class);

    /** Em-dash placeholder for {@code null} or blank field values. */
    private static final String EM_DASH = "\u2014";

    private static final String FALLBACK_FONT = "DejaVuSans";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ── page margins (left, right, top, bottom) in points ──────────────────
    private static final float MARGIN_H = 40f;
    private static final float MARGIN_TOP = 60f;
    private static final float MARGIN_BOTTOM = 40f;

    // ── column widths for 2-column entity table ─────────────────────────────
    private static final float[] COL_WIDTHS = {35f, 65f};

    // ── injected dependencies ───────────────────────────────────────────────
    private final PdfReportProperties properties;
    private final TrainerService trainerService;
    private final LecturerService lecturerService;
    private final TechnicianService technicianService;
    private final ParticipantService participantService;
    private final CourseTypeService courseTypeService;

    // ── font cache (populated once at startup) ──────────────────────────────
    private FontSet cachedFonts;

    // ── inner types ─────────────────────────────────────────────────────────

    /**
     * Cached set of PDF {@link Font} instances for all typographic roles.
     *
     * @param regular  normal body text
     * @param heading  bold section label (used for table row labels)
     * @param title    large bold document title
     * @param small    small footnote/metadata text
     */
    private record FontSet(Font regular, Font heading, Font title, Font small) {}

    /**
     * Normalised row for person-type entities (Trainer / Lecturer / Technician).
     */
    private record PersonRow(
            String id,
            String name,
            String surname,
            String nickname,
            String email,
            String phone,
            String address,
            String notes
    ) {}

    // ── lifecycle ───────────────────────────────────────────────────────────

    /**
     * Loads and caches TrueType fonts at application startup.
     *
     * <p>Falls back to {@code DejaVuSans} if the configured font is not found.
     * Throws {@link IllegalStateException} if neither font can be loaded.
     */
    @PostConstruct
    public void initFonts() {
        String configured = properties.getFont().getName();
        try {
            cachedFonts = loadFontSet(configured);
            LOGGER.info("PDF fonts loaded: '{}'", configured);
        } catch (Exception primary) {
            LOGGER.warn("Font '{}' not found in classpath/fonts/, falling back to '{}': {}",
                    configured, FALLBACK_FONT, primary.getMessage());
            if (configured.equals(FALLBACK_FONT)) {
                throw new IllegalStateException(buildFontErrorMessage(configured), primary);
            }
            try {
                cachedFonts = loadFontSet(FALLBACK_FONT);
                LOGGER.info("Fallback font '{}' loaded successfully", FALLBACK_FONT);
            } catch (Exception fallback) {
                throw new IllegalStateException(buildFontErrorMessage(configured), fallback);
            }
        }
    }

    // ── public API ──────────────────────────────────────────────────────────

    /**
     * Generates a PDF report listing {@link TrainerDTO} records.
     *
     * @param trainers list of trainer DTOs to include
     * @return PDF bytes
     * @throws IOException if PDF generation fails
     */
    public byte[] generateTrainersPdf(List<TrainerDTO> trainers) throws IOException {
        List<PersonRow> rows = trainers.stream()
                .map(t -> new PersonRow(
                        str(t.getId()), t.getName(), t.getSurname(),
                        t.getNickname(), t.getEmail(), t.getPhoneNumber(),
                        t.getAddress(), t.getNotes()))
                .toList();
        return generatePersonListPdf("Trainers Report", rows);
    }

    /**
     * Generates a PDF report listing {@link LecturerDTO} records.
     *
     * @param lecturers list of lecturer DTOs to include
     * @return PDF bytes
     * @throws IOException if PDF generation fails
     */
    public byte[] generateLecturersPdf(List<LecturerDTO> lecturers) throws IOException {
        List<PersonRow> rows = lecturers.stream()
                .map(l -> new PersonRow(
                        str(l.getId()), l.getName(), l.getSurname(),
                        l.getNickname(), l.getEmail(), l.getPhoneNumber(),
                        l.getAddress(), l.getNotes()))
                .toList();
        return generatePersonListPdf("Lecturers Report", rows);
    }

    /**
     * Generates a PDF report listing {@link TechnicianDTO} records.
     *
     * @param technicians list of technician DTOs to include
     * @return PDF bytes
     * @throws IOException if PDF generation fails
     */
    public byte[] generateTechniciansPdf(List<TechnicianDTO> technicians) throws IOException {
        List<PersonRow> rows = technicians.stream()
                .map(t -> new PersonRow(
                        str(t.getId()), t.getName(), t.getSurname(),
                        t.getNickname(), t.getEmail(), t.getPhoneNumber(),
                        t.getAddress(), t.getNotes()))
                .toList();
        return generatePersonListPdf("Technicians Report", rows);
    }

    /**
     * Generates a PDF report listing {@link ParticipantDTO} records.
     *
     * @param participants list of participant DTOs to include
     * @return PDF bytes
     * @throws IOException if PDF generation fails
     */
    public byte[] generateParticipantsPdf(List<ParticipantDTO> participants) throws IOException {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = openDocument(baos);
            
            // Cover page
            addCoverPage(document, "Participants Report", participants.size());

            // Determine layout strategy
            PdfPageLayout.LayoutStrategy strategy = PdfPageLayout.decideParticipantLayout(participants);

            if (strategy == PdfPageLayout.LayoutStrategy.TABLE_VIEW && properties.isTableViewEnabled()) {
                renderParticipantsTableView(document, participants);
            } else {
                renderParticipantsCardView(document, participants);
            }

            document.close();
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IOException("Failed to generate Participants PDF", e);
        }
    }

    /**
     * Generates a PDF report listing {@link CoursesDTO} records.
     *
     * <p>Resolves participant, course-type, trainer, lecturer and technician IDs
     * to human-readable names using the injected services.
     *
     * @param courses list of course DTOs to include
     * @return PDF bytes
     * @throws IOException if PDF generation fails
     */
    public byte[] generateCoursesPdf(List<CoursesDTO> courses) throws IOException {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = openDocument(baos);
            
            // Cover page
            addCoverPage(document, "Courses Report", courses.size());

            // Determine layout strategy
            PdfPageLayout.LayoutStrategy strategy = PdfPageLayout.decideCourseLayout(courses);

            if (strategy == PdfPageLayout.LayoutStrategy.TABLE_VIEW && properties.isTableViewEnabled()) {
                renderCoursesTableView(document, courses);
            } else {
                renderCoursesCardView(document, courses);
            }

            document.close();
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IOException("Failed to generate Courses PDF", e);
        }
    }

    /**
     * Generates a PDF report listing {@link CourseTypeDTO} records.
     *
     * @param courseTypes list of course-type DTOs to include
     * @return PDF bytes
     * @throws IOException if PDF generation fails
     */
    public byte[] generateCourseTypesPdf(List<CourseTypeDTO> courseTypes) throws IOException {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = openDocument(baos);

            // Cover page
            addCoverPage(document, "Course Types Report", courseTypes.size());

            for (int i = 0; i < courseTypes.size(); i++) {
                CourseTypeDTO ct = courseTypes.get(i);
                if (i > 0) {
                    addSeparator(document);
                }
                PdfPTable table = createTable();
                addRow(table, "ID",               str(ct.getId()));
                addRow(table, "Code",             strBlank(ct.getCode()));
                addRow(table, "Description",      strBlank(ct.getDescription()));
                addRow(table, "Long Description", strBlank(ct.getLongDescription()));
                document.add(table);
            }

            document.close();
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IOException("Failed to generate Course Types PDF", e);
        }
    }

    /**
     * Generates a PDF report listing {@link CourseCounterDTO} records.
     *
     * @param courseCounters list of course-counter DTOs to include
     * @return PDF bytes
     * @throws IOException if PDF generation fails
     */
    public byte[] generateCourseCountersPdf(List<CourseCounterDTO> courseCounters) throws IOException {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = openDocument(baos);

            // Cover page
            addCoverPage(document, "Course Counters Report", courseCounters.size());

            for (int i = 0; i < courseCounters.size(); i++) {
                CourseCounterDTO cc = courseCounters.get(i);
                if (i > 0) {
                    addSeparator(document);
                }
                PdfPTable table = createTable();
                addRow(table, "UUID",       str(cc.uuid()));
                addRow(table, "Counter ID", String.valueOf(cc.counter()));
                document.add(table);
            }

            document.close();
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IOException("Failed to generate Course Counters PDF", e);
        }
    }

    // ── private PDF building helpers ────────────────────────────────────────

    /**
     * Renders participants in TABLE_VIEW format (compact columns).
     * Columns: Name, Surname, Email, Phone, BirthDate, Nickname
     */
    private void renderParticipantsTableView(Document document, List<ParticipantDTO> participants) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{16f, 16f, 22f, 14f, 14f, 14f});
        table.setWidthPercentage(100f);
        table.setSpacingBefore(6f);

        String[] headers = {"Name", "Surname", "Email", "Phone", "Birth Date", "Nickname"};
        for (String header : headers) {
            addTableHeaderCell(table, header);
        }

        for (int i = 0; i < participants.size(); i++) {
            ParticipantDTO p = participants.get(i);
            java.awt.Color rowBg = PdfColorScheme.getRowBackgroundColor(i);
            addTableCell(table, strBlank(p.getName()), rowBg);
            addTableCell(table, strBlank(p.getSurname()), rowBg);
            addTableCell(table, strBlank(p.getEmail()), rowBg);
            addTableCell(table, strBlank(p.getPhoneNumber()), rowBg);
            addTableCell(table, p.getBirthDate() != null ? p.getBirthDate().format(DATE_FMT) : EM_DASH, rowBg);
            addTableCell(table, strBlank(p.getNickname()), rowBg);
        }

        document.add(table);
    }

    /**
     * Renders participants in CARD_VIEW format (detailed, one per section with frame).
     */
    private void renderParticipantsCardView(Document document, List<ParticipantDTO> participants) throws DocumentException {
        for (int i = 0; i < participants.size(); i++) {
            ParticipantDTO p = participants.get(i);
            if (i > 0) {
                addDecorativeSeparator(document);
            }

            Paragraph counterPara = new Paragraph(
                    "Record " + (i + 1) + " of " + participants.size(),
                    cachedFonts.small());
            counterPara.setAlignment(Element.ALIGN_RIGHT);
            counterPara.setSpacingAfter(4f);
            document.add(counterPara);

            PdfPTable card = createCardTable();
            addRow(card, "ID",           str(p.getId()));
            addRow(card, "UUID",         str(p.getParticipantUuid()));
            addRow(card, "Name",         strBlank(p.getName()));
            addRow(card, "Surname",      strBlank(p.getSurname()));
            addRow(card, "Birth Date",   p.getBirthDate() != null ? p.getBirthDate().format(DATE_FMT) : EM_DASH);
            addRow(card, "Nickname",     strBlank(p.getNickname()));
            addRow(card, "Email",        strBlank(p.getEmail()));
            addRow(card, "Phone",        strBlank(p.getPhoneNumber()));
            addRow(card, "Address",      strBlank(p.getAddress()));
            addRow(card, "Notes",        strBlank(p.getNotes()));
            document.add(card);
        }
    }

    /**
     * Renders courses in TABLE_VIEW format (compact columns).
     * Columns: ID, Participant, Course Type, Start Date, End Date, Counter
     */
    private void renderCoursesTableView(Document document, List<CoursesDTO> courses) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{8f, 20f, 24f, 14f, 14f, 10f});
        table.setWidthPercentage(100f);
        table.setSpacingBefore(6f);

        String[] headers = {"ID", "Participant", "Course Type", "Start Date", "End Date", "Counter"};
        for (String header : headers) {
            addTableHeaderCell(table, header);
        }

        for (int i = 0; i < courses.size(); i++) {
            CoursesDTO c = courses.get(i);
            java.awt.Color rowBg = PdfColorScheme.getRowBackgroundColor(i);
            addTableCell(table, str(c.getId()), rowBg);
            addTableCell(table, resolveParticipantName(c.getParticipantUuid()), rowBg);
            addTableCell(table, resolveCourseTypeLabel(c.getCourseTypeId()), rowBg);
            addTableCell(table, c.getStartDate() != null ? c.getStartDate().format(DATE_FMT) : EM_DASH, rowBg);
            addTableCell(table, c.getEndDate() != null ? c.getEndDate().format(DATE_FMT) : EM_DASH, rowBg);
            addTableCell(table, str(c.getCounter()), rowBg);
        }

        document.add(table);
    }

    /**
     * Renders courses in CARD_VIEW format (detailed, one per section with frame).
     */
    private void renderCoursesCardView(Document document, List<CoursesDTO> courses) throws DocumentException {
        for (int i = 0; i < courses.size(); i++) {
            CoursesDTO c = courses.get(i);
            if (i > 0) {
                addDecorativeSeparator(document);
            }

            Paragraph counterPara = new Paragraph(
                    "Record " + (i + 1) + " of " + courses.size(),
                    cachedFonts.small());
            counterPara.setAlignment(Element.ALIGN_RIGHT);
            counterPara.setSpacingAfter(4f);
            document.add(counterPara);

            PdfPTable card = createCardTable();
            addRow(card, "Course ID",    str(c.getId()));
            addRow(card, "UUID",         str(c.getUuid()));
            addRow(card, "Participant",  resolveParticipantName(c.getParticipantUuid()));
            addRow(card, "Course Type",  resolveCourseTypeLabel(c.getCourseTypeId()));
            addRow(card, "Start Date",   c.getStartDate() != null ? c.getStartDate().format(DATE_FMT) : EM_DASH);
            addRow(card, "End Date",     c.getEndDate()   != null ? c.getEndDate().format(DATE_FMT)   : EM_DASH);
            addRow(card, "Counter",      str(c.getCounter()));
            addRow(card, "Trainers",     resolvePersonNames(c.getTrainerIds(),    this::resolveTrainerName));
            addRow(card, "Lecturers",    resolvePersonNames(c.getLecturerIds(),   this::resolveLecturerName));
            addRow(card, "Technicians",  resolvePersonNames(c.getTechnicianIds(), this::resolveTechnicianName));
            document.add(card);
        }
    }

    /**
     * Adds a cover page (title page) with report metadata.
     * Includes: accent bar, report title, generation date/time, company name, record count, and page break.
     */
    private void addCoverPage(Document document, String reportTitle, int recordCount) throws DocumentException {
        // Company name heading
        Paragraph companyPara = new Paragraph(properties.getCompanyName(), cachedFonts.heading());
        companyPara.setAlignment(Element.ALIGN_CENTER);
        companyPara.setSpacingAfter(8f);
        document.add(companyPara);

        // Accent bar (colored line under company name)
        LineSeparator accentBar = new LineSeparator(2.5f, 30f, PdfColorScheme.ACCENT_BLUE, Element.ALIGN_CENTER, -2f);
        Paragraph accentPara = new Paragraph(new Chunk(accentBar));
        accentPara.setSpacingAfter(20f);
        document.add(accentPara);

        // Spacing
        document.add(new Paragraph("\n\n"));

        // Main title
        Paragraph titlePara = new Paragraph(reportTitle, cachedFonts.title());
        titlePara.setAlignment(Element.ALIGN_CENTER);
        titlePara.setSpacingAfter(24f);
        document.add(titlePara);

        // Decorative separator
        addDecorativeSeparator(document);

        document.add(new Paragraph("\n"));

        // Statistics
        String generatedTime = LocalDateTime.now().format(DATETIME_FMT);
        String statsText = "Generated: " + generatedTime + "\n"
                         + "Records: " + recordCount;
        Paragraph statsPara = new Paragraph(statsText, cachedFonts.small());
        statsPara.setAlignment(Element.ALIGN_CENTER);
        statsPara.setSpacingBefore(12f);
        document.add(statsPara);

        // Page break after cover page
        document.newPage();
    }

    /**
     * Adds a decorative separator (horizontal line) to the document.
     * Used in cover page and between record sections.
     */
    private void addDecorativeSeparator(Document document) throws DocumentException {
        LineSeparator ls = new LineSeparator(1.0f, 50f, PdfColorScheme.DIVIDER, Element.ALIGN_CENTER, -4f);
        Paragraph sep = new Paragraph(new Chunk(ls));
        sep.setSpacingBefore(8f);
        sep.setSpacingAfter(8f);
        document.add(sep);
    }

    /**
     * Adds a page header with company branding.
     * This is a simplified header printed at the top of content (not floating).
     */
    private void addPageHeader(Document document, String reportTitle) throws DocumentException {
        Paragraph headerPara = new Paragraph(properties.getCompanyName() + " — " + reportTitle, cachedFonts.small());
        headerPara.setAlignment(Element.ALIGN_CENTER);
        headerPara.setSpacingAfter(6f);
        document.add(headerPara);

        addDecorativeSeparator(document);
        document.add(new Paragraph());
    }

    /**
     * Shared person-list PDF builder used by trainers, lecturers and technicians.
     */
    private byte[] generatePersonListPdf(String docTitle, List<PersonRow> rows) throws IOException {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = openDocument(baos);

            // Cover page
            addCoverPage(document, docTitle, rows.size());

            // Determine layout strategy
            PdfPageLayout.LayoutStrategy strategy = decidePersonLayout(rows.size());

            if (strategy == PdfPageLayout.LayoutStrategy.TABLE_VIEW && properties.isTableViewEnabled()) {
                renderPersonTableView(document, rows);
            } else {
                renderPersonCardView(document, docTitle, rows);
            }

            document.close();
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IOException("Failed to generate '" + docTitle + "' PDF", e);
        }
    }

    /**
     * Renders person data in TABLE_VIEW format (compact, columns-based).
     */
    private void renderPersonTableView(Document document, List<PersonRow> rows) throws DocumentException {
        // Table with 6 columns: Name, Surname, Email, Phone, Nickname, Address
        PdfPTable table = new PdfPTable(new float[]{15f, 15f, 20f, 15f, 15f, 20f});
        table.setWidthPercentage(100f);
        table.setSpacingBefore(6f);

        // Table header
        String[] headers = {"Name", "Surname", "Email", "Phone", "Nickname", "Address"};
        for (String header : headers) {
            PdfPCell headerCell = new PdfPCell(new Phrase(header, cachedFonts.heading()));
            headerCell.setBackgroundColor(PdfColorScheme.TABLE_HEADER_BG);
            headerCell.setBorder(Rectangle.NO_BORDER);
            headerCell.setPadding(6f);

            // Header text color
            Font headerFont = new Font(cachedFonts.heading().getBaseFont(),
                    properties.getFont().getSize().getRegular(), Font.BOLD);
            headerFont.setColor(PdfColorScheme.TABLE_HEADER_TEXT);
            headerCell.setPhrase(new Phrase(header, headerFont));

            table.addCell(headerCell);
        }

        // Table rows with alternating colors
        for (int i = 0; i < rows.size(); i++) {
            PersonRow r = rows.get(i);
            java.awt.Color rowBg = PdfColorScheme.getRowBackgroundColor(i);

            addTableCell(table, r.name(), rowBg);
            addTableCell(table, r.surname(), rowBg);
            addTableCell(table, r.email(), rowBg);
            addTableCell(table, r.phone(), rowBg);
            addTableCell(table, r.nickname(), rowBg);
            addTableCell(table, r.address(), rowBg);
        }

        document.add(table);
    }

    /**
     * Renders person data in CARD_VIEW format (detailed, one person per section with frame).
     */
    private void renderPersonCardView(Document document, String docTitle, List<PersonRow> rows) throws DocumentException {
        for (int i = 0; i < rows.size(); i++) {
            PersonRow r = rows.get(i);
            if (i > 0) {
                addDecorativeSeparator(document);
            }

            // Record counter
            Paragraph counterPara = new Paragraph(
                    "Record " + (i + 1) + " of " + rows.size(),
                    cachedFonts.small());
            counterPara.setAlignment(Element.ALIGN_RIGHT);
            counterPara.setSpacingAfter(4f);
            document.add(counterPara);

            PdfPTable card = createCardTable();
            addRow(card, "UUID", r.id());
            addRow(card, "Name", strBlank(r.name()));
            addRow(card, "Surname", strBlank(r.surname()));
            addRow(card, "Nickname", strBlank(r.nickname()));
            addRow(card, "Email", strBlank(r.email()));
            addRow(card, "Phone", strBlank(r.phone()));
            addRow(card, "Address", strBlank(r.address()));
            addRow(card, "Notes", strBlank(r.notes()));
            document.add(card);
        }
    }

    /**
     * Decides layout strategy for person entities (Trainer, Lecturer, Technician).
     */
    private PdfPageLayout.LayoutStrategy decidePersonLayout(int recordCount) {
        if (!properties.isTableViewEnabled()) {
            return PdfPageLayout.LayoutStrategy.CARD_VIEW;
        }
        return recordCount > 20 ? PdfPageLayout.LayoutStrategy.CARD_VIEW : PdfPageLayout.LayoutStrategy.TABLE_VIEW;
    }

    /**
     * Adds a cell to a table with optional background color.
     */
    private void addTableCell(PdfPTable table, String value, java.awt.Color backgroundColor) {
        String displayValue = (value != null && !value.isBlank()) ? value : EM_DASH;
        PdfPCell cell = new PdfPCell(new Phrase(displayValue, cachedFonts.regular()));
        cell.setBackgroundColor(backgroundColor);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(4f);
        table.addCell(cell);
    }


    /**
     * Opens an A4 {@link Document} backed by the given output stream.
     * Registers the {@link PdfHeaderFooterEvent} for footer rendering.
     * The caller is responsible for calling {@link Document#close()}.
     */
    private Document openDocument(ByteArrayOutputStream baos) throws DocumentException {
        Document document = new Document(PageSize.A4, MARGIN_H, MARGIN_H, MARGIN_TOP, MARGIN_BOTTOM);
        PdfWriter writer = PdfWriter.getInstance(document, baos);

        // Register header/footer event
        writer.setPageEvent(new PdfHeaderFooterEvent(
                properties.getCompanyName(),
                cachedFonts.regular().getBaseFont(),
                properties.getFont().getSize().getSmall()));

        document.open();
        return document;
    }

    /**
     * Adds the report title and generation timestamp to the document.
     */
    private void addDocumentHeader(Document document, String docTitle) throws DocumentException {
        Paragraph titlePara = new Paragraph(docTitle, cachedFonts.title());
        titlePara.setAlignment(Element.ALIGN_CENTER);
        titlePara.setSpacingAfter(4f);
        document.add(titlePara);

        String generated = "Generated: " + LocalDateTime.now().format(DATETIME_FMT);
        Paragraph datePara = new Paragraph(generated, cachedFonts.small());
        datePara.setAlignment(Element.ALIGN_CENTER);
        datePara.setSpacingAfter(14f);
        document.add(datePara);
    }

    /**
     * Adds a horizontal separator line between entity records.
     */
    private void addSeparator(Document document) throws DocumentException {
        LineSeparator ls = new LineSeparator(0.5f, 100f, java.awt.Color.GRAY, Element.ALIGN_CENTER, -4f);
        Paragraph sep = new Paragraph(new Chunk(ls));
        sep.setSpacingBefore(6f);
        sep.setSpacingAfter(6f);
        document.add(sep);
    }

    /**
     * Creates a two-column {@link PdfPTable} with no outer border,
     * using column widths 35% (label) and 65% (value).
     */
    private PdfPTable createTable() throws DocumentException {
        PdfPTable table = new PdfPTable(COL_WIDTHS);
        table.setWidthPercentage(100f);
        table.setSpacingBefore(4f);
        table.setSpacingAfter(4f);
        return table;
    }

    /**
     * Creates a two-column card-style {@link PdfPTable} with a subtle frame
     * (light background and border) for CARD_VIEW layouts.
     */
    private PdfPTable createCardTable() throws DocumentException {
        PdfPTable table = new PdfPTable(COL_WIDTHS);
        table.setWidthPercentage(100f);
        table.setSpacingBefore(4f);
        table.setSpacingAfter(4f);

        // Outer table cell acts as a frame wrapper
        PdfPCell frameCell = new PdfPCell(table);
        // We apply frame styling via individual cell backgrounds instead,
        // since OpenPDF doesn't support outer table borders easily.
        // The frame effect is achieved through alternating row backgrounds.
        return table;
    }

    /**
     * Adds a header cell to a multi-column table with dark background and white text.
     */
    private void addTableHeaderCell(PdfPTable table, String header) {
        Font headerFont = new Font(cachedFonts.heading().getBaseFont(),
                properties.getFont().getSize().getRegular(), Font.BOLD);
        headerFont.setColor(PdfColorScheme.TABLE_HEADER_TEXT);

        PdfPCell headerCell = new PdfPCell(new Phrase(header, headerFont));
        headerCell.setBackgroundColor(PdfColorScheme.TABLE_HEADER_BG);
        headerCell.setBorder(Rectangle.NO_BORDER);
        headerCell.setPadding(6f);
        table.addCell(headerCell);
    }

    /**
     * Adds a single label-value row to the given table.
     *
     * @param table target table
     * @param label row label (rendered in bold at regular size)
     * @param value row value (rendered in regular font); {@code null}/blank → em-dash
     */
    private void addRow(PdfPTable table, String label, String value) {
        // Derive bold-at-regular-size font from the heading BaseFont
        Font labelFont = new Font(
                cachedFonts.heading().getBaseFont(),
                properties.getFont().getSize().getRegular(),
                Font.BOLD);

        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPaddingBottom(3f);
        labelCell.setPaddingTop(1f);

        String displayValue = (value != null && !value.isBlank()) ? value : EM_DASH;
        PdfPCell valueCell = new PdfPCell(new Phrase(displayValue, cachedFonts.regular()));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPaddingBottom(3f);
        valueCell.setPaddingTop(1f);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    // ── name resolution helpers ─────────────────────────────────────────────

    private String resolveParticipantName(UUID uuid) {
        if (uuid == null) return EM_DASH;
        ParticipantDTO dto = participantService.findByUuid(uuid);
        if (dto == null) return EM_DASH;
        return fullName(dto.getName(), dto.getSurname());
    }

    private String resolveCourseTypeLabel(Long id) {
        if (id == null) return EM_DASH;
        try {
            CourseTypeDTO ct = courseTypeService.findById(id);
            String code     = strBlank(ct.getCode());
            String desc     = strBlank(ct.getDescription());
            String longDesc = strBlank(ct.getLongDescription());
            return code + " \u2014 " + desc + " (" + longDesc + ")";
        } catch (Exception e) {
            LOGGER.warn("CourseType id={} not found: {}", id, e.getMessage());
            return EM_DASH;
        }
    }

    private String resolveTrainerName(UUID uuid) {
        TrainerDTO dto = trainerService.findById(uuid);
        return dto != null ? fullName(dto.getName(), dto.getSurname()) : null;
    }

    private String resolveLecturerName(UUID uuid) {
        LecturerDTO dto = lecturerService.findById(uuid);
        return dto != null ? fullName(dto.getName(), dto.getSurname()) : null;
    }

    private String resolveTechnicianName(UUID uuid) {
        TechnicianDTO dto = technicianService.findById(uuid);
        return dto != null ? fullName(dto.getName(), dto.getSurname()) : null;
    }

    /**
     * Resolves a set of UUIDs to display names using the provided resolver function,
     * filters {@code null} results (unknown IDs) and joins with {@code ", "}.
     */
    private String resolvePersonNames(Set<UUID> ids,
                                      java.util.function.Function<UUID, String> resolver) {
        if (ids == null || ids.isEmpty()) return EM_DASH;
        String names = ids.stream()
                .map(resolver)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
        return names.isBlank() ? EM_DASH : names;
    }

    // ── font loading ────────────────────────────────────────────────────────

    /**
     * Loads a complete {@link FontSet} for the given font family name.
     *
     * @param fontName base font family name (e.g. "DejaVuSans")
     * @throws IOException       if a font file cannot be read from the classpath
     * @throws DocumentException if OpenPDF rejects the font data
     */
    private FontSet loadFontSet(String fontName) throws IOException, DocumentException {
        BaseFont regularBase = loadBaseFont(fontName + ".ttf");
        BaseFont boldBase    = loadBaseFont(fontName + "-Bold.ttf");

        PdfReportProperties.FontConfig.SizeConfig sizes = properties.getFont().getSize();

        return new FontSet(
                new Font(regularBase, sizes.getRegular()),
                new Font(boldBase,    sizes.getHeading()),
                new Font(boldBase,    sizes.getTitle()),
                new Font(regularBase, sizes.getSmall())
        );
    }

    /**
     * Loads a {@link BaseFont} from the classpath path {@code /fonts/{fileName}}
     * with {@code IDENTITY_H} encoding and the {@code EMBEDDED} flag so the font
     * is self-contained inside the PDF (supports full Unicode — Polish, German, …).
     *
     * @param fileName TTF file name, e.g. {@code "DejaVuSans.ttf"}
     * @throws IOException       if the file is absent from the classpath
     * @throws DocumentException if OpenPDF cannot parse the font data
     */
    private BaseFont loadBaseFont(String fileName) throws IOException, DocumentException {
        try (InputStream is = getClass().getResourceAsStream("/fonts/" + fileName)) {
            if (is == null) {
                throw new IOException("Font file not found on classpath: /fonts/" + fileName);
            }
            byte[] data = is.readAllBytes();
            return BaseFont.createFont(fileName, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, data, null);
        }
    }

    // ── static utility helpers ──────────────────────────────────────────────

    /** Converts any object to String, or {@link #EM_DASH} for {@code null}. */
    private static String str(Object value) {
        return value != null ? value.toString() : EM_DASH;
    }

    /**
     * Returns the value itself when non-null and non-blank,
     * otherwise returns {@link #EM_DASH}.
     */
    private static String strBlank(String value) {
        return (value != null && !value.isBlank()) ? value : EM_DASH;
    }

    /** Joins name + surname trimmed, or {@link #EM_DASH} when the result is blank. */
    private static String fullName(String name, String surname) {
        String n = name    != null ? name.trim()    : "";
        String s = surname != null ? surname.trim() : "";
        String full = (n + " " + s).trim();
        return full.isEmpty() ? EM_DASH : full;
    }

    private static String buildFontErrorMessage(String configuredFont) {
        return ("Neither the configured font '%s' nor the fallback 'DejaVuSans' could be loaded. " +
                "Please add the following TrueType font files to src/main/resources/fonts/: " +
                "'%s.ttf', '%s-Bold.ttf' (or 'DejaVuSans.ttf', 'DejaVuSans-Bold.ttf'). " +
                "DejaVuSans can be downloaded from https://dejavu-fonts.github.io/")
                .formatted(configuredFont, configuredFont, configuredFont);
    }
}
