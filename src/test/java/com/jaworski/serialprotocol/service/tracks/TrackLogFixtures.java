package com.jaworski.serialprotocol.service.tracks;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

/** Tracking log lines and files as logback writes them. */
final class TrackLogFixtures {

    private static final String TUGS = "bowTug=TugDTO(tugForce=0.0, tugDirection=0.0), sternTug=TugDTO(tugForce=0.0, tugDirection=22.35)";

    private TrackLogFixtures() {
    }

    /** The current format, with the checksum flag. */
    static String line(String time, int modelId, String x, String y, String heading, boolean crc) {
        return time + " - Translated message: ModelTrackDTO(modelName=" + modelId + ", positionX=" + x
                + ", positionY=" + y + ", speed=0.1, heading=" + heading + ", rudder=-12.3, gpsQuality=0.03, engine=6.0, "
                + "bowThruster=111.0, " + TUGS + ", isCRCValid=" + crc + ")";
    }

    static String line(String time, int modelId) {
        return line(time, modelId, "71.14", "59.78", "232.5", true);
    }

    /** The format from before the checksum flag. */
    static String oldLine(String time, int modelId) {
        return time + " - Translated message: ModelTrackDTO(modelName=" + modelId + ", positionX=71.14, positionY=59.78, "
                + "speed=0.1, heading=232.5, rudder=-12.3, gpsQuality=0.03, engine=6.0, bowThruster=111.0, " + TUGS + ")";
    }

    static void writeLog(Path file, String... lines) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
    }

    static void writeGz(Path file, String... lines) throws IOException {
        Files.createDirectories(file.getParent());
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
            out.write((String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }
}
