package com.jaworski.serialprotocol.service.pdf;

import com.jaworski.serialprotocol.configuration.TracksProperties;
import com.jaworski.serialprotocol.dto.Models;
import com.jaworski.serialprotocol.dto.tracks.TrackPdfRequest;
import com.jaworski.serialprotocol.service.tracks.InvalidTrackQueryException;
import org.openpdf.text.Document;
import org.openpdf.text.Image;
import org.openpdf.text.Jpeg;
import org.openpdf.text.PageSize;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The A4 portrait page behind {@code /tracks/pdf}: a header, the map image the browser rendered, the
 * colour legend and a metric scale bar. The server owns the layout; the page only has to know the
 * size of the image box, see {@link #IMAGE_BOX_WIDTH_MM}.
 */
@Service
public class TrackPdfService {

    /**
     * The box the image is fitted into, in millimetres. The tracks page reads it (data-box-* on its
     * form) to render the image at the pixel size the box needs at 300 dpi, so change it here only.
     */
    public static final int IMAGE_BOX_WIDTH_MM = 190;
    public static final int IMAGE_BOX_HEIGHT_MM = 235;

    private static final float MM = 72f / 25.4f;
    private static final float PAGE_HEIGHT = PageSize.A4.getHeight();
    private static final float MARGIN = 10 * MM;
    private static final float IMAGE_TOP = 32 * MM;            // below the header
    private static final float SCALE_BAR_MAX_MM = 40;
    private static final int MAX_IMAGE_SIDE_PX = 5000;          // a 300 dpi A4 is 3508
    private static final int MAX_SILHOUETTE_PERIOD_S = 60;
    private static final int MAX_CAPTAIN_LENGTH = 120;
    private static final float LEGEND_LINE = 5 * MM;
    private static final float LEGEND_SWATCH = 3 * MM;
    private static final float LEGEND_INDENT = LEGEND_SWATCH + 2;   // text starts right of the swatch
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FILE_DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("HHmm");

    /** {@link Models} holds CSS colour names; the PDF needs RGB. */
    static final Map<String, Color> CSS_COLORS = Map.of(
            "orange", new Color(255, 165, 0),
            "blue", new Color(0, 0, 255),
            "purple", new Color(128, 0, 128),
            "green", new Color(0, 128, 0),
            "darkblue", new Color(0, 0, 139),
            "lightgray", new Color(211, 211, 211));

    private final TracksProperties properties;
    private final BaseFont regular;
    private final BaseFont bold;

    public TrackPdfService(TracksProperties properties) {
        this.properties = properties;
        try {
            regular = PdfReportService.loadBaseFont("DejaVuSans.ttf");
            bold = PdfReportService.loadBaseFont("DejaVuSans-Bold.ttf");
        } catch (IOException | org.openpdf.text.DocumentException e) {
            throw new IllegalStateException("The track PDF fonts cannot be loaded", e);
        }
    }

    /** {@code tracks_2026-11-11_1400-1500.pdf} */
    public static String fileName(TrackPdfRequest request) {
        return "tracks_" + FILE_DAY.format(request.from()) + "_" + FILE_TIME.format(request.from())
                + "-" + FILE_TIME.format(request.to()) + ".pdf";
    }

    /** @throws InvalidTrackQueryException when the request cannot make a page; the message is for the user */
    public byte[] create(TrackPdfRequest request) {
        Image image = readImage(request.image());
        validate(request);
        List<Models> ships = request.ships().stream().distinct().map(Models::fromId).toList();
        Legend legend = layoutLegend(ships, captains(request, ships));

        Document document = new Document(PageSize.A4);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();
            PdfContentByte canvas = writer.getDirectContent();
            drawHeader(canvas, request);
            // The legend is laid out first; the image takes the height left above it and the scale bar.
            float boxHeight = Math.min(IMAGE_BOX_HEIGHT_MM * MM, PAGE_HEIGHT - IMAGE_TOP - MARGIN - 12 * MM - legend.depth());
            float width = Math.min(IMAGE_BOX_WIDTH_MM * MM, boxHeight * image.getWidth() / image.getHeight());
            float imageBottom = drawImage(canvas, image, width);
            drawLegend(canvas, legend, imageBottom - 6 * MM);
            drawScaleBar(canvas, request.widthMeters(), width / MM, imageBottom - 12 * MM - legend.depth());
            document.close();
        } catch (org.openpdf.text.DocumentException e) {
            throw new IllegalStateException("The track PDF cannot be written", e);
        }
        return out.toByteArray();
    }

    private static Image readImage(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new InvalidTrackQueryException("The map image is missing.");
        }
        Image image;
        try {
            image = Image.getInstance(bytes);
        } catch (IOException | org.openpdf.text.BadElementException e) {
            throw new InvalidTrackQueryException("The map image is not a valid image.");
        }
        if (!(image instanceof Jpeg)) {
            throw new InvalidTrackQueryException("The map image must be a JPEG.");
        }
        if (image.getWidth() > MAX_IMAGE_SIDE_PX || image.getHeight() > MAX_IMAGE_SIDE_PX) {
            throw new InvalidTrackQueryException("The map image is larger than " + MAX_IMAGE_SIDE_PX + " px.");
        }
        return image;
    }

    private static void validate(TrackPdfRequest request) {
        if (request.from() == null || request.to() == null || !request.from().isBefore(request.to())) {
            throw new InvalidTrackQueryException("From must be earlier than To.");
        }
        if (request.ships() == null || request.ships().isEmpty()
                || request.ships().stream().anyMatch(id -> Models.fromId(id) == null)) {
            throw new InvalidTrackQueryException("Unknown or missing ship.");
        }
        if (request.silhouettes() < -1 || request.silhouettes() > MAX_SILHOUETTE_PERIOD_S) {
            throw new InvalidTrackQueryException("The silhouette period is out of range.");
        }
        if (!Double.isFinite(request.widthMeters()) || request.widthMeters() <= 0) { // NaN and Infinity too
            throw new InvalidTrackQueryException("The map width must be positive.");
        }
    }

    /** Printed ships only; NFC, one-line, trimmed, blank dropped. Names are personal data: they never reach a message. */
    private static Map<Integer, String> captains(TrackPdfRequest request, List<Models> ships) {
        Map<Integer, String> captains = new HashMap<>();
        if (request.captains() != null) {
            request.captains().forEach((id, name) -> {
                String clean = name == null ? "" : Normalizer.normalize(name, Normalizer.Form.NFC)
                        .replaceAll("[\\p{Cc}&&[^\\s]]", "").replaceAll("[\\p{Z}\\s]+", " ").strip();
                if (clean.length() > MAX_CAPTAIN_LENGTH) {
                    throw new InvalidTrackQueryException("A captain's name is longer than " + MAX_CAPTAIN_LENGTH + " characters.");
                }
                if (ships.stream().noneMatch(ship -> ship.getId() == id)) {
                    throw new InvalidTrackQueryException("A captain is given for a ship that is not printed.");
                }
                if (!clean.isEmpty()) {
                    captains.put(id, clean);
                }
            });
        }
        return captains;
    }

    // ---------------------------------------------------------------- layout

    private void drawHeader(PdfContentByte canvas, TrackPdfRequest request) {
        text(canvas, bold, 16, MARGIN, PAGE_HEIGHT - MARGIN - 12, "Ship tracks");
        text(canvas, regular, 10, MARGIN, PAGE_HEIGHT - MARGIN - 12 - 6 * MM, rangeText(request));
        text(canvas, regular, 10, MARGIN, PAGE_HEIGHT - MARGIN - 12 - 11 * MM, silhouetteText(request.silhouettes()));
    }

    /** "11/11/2026 14:00:00 – 15:00:00 (Europe/Warsaw)"; both dates when the range crosses midnight. */
    String rangeText(TrackPdfRequest request) {
        LocalDateTime from = request.from();
        LocalDateTime to = request.to();
        String end = from.toLocalDate().equals(to.toLocalDate()) ? TIME.format(to) : DATE_TIME.format(to);
        return DATE_TIME.format(from) + " – " + end + " (" + properties.displayZone().getId() + ")";
    }

    static String silhouetteText(int period) {
        return switch (period) {
            case -1 -> "Silhouettes hidden";
            case 0 -> "Silhouettes at every point";
            default -> "Silhouettes every " + period + " s";
        };
    }

    /** Fits the image into the box, keeping its ratio; returns the y of its bottom edge. */
    private float drawImage(PdfContentByte canvas, Image image, float width) throws org.openpdf.text.DocumentException {
        float height = width * image.getHeight() / image.getWidth();
        float x = (PageSize.A4.getWidth() - width) / 2;
        float y = PAGE_HEIGHT - IMAGE_TOP - height;
        canvas.addImage(image, width, 0, 0, height, x, y);
        canvas.setLineWidth(0.5f);
        canvas.setColorStroke(Color.GRAY);
        canvas.rectangle(x, y, width, height);
        canvas.stroke();
        return y;
    }

    /** One legend entry: where it starts and its lines of text, below the first row's baseline. */
    private record LegendEntry(Color color, float x, float down, List<String> lines) {
    }

    /** {@code depth} is the distance from the first baseline to the last. */
    private record Legend(List<LegendEntry> entries, float depth) {
    }

    /**
     * "Ship" or "Ship — Captain" per ship, filled row by row. An entry wider than half the row takes a
     * row of its own and wraps; nothing is ever cut off.
     */
    private Legend layoutLegend(List<Models> ships, Map<Integer, String> captains) {
        float right = PageSize.A4.getWidth() - MARGIN;
        float rowWidth = right - MARGIN;
        List<LegendEntry> entries = new ArrayList<>();
        float x = MARGIN;
        float row = 0;       // baseline of the current row, below the first one
        float depth = 0;     // baseline of the last line so far
        for (Models ship : ships) {
            String text = captains.containsKey(ship.getId()) ? ship.getName() + " — " + captains.get(ship.getId()) : ship.getName();
            float width = LEGEND_INDENT + regular.getWidthPoint(text, 9) + 5 * MM;
            boolean wrapped = width > rowWidth / 2;
            if (!entries.isEmpty() && (wrapped || x + width > right)) {
                row = depth + LEGEND_LINE;
                x = MARGIN;
            }
            List<String> lines = wrapped ? wrap(text, rowWidth - LEGEND_INDENT) : List.of(text);
            entries.add(new LegendEntry(CSS_COLORS.get(ship.getColor()), x, row, lines));
            depth = row + (lines.size() - 1) * LEGEND_LINE;
            x = wrapped ? right : x + width;   // a wrapped entry owns its row
        }
        return new Legend(entries, depth);
    }

    /** Greedy word wrap; a word wider than the row is broken inside, between whole characters. */
    private List<String> wrap(String text, float width) {
        List<String> lines = new ArrayList<>();
        String line = "";
        for (String word : text.split(" ")) {
            while (regular.getWidthPoint(word, 9) > width) {
                int cut = word.offsetByCodePoints(0, 1);
                while (cut < word.length()) {
                    int next = word.offsetByCodePoints(cut, 1);
                    if (regular.getWidthPoint(word.substring(0, next), 9) > width) {
                        break;
                    }
                    cut = next;
                }
                if (!line.isEmpty()) {
                    lines.add(line);
                    line = "";
                }
                lines.add(word.substring(0, cut));
                word = word.substring(cut);
            }
            String joined = line.isEmpty() ? word : line + " " + word;
            if (line.isEmpty() || regular.getWidthPoint(joined, 9) <= width) {
                line = joined;
            } else {
                lines.add(line);
                line = word;
            }
        }
        lines.add(line);
        return lines;
    }

    private void drawLegend(PdfContentByte canvas, Legend legend, float y) {
        for (LegendEntry entry : legend.entries()) {
            float top = y - entry.down();
            canvas.setColorFill(entry.color());
            canvas.rectangle(entry.x(), top - 1, LEGEND_SWATCH, LEGEND_SWATCH);
            canvas.fillStroke();
            for (int i = 0; i < entry.lines().size(); i++) {
                text(canvas, regular, 9, entry.x() + LEGEND_INDENT, top - i * LEGEND_LINE, entry.lines().get(i));
            }
        }
    }

    private void drawScaleBar(PdfContentByte canvas, double widthMeters, float printedWidthMm, float y) {
        double metres = scaleBarMeters(widthMeters, printedWidthMm);
        float length = (float) (metres / widthMeters * printedWidthMm * MM);
        canvas.setColorStroke(Color.BLACK);
        canvas.setLineWidth(0.75f);
        canvas.moveTo(MARGIN, y + 3);
        canvas.lineTo(MARGIN, y);
        canvas.lineTo(MARGIN + length, y);
        canvas.lineTo(MARGIN + length, y + 3);
        canvas.stroke();
        text(canvas, regular, 9, MARGIN + length + 4, y, metres >= 1000 ? plain(metres / 1000) + " km" : plain(metres) + " m");
    }

    /** The longest 1, 2 or 5 x 10^n metres that is drawn no wider than {@link #SCALE_BAR_MAX_MM}. */
    static double scaleBarMeters(double widthMeters, double printedWidthMm) {
        double limit = SCALE_BAR_MAX_MM * widthMeters / printedWidthMm;
        double decade = Math.pow(10, Math.floor(Math.log10(limit)));
        for (double step : new double[]{5, 2, 1}) {
            if (step * decade <= limit) {
                return step * decade;
            }
        }
        return decade; // not reached: 1 * decade <= limit by construction
    }

    private static String plain(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static void text(PdfContentByte canvas, BaseFont font, float size, float x, float y, String text) {
        canvas.beginText();
        canvas.setColorFill(Color.BLACK);
        canvas.setFontAndSize(font, size);
        canvas.showTextAligned(PdfContentByte.ALIGN_LEFT, text, x, y, 0);
        canvas.endText();
    }
}
