package com.jaworski.shipsim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerateCommandTest {

    private static final String FAULTS = Path.of("scenarios", "faults.yaml").toString();

    @Test
    void loopWithAFileSinkIsRefused(@TempDir Path dir) {
        int code = new CommandLine(new ShipSim()).execute("generate", "--scenario", FAULTS,
                "--to", "jsonl://" + dir.resolve("out.jsonl"), "--loop");

        assertEquals(1, code);
        assertTrue(Files.notExists(dir.resolve("out.jsonl")) || isEmpty(dir.resolve("out.jsonl")), "nothing was written");
    }

    @Test
    void aFileSinkWithoutADurationIsRefused(@TempDir Path dir) throws IOException {
        Path endless = dir.resolve("endless.yaml");
        Files.writeString(endless, """
                rate: 1s
                ships:
                  - prefix: w1
                    model: { K: 0.05, T: 8.0, accel-time: 20.0 }
                """, StandardCharsets.UTF_8);

        int refused = new CommandLine(new ShipSim()).execute("generate", "--scenario", endless.toString(),
                "--to", "jsonl://" + dir.resolve("out.jsonl"));
        int accepted = new CommandLine(new ShipSim()).execute("generate", "--scenario", endless.toString(),
                "--to", "jsonl://" + dir.resolve("out.jsonl"), "--duration", "3s");

        assertEquals(1, refused);
        assertEquals(0, accepted);
    }

    private static boolean isEmpty(Path file) {
        try {
            return Files.size(file) == 0;
        } catch (IOException e) {
            return false;
        }
    }
}
