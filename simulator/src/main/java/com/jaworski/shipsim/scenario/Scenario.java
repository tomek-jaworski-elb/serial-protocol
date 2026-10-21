package com.jaworski.shipsim.scenario;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A whole scenario: the frame rate, how long it runs, the ships and the faults.
 *
 * @param rate     the gap between two frames of one ship
 * @param duration how long the scenario runs, or null for "until stopped"
 * @param ships    at least one
 * @param faults   kept sorted by time; every fault names a ship of the scenario
 */
public record Scenario(Duration rate, Duration duration, List<ShipSpec> ships, List<FaultSpec> faults) {

    public Scenario {
        if (rate == null || rate.isNegative() || rate.isZero()) {
            throw new IllegalArgumentException("'rate' must be a positive duration");
        }
        if (duration != null && (duration.isNegative() || duration.isZero())) {
            throw new IllegalArgumentException("'duration' must be a positive duration when given");
        }
        if (ships == null || ships.isEmpty()) {
            throw new IllegalArgumentException("a scenario needs at least one ship");
        }
        Set<String> prefixes = new HashSet<>();
        for (ShipSpec ship : ships) {
            if (!prefixes.add(ship.prefix())) {
                throw new IllegalArgumentException("ship prefix " + ship.prefix() + " appears twice");
            }
        }
        faults = faults == null ? List.of() : faults.stream().sorted(Comparator.comparing(FaultSpec::at)).toList();
        for (FaultSpec fault : faults) {
            if (!prefixes.contains(fault.ship())) {
                throw new IllegalArgumentException("fault at " + fault.at() + " names ship " + fault.ship() + ", which is not in the scenario");
            }
        }
        ships = List.copyOf(ships);
    }

    /** The same scenario with another duration - the {@code --duration} override. */
    public Scenario withDuration(Duration newDuration) {
        return new Scenario(rate, newDuration, ships, faults);
    }
}
