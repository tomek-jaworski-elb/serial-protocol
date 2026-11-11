package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.dto.tracks.ShipTrack;
import com.jaworski.serialprotocol.dto.tracks.TrackCoverage;
import com.jaworski.serialprotocol.dto.tracks.TrackPoint;
import com.jaworski.serialprotocol.dto.tracks.TrackQueryResult;
import com.jaworski.serialprotocol.service.tracks.InvalidTrackQueryException;
import com.jaworski.serialprotocol.service.tracks.TrackLogSource;
import com.jaworski.serialprotocol.service.tracks.TrackQueryBusyException;
import com.jaworski.serialprotocol.service.tracks.TrackQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The JSON API behind /tracks; the services are covered on their own, so they are mocked here. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class TracksControllerTest {

    private static final String FROM = "2026-01-15T14:00:00";
    private static final String TO = "2026-01-15T15:00:00";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrackQueryService queryService;

    @MockitoBean
    private TrackLogSource logSource;

    @Test
    void daysListsTheRecordedDaysAndTheDisplayZone() throws Exception {
        when(logSource.availableDays()).thenReturn(new TreeSet<>(Set.of(LocalDate.of(2026, 1, 14), LocalDate.of(2026, 1, 15))));

        mockMvc.perform(get("/tracks/api/days"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0]").value("2026-01-14"))
                .andExpect(jsonPath("$.days[1]").value("2026-01-15"))
                .andExpect(jsonPath("$.displayZone").value("Europe/Warsaw"));
    }

    @Test
    void pointsAreCompactArraysWithCoverage() throws Exception {
        var result = new TrackQueryResult(
                List.of(new ShipTrack(1, List.of(new TrackPoint(1768482000000L, 71.14f, -59.78f, 232.5))), new ShipTrack(2, List.of())),
                new TrackCoverage(true, LocalDate.of(2026, 1, 13), LocalDate.of(2026, 1, 15), 1768482000000L, 1768482000000L));
        when(queryService.query(LocalDateTime.parse(FROM), LocalDateTime.parse(TO), Set.of(1, 2))).thenReturn(result);

        mockMvc.perform(get("/tracks/api/points").param("from", FROM).param("to", TO).param("models", "1,2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ships[0].id").value(1))
                .andExpect(jsonPath("$.ships[0].points[0][0]").value(1768482000000L))
                .andExpect(jsonPath("$.ships[0].points[0][1]").value(71.14))
                .andExpect(jsonPath("$.ships[0].points[0][2]").value(-59.78))
                .andExpect(jsonPath("$.ships[0].points[0][3]").value(232.5))
                .andExpect(jsonPath("$.ships[1].points").isEmpty())
                .andExpect(jsonPath("$.coverage.logsFound").value(true))
                .andExpect(jsonPath("$.coverage.historyFrom").value("2026-01-13"))
                .andExpect(jsonPath("$.coverage.firstPoint").value(1768482000000L));
    }

    @Test
    void anInvalidQueryIs400WithAMessage() throws Exception {
        when(queryService.query(LocalDateTime.parse(FROM), LocalDateTime.parse(TO), Set.of(1)))
                .thenThrow(new InvalidTrackQueryException("The range lies in the future."));

        mockMvc.perform(get("/tracks/api/points").param("from", FROM).param("to", TO).param("models", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The range lies in the future."));
    }

    @Test
    void unreadableParametersAre400WithAMessage() throws Exception {
        mockMvc.perform(get("/tracks/api/points").param("from", "yesterday").param("to", TO).param("models", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
        mockMvc.perform(get("/tracks/api/points").param("from", FROM).param("to", TO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void aBusyServerIs503WithAMessage() throws Exception {
        when(queryService.query(LocalDateTime.parse(FROM), LocalDateTime.parse(TO), Set.of(1)))
                .thenThrow(new TrackQueryBusyException());

        mockMvc.perform(get("/tracks/api/points").param("from", FROM).param("to", TO).param("models", "1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("The server is busy with another track query, try again in a moment."));
    }

    // ---------------------------------------------------------------- POST /tracks/pdf

    private static MockMultipartFile jpeg() throws IOException {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(300, 420, BufferedImage.TYPE_INT_RGB), "jpg", out);
        return new MockMultipartFile("image", "tracks.jpg", "image/jpeg", out.toByteArray());
    }

    private static MockMultipartHttpServletRequestBuilder pdfRequest(MockMultipartFile image) {
        return multipart("/tracks/pdf").file(image)
                .param("from", FROM).param("to", TO).param("silhouettes", "20").param("ships", "1,2").param("widthMeters", "500");
    }

    @Test
    void thePdfIsServedInlineUnderANameFromTheRange() throws Exception {
        mockMvc.perform(pdfRequest(jpeg()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"tracks_2026-01-15_1400-1500.pdf\""));
    }

    @Test
    void aBadPdfRequestIs400WithPlainText() throws Exception {
        mockMvc.perform(pdfRequest(new MockMultipartFile("image", "tracks.png", "image/png", new byte[]{1, 2, 3})).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string("The map image is not a valid image."));
        mockMvc.perform(multipart("/tracks/pdf").param("from", FROM).param("to", TO).param("silhouettes", "20")
                        .param("ships", "1").param("widthMeters", "500").with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("The map image is missing."));
        mockMvc.perform(pdfRequest(jpeg()).param("ships", "99").with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void polishCaptainsArePrinted() throws Exception {
        mockMvc.perform(pdfRequest(jpeg()).param("captains", "{\"1\":\"Zażółć Gęślą\",\"2\":\" \"}").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
    }

    @Test
    void badCaptainsAre400WithPlainText() throws Exception {
        for (String captains : new String[]{"{not json", "[\"Jan\"]", "{\"1\":\"" + "x".repeat(121) + "\"}", "{\"5\":\"Jan\"}"}) {
            mockMvc.perform(pdfRequest(jpeg()).param("captains", captains).with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("text/plain"));
        }
    }

    @Test
    void thePdfNeedsTheCsrfToken() throws Exception {
        mockMvc.perform(pdfRequest(jpeg()))
                .andExpect(status().isForbidden());
    }
}
