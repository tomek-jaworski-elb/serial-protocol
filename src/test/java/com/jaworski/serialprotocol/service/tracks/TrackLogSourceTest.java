package com.jaworski.serialprotocol.service.tracks;

import com.jaworski.serialprotocol.configuration.TracksProperties;
import com.jaworski.serialprotocol.resources.Resources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static com.jaworski.serialprotocol.service.tracks.TrackLogFixtures.line;
import static com.jaworski.serialprotocol.service.tracks.TrackLogFixtures.writeGz;
import static com.jaworski.serialprotocol.service.tracks.TrackLogFixtures.writeLog;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrackLogSourceTest {

    @TempDir
    Path logs;

    private TrackLogSource source;

    @BeforeEach
    void setUp() {
        Resources resources = mock(Resources.class);
        when(resources.getLogFilePath()).thenReturn(logs.toString());
        when(resources.getTrackingLogFileName()).thenReturn("tracking");
        // UTC log zone, Warsaw display zone: a UTC day overlaps two Warsaw days
        source = new TrackLogSource(resources, new TracksProperties(ZoneOffset.UTC, ZoneId.of("Europe/Warsaw"), 10, 2));
    }

    @Test
    void findsTheActiveFileAndTheArchivesAndIgnoresTheRest() throws IOException {
        writeGz(logs.resolve("2026/01/tracking_13-01-2026.1.log.gz"), line("13-01-2026 10:00:00.000", 1));
        writeGz(logs.resolve("2026/01/tracking_13-01-2026.2.log.gz"), line("13-01-2026 20:00:00.000", 1));
        writeGz(logs.resolve("2026/01/tracking_14-01-2026.1.log.gz"), line("14-01-2026 10:00:00.000", 1));
        writeGz(logs.resolve("log_01-2026.1.log.gz"), "other log");
        Files.writeString(logs.resolve("2026/01/tracking_15-01-2026.1.log.gz.tmp"), "half");
        writeActive("15-01-2026 00:00:01.000", LocalDateTime.of(2026, 1, 15, 9, 0));

        assertThat(source.filesFor(LocalDate.of(2026, 1, 13), LocalDate.of(2026, 1, 13))).hasSize(2);
        assertThat(source.filesFor(LocalDate.of(2026, 1, 13), LocalDate.of(2026, 1, 15))).hasSize(4);
        assertThat(source.filesFor(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 15)))
                .extracting(f -> f.path().getFileName().toString()).containsExactly("tracking.log");
        assertThat(source.filesFor(LocalDate.of(2026, 1, 16), LocalDate.of(2026, 1, 20))).isEmpty();
    }

    @Test
    void theActiveFileCoversFromItsFirstLineToItsLastModification() throws IOException {
        writeActive("14-01-2026 22:00:00.000", LocalDateTime.of(2026, 1, 16, 1, 0));

        assertThat(source.filesFor(LocalDate.of(2026, 1, 14), LocalDate.of(2026, 1, 14))).hasSize(1);
        assertThat(source.filesFor(LocalDate.of(2026, 1, 16), LocalDate.of(2026, 1, 16))).hasSize(1);
        assertThat(source.filesFor(LocalDate.of(2026, 1, 13), LocalDate.of(2026, 1, 13))).isEmpty();
    }

    @Test
    void availableDaysAreInTheDisplayZone() throws IOException {
        writeGz(logs.resolve("2026/01/tracking_13-01-2026.1.log.gz"), line("13-01-2026 10:00:00.000", 1));

        // 13/01 00:00-24:00 UTC is 01:00 on the 13th to 01:00 on the 14th in Warsaw
        assertThat(source.availableDays()).containsExactly(LocalDate.of(2026, 1, 13), LocalDate.of(2026, 1, 14));
    }

    @Test
    void noLogsMeansNoFilesAndNoDays() {
        assertThat(source.availableDays()).isEmpty();
        assertThat(source.filesFor(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))).isEmpty();
    }

    @Test
    void readsPlainAndCompressedFiles() throws IOException {
        writeGz(logs.resolve("2026/01/tracking_14-01-2026.1.log.gz"), "a", "b");
        writeActive("15-01-2026 00:00:01.000", LocalDateTime.of(2026, 1, 15, 9, 0));

        assertThat(readAll(LocalDate.of(2026, 1, 14), LocalDate.of(2026, 1, 15))).hasSize(4).contains("a", "b");
    }

    @Test
    void aTruncatedArchiveYieldsWhatWasReadAndNoError() throws IOException {
        Path gz = logs.resolve("2026/01/tracking_14-01-2026.1.log.gz");
        writeGz(gz, "x".repeat(100_000) + "\n" + "y".repeat(100_000));
        byte[] bytes = Files.readAllBytes(gz);
        try (OutputStream out = Files.newOutputStream(gz)) {
            out.write(bytes, 0, bytes.length / 2);
        }

        assertThat(readAll(LocalDate.of(2026, 1, 14), LocalDate.of(2026, 1, 14))).hasSizeLessThanOrEqualTo(1);
    }

    @Test
    void anArchiveThatIsNotGzipIsSkipped() throws IOException {
        Files.createDirectories(logs.resolve("2026/01"));
        Files.writeString(logs.resolve("2026/01/tracking_14-01-2026.1.log.gz"), "not gzip at all");

        assertThat(readAll(LocalDate.of(2026, 1, 14), LocalDate.of(2026, 1, 14))).isEmpty();
    }

    @Test
    void aFileThatVanishesBeforeItIsOpenedIsSkipped() throws IOException {
        Path gz = logs.resolve("2026/01/tracking_14-01-2026.1.log.gz");
        writeGz(gz, "a");
        var files = source.filesFor(LocalDate.of(2026, 1, 14), LocalDate.of(2026, 1, 14));
        Files.delete(gz);

        List<String> read = new ArrayList<>();
        files.forEach(f -> source.read(f, read::add));

        assertThat(read).isEmpty();
    }

    private List<String> readAll(LocalDate first, LocalDate last) {
        List<String> read = new ArrayList<>();
        source.filesFor(first, last).forEach(f -> source.read(f, read::add));
        return read;
    }

    private void writeActive(String firstTime, LocalDateTime lastModified) throws IOException {
        Path active = logs.resolve("tracking.log");
        writeLog(active, line(firstTime, 1), line(firstTime, 2));
        Files.setLastModifiedTime(active, FileTime.from(lastModified.toInstant(ZoneOffset.UTC)));
    }
}
