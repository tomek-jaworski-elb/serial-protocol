package com.jaworski.serialprotocol.golden;

import com.jaworski.serialprotocol.dto.ModelTrackDTO;
import com.jaworski.serialprotocol.service.utils.MassageValuesOperations;
import com.jaworski.serialprotocol.service.utils.MessageTranslator;
import com.jaworski.serialprotocol.service.utils.impl.MessageCommon;
import com.jaworski.serialprotocol.service.utils.impl.MessageLadyMarie;
import org.junit.jupiter.api.DynamicTest;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * The decoder's half of the golden contract: every line of {@code golden/frames.jsonl} still
 * decodes to the fields recorded in it. The simulator's {@code FrameEncoderTest} is the other
 * half. A change to the frame format must change the file first and turn both red.
 */
class GoldenFramesTest {

    private static final Path GOLDEN = Path.of("src", "test", "resources", "golden", "frames.jsonl");
    private static final JsonMapper MAPPER = new JsonMapper();
    private static final float NO_TOLERANCE = 0f;

    @TestFactory
    Stream<DynamicTest> everyGoldenLineDecodesToItsRecordedFields() throws IOException {
        List<String> lines = Files.readAllLines(GOLDEN, StandardCharsets.UTF_8).stream().filter(l -> !l.isBlank()).toList();
        assertTrue(lines.size() >= 60, () -> "golden file looks truncated: " + lines.size() + " lines");
        assertTrue(lines.stream().anyMatch(l -> l.contains("\"reserved\"")), "no Lady Marie frame in the golden file");

        MassageValuesOperations values = new MassageValuesOperations();
        MessageTranslator translator = new MessageTranslator(new MessageCommon(values), new MessageLadyMarie(values));
        return IntStream.range(0, lines.size()).mapToObj(i -> {
            JsonNode line = MAPPER.readTree(lines.get(i));
            return dynamicTest("line " + (i + 1) + ": " + line.get("prefix").asString(), () -> assertDecodesTo(translator, line));
        });
    }

    private static void assertDecodesTo(MessageTranslator translator, JsonNode line) {
        JsonNode array = line.get("bytes");
        byte[] bytes = new byte[array.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) array.get(i).intValue();
        }

        ModelTrackDTO dto = translator.getDTO(bytes);

        assertTrue(dto.isCRCValid(), "checksum");
        assertEquals(line.get("modelName").intValue(), dto.getModelName(), "modelName");
        assertEquals(line.get("positionX").floatValue(), dto.getPositionX(), NO_TOLERANCE, "positionX");
        assertEquals(line.get("positionY").floatValue(), dto.getPositionY(), NO_TOLERANCE, "positionY");
        assertEquals(line.get("speed").doubleValue(), dto.getSpeed(), "speed");
        assertEquals(line.get("heading").doubleValue(), dto.getHeading(), "heading");
        assertEquals(line.get("rudder").doubleValue(), dto.getRudder(), "rudder");
        assertEquals(line.get("gpsQuality").doubleValue(), dto.getGpsQuality(), "gpsQuality");
        assertEquals(line.get("engine").intValue(), (int) dto.getEngine(), "engine");
        assertEquals(line.get("bowThruster").intValue(), (int) dto.getBowThruster(), "bowThruster");
        assertEquals(line.get("bowTugForce").doubleValue(), dto.getBowTug().getTugForce(), "bowTugForce");
        assertEquals(line.get("bowTugDirection").doubleValue(), dto.getBowTug().getTugDirection(), "bowTugDirection");
        assertEquals(line.get("sternTugForce").doubleValue(), dto.getSternTug().getTugForce(), "sternTugForce");
        assertEquals(line.get("sternTugDirection").doubleValue(), dto.getSternTug().getTugDirection(), "sternTugDirection");
    }
}
