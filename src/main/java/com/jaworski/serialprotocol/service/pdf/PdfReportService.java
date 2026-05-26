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
            addDocumentHeader(document, "Participants Report");

            for (int i = 0; i < participants.size(); i++) {
                ParticipantDTO p = participants.get(i);
                if (i > 0) {
                    addSeparator(document);
                }
                PdfPTable table = createTable();
                addRow(table, "ID",           str(p.getId()));
                addRow(table, "UUID",         str(p.getParticipantUuid()));
                addRow(table, "Name",         strBlank(p.getName()));
                addRow(table, "Surname",      strBlank(p.getSurname()));
                addRow(table, "Birth Date",   p.getBirthDate() != null ? p.getBirthDate().format(DATE_FMT) : EM_DASH);
                addRow(table, "Nickname",     strBlank(p.getNickname()));
                addRow(table, "Email",        strBlank(p.getEmail()));
                addRow(table, "Phone",        strBlank(p.getPhoneNumber()));
                addRow(table, "Address",      strBlank(p.getAddress()));
                addRow(table, "Notes",        strBlank(p.getNotes()));
                document.add(table);
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
            addDocumentHeader(document, "Courses Report");

            for (int i = 0; i < courses.size(); i++) {
                CoursesDTO c = courses.get(i);
                if (i > 0) {
                    addSeparator(document);
                }
                PdfPTable table = createTable();
                addRow(table, "Course ID",    str(c.getId()));
                addRow(table, "UUID",         str(c.getUuid()));
                addRow(table, "Participant",  resolveParticipantName(c.getParticipantUuid()));
                addRow(table, "Course Type",  resolveCourseTypeLabel(c.getCourseTypeId()));
                addRow(table, "Start Date",   c.getStartDate() != null ? c.getStartDate().format(DATE_FMT) : EM_DASH);
                addRow(table, "End Date",     c.getEndDate()   != null ? c.getEndDate().format(DATE_FMT)   : EM_DASH);
                addRow(table, "Counter",      str(c.getCounter()));
                addRow(table, "Trainers",     resolvePersonNames(c.getTrainerIds(),    this::resolveTrainerName));
                addRow(table, "Lecturers",    resolvePersonNames(c.getLecturerIds(),   this::resolveLecturerName));
                addRow(table, "Technicians",  resolvePersonNames(c.getTechnicianIds(), this::resolveTechnicianName));
                document.add(table);
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
            addDocumentHeader(document, "Course Types Report");

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
            addDocumentHeader(document, "Course Counters Report");

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
     * Shared person-list PDF builder used by trainers, lecturers and technicians.
     */
    private byte[] generatePersonListPdf(String docTitle, List<PersonRow> rows) throws IOException {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = openDocument(baos);
            addDocumentHeader(document, docTitle);

            for (int i = 0; i < rows.size(); i++) {
                PersonRow r = rows.get(i);
                if (i > 0) {
                    addSeparator(document);
                }
                PdfPTable table = createTable();
                addRow(table, "UUID",     r.id());
                addRow(table, "Name",     strBlank(r.name()));
                addRow(table, "Surname",  strBlank(r.surname()));
                addRow(table, "Nickname", strBlank(r.nickname()));
                addRow(table, "Email",    strBlank(r.email()));
                addRow(table, "Phone",    strBlank(r.phone()));
                addRow(table, "Address",  strBlank(r.address()));
                addRow(table, "Notes",    strBlank(r.notes()));
                document.add(table);
            }

            document.close();
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new IOException("Failed to generate '" + docTitle + "' PDF", e);
        }
    }

    /**
     * Opens an A4 {@link Document} backed by the given output stream.
     * The caller is responsible for calling {@link Document#close()}.
     */
    private Document openDocument(ByteArrayOutputStream baos) throws DocumentException {
        Document document = new Document(PageSize.A4, MARGIN_H, MARGIN_H, MARGIN_TOP, MARGIN_BOTTOM);
        PdfWriter.getInstance(document, baos);
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
