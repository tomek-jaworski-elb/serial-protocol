package com.jaworski.shipsim.source;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads frames back out of the application's own log.
 *
 * <p>{@code SerialPortListenerImpl} logs every delimited message as
 * {@code "On port X Received delimited message: [119, 49, ...]"}, with the frame printed as a
 * Java array of signed bytes. That text is an API (CLAUDE.md, invariants): this class parses it,
 * so {@code docs/LogTerminal.txt} and any {@code logs/log.log} can be replayed. Lines without a
 * frame are ignored. The leading timestamp ({@code dd-MM-yyyy HH:mm:ss.SSS}) is kept for
 * original-timing playback; a line whose timestamp does not parse keeps its frame and gets no
 * timestamp.</p>
 */
public final class LogReplaySource {

    /**
     * @param frame     the bytes as logged, delimiter included
     * @param timestamp when the line was logged, or null when the stamp could not be parsed
     * @param line      1-based line number in the source file, for messages
     */
    public record Entry(byte[] frame, Instant timestamp, int line) {

        /** The application only decodes these two lengths; anything else is a fragment. */
        public boolean isFullFrame() {
            return frame.length == 27 || frame.length == 29;
        }
    }

    private static final Pattern FRAME = Pattern.compile("Received delimited message: \\[([-0-9, ]*)]");
    private static final Pattern STAMP = Pattern.compile("^(\\d{2}-\\d{2}-\\d{4} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})");
    private static final DateTimeFormatter STAMP_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss.SSS");

    private LogReplaySource() {
    }

    /**
     * @param includeFragments whether entries that are not 27 or 29 bytes long are returned too
     */
    public static List<Entry> read(Path file, boolean includeFragments) throws IOException {
        List<Entry> entries = new ArrayList<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher frame = FRAME.matcher(line);
            if (!frame.find()) {
                continue;
            }
            Entry entry = new Entry(parseArray(frame.group(1)), parseStamp(line), i + 1);
            if (includeFragments || entry.isFullFrame()) {
                entries.add(entry);
            }
        }
        return entries;
    }

    /** Parses {@code "119, 49, -69"} - the body of a Java array literal - into bytes. */
    public static byte[] parseArray(String csv) {
        String trimmed = csv.trim();
        if (trimmed.isEmpty()) {
            return new byte[0];
        }
        String[] parts = trimmed.split("\\s*,\\s*");
        byte[] bytes = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            int value = Integer.parseInt(parts[i]);
            if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
                throw new IllegalArgumentException("not a byte: " + value);
            }
            bytes[i] = (byte) value;
        }
        return bytes;
    }

    private static Instant parseStamp(String line) {
        Matcher stamp = STAMP.matcher(line);
        if (!stamp.find()) {
            return null;
        }
        try {
            return LocalDateTime.parse(stamp.group(1), STAMP_FORMAT).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
