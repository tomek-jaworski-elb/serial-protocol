package com.jaworski.serialprotocol.service.tracks;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.jaworski.serialprotocol.service.tracks.TrackLogFixtures.line;
import static com.jaworski.serialprotocol.service.tracks.TrackLogFixtures.oldLine;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrackLogLineParserTest {

    private static final String TIME = "19-09-2024 14:10:37.358";

    @Test
    void readsTheLineFromBeforeTheChecksumFlag() {
        assertThat(TrackLogLineParser.parse(oldLine(TIME, 1)))
                .contains(new TrackLogLineParser.Fix(1, 71.14f, 59.78f, 232.5));
    }

    @Test
    void readsTheCurrentLine() {
        assertThat(TrackLogLineParser.parse(line(TIME, 6)))
                .contains(new TrackLogLineParser.Fix(6, 71.14f, 59.78f, 232.5));
    }

    @Test
    void readsNegativeCoordinatesAndScientificNotation() {
        assertThat(TrackLogLineParser.parse(line(TIME, 2, "-208.19", "1.0E-4", "3.5E2", true)))
                .contains(new TrackLogLineParser.Fix(2, -208.19f, 1.0E-4f, 350.0));
    }

    @Test
    void skipsAFrameThatFailedItsChecksum() {
        assertThat(TrackLogLineParser.parse(line(TIME, 1, "1.0", "2.0", "3.0", false))).isEmpty();
    }

    @Test
    void skipsAnUnknownShip() {
        assertThat(TrackLogLineParser.parse(line(TIME, -1))).isEmpty();
        assertThat(TrackLogLineParser.parse(line(TIME, 9))).isEmpty();
    }

    @Test
    void ignoresLinesThatAreNotTrackLines() {
        assertThat(TrackLogLineParser.parse(TIME + " - Received override message with length 52. Trying to parse...")).isEmpty();
        assertThat(TrackLogLineParser.parse("")).isEmpty();
        assertThat(TrackLogLineParser.parse("garbage")).isEmpty();
    }

    @Test
    void rejectsATrackLineItCannotRead() {
        String damaged = line(TIME, 1).replace("positionX=71.14", "positionX=abc");
        assertThatThrownBy(() -> TrackLogLineParser.parse(damaged)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TrackLogLineParser.parse(line(TIME, 1).substring(0, 90)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readsTheTimestampOfAnyLine() {
        assertThat(TrackLogLineParser.timeOf(line(TIME, 1))).isEqualTo(LocalDateTime.of(2024, 9, 19, 14, 10, 37, 358_000_000));
        assertThat(TrackLogLineParser.timeOf("short")).isNull();
        assertThat(TrackLogLineParser.timeOf("xx-xx-xxxx xx:xx:xx.xxx - something")).isNull();
        assertThat(TrackLogLineParser.timeOf("99-01-2024 10:00:00.000 - x")).isNull();
    }
}
