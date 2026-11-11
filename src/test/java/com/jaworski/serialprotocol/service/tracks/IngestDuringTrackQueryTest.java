package com.jaworski.serialprotocol.service.tracks;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.FileAppender;
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
 * the same tracking file is open, and every one is still published and appended.
 *
 * <p>The tracking file is attached to the translator's logger here with the pattern of
 * {@code SAVE-TRANSMISSION-FILE}, because the test logging config has no file appender. The
 * rollover of logback under a reader on Windows cannot be reproduced from a test; that is the
 * manual check of stage 6.</p>
 */
class IngestDuringTrackQueryTest {

    private static final byte[] WARTA = {119, 49, 8, -69, 8, -69, 0, 6, 0, 8, -69, -123, 1, 50, 73, -114, 66, 4, 27, 111, 66, 111, 0, 3, 10, 13, 10};
    private static final int FRAMES = 200;

    @TempDir
    Path logs;

    private final Logger translatorLog = (Logger) LoggerFactory.getLogger(MessageTranslator.class);
    private FileAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender;

    @BeforeEach
    void attachTrackingFile() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%d{dd-MM-yyyy HH:mm:ss.SSS} - %msg%n");
        encoder.start();
        appender = new FileAppender<>();
        appender.setContext(context);
        appender.setFile(logs.resolve("tracking.log").toString());
        appender.setEncoder(encoder);
        appender.start();
        translatorLog.addAppender(appender);
    }

    @AfterEach
    void detachTrackingFile() {
        translatorLog.detachAppender(appender);
        appender.stop();
    }

    @Test
    void everyFrameIsPublishedAndLoggedWhileTheLogIsBeingRead() throws IOException {
        WebSocketPublisher publisher = mock(WebSocketPublisher.class);
        MassageValuesOperations values = new MassageValuesOperations();
        var ingest = new FrameIngestService(publisher, new JsonMapperService(),
                new MessageTranslator(new MessageCommon(values), new MessageLadyMarie(values)));
        Resources resources = mock(Resources.class);
        when(resources.getLogFilePath()).thenReturn(logs.toString());
        when(resources.getTrackingLogFileName()).thenReturn("tracking");
        ZoneId zone = ZoneId.systemDefault();
        var properties = new TracksProperties(zone, zone, 10, 2);
        var source = new TrackLogSource(resources, properties);

        ingest.ingest(WARTA, "test");
        LocalDate today = LocalDate.now(zone);
        var files = source.filesFor(today.minusDays(1), today.plusDays(1));
        assertThat(files).hasSize(1);

        // the reader holds the file open while the rest of the frames arrive (it even sees them)
        AtomicInteger lines = new AtomicInteger();
        source.read(files.getFirst(), line -> {
            if (lines.getAndIncrement() == 0) {
                for (int i = 0; i < FRAMES; i++) {
                    ingest.ingest(WARTA, "test");
                }
            }
        });

        verify(publisher, times(FRAMES + 1)).publishForAllClients(anyString(), eq(SessionType.JSON));
        assertThat(Files.readAllLines(logs.resolve("tracking.log"))).hasSize(FRAMES + 1);

        // and the real lines written by the translator are what the query reads back
        LocalDateTime now = LocalDateTime.now(zone);
        TrackQueryResult result = new TrackQueryService(source, properties).query(now.minusMinutes(5), now, Set.of(1));
        assertThat(result.ships().getFirst().points()).hasSize(FRAMES + 1);
        assertThat(result.coverage().logsFound()).isTrue();
        assertThat(List.of(result.coverage().historyFrom())).containsExactly(today);
    }
}
