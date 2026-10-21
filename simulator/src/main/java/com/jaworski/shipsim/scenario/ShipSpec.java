package com.jaworski.shipsim.scenario;

import java.util.Comparator;
import java.util.List;

/**
 * One ship of a scenario.
 *
 * @param prefix   the two-character ship prefix, {@code w1 b2 d3 c4 k5 l6}
 * @param model    its motion parameters, or null to take them from the ship parameter file
 * @param start    its state at time zero
 * @param timeline scripted changes, kept sorted by time
 */
public record ShipSpec(String prefix, MotionParams model, ShipState start, List<TimelineEntry> timeline) {

    public ShipSpec {
        if (prefix == null || prefix.length() != 2 || prefix.chars().anyMatch(c -> c > 127)) {
            throw new IllegalArgumentException("ship prefix must be two ASCII characters, was: " + prefix);
        }
        if (start == null) {
            throw new IllegalArgumentException("ship " + prefix + " has no 'start'");
        }
        timeline = timeline == null ? List.of() : timeline.stream().sorted(Comparator.comparing(TimelineEntry::at)).toList();
        // A timeline entry must be valid against some state; applying them in order finds a bad key or value now.
        ShipState probe = start;
        for (TimelineEntry entry : timeline) {
            probe = probe.apply(entry.set());
        }
    }
}
