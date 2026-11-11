package com.jaworski.serialprotocol.service.pdf;

import com.jaworski.serialprotocol.configuration.TracksProperties;
import com.jaworski.serialprotocol.dto.Models;
import com.jaworski.serialprotocol.dto.tracks.TrackPdfRequest;
import com.jaworski.serialprotocol.service.tracks.InvalidTrackQueryException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

class TrackPdfServiceTest {

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 11, 11, 14, 0, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 11, 11, 15, 0, 0);

    private final TrackPdfService service = new TrackPdfService(new TracksProperties(null, ZoneId.of("Europe/Warsaw"), 10, 2));

    static byte[] image(String format, int width, int height) throws IOException {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }

    private static TrackPdfRequest request(byte[] image, int silhouettes, List<Integer> ships) {
        return new TrackPdfRequest(image, FROM, TO, silhouettes, ships, 500, null);
    }

    @Test
    void everyModelColourHasAnRgbEntry() {
        for (Models model : Models.values()) {
            assertThat(TrackPdfService.CSS_COLORS).as(model.getColor()).containsKey(model.getColor());
        }
    }

    @Test
    void theFileNameCarriesTheRange() {
        assertThat(TrackPdfService.fileName(request(null, 20, List.of(1)))).isEqualTo("tracks_2026-11-11_1400-1500.pdf");
    }

    @Test
    void theHeaderNamesTheRangeTheSilhouettesAndEveryShipOnOneA4Page() throws IOException {
        byte[] pdf = service.create(request(image("jpg", 600, 850), 20, List.of(1, 5)));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            PDPage page = document.getPage(0);
            assertThat(page.getMediaBox().getWidth()).isEqualTo(595.28f, offset(0.5f));
            assertThat(page.getMediaBox().getHeight()).isEqualTo(841.89f, offset(0.5f));
            assertThat(page.getResources().getXObjectNames()).hasSize(1);

            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Ship tracks", "11/11/2026 14:00:00 – 15:00:00 (Europe/Warsaw)",
                    "Silhouettes every 20 s", "Warta", "Kołobrzeg", "100 m");
            assertThat(text).doesNotContain("Blue Lady");
        }
    }

    @Test
    void theSilhouetteSettingHasThreeWordings() {
        assertThat(TrackPdfService.silhouetteText(0)).isEqualTo("Silhouettes at every point");
        assertThat(TrackPdfService.silhouetteText(-1)).isEqualTo("Silhouettes hidden");
        assertThat(TrackPdfService.silhouetteText(7)).isEqualTo("Silhouettes every 7 s");
    }

    @Test
    void aRangeAcrossMidnightNamesBothDates() {
        var request = new TrackPdfRequest(null, FROM, LocalDateTime.of(2026, 11, 12, 1, 0, 0), 0, List.of(1), 500, null);

        assertThat(service.rangeText(request)).isEqualTo("11/11/2026 14:00:00 – 12/11/2026 01:00:00 (Europe/Warsaw)");
    }

    @Test
    void theScaleBarIsARoundLengthNoWiderThanFortyMillimetres() {
        assertThat(TrackPdfService.scaleBarMeters(1000, 100)).isEqualTo(200);   // 400 m fit in 40 mm
        assertThat(TrackPdfService.scaleBarMeters(500, 190)).isEqualTo(100);    // 105 m
        assertThat(TrackPdfService.scaleBarMeters(2375, 190)).isEqualTo(500);   // exactly 500 m
        assertThat(TrackPdfService.scaleBarMeters(19, 190)).isEqualTo(2);       // 4 m
        assertThat(TrackPdfService.scaleBarMeters(5, 190)).isEqualTo(1);        // 1.05 m
    }

    @Test
    void badRequestsAreRefusedWithAMessage() throws IOException {
        byte[] jpeg = image("jpg", 600, 850);
        assertRefused(request(null, 20, List.of(1)), "image is missing");
        assertRefused(request(image("png", 10, 10), 20, List.of(1)), "must be a JPEG");
        assertRefused(request(new byte[]{1, 2, 3}, 20, List.of(1)), "not a valid image");
        assertRefused(request(image("jpg", 5001, 10), 20, List.of(1)), "larger than");
        assertRefused(new TrackPdfRequest(jpeg, TO, FROM, 20, List.of(1), 500, null), "earlier");
        assertRefused(request(jpeg, 20, List.of(1, 99)), "ship");
        assertRefused(request(jpeg, 20, List.of()), "ship");
        assertRefused(request(jpeg, 61, List.of(1)), "silhouette");
        assertRefused(new TrackPdfRequest(jpeg, FROM, TO, 20, List.of(1), 0, null), "width");
    }

    private static final String LONG_NAME = "Zażółć gęślą jaźń ".repeat(7).substring(0, 120);

    private String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).as("one A4 page").isEqualTo(1);
            return new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
        }
    }

    private TrackPdfRequest withCaptains(Map<Integer, String> captains, List<Integer> ships) throws IOException {
        return new TrackPdfRequest(image("jpg", 600, 850), FROM, TO, 20, ships, 500, captains);
    }

    @Test
    void theLegendPrintsShipAndCaptainWithPolishLetters() throws IOException {
        String text = text(service.create(withCaptains(Map.of(1, "Zażółć Gęślą-Jaźń"), List.of(1, 5))));

        assertThat(text).contains("Warta — Zażółć Gęślą-Jaźń", "Kołobrzeg");
        assertThat(text).doesNotContain("Kołobrzeg —");
    }

    @Test
    void sixLongCaptainsWrapAndStillMakeOneA4PageWithEveryNameInFull() throws IOException {
        Map<Integer, String> captains = new HashMap<>();
        for (Models ship : Models.values()) {
            captains.put(ship.getId(), LONG_NAME);
        }
        String text = text(service.create(withCaptains(captains, List.of(1, 2, 3, 4, 5, 6))));

        assertThat(LONG_NAME).hasSize(120);
        assertThat(text).contains(LONG_NAME);
        assertThat(text.split(Pattern.quote(LONG_NAME), -1)).as("one per ship").hasSize(7);
        assertThat(text).contains("100 m");
    }

    @Test
    void aWordWiderThanTheRowIsBrokenNotCut() throws IOException {
        String word = "Ż".repeat(120);

        assertThat(text(service.create(withCaptains(Map.of(1, word), List.of(1)))).replace(" ", "")).contains(word);
    }

    @Test
    void captainsAreNormalised() throws IOException {
        // "e" + combining acute becomes the single é; the tab and newline separate words; NUL and the bell vanish
        String text = text(service.create(withCaptains(Map.of(1, "  Jose\u0301\t\n  Ko\u0000wal\u0007ski  ", 2, " \t "), List.of(1, 2))));

        assertThat(text).contains("Warta — José Kowalski").doesNotContain("Blue Lady —");
    }

    @Test
    void theLengthLimitCountsTheNormalisedName() throws IOException {
        String name = "Jan ".repeat(29) + "Jank";   // 120 characters
        String padded = "  " + name.replace(" ", " \u0000 ") + " ";
        assertThat(text(service.create(withCaptains(Map.of(1, padded), List.of(1))))).contains(name);
    }

    @Test
    void captainsAreRefused() throws IOException {
        assertRefused(withCaptains(Map.of(1, "x".repeat(121)), List.of(1)), "longer than 120");
        assertRefused(withCaptains(Map.of(2, "Jan"), List.of(1)), "not printed");
    }

    private void assertRefused(TrackPdfRequest request, String message) {
        assertThatThrownBy(() -> service.create(request)).isInstanceOf(InvalidTrackQueryException.class).hasMessageContaining(message);
    }
}
