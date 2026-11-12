package com.jaworski.serialprotocol.service.tracks;

import com.jaworski.serialprotocol.dto.Models;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the tracking log line written by {@code MessageTranslator}. The text of that line is the
 * storage format, see {@code CLAUDE.md}.
 *
 * <pre>
 * 19-09-2024 14:10:37.358 - Translated message: ModelTrackDTO(modelName=1, positionX=71.14, positionY=59.78, speed=0.1, heading=232.5, ..., sternTug=TugDTO(tugForce=0.0, tugDirection=22.35))
 * 11-11-2026 14:00:00.123 - Translated message: ModelTrackDTO(modelName=1, positionX=-1.0E-4, ..., sternTug=TugDTO(...), isCRCValid=true)
 * </pre>
 *
 * The first form predates the checksum field. Callers cut lines by {@link #timeOf} first, so lines
 * outside the range never reach the regex.
 */
public final class TrackLogLineParser {

    /** Length of {@code dd-MM-yyyy HH:mm:ss.SSS}. */
    private static final int TIMESTAMP_LENGTH = 23;
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss.SSS");
    private static final String TRACK_MARKER = " - Translated message: ModelTrackDTO(";
    private static final String CRC_FAILED = "isCRCValid=false)";
    /** Float.toString and Double.toString may print an exponent. */
    private static final String NUMBER = "(-?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?)";
    private static final Pattern TRACK = Pattern.compile(
            Pattern.quote(TRACK_MARKER) + "modelName=(-?\\d+), positionX=" + NUMBER + ", positionY=" + NUMBER
                    + ", speed=[^,]+, heading=" + NUMBER + ", .*\\)");

    private TrackLogLineParser() {
    }

    /** A position of one ship. */
    public record Fix(int modelId, float x, float y, double heading) {
    }

    /** The time a line was written, or null when it does not start with a timestamp. */
    public static LocalDateTime timeOf(String line) {
        if (line.length() < TIMESTAMP_LENGTH) {
            return null;
        }
        try {
            return LocalDateTime.parse(line.subSequence(0, TIMESTAMP_LENGTH), TIMESTAMP);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * @return the position, or empty for a line that is not a track line, failed its checksum or
     * names an unknown ship
     * @throws IllegalArgumentException for a line that is a track line but cannot be read
     */
    public static Optional<Fix> parse(String line) {
        if (!line.startsWith(TRACK_MARKER, TIMESTAMP_LENGTH)) {
            return Optional.empty();
        }
        Matcher matcher = TRACK.matcher(line).region(TIMESTAMP_LENGTH, line.length());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unreadable track line: " + line);
        }
        int modelId = Integer.parseInt(matcher.group(1));
        if (Models.fromId(modelId) == null || line.endsWith(CRC_FAILED)) {
            return Optional.empty();
        }
        return Optional.of(new Fix(modelId, Float.parseFloat(matcher.group(2)),
                Float.parseFloat(matcher.group(3)), Double.parseDouble(matcher.group(4))));
    }
}
