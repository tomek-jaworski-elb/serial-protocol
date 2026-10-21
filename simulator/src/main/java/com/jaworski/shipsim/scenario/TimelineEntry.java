package com.jaworski.shipsim.scenario;

import java.time.Duration;
import java.util.Map;

/**
 * A scripted change: at {@code at}, the ship's fields in {@code set} take the given values.
 * Validated against {@link ShipState#FIELDS} when the scenario is read.
 */
public record TimelineEntry(Duration at, Map<String, Double> set) {

    public TimelineEntry {
        if (at == null || at.isNegative()) {
            throw new IllegalArgumentException("timeline 'at' must be a non-negative duration");
        }
        if (set == null || set.isEmpty()) {
            throw new IllegalArgumentException("timeline entry at " + at + " sets nothing");
        }
        set = Map.copyOf(set);
    }
}
