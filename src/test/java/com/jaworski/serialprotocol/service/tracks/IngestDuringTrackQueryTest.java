package com.jaworski.serialprotocol.service.tracks;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.util.FileSize;
import com.jaworski.serialprotocol.configuration.TracksProperties;
import com.jaworski.serialprotocol.dto.tracks.TrackQueryResult;
import com.jaworski.serialprotocol.resources.Resources;
import com.jaworski.serialprotocol.serial.SessionType;
import com.jaworski.serialprotocol.service.WebSocketPublisher;
import com.jaworski.serialprotocol.service.ingest.FrameIngestService;
import com.jaworski.serialprotocol.service.utils.JsonMapperService;
import com.jaworski.serialprotocol.service.utils.MassageValuesOperations;
import com.jaworski.serialprotocol.service.utils.MessageTranslator;
import com.jaworski.serialprotocol.service.utils.impl.MessageCommon;
import com.jaworski.serialprotocol.service.utils.impl.MessageLadyMarie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Replay must never hold up logging: frames go through the real pipeline while a replay read of
 * the same tracking file is open, and every one is still published and appended, across a real
 * logback size rollover too.
 *
 * <p>The tracking file is attached to the translator's logger here with the pattern and the rolling
 * policy of {@code SAVE-TRANSMISSION-FILE}, because the test logging config has no file appender.
 * Since logback 1.5.8 the size trigger counts the bytes written and is not throttled, so a small
 * {@code maxFileSize} rolls at a predictable line; the archives are compressed asynchronously and
 * stopping the appender waits for that.</p>
 */
class IngestDuringTrackQueryTest {

    private static final byte[] WARTA = {119, 49, 8, -69, 8, -69, 0, 6, 0, 8, -69, -123, 1, 50, 73, -114, 66, 4, 27, 111, 66, 111, 0, 3, 10, 13, 10};
    private static final int FRAMES = 200;

    @TempDir
    Path logs;

    private final Logger translatorLog = (Logger) LoggerFactory.getLogger(MessageTranslator.class);
    private RollingFileAppender<ILoggingEvent> appender;
    private WebSocketPublisher publisher;
    private FrameIngestService ingest;
    private TracksProperties properties;
    private TrackLogSource source;

    @BeforeEach
    void setUp() {
        publisher = mock(WebSocketPublisher.class);
        MassageValuesOperations values = new MassageValuesOperations();
        ingest = new FrameIngestService(publisher, new JsonMapperService(),
                new MessageTranslator(new MessageCommon(values), new MessageLadyMarie(values)));
        Resources resources = mock(Resources.class);
        when(resources.getLogFilePath()).thenReturn(logs.toString());
        when(resources.getTrackingLogFileName()).thenReturn("tracking");
        ZoneId zone = ZoneId.systemDefault();
        properties = new TracksProperties(zone, zone, 10, 2);
        source = new TrackLogSource(resources, properties);
    }

    private void attachTrackingFile(String maxFileSize) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%d{dd-MM-yyyy HH:mm:ss.SSS} - %msg%n");
        encoder.start();
        appender = new RollingFileAppender<>();
        appender.setContext(context);
        appender.setFile(logs.resolve("tracking.log").toString());
        appender.setEncoder(encoder);
        var policy = new SizeAndTimeBasedRollingPolicy<ILoggingEvent>();
        policy.setContext(context);
        policy.setParent(appender);
        policy.setFileNamePattern(logs + "/%d{yyyy/MM,aux}/tracking_%d{dd-MM-yyyy}.%i.log.gz");
        policy.setMaxFileSize(FileSize.valueOf(maxFileSize));
        policy.start();
        appender.setRollingPolicy(policy);
        appender.start();
        translatorLog.addAppender(appender);
    }

    @AfterEach
    void detachTrackingFile() {
        translatorLog.detachAppender(appender);
        appender.stop();
    }

    /** One frame, then the rest of them while a replay read of the active file is still open. */
    private void ingestWhileReading() {
        ingest.ingest(WARTA, "test");
        LocalDate today = LocalDate.now(properties.logZone());
        var files = source.filesFor(today.minusDays(1), today.plusDays(1));
        assertThat(files).hasSize(1);
        AtomicInteger lines = new AtomicInteger();
        source.read(files.getFirst(), line -> {
            if (lines.getAndIncrement() == 0) {
                for (int i = 0; i < FRAMES; i++) {
                    ingest.ingest(WARTA, "test");
                }
            }
        });
        verify(publisher, times(FRAMES + 1)).publishForAllClients(anyString(), eq(SessionType.JSON));
    }

    private int queriedPoints() {
        LocalDateTime now = LocalDateTime.now(properties.displayZone());
        TrackQueryResult result = new TrackQueryService(source, properties).query(now.minusMinutes(5), now, Set.of(1));
        assertThat(result.coverage().logsFound()).isTrue();
        return result.ships().getFirst().points().size();
    }

    @Test
    void everyFrameIsPublishedAndLoggedWhileTheLogIsBeingRead() throws IOException {
        attachTrackingFile("100MB");

        ingestWhileReading();

        assertThat(Files.readAllLines(logs.resolve("tracking.log"))).hasSize(FRAMES + 1);
        // and the real lines written by the translator are what the query reads back
        assertThat(queriedPoints()).isEqualTo(FRAMES + 1);
        assertThat(List.of(source.availableDays().first())).containsExactly(LocalDate.now(properties.logZone()));
    }

    @Test
    void noFrameIsLostWhenTheLogRollsOverUnderTheReader() throws IOException {
        attachTrackingFile("4KB");

        ingestWhileReading();
        appender.stop(); // waits for the archives to finish compressing

        try (Stream<Path> files = Files.walk(logs)) {
            assertThat(files.filter(file -> file.getFileName().toString().endsWith(".log.gz"))).isNotEmpty();
        }
        assertThat(Files.size(logs.resolve("tracking.log"))).isLessThan(4096 + 512);
        assertThat(queriedPoints()).isEqualTo(FRAMES + 1);
    }
}
