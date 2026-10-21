package com.jaworski.shipsim;

import java.time.Duration;

/**
 * Keeps a live feed on schedule. Each call to {@link #next(Duration)} moves the deadline by the
 * given gap and sleeps until it, so the time spent sending does not stretch the gaps and the gap
 * between two passes of a looped run is the same as any other. The first call returns at once.
 * If the deadline is already behind - the machine stalled - the schedule restarts from now rather
 * than catching up with a burst.
 */
final class Pacer {

    private static final long UNSET = Long.MIN_VALUE;
    private long deadlineNanos = UNSET;

    void next(Duration gap) throws InterruptedException {
        long now = System.nanoTime();
        if (deadlineNanos == UNSET) {
            deadlineNanos = now;
            return;
        }
        deadlineNanos += gap.toNanos();
        long wait = deadlineNanos - now;
        if (wait > 0) {
            Thread.sleep(Duration.ofNanos(wait));
        } else {
            deadlineNanos = now;
        }
    }
}
