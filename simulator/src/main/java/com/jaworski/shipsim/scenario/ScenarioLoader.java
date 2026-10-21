package com.jaworski.shipsim.scenario;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jaworski.shipsim.Durations;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads a scenario file and the per-ship parameter file. The YAML is bound to plain records
 * first (every key kebab-case, unknown keys rejected), then converted and validated into the
 * domain records, so a mistake is reported with the field's name.
 *
 * <pre>
 * rate: 1s
 * duration: 120s
 * ships:
 *   - prefix: w1
 *     model: { K: 0.05, T: 8.0, accel-time: 20.0 }     # optional when ships.yaml has the ship
 *     start: { x: 71.1, y: 59.7, heading: 45.0, speed: 2.0, engine: 50 }
 *     timeline:
 *       - at: 20s
 *         set: { rudder: 8.0 }
 * faults:
 *   - { at: 30s, ship: w1, kind: bad-crc }
 *   - { at: 50s, ship: w1, kind: burst, count: 20 }
 *   - { at: 60s, ship: w1, kind: silence, seconds: 30s }
 * </pre>
 */
public final class ScenarioLoader {

    private static final YAMLMapper MAPPER = YAMLMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    record ScenarioYaml(String rate, String duration, List<ShipYaml> ships, List<FaultYaml> faults) {
    }

    record ShipYaml(String prefix, MotionParamsYaml model, Map<String, Double> start, List<TimelineYaml> timeline) {
    }

    record MotionParamsYaml(@JsonProperty("K") Double k, @JsonProperty("T") Double t, Double accelTime) {
    }

    record TimelineYaml(String at, Map<String, Double> set) {
    }

    record FaultYaml(String at, String ship, String kind, Integer count, String seconds) {
    }

    private ScenarioLoader() {
    }

    /**
     * @param file     the scenario
     * @param defaults motion parameters by prefix for ships without a {@code model} block
     */
    public static Scenario load(Path file, Map<String, MotionParams> defaults) throws IOException {
        ScenarioYaml yaml = read(file, ScenarioYaml.class);
        if (yaml.rate() == null) {
            throw new IllegalArgumentException(file + ": 'rate' is required");
        }
        Duration rate = Durations.parse(yaml.rate());
        Duration duration = yaml.duration() == null ? null : Durations.parse(yaml.duration());
        List<ShipSpec> ships = new ArrayList<>();
        for (ShipYaml ship : yaml.ships() == null ? List.<ShipYaml>of() : yaml.ships()) {
            ships.add(toShip(ship, defaults, file));
        }
        List<FaultSpec> faults = new ArrayList<>();
        for (FaultYaml fault : yaml.faults() == null ? List.<FaultYaml>of() : yaml.faults()) {
            faults.add(toFault(fault));
        }
        return new Scenario(rate, duration, ships, faults);
    }

    /** {@code ships.yaml}: a map from prefix to {@code { K, T, accel-time }}. */
    public static Map<String, MotionParams> loadShipParameters(Path file) throws IOException {
        Map<String, MotionParamsYaml> yaml = MAPPER.readValue(Files.readString(file, StandardCharsets.UTF_8),
                new TypeReference<Map<String, MotionParamsYaml>>() {
                });
        Map<String, MotionParams> params = new LinkedHashMap<>();
        yaml.forEach((prefix, p) -> params.put(prefix, toParams(p, "ship " + prefix + " in " + file)));
        return params;
    }

    private static <T> T read(Path file, Class<T> type) throws IOException {
        try {
            return MAPPER.readValue(Files.readString(file, StandardCharsets.UTF_8), type);
        } catch (tools.jackson.core.JacksonException e) {
            throw new IllegalArgumentException(file + ": " + e.getOriginalMessage(), e);
        }
    }

    private static ShipSpec toShip(ShipYaml ship, Map<String, MotionParams> defaults, Path file) {
        if (ship.prefix() == null) {
            throw new IllegalArgumentException(file + ": a ship has no 'prefix'");
        }
        MotionParams model = ship.model() != null
                ? toParams(ship.model(), "ship " + ship.prefix() + " in " + file)
                : defaults.get(ship.prefix());
        if (model == null) {
            throw new IllegalArgumentException(file + ": ship " + ship.prefix()
                    + " has no 'model' and the ship parameter file does not list it");
        }
        Map<String, Double> start = ship.start() == null ? Map.of() : ship.start();
        List<TimelineEntry> timeline = new ArrayList<>();
        for (TimelineYaml entry : ship.timeline() == null ? List.<TimelineYaml>of() : ship.timeline()) {
            if (entry.at() == null) {
                throw new IllegalArgumentException(file + ": ship " + ship.prefix() + " has a timeline entry without 'at'");
            }
            timeline.add(new TimelineEntry(Durations.parse(entry.at()), entry.set() == null ? Map.of() : entry.set()));
        }
        try {
            return new ShipSpec(ship.prefix(), model, ShipState.fromMap(new HashMap<>(start)), timeline);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(file + ": ship " + ship.prefix() + ": " + e.getMessage(), e);
        }
    }

    private static MotionParams toParams(MotionParamsYaml p, String where) {
        if (p.k() == null || p.t() == null || p.accelTime() == null) {
            throw new IllegalArgumentException(where + ": 'model' needs K, T and accel-time");
        }
        try {
            return new MotionParams(p.k(), p.t(), p.accelTime());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(where + ": " + e.getMessage(), e);
        }
    }

    private static FaultSpec toFault(FaultYaml fault) {
        if (fault.at() == null) {
            throw new IllegalArgumentException("a fault has no 'at'");
        }
        return new FaultSpec(
                Durations.parse(fault.at()),
                fault.ship(),
                FaultKind.fromYaml(fault.kind()),
                fault.count() == null ? FaultSpec.DEFAULT_BURST_COUNT : fault.count(),
                fault.seconds() == null ? FaultSpec.DEFAULT_SILENCE : Durations.parse(fault.seconds()));
    }
}
