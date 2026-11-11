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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
