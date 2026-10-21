package com.jaworski.shipsim.sink;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SinkUriTest {

    @Test
    void stdoutIsASink() throws IOException {
        try (Sink sink = SinkUri.open("stdout")) {
            assertInstanceOf(StdoutSink.class, sink);
        }
    }

    @Test
    void udpTakesHostAndPortAndDefaultsThePort() throws IOException {
        try (Sink sink = SinkUri.open("udp://127.0.0.1:5000")) {
            assertEquals("udp://127.0.0.1:5000", sink.toString());
        }
        try (Sink sink = SinkUri.open("udp://localhost")) {
            assertEquals("udp://localhost:" + SinkUri.DEFAULT_UDP_PORT, sink.toString());
        }
    }

    @Test
    void udpRejectsAMissingHostOrABadPort() {
        assertThrows(IllegalArgumentException.class, () -> SinkUri.open("udp://:5000"));
        assertThrows(IllegalArgumentException.class, () -> SinkUri.open("udp://localhost:abc"));
        assertThrows(IllegalArgumentException.class, () -> SinkUri.open("udp://localhost:70000"));
    }

    @Test
    void fileWritesTheRawFramesToThePath(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("frames.bin");
        byte[] frame = {1, 2, 13, 10};

        try (Sink sink = SinkUri.open("file://" + target)) {
            sink.send(frame);
            sink.send(frame);
        }

        assertArrayEquals(new byte[]{1, 2, 13, 10, 1, 2, 13, 10}, Files.readAllBytes(target));
    }

    @Test
    void aWindowsPathBehindTheSchemeLosesItsLeadingSlash() {
        assertEquals(Path.of("C:/data/frames.bin"), SinkUri.filePath("/C:/data/frames.bin"));
        assertEquals(Path.of("/tmp/frames.bin"), SinkUri.filePath("/tmp/frames.bin"));
        assertEquals(Path.of("frames.bin"), SinkUri.filePath("frames.bin"));
    }

    @Test
    void anUnknownSchemeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SinkUri.open("ftp://somewhere"));
        assertThrows(IllegalArgumentException.class, () -> SinkUri.open(""));
    }
}
