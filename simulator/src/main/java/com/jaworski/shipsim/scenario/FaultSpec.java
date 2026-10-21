package com.jaworski.shipsim.scenario;

import java.time.Duration;

/**
 * One fault in a scenario.
 *
 * @param at      when, from the start of the scenario
 * @param ship    the prefix of the ship it hits
 * @param kind    what happens
 * @param count   {@code burst} only: how many copies
 * @param silence {@code silence} only: for how long
 */
public record FaultSpec(Duration at, String ship, FaultKind kind, int count, Duration silence) {

    public static final int DEFAULT_BURST_COUNT = 100;
    public static final Duration DEFAULT_SILENCE = Duration.ofSeconds(30);

    public FaultSpec {
        if (at == null || at.isNegative()) {
            throw new IllegalArgumentException("fault 'at' must be a non-negative duration");
        }
        if (ship == null || ship.isBlank()) {
            throw new IllegalArgumentException("fault 'ship' is required");
        }
        if (kind == null) {
            throw new IllegalArgumentException("fault 'kind' is required");
        }
        if (count < 1) {
            throw new IllegalArgumentException("fault 'count' must be at least 1, was " + count);
        }
        if (silence == null || silence.isNegative() || silence.isZero()) {
            throw new IllegalArgumentException("fault 'seconds' must be a positive duration");
        }
    }
}
