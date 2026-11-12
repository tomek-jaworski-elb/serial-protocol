package com.jaworski.serialprotocol.service.tracks;

import com.jaworski.serialprotocol.configuration.TracksProperties;
import com.jaworski.serialprotocol.dto.tracks.ShipTrack;
import com.jaworski.serialprotocol.dto.tracks.TrackPoint;
import com.jaworski.serialprotocol.dto.tracks.TrackQueryResult;
import com.jaworski.serialprotocol.resources.Resources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.jaworski.serialprotocol.service.tracks.TrackLogFixtures.line;
import static com.jaworski.serialprotocol.service.tracks.TrackLogFixtures.writeGz;
import static com.jaworski.serialprotocol.service.tracks.TrackLogFixtures.writeLog;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Log zone UTC, display zone Warsaw (UTC+1 in January). */
class TrackQueryServiceTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final Set<Integer> WARTA = Set.of(1);

    @TempDir
    Path logs;

    private TracksProperties properties;
    private TrackLogSource source;
    private TrackQueryService service;

    @BeforeEach
    void setUp() {
        Resources resources = mock(Resources.class);
        when(resources.getLogFilePath()).thenReturn(logs.toString());
        when(resources.getTrackingLogFileName()).thenReturn("tracking");
        properties = new TracksProperties(ZoneOffset.UTC, WARSAW, 10, 2);
        source = new TrackLogSource(resources, properties);
        service = new TrackQueryService(source, properties);
    }

    @Test
    void theRangeIncludesItsFirstAndLastSecondAndNothingOutside() throws IOException {
        writeActive("15-01-2026 12:59:59.999", "15-01-2026 13:00:00.000", "15-01-2026 14:00:00.999", "15-01-2026 14:00:01.000");

        // 14:00:00-15:00:00 in Warsaw is 13:00:00-14:00:00 in UTC
        TrackQueryResult result = service.query(warsaw("2026-01-15T14:00:00"), warsaw("2026-01-15T15:00:00"), WARTA);

        assertThat(times(result.ships().getFirst())).containsExactly(utc("2026-01-15T13:00:00"), utc("2026-01-15T14:00:00").plusMillis(999));
        assertThat(result.coverage().firstPoint()).isEqualTo(utc("2026-01-15T13:00:00").toEpochMilli());
        assertThat(result.coverage().lastPoint()).isEqualTo(utc("2026-01-15T14:00:00").plusMillis(999).toEpochMilli());
    }

    @Test
    void aRangeAcrossMidnightReadsBothFilesInTimeOrder() throws IOException {
        writeGz(logs.resolve("2026/01/tracking_14-01-2026.1.log.gz"),
                line("14-01-2026 23:40:00.000", 1), line("14-01-2026 23:50:00.000", 1));
        writeActive("15-01-2026 00:10:00.000", "15-01-2026 00:20:00.000");

        // 00:30-01:30 Warsaw on the 15th is 23:30 on the 14th to 00:30 on the 15th in UTC
        TrackQueryResult result = service.query(warsaw("2026-01-15T00:30:00"), warsaw("2026-01-15T01:30:00"), WARTA);

        assertThat(times(result.ships().getFirst())).containsExactly(
                utc("2026-01-14T23:40:00"), utc("2026-01-14T23:50:00"), utc("2026-01-15T00:10:00"), utc("2026-01-15T00:20:00"));
    }

    @Test
    void onlyTheRequestedShipsAndOnlyValidFramesAreReturned() throws IOException {
        writeLog(logs.resolve("tracking.log"),
                line("15-01-2026 13:10:00.000", 1), line("15-01-2026 13:10:01.000", 2),
                line("15-01-2026 13:10:02.000", 1, "1.0", "2.0", "3.0", false), line("15-01-2026 13:10:03.000", 1, "-5.5", "6.5", "90.0", true),
                "15-01-2026 13:10:04.000 - Translated message: ModelTrackDTO(modelName=1, broken");
        touch("15-01-2026 14:00:00.000");

        TrackQueryResult result = service.query(warsaw("2026-01-15T14:00:00"), warsaw("2026-01-15T15:00:00"), Set.of(1, 6));

        assertThat(result.ships()).extracting(ShipTrack::id).containsExactly(1, 6);
        assertThat(result.ships().get(0).points()).hasSize(2)
                .last().isEqualTo(new TrackPoint(utc("2026-01-15T13:10:03").toEpochMilli(), -5.5f, 6.5f, 90.0));
        assertThat(result.ships().get(1).points()).isEmpty();
        assertThat(result.coverage().unreadableLines()).isEqualTo(1);
    }

    @Test
    void aRangeOfExactly24HoursIsAllowedButNotOneSecondMore() {
        assertThat(service.query(warsaw("2026-01-14T00:00:00"), warsaw("2026-01-15T00:00:00"), WARTA)).isNotNull();

        assertThatThrownBy(() -> service.query(warsaw("2026-01-14T00:00:00"), warsaw("2026-01-15T00:00:01"), WARTA))
                .isInstanceOf(InvalidTrackQueryException.class).hasMessageContaining("24 hours");
    }

    @Test
    void theStartMustBeBeforeTheEnd() {
        assertThatThrownBy(() -> service.query(warsaw("2026-01-15T10:00:00"), warsaw("2026-01-15T10:00:00"), WARTA))
                .isInstanceOf(InvalidTrackQueryException.class);
        assertThatThrownBy(() -> service.query(warsaw("2026-01-15T11:00:00"), warsaw("2026-01-15T10:00:00"), WARTA))
                .isInstanceOf(InvalidTrackQueryException.class);
    }

    @Test
    void aRangeStartingInTheFutureIsRejected() {
        LocalDateTime tomorrow = LocalDateTime.now(WARSAW).plusDays(1);

        assertThatThrownBy(() -> service.query(tomorrow, tomorrow.plusHours(1), WARTA))
                .isInstanceOf(InvalidTrackQueryException.class).hasMessageContaining("future");
    }

    @Test
    void aRangeThatEndsInTheFutureIsAllowed() {
        LocalDateTime now = LocalDateTime.now(WARSAW);

        assertThat(service.query(now.minusHours(1), now.plusHours(1), WARTA)).isNotNull();
    }

    @Test
    void shipsMustBeKnownAndNotEmpty() {
        assertThatThrownBy(() -> service.query(warsaw("2026-01-15T10:00:00"), warsaw("2026-01-15T11:00:00"), Set.of()))
                .isInstanceOf(InvalidTrackQueryException.class);
        assertThatThrownBy(() -> service.query(warsaw("2026-01-15T10:00:00"), warsaw("2026-01-15T11:00:00"), Set.of(1, 99)))
                .isInstanceOf(InvalidTrackQueryException.class);
    }

    @Test
    void withNoLogsAtAllTheCoverageSaysSo() {
        TrackQueryResult result = service.query(warsaw("2026-01-15T10:00:00"), warsaw("2026-01-15T11:00:00"), WARTA);

        assertThat(result.ships()).singleElement().satisfies(ship -> assertThat(ship.points()).isEmpty());
        assertThat(result.coverage().logsFound()).isFalse();
        assertThat(result.coverage().historyFrom()).isNull();
        assertThat(result.coverage().historyTo()).isNull();
        assertThat(result.coverage().firstPoint()).isNull();
    }

    @Test
    void withLogsButNoPointsTheCoverageHasTheHistory() throws IOException {
        writeActive("15-01-2026 13:10:00.000");
        writeGz(logs.resolve("2026/01/tracking_13-01-2026.1.log.gz"), line("13-01-2026 10:00:00.000", 1));

        TrackQueryResult result = service.query(warsaw("2026-01-15T16:00:00"), warsaw("2026-01-15T17:00:00"), WARTA);

        assertThat(result.ships().getFirst().points()).isEmpty();
        assertThat(result.coverage().logsFound()).isTrue();
        assertThat(result.coverage().historyFrom()).isEqualTo(LocalDate.of(2026, 1, 13));
        // the UTC day of the 15th reaches into the 16th in Warsaw, a false positive of one hour
        assertThat(result.coverage().historyTo()).isEqualTo(LocalDate.of(2026, 1, 16));
        assertThat(result.coverage().firstPoint()).isNull();
        assertThat(result.coverage().lastPoint()).isNull();
    }

    @Test
    void aQueryBeyondTheLimitOfConcurrentScansIsRefusedAtOnce() throws Exception {
        TrackLogSource blocking = mock(TrackLogSource.class);
        CountDownLatch scanning = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(blocking.filesFor(any(), any())).thenAnswer(invocation -> {
            scanning.countDown();
            release.await(10, TimeUnit.SECONDS);
            return List.of();
        });
        when(blocking.availableDays()).thenReturn(new TreeSet<>());
        var single = new TrackQueryService(blocking, new TracksProperties(ZoneOffset.UTC, WARSAW, 10, 1));
        AtomicReference<Object> first = new AtomicReference<>();
        Thread scan = new Thread(() -> first.set(single.query(warsaw("2026-01-15T10:00:00"), warsaw("2026-01-15T11:00:00"), WARTA)));
        scan.start();
        assertThat(scanning.await(10, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> single.query(warsaw("2026-01-15T10:00:00"), warsaw("2026-01-15T11:00:00"), WARTA))
                .isInstanceOf(TrackQueryBusyException.class);

        release.countDown();
        scan.join(10_000);
        assertThat(first.get()).isNotNull();
        // the permit is back
        assertThat(single.query(warsaw("2026-01-15T10:00:00"), warsaw("2026-01-15T11:00:00"), WARTA)).isNotNull();
    }

    /** Writes the active log with one Warta line per time and dates it after the last of them. */
    private void writeActive(String... times) throws IOException {
        String[] lines = new String[times.length];
        for (int i = 0; i < times.length; i++) {
            lines[i] = line(times[i], 1);
        }
        writeLog(logs.resolve("tracking.log"), lines);
        touch(times[times.length - 1]);
    }

    private void touch(String logTime) throws IOException {
        LocalDateTime time = LocalDateTime.parse(logTime, DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss.SSS"));
        Files.setLastModifiedTime(logs.resolve("tracking.log"), FileTime.from(time.toInstant(ZoneOffset.UTC)));
    }

    private static LocalDateTime warsaw(String isoTime) {
        return LocalDateTime.parse(isoTime);
    }

    private static Instant utc(String isoTime) {
        return LocalDateTime.parse(isoTime).toInstant(ZoneOffset.UTC);
    }

    private static List<Instant> times(ShipTrack ship) {
        return ship.points().stream().map(point -> Instant.ofEpochMilli(point.time())).toList();
    }
}
