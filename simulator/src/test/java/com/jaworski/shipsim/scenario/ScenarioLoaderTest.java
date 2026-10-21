package com.jaworski.shipsim.scenario;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioLoaderTest {

    private static final Path SCENARIOS = Path.of("scenarios");
    private static final Map<String, MotionParams> NO_DEFAULTS = Map.of();

    @Test
    void readsAScenarioWithShipsTimelineAndFaults(@TempDir Path dir) throws IOException {
        Path file = write(dir, """
                rate: 500ms
                duration: 2m
                ships:
                  - prefix: w1
                    model: { K: 0.05, T: 8.0, accel-time: 20.0 }
                    start: { x: 71.1, y: 59.7, heading: 45, speed: 2.0, engine: 50, bow-thruster: 111 }
                    timeline:
                      - at: 60s
                        set: { rudder: 8.0, engine: 60 }
                      - at: 20s
                        set: { speed: 3.0 }
                faults:
                  - { at: 30s, ship: w1, kind: burst, count: 20 }
                  - { at: 10s, ship: w1, kind: bad-crc }
                """);

        Scenario scenario = ScenarioLoader.load(file, NO_DEFAULTS);

        assertEquals(Duration.ofMillis(500), scenario.rate());
        assertEquals(Duration.ofMinutes(2), scenario.duration());
        ShipSpec ship = scenario.ships().get(0);
        assertEquals("w1", ship.prefix());
        assertEquals(new MotionParams(0.05, 8.0, 20.0), ship.model());
        assertEquals(71.1, ship.start().x());
        assertEquals(45.0, ship.start().heading());
        assertEquals(111.0, ship.start().bowThruster());
        assertEquals(List.of(Duration.ofSeconds(20), Duration.ofSeconds(60)),
                ship.timeline().stream().map(TimelineEntry::at).toList(), "timeline is sorted");
        assertEquals(List.of(Duration.ofSeconds(10), Duration.ofSeconds(30)),
                scenario.faults().stream().map(FaultSpec::at).toList(), "faults are sorted");
        assertEquals(20, scenario.faults().get(1).count());
        assertEquals(FaultSpec.DEFAULT_SILENCE, scenario.faults().get(0).silence());
    }

    @Test
    void theShipParameterFileSuppliesAMissingModel(@TempDir Path dir) throws IOException {
        Path params = write(dir, "ships.yaml", """
                w1: { K: 0.05, T: 8.0, accel-time: 20.0 }
                l6: { K: 0.04, T: 10.0, accel-time: 25.0 }
                """);
        Path file = write(dir, """
                rate: 1s
                ships:
                  - prefix: l6
                    start: { heading: 10 }
                """);

        Scenario scenario = ScenarioLoader.load(file, ScenarioLoader.loadShipParameters(params));

        assertEquals(new MotionParams(0.04, 10.0, 25.0), scenario.ships().get(0).model());
        assertNull(scenario.duration());
    }

    @Test
    void aShipWithoutAnyModelIsRejectedByName(@TempDir Path dir) throws IOException {
        Path file = write(dir, """
                rate: 1s
                ships:
                  - prefix: k5
                    start: { heading: 10 }
                """);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ScenarioLoader.load(file, NO_DEFAULTS));

        assertTrue(e.getMessage().contains("k5") && e.getMessage().contains("model"), e.getMessage());
    }

    @Test
    void anUnknownFieldInATimelineIsRejectedByName(@TempDir Path dir) throws IOException {
        Path file = write(dir, """
                rate: 1s
                ships:
                  - prefix: w1
                    model: { K: 0.05, T: 8.0, accel-time: 20.0 }
                    start: { heading: 10 }
                    timeline:
                      - at: 5s
                        set: { ruder: 5 }
                """);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ScenarioLoader.load(file, NO_DEFAULTS));

        assertTrue(e.getMessage().contains("ruder"), e.getMessage());
    }

    @Test
    void aValueOutsideTheFrameRangeIsRejectedByName(@TempDir Path dir) throws IOException {
        Path file = write(dir, """
                rate: 1s
                ships:
                  - prefix: w1
                    model: { K: 0.05, T: 8.0, accel-time: 20.0 }
                    start: { rudder: 20 }
                """);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ScenarioLoader.load(file, NO_DEFAULTS));

        assertTrue(e.getMessage().contains("rudder"), e.getMessage());
    }

    @Test
    void anUnknownFaultKindOrTopLevelKeyIsRejected(@TempDir Path dir) throws IOException {
        Path badKind = write(dir, "kind.yaml", """
                rate: 1s
                ships:
                  - prefix: w1
                    model: { K: 0.05, T: 8.0, accel-time: 20.0 }
                faults:
                  - { at: 1s, ship: w1, kind: explode }
                """);
        Path badKey = write(dir, "key.yaml", """
                rat: 1s
                ships:
                  - prefix: w1
                    model: { K: 0.05, T: 8.0, accel-time: 20.0 }
                """);

        assertTrue(assertThrows(IllegalArgumentException.class, () -> ScenarioLoader.load(badKind, NO_DEFAULTS))
                .getMessage().contains("explode"));
        assertThrows(IllegalArgumentException.class, () -> ScenarioLoader.load(badKey, NO_DEFAULTS));
    }

    @Test
    void aFaultMustNameAShipOfTheScenario(@TempDir Path dir) throws IOException {
        Path file = write(dir, """
                rate: 1s
                ships:
                  - prefix: w1
                    model: { K: 0.05, T: 8.0, accel-time: 20.0 }
                faults:
                  - { at: 1s, ship: b2, kind: bad-crc }
                """);

        assertTrue(assertThrows(IllegalArgumentException.class, () -> ScenarioLoader.load(file, NO_DEFAULTS))
                .getMessage().contains("b2"));
    }

    @Test
    void theShippedScenariosLoadWithTheShippedParameters() throws IOException {
        Map<String, MotionParams> params = ScenarioLoader.loadShipParameters(SCENARIOS.resolve("ships.yaml"));
        assertEquals(6, params.size());

        Scenario sixShips = ScenarioLoader.load(SCENARIOS.resolve("six-ships.yaml"), params);
        Scenario wholeLake = ScenarioLoader.load(SCENARIOS.resolve("whole-lake.yaml"), params);
        Scenario faults = ScenarioLoader.load(SCENARIOS.resolve("faults.yaml"), params);

        assertEquals(6, sixShips.ships().size());
        assertEquals(6, wholeLake.ships().size());
        assertEquals(3, faults.ships().size());
        assertEquals(7, faults.faults().size());
    }

    private static Path write(Path dir, String yaml) throws IOException {
        return write(dir, "scenario.yaml", yaml);
    }

    private static Path write(Path dir, String name, String yaml) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
        return file;
    }
}
