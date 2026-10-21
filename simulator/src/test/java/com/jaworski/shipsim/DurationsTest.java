package com.jaworski.shipsim;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DurationsTest {

    @Test
    void parsesMillisecondsSecondsAndMinutes() {
        assertEquals(Duration.ofMillis(250), Durations.parse("250ms"));
        assertEquals(Duration.ofSeconds(1), Durations.parse("1s"));
        assertEquals(Duration.ofMillis(2_500), Durations.parse("2.5s"));
        assertEquals(Duration.ofMinutes(3), Durations.parse(" 3m "));
    }

    @Test
    void rejectsAnythingElse() {
        assertThrows(IllegalArgumentException.class, () -> Durations.parse("1"));
        assertThrows(IllegalArgumentException.class, () -> Durations.parse("1h"));
        assertThrows(IllegalArgumentException.class, () -> Durations.parse(null));
    }

    @Test
    void scaleDividesByTheSpeed() {
        assertEquals(Duration.ofMillis(500), Durations.scale(Duration.ofSeconds(1), 2.0));
        assertEquals(Duration.ofSeconds(2), Durations.scale(Duration.ofSeconds(1), 0.5));
        assertThrows(IllegalArgumentException.class, () -> Durations.scale(Duration.ofSeconds(1), 0));
    }
}
