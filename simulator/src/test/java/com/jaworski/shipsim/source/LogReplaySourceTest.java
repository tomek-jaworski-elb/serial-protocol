package com.jaworski.shipsim.source;

import com.jaworski.shipsim.source.LogReplaySource.Entry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs over the real recording in the application repository, {@code docs/LogTerminal.txt}. */
class LogReplaySourceTest {

    private static final Path RECORDED_LOG = Path.of("..", "docs", "LogTerminal.txt");

    @Test
    void theRecordingYieldsSixtyOneFullFramesFromAllSixShips() throws IOException {
        List<Entry> entries = LogReplaySource.read(RECORDED_LOG, false);

        assertEquals(61, entries.size());
        assertTrue(entries.stream().allMatch(Entry::isFullFrame));
        Set<String> prefixes = entries.stream()
                .map(e -> new String(e.frame(), 0, 2, StandardCharsets.US_ASCII))
                .collect(Collectors.toSet());
        assertEquals(Set.of("w1", "b2", "d3", "c4", "k5", "l6"), prefixes);
    }

    @Test
    void fragmentsAreReturnedOnlyOnRequest() throws IOException {
        assertEquals(64, LogReplaySource.read(RECORDED_LOG, true).size());
    }

    @Test
    void aTruncatedTimestampKeepsTheFrameButNotTheTime() throws IOException {
        // The recording's first line starts with "0-07-2024": one digit short of dd-MM-yyyy.
        List<Entry> entries = LogReplaySource.read(RECORDED_LOG, false);

        assertEquals(1, entries.get(0).line());
        assertNull(entries.get(0).timestamp());
        assertNotNull(entries.get(1).timestamp());
    }

    @Test
    void linesWithoutAFrameAreIgnored(@TempDir Path dir) throws IOException {
        Path log = dir.resolve("log.log");
        Files.write(log, List.of(
                "20-07-2024 12:07:19.345 [Thread-14] INFO WebSocketPublisherImpl - Message sent to client: {\"modelName\":6}",
                "20-07-2024 12:07:19.457 [Thread-14] INFO SerialPortListenerImpl.serialEvent - On port COM1 Received delimited message: [119, 49, 8, -69, 8, -69, 0, 6, 0, 8, -69, -123, 1, 50, 73, -114, 66, 4, 27, 111, 66, 111, 0, 3, 10, 13, 10]",
                "20-07-2024 12:07:20.001 [Thread-14] INFO SerialPortListenerImpl.serialEvent - On port COM1 Received delimited message: []",
                ""), StandardCharsets.UTF_8);

        List<Entry> fullFrames = LogReplaySource.read(log, false);
        List<Entry> everything = LogReplaySource.read(log, true);

        assertEquals(1, fullFrames.size());
        assertEquals(2, fullFrames.get(0).line());
        assertEquals(27, fullFrames.get(0).frame().length);
        assertEquals(2, everything.size());
        assertEquals(0, everything.get(1).frame().length);
    }

    @Test
    void aLogOfAUdpFedRunReplaysLikeASerialOne(@TempDir Path dir) throws IOException {
        Path log = dir.resolve("log.log");
        Files.write(log, List.of(
                "21-10-2026 20:56:38.300 [virtual-134] INFO  c.j.s.u.server.UdpPacketHandlerImpl.handle - From UDP /127.0.0.1:55761 Received delimited message: [119, 49, 8, -69, 8, -69, 0, 6, 0, 8, -69, -123, 1, 50, 73, -114, 66, 4, 27, 111, 66, 111, 0, 3, 10, 13, 10]"),
                StandardCharsets.UTF_8);

        List<Entry> entries = LogReplaySource.read(log, false);

        assertEquals(1, entries.size());
        assertEquals(27, entries.get(0).frame().length);
        assertNotNull(entries.get(0).timestamp());
    }

    @Test
    void parseArrayReadsSignedBytesAndRejectsAnythingElse() {
        assertArrayEquals(new byte[]{119, 49, -69, 13, 10}, LogReplaySource.parseArray("119, 49,-69 , 13, 10"));
        assertArrayEquals(new byte[0], LogReplaySource.parseArray("  "));
        assertThrows(IllegalArgumentException.class, () -> LogReplaySource.parseArray("300"));
        assertThrows(NumberFormatException.class, () -> LogReplaySource.parseArray("1, x"));
    }
}
