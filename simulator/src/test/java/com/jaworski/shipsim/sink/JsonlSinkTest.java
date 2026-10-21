package com.jaworski.shipsim.sink;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonlSinkTest {

    @Test
    void writesOneJsonObjectPerMessageWithItsProvenance(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("frames.jsonl");
        byte[] bytes = {119, 49, 13, 10};

        try (Sink sink = SinkUri.open("jsonl://" + file)) {
            assertFalse(sink.paced());
            sink.send(new TaggedFrame(bytes, "w1", "bad-crc", 12.0));
            sink.send(bytes);
        }

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(2, lines.size());
        JsonNode tagged = new JsonMapper().readTree(lines.get(0));
        assertEquals(12.0, tagged.get("seconds").doubleValue());
        assertEquals("w1", tagged.get("ship").asString());
        assertEquals("bad-crc", tagged.get("fault").asString());
        assertEquals(4, tagged.get("bytes").size());
        assertEquals(119, tagged.get("bytes").get(0).intValue());
        JsonNode plain = new JsonMapper().readTree(lines.get(1));
        assertTrue(plain.get("seconds").isNull());
        assertEquals("w1", plain.get("ship").asString(), "the ship comes from the prefix when nothing else is known");
        assertTrue(plain.get("fault").isNull());
    }
}
