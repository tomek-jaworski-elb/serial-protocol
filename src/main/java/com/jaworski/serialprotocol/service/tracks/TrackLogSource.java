package com.jaworski.serialprotocol.service.tracks;

import com.jaworski.serialprotocol.configuration.TracksProperties;
import com.jaworski.serialprotocol.resources.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * Finds and reads the tracking log: the active {@code <logs>/<name>.log} and the archives
 * {@code <logs>/yyyy/MM/<name>_dd-MM-yyyy.N.log.gz} (see {@code SAVE-TRANSMISSION-FILE} in
 * logback-spring.xml). Read-only.
 *
 * <p>Files are opened through NIO only. On Windows that shares read, write and delete, so a reader
 * never keeps logback from renaming the file at rollover; {@code FileInputStream} would. Every
 * stream lives for one {@link #read} call. A file that vanishes, a {@code .gz} that logback is
 * still writing or a half-written last line is skipped with a warning, because the rollover
 * cannot be prevented from here.</p>
 */
@Component
public class TrackLogSource {

    private static final Logger LOG = LoggerFactory.getLogger(TrackLogSource.class);
    private static final DateTimeFormatter ARCHIVE_DAY = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    /** logs/yyyy/MM/file. */
    private static final int ARCHIVE_DEPTH = 3;

    private final Path logDir;
    private final Path activeFile;
    private final Pattern archiveName;
    private final TracksProperties properties;

    public TrackLogSource(Resources resources, TracksProperties properties) {
        String name = resources.getTrackingLogFileName();
        this.logDir = Path.of(resources.getLogFilePath());
        this.activeFile = logDir.resolve(name + ".log");
        this.archiveName = Pattern.compile(Pattern.quote(name) + "_(\\d{2}-\\d{2}-\\d{4})\\.\\d+\\.log\\.gz");
        this.properties = properties;
    }

    /** A log file and the first and last day (log zone) it holds lines of. */
    public record LogFile(Path path, LocalDate firstDay, LocalDate lastDay) {
    }

    /** Days that have a log, in the display zone. A log day yields every display day it overlaps. */
    public SortedSet<LocalDate> availableDays() {
        SortedSet<LocalDate> days = new TreeSet<>();
        for (LogFile file : files()) {
            var start = file.firstDay().atStartOfDay(properties.logZone()).withZoneSameInstant(properties.displayZone());
            var end = file.lastDay().plusDays(1).atStartOfDay(properties.logZone())
                    .minusNanos(1).withZoneSameInstant(properties.displayZone());
            for (LocalDate day = start.toLocalDate(); !day.isAfter(end.toLocalDate()); day = day.plusDays(1)) {
                days.add(day);
            }
        }
        return days;
    }

    /** The files that may hold lines of the log-zone days {@code first}..{@code last}. */
    public List<LogFile> filesFor(LocalDate first, LocalDate last) {
        return files().stream()
                .filter(file -> !file.firstDay().isAfter(last) && !file.lastDay().isBefore(first))
                .toList();
    }

    /** Feeds every line of the file to {@code lines}; what cannot be read is skipped. */
    public void read(LogFile file, Consumer<String> lines) {
        try (BufferedReader reader = open(file.path())) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                lines.accept(line);
            }
        } catch (NoSuchFileException e) {
            LOG.warn("Log file {} disappeared while reading, probably a rollover", file.path());
        } catch (EOFException e) {
            LOG.warn("Log file {} ends early, probably still being compressed; using what was read", file.path());
        } catch (IOException e) {
            LOG.warn("Cannot read log file {}: {}", file.path(), e.toString());
        }
    }

    private List<LogFile> files() {
        List<LogFile> files = new ArrayList<>();
        activeFile().ifPresent(files::add);
        try (Stream<Path> paths = Files.walk(logDir, ARCHIVE_DEPTH)) {
            paths.forEach(path -> archiveDay(path).ifPresent(day -> files.add(new LogFile(path, day, day))));
        } catch (NoSuchFileException e) {
            // no logs yet
        } catch (IOException | UncheckedIOException e) {
            LOG.warn("Cannot list {}: {}", logDir, e.toString());
        }
        return files;
    }

    private Optional<LocalDate> archiveDay(Path path) {
        Matcher matcher = archiveName.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(matcher.group(1), ARCHIVE_DAY));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** First day from the first line, last day from the modification time: no scan of the file. */
    private Optional<LogFile> activeFile() {
        try (BufferedReader reader = open(activeFile)) {
            String first = reader.readLine();
            LocalDateTime start = first == null ? null : TrackLogLineParser.timeOf(first);
            if (start == null) {
                return Optional.empty();
            }
            LocalDate end = Files.getLastModifiedTime(activeFile).toInstant()
                    .atZone(properties.logZone()).toLocalDate();
            LocalDate startDay = start.toLocalDate();
            return Optional.of(new LogFile(activeFile, startDay, end.isBefore(startDay) ? startDay : end));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            LOG.warn("Cannot read {}: {}", activeFile, e.toString());
            return Optional.empty();
        }
    }

    private static BufferedReader open(Path path) throws IOException {
        InputStream in = Files.newInputStream(path);
        try {
            if (path.getFileName().toString().endsWith(".gz")) {
                in = new GZIPInputStream(in);
            }
            return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            in.close();
            throw e;
        }
    }
}
