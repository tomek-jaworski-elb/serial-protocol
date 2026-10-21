package com.jaworski.shipsim;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PacerTest {

    @Test
    void theFirstCallReturnsAtOnceAndLaterCallsKeepTheGap() throws InterruptedException {
        Pacer pacer = new Pacer();
        long start = System.nanoTime();

        pacer.next(Duration.ofMillis(500));
        long afterFirst = System.nanoTime() - start;
        assertTrue(afterFirst < 200_000_000L, "first call waited " + afterFirst / 1_000_000 + " ms");

        pacer.next(Duration.ofMillis(60));
        pacer.next(Duration.ofMillis(60));
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsed >= 110, "two 60 ms gaps took only " + elapsed + " ms");
    }

    @Test
    void aStalledScheduleRestartsFromNowInsteadOfBursting() throws InterruptedException {
        Pacer pacer = new Pacer();
        pacer.next(Duration.ofMillis(10));
        Thread.sleep(80);

        long start = System.nanoTime();
        pacer.next(Duration.ofMillis(10));
        pacer.next(Duration.ofMillis(30));
        long elapsed = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsed >= 25, "the gap after a stall was " + elapsed + " ms, so the backlog was not skipped");
    }
}
