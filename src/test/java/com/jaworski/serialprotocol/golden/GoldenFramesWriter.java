package com.jaworski.serialprotocol.golden;

import com.jaworski.serialprotocol.dto.ModelTrackDTO;
import com.jaworski.serialprotocol.service.utils.MassageValuesOperations;
import com.jaworski.serialprotocol.service.utils.MessageTranslator;
import com.jaworski.serialprotocol.service.utils.impl.MessageCommon;
import com.jaworski.serialprotocol.service.utils.impl.MessageLadyMarie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A tool dressed as a test: writes {@code src/test/resources/golden/frames.jsonl}, the contract
 * between this decoder and the simulator's encoder (see {@code docs/e2e-simulator-plan.md}).
 *
 * <p>Run on demand with {@code .\mvnw.cmd -Dtest=GoldenFramesWriter -Dgolden.write=true test};
 * without the property it is skipped. Sources: every full frame of {@code docs/LogTerminal.txt}
 * in file order, then the {@code ModelDTOTest} arrays that are not already there. Each line
 * carries the bytes and what {@link MessageTranslator} decodes from them; positions are stored
 * twice, rounded as the decoder returns them and raw ({@code Float.toString} of the exact float
 * in the bytes) for the encoder. Lady Marie lines carry the two reserved bytes the decoder skips.</p>
 */
@EnabledIfSystemProperty(named = "golden.write", matches = "true")
class GoldenFramesWriter {

    static final Path OUTPUT = Path.of("src", "test", "resources", "golden", "frames.jsonl");
    static final Path RECORDED_LOG = Path.of("docs", "LogTerminal.txt");

    private static final Pattern FRAME = Pattern.compile("Received delimited message: \\[([-0-9, ]*)]");
    private static final int COMMON_LENGTH = 27;
    private static final int LADY_MARIE_LENGTH = 29;
    private static final int COMMON_POSITION_OFFSET = 13;
    private static final int LADY_MARIE_POSITION_OFFSET = 15;

    /** The checksummed arrays of ModelDTOTest, duplicates included - they are deduplicated below. */
    private static final List<byte[]> MODEL_DTO_TEST_FRAMES = List.of(
            new byte[]{119, 49, 8, -69, 8, -69, 0, 6, 0, 8, -69, -123, 1, 50, 73, -114, 66, 4, 27, 111, 66, 111, 0, 3, 10, 13, 10},
            new byte[]{119, 49, 8, -69, 8, -69, 0, 6, 0, 8, -69, -123, 0, -57, 77, -114, 66, -33, 33, 111, 66, 111, 0, 3, -125, 13, 10},
            new byte[]{119, 49, 8, -69, 8, -69, 0, 6, 0, 8, -69, -123, 0, 62, 73, -114, 66, 60, 32, 111, 66, 111, 0, 2, 81, 13, 10},
            new byte[]{98, 50, 5, 76, 5, 76, 3, -97, 3, 5, 76, 127, 10, 2, 77, 42, -61, 65, -104, -82, 67, -125, 0, 3, 65, 13, 10},
            new byte[]{98, 50, 5, 70, 5, 70, 3, -98, 3, 5, 70, -126, 9, 69, 28, 42, -61, -102, 106, -82, 67, -125, 0, 5, 111, 13, 10},
            new byte[]{98, 50, 5, 80, 5, 80, 3, -97, 3, 5, 80, -126, 10, 120, 108, 42, -61, 73, -81, -82, 67, -125, 0, 4, 5, 13, 10},
            new byte[]{98, 50, 12, 47, 12, 47, 3, 100, 3, 12, 47, -126, 0, 68, 35, -21, 65, -67, 44, -104, 66, 127, 0, 4, 10, 13, 10},
            new byte[]{98, 50, 12, 47, 12, 47, 3, 100, 3, 12, 47, -126, 1, -59, 27, -21, 65, 123, 46, -104, 66, 127, 0, 4, 68, 13, 10},
            new byte[]{98, 50, 12, 47, 12, 47, 3, 100, 3, 12, 47, -126, 2, -7, 20, -21, 65, 126, 41, -104, 66, 127, 0, 4, 112, 13, 10},
            new byte[]{100, 51, 4, -1, 3, 4, 96, 101, 98, 3, -4, 127, 1, 104, -69, -10, 65, 88, 52, -96, 66, 127, 0, 9, -105, 13, 10},
            new byte[]{100, 51, 5, 1, 3, 4, 96, 101, 98, 3, -4, 127, 2, -74, -77, -10, 65, 122, 51, -96, 66, -128, 0, 7, 1, 13, 10},
            new byte[]{100, 51, 5, 1, 3, 4, 97, 101, 97, 3, -4, 105, 1, -6, -52, -10, 65, 15, 51, -96, 66, -128, 0, 9, -34, 13, 10},
            new byte[]{108, 54, 8, -96, 0, 0, 100, 100, 100, -73, 97, 0, 0, 124, 2, 23, -44, 95, 66, 57, -39, 126, 66, -127, 0, -97, -118, 13, 10},
            new byte[]{108, 54, 0, -57, 0, 0, -118, 100, 74, 100, 99, 0, 0, 127, 3, -35, -81, -39, 66, -121, 57, 54, 65, -128, 0, 16, 88, 13, 10});

