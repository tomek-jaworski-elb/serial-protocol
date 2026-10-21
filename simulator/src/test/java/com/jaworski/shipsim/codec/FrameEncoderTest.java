package com.jaworski.shipsim.codec;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * The contract with the application: every frame in its golden file, decoded there by the real
 * decoder, must come back out of this encoder byte for byte. The file lives in the application
 * because its decoder is the source of truth; {@code -Dgolden.file=...} points elsewhere.
 */
class FrameEncoderTest {

    private static final String GOLDEN_PROPERTY = "golden.file";
    private static final Path DEFAULT_GOLDEN = Path.of("..", "src", "test", "resources", "golden", "frames.jsonl");
    private static final JsonMapper MAPPER = new JsonMapper();

    private final FrameEncoder encoder = new FrameEncoder();

    @TestFactory
    Stream<DynamicTest> everyGoldenFrameIsReproducedByteForByte() throws IOException {
        Path golden = Path.of(System.getProperty(GOLDEN_PROPERTY, DEFAULT_GOLDEN.toString()));
        assertTrue(Files.exists(golden), () -> "golden file not found at " + golden.toAbsolutePath()
                + " - run GoldenFramesWriter in the application (-Dgolden.write=true) or pass -D" + GOLDEN_PROPERTY + "=<path>");
        List<String> lines = Files.readAllLines(golden, StandardCharsets.UTF_8).stream().filter(l -> !l.isBlank()).toList();
        assertTrue(lines.size() >= 60, () -> "golden file looks truncated: " + lines.size() + " lines");
        return IntStream.range(0, lines.size()).mapToObj(i -> {
            JsonNode line = MAPPER.readTree(lines.get(i));
            return dynamicTest("line " + (i + 1) + ": " + line.get("prefix").asString() + ", " + line.get("bytes").size() + " bytes",
                    () -> assertArrayEquals(bytesOf(line), encoder.encode(frameOf(line))));
        });
    }

    @Test
    void aCommonFrameIs27BytesEndingInCrLfWithAValidChecksum() {
        byte[] bytes = encoder.encode(sample("w1"));

        assertEquals(FrameEncoder.COMMON_LENGTH, bytes.length);
        assertEquals('w', bytes[0]);
        assertEquals('1', bytes[1]);
        assertEquals(FrameEncoder.checksum(bytes, 24), bytes[24]);
        assertEquals(FrameEncoder.CR, bytes[25]);
        assertEquals(FrameEncoder.LF, bytes[26]);
    }

    @Test
    void aLadyMarieFrameIs29BytesWithZeroReservedBytesWhenNoneAreGiven() {
        byte[] bytes = encoder.encode(sample("l6"));

        assertEquals(FrameEncoder.LADY_MARIE_LENGTH, bytes.length);
        assertEquals(0, bytes[13]);
        assertEquals(0, bytes[14]);
        assertEquals(FrameEncoder.checksum(bytes, 26), bytes[26]);
        assertEquals(FrameEncoder.CR, bytes[27]);
        assertEquals(FrameEncoder.LF, bytes[28]);
    }

    @Test
    void scaledFieldsAreRoundedNotTruncated() {
        // 256.94 * 100 is 25693.999999999996 as a double; the application decodes 25694 as 256.94.
        Frame frame = new Frame("b2", 0, 0, 0, 0, 0, 256.94, 0, 0, 0f, 0f, 0, 0, null);

        byte[] bytes = encoder.encode(frame);

        assertEquals(25694, ((bytes[9] & 0xFF) << 8) | (bytes[10] & 0xFF));
    }

    @Test
    void aRudderBeyondTheInt8RangeIsRejectedByName() {
        Frame frame = new Frame("w1", 0, 0, 0, 0, 0, 0, 20.0, 0, 0f, 0f, 0, 0, null);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> encoder.encode(frame));

        assertTrue(e.getMessage().contains("rudder"), e.getMessage());
    }

    @Test
    void aHeadingBeyondTheUint16RangeIsRejected() {
        Frame frame = new Frame("w1", 7000.0, 0, 0, 0, 0, 0, 0, 0, 0f, 0f, 0, 0, null);

        assertThrows(IllegalArgumentException.class, () -> encoder.encode(frame));
    }

    @Test
    void aPrefixMustBeTwoAsciiCharacters() {
        assertThrows(IllegalArgumentException.class, () -> new Frame("w", 0, 0, 0, 0, 0, 0, 0, 0, 0f, 0f, 0, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new Frame("w1", 0, 0, 0, 0, 0, 0, 0, 0, 0f, 0f, 0, 0, new byte[3]));
    }

    static Frame sample(String prefix) {
        return new Frame(prefix, 223.5, 22.35, 0.0, 6, 0.0, 22.35, -12.3, 0.1, 71.14f, 59.78f, 111, 0.03, null);
    }

    static byte[] bytesOf(JsonNode line) {
        JsonNode array = line.get("bytes");
        byte[] bytes = new byte[array.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) array.get(i).intValue();
        }
        return bytes;
    }

    static Frame frameOf(JsonNode line) {
        byte[] reserved = null;
        if (line.has("reserved")) {
            reserved = new byte[]{(byte) line.get("reserved").get(0).intValue(), (byte) line.get("reserved").get(1).intValue()};
        }
        return new Frame(
                line.get("prefix").asString(),
                line.get("heading").doubleValue(),
                line.get("bowTugDirection").doubleValue(),
                line.get("bowTugForce").doubleValue(),
                line.get("engine").intValue(),
                line.get("sternTugForce").doubleValue(),
                line.get("sternTugDirection").doubleValue(),
                line.get("rudder").doubleValue(),
                line.get("speed").doubleValue(),
                Float.parseFloat(line.get("positionXRaw").asString()),
                Float.parseFloat(line.get("positionYRaw").asString()),
                line.get("bowThruster").intValue(),
                line.get("gpsQuality").doubleValue(),
                reserved);
    }
}
