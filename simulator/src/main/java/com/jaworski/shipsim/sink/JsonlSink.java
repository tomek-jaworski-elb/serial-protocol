package com.jaworski.shipsim.sink;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * One JSON object per message, for test fixtures:
 * {@code {"seconds":12.0,"ship":"w1","fault":"bad-crc","bytes":[...]}}. A glued or split message
 * cannot be recovered from a flat byte file, and a test needs to know which fault produced a
 * line to assert the right outcome - that is why this exists next to {@link FileSink}.
 */
public final class JsonlSink implements Sink {

    private static final JsonMapper MAPPER = new JsonMapper();

    private final Path path;
    private final BufferedWriter out;

    public JsonlSink(Path path) throws IOException {
        this.path = path;
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        this.out = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    @Override
    public void send(byte[] frame) throws IOException {
        String ship = frame.length >= 2 ? new String(frame, 0, 2, StandardCharsets.US_ASCII) : null;
        send(new TaggedFrame(frame, ship, null, Double.NaN));
    }

    @Override
    public void send(TaggedFrame frame) throws IOException {
        ObjectNode line = MAPPER.createObjectNode();
        if (Double.isNaN(frame.seconds())) {
            line.putNull("seconds");
        } else {
            line.put("seconds", frame.seconds());
        }
        line.put("ship", frame.ship());
        line.put("fault", frame.fault());
        ArrayNode bytes = line.putArray("bytes");
        for (byte b : frame.bytes()) {
            bytes.add(b);
        }
        out.write(MAPPER.writeValueAsString(line));
        out.write('\n');
    }

    @Override
    public boolean paced() {
        return false;
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    @Override
    public String toString() {
        return "jsonl://" + path;
    }
}
