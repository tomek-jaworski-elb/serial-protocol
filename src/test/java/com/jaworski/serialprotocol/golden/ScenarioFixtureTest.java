package com.jaworski.serialprotocol.golden;

import com.jaworski.serialprotocol.dto.ModelTrackDTO;
import com.jaworski.serialprotocol.service.utils.MassageValuesOperations;
import com.jaworski.serialprotocol.service.utils.MessageTranslator;
import com.jaworski.serialprotocol.service.utils.impl.MessageCommon;
import com.jaworski.serialprotocol.service.utils.impl.MessageLadyMarie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * What the simulator's generator produces, replayed through the real decoder: the fixtures in
 * {@code src/test/resources/frames/} were written by {@code ship-sim generate --to jsonl://...}
 * from {@code simulator/scenarios/six-ships.yaml} (60 s) and {@code faults.yaml} (120 s). Each
 * line carries the message bytes, the ship and the fault that produced it, and this test asserts
 * the outcome the plan promises for that fault (docs/e2e-simulator-plan.md, stage 2).
 */
class ScenarioFixtureTest {

    private static final Path SIX_SHIPS = Path.of("src", "test", "resources", "frames", "six-ships.jsonl");
    private static final Path FAULTS = Path.of("src", "test", "resources", "frames", "faults.jsonl");
    private static final JsonMapper MAPPER = new JsonMapper();
    private static final double CRLF_HEADING = 333.8;
    private static final byte CR = 13;
    private static final byte LF = 10;

    private static MessageTranslator translator;

    @BeforeAll
    static void translator() {
        MassageValuesOperations values = new MassageValuesOperations();
        translator = new MessageTranslator(new MessageCommon(values), new MessageLadyMarie(values));
    }

    @TestFactory
    Stream<DynamicTest> everyGeneratedFrameOfSixShipsDecodesToItsShip() throws IOException {
        List<JsonNode> lines = read(SIX_SHIPS);
        Set<String> ships = lines.stream().map(l -> l.get("ship").asString()).collect(Collectors.toSet());
        assertEquals(Set.of("w1", "b2", "d3", "c4", "k5", "l6"), ships);
        assertTrue(lines.stream().allMatch(l -> l.get("fault").isNull()), "six-ships has no faults");
        return lines.stream().map(line -> dynamicTest(name(line), () -> {
            ModelTrackDTO dto = translator.getDTO(bytes(line));
            assertTrue(dto.isCRCValid());
            assertEquals(MessageTranslator.MODEL_MAP.get(line.get("ship").asString()), dto.getModelName());
        }));
    }

    @TestFactory
    Stream<DynamicTest> everyFaultHasThePromisedOutcome() throws IOException {
        List<JsonNode> lines = read(FAULTS);
        return lines.stream().filter(l -> !l.get("fault").isNull()).map(line -> dynamicTest(name(line), () -> {
            byte[] bytes = bytes(line);
            String ship = line.get("ship").asString();
            switch (line.get("fault").asString()) {
                case "bad-crc" -> assertFalse(translator.getDTO(bytes).isCRCValid(), "dropped before /json");
                case "crlf-in-payload" -> {
                    ModelTrackDTO whole = translator.getDTO(bytes);
                    assertTrue(whole.isCRCValid(), "as one datagram the frame is fine");
                    assertEquals(CRLF_HEADING, whole.getHeading(), 1e-9);
                    int split = indexOfCrLf(bytes);
                    byte[] fragment = Arrays.copyOfRange(bytes, 0, split + 2);
                    byte[] remainder = Arrays.copyOfRange(bytes, split + 2, bytes.length);
                    assertEquals(4, fragment.length, "a serial listener cuts after the heading bytes");
                    assertThrows(IllegalArgumentException.class, () -> translator.getDTO(fragment));
                    assertThrows(IllegalArgumentException.class, () -> translator.getDTO(remainder));
                }
                case "glue-next" -> {
                    ModelTrackDTO dto = translator.getDTO(bytes);
                    if ("l6".equals(ship)) {
                        assertTrue(dto.isCRCValid(), "two glued Lady Marie frames: the second one is recovered");
                        assertEquals(6, dto.getModelName());
                    } else {
                        assertFalse(dto.isCRCValid(), "two glued 27-byte frames are dropped");
                    }
                }
                case "unknown-prefix" -> {
                    ModelTrackDTO dto = translator.getDTO(bytes);
                    assertTrue(dto.isCRCValid());
                    assertEquals(-1, dto.getModelName());
                }
                case "burst" -> assertTrue(translator.getDTO(bytes).isCRCValid());
                default -> throw new AssertionError("unexpected fault " + line.get("fault"));
            }
        }));
    }

    @Test
    void aSilentShipSendsNothingForTheWholeWindowAndThenResumes() throws IOException {
        List<JsonNode> ladyMarie = read(FAULTS).stream().filter(l -> "l6".equals(l.get("ship").asString())).toList();
        Set<Double> seconds = ladyMarie.stream().map(l -> l.get("seconds").doubleValue()).collect(Collectors.toSet());

        assertTrue(seconds.contains(59.0));
        for (int s = 60; s < 90; s++) {
            assertFalse(seconds.contains((double) s), "silent at " + s + " s");
        }
        assertTrue(seconds.contains(90.0));
    }

    @Test
    void aBurstIsTwentyCopiesInOneSecond() throws IOException {
        List<JsonNode> burst = read(FAULTS).stream().filter(l -> "burst".equals(l.get("fault").asString(""))).toList();

        assertEquals(20, burst.size());
        assertTrue(burst.stream().allMatch(l -> l.get("seconds").doubleValue() == 50.0));
    }

    private static List<JsonNode> read(Path file) throws IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                .filter(l -> !l.isBlank())
                .map(MAPPER::readTree)
                .toList();
    }

    private static byte[] bytes(JsonNode line) {
        JsonNode array = line.get("bytes");
        byte[] bytes = new byte[array.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) array.get(i).intValue();
        }
        return bytes;
    }

    private static String name(JsonNode line) {
        return line.get("ship").asString() + " at " + line.get("seconds").doubleValue() + " s"
                + (line.get("fault").isNull() ? "" : " (" + line.get("fault").asString() + ")");
    }

    private static int indexOfCrLf(byte[] bytes) {
        for (int i = 0; i < bytes.length - 1; i++) {
            if (bytes[i] == CR && bytes[i + 1] == LF) {
                return i;
            }
        }
        throw new AssertionError("no CR LF in " + Arrays.toString(bytes));
    }
}