    @Test
    void writeGoldenFile() throws IOException {
        Map<String, byte[]> frames = new LinkedHashMap<>();
        for (String line : Files.readAllLines(RECORDED_LOG, StandardCharsets.UTF_8)) {
            Matcher matcher = FRAME.matcher(line);
            if (matcher.find()) {
                byte[] bytes = parseArray(matcher.group(1));
                if (bytes.length == COMMON_LENGTH || bytes.length == LADY_MARIE_LENGTH) {
                    frames.putIfAbsent(Arrays.toString(bytes), bytes);
                }
            }
        }
        for (byte[] bytes : MODEL_DTO_TEST_FRAMES) {
            frames.putIfAbsent(Arrays.toString(bytes), bytes);
        }

        MassageValuesOperations values = new MassageValuesOperations();
        MessageTranslator translator = new MessageTranslator(new MessageCommon(values), new MessageLadyMarie(values));
        JsonMapper mapper = new JsonMapper();
        List<String> lines = new ArrayList<>();
        for (byte[] bytes : frames.values()) {
            ModelTrackDTO dto = translator.getDTO(bytes);
            assertTrue(dto.isCRCValid(), () -> "a golden frame must verify: " + Arrays.toString(bytes));
            lines.add(mapper.writeValueAsString(line(mapper, bytes, dto)));
        }

        Files.createDirectories(OUTPUT.getParent());
        Files.write(OUTPUT, lines, StandardCharsets.UTF_8);
        System.out.println("Wrote " + lines.size() + " golden frames to " + OUTPUT.toAbsolutePath());
    }

    private static ObjectNode line(JsonMapper mapper, byte[] bytes, ModelTrackDTO dto) {
        boolean ladyMarie = bytes.length == LADY_MARIE_LENGTH;
        int position = ladyMarie ? LADY_MARIE_POSITION_OFFSET : COMMON_POSITION_OFFSET;
        ObjectNode line = mapper.createObjectNode();
        ArrayNode array = line.putArray("bytes");
        for (byte b : bytes) {
            array.add(b);
        }
        line.put("prefix", new String(bytes, 0, 2, StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT));
        line.put("modelName", dto.getModelName());
        line.put("heading", dto.getHeading());
        line.put("bowTugDirection", dto.getBowTug().getTugDirection());
        line.put("bowTugForce", dto.getBowTug().getTugForce());
        line.put("engine", (int) dto.getEngine());
        line.put("sternTugForce", dto.getSternTug().getTugForce());
        line.put("sternTugDirection", dto.getSternTug().getTugDirection());
        line.put("rudder", dto.getRudder());
        line.put("speed", dto.getSpeed());
        line.put("positionX", dto.getPositionX());
        line.put("positionXRaw", Float.toString(rawFloat(bytes, position)));
        line.put("positionY", dto.getPositionY());
        line.put("positionYRaw", Float.toString(rawFloat(bytes, position + 4)));
        line.put("bowThruster", (int) dto.getBowThruster());
        line.put("gpsQuality", dto.getGpsQuality());
        if (ladyMarie) {
            ArrayNode reserved = line.putArray("reserved");
            reserved.add(bytes[COMMON_POSITION_OFFSET]);
            reserved.add(bytes[COMMON_POSITION_OFFSET + 1]);
        }
        return line;
    }

    private static float rawFloat(byte[] bytes, int offset) {
        return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
    }

    private static byte[] parseArray(String csv) {
        String trimmed = csv.trim();
        if (trimmed.isEmpty()) {
            return new byte[0];
        }
        String[] parts = trimmed.split("\\s*,\\s*");
        byte[] bytes = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            bytes[i] = (byte) Integer.parseInt(parts[i]);
        }
        return bytes;
    }
}
