package com.jaworski.shipsim;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the short duration notation of the command line: {@code 250ms}, {@code 1s}, {@code 2.5s}, {@code 3m}. */
public final class Durations {

    private static final Pattern NOTATION = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)\\s*(ms|s|m)\\s*$");

    private Durations() {
    }

    public static Duration parse(String text) {
        Matcher m = text == null ? null : NOTATION.matcher(text);
        if (m == null || !m.matches()) {
            throw new IllegalArgumentException("not a duration: '" + text + "' (use e.g. 250ms, 1s, 2.5s, 3m)");
        }
        double amount = Double.parseDouble(m.group(1));
        double millis = switch (m.group(2)) {
            case "ms" -> amount;
            case "s" -> amount * 1_000;
            default -> amount * 60_000;
        };
        return Duration.ofNanos(Math.round(millis * 1_000_000));
    }

    /** {@code d / speed}: a speed of 2 halves every gap, a speed of 0.5 doubles it. */
    public static Duration scale(Duration d, double speed) {
        if (!(speed > 0) || Double.isInfinite(speed)) {
            throw new IllegalArgumentException("speed must be a positive number, was " + speed);
        }
        return Duration.ofNanos(Math.round(d.toNanos() / speed));
    }
}
