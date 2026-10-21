package com.jaworski.shipsim.codec;

import java.util.Locale;

/**
 * One telemetry frame of a ship model, in engineering units, exactly the fields the application
 * decodes (proposals note, section 3).
 *
 * <p>The two-byte {@code reserved} block exists only in the 29-byte Lady Marie layout, at
 * offsets 13-14, and the application ignores it. It is carried here so that a recorded frame can
 * be reproduced byte for byte; a generated frame leaves it {@code null}, which encodes as zeros.</p>
 *
 * @param prefix          two ASCII characters naming the ship: {@code w1 b2 d3 c4 k5 l6}
 * @param heading         degrees, encoded x10 as uint16
 * @param bowTugDirection degrees, x100 as uint16
 * @param bowTugForce     x10 as int8
 * @param engine          throttle setting, int8, written as given
 * @param sternTugForce   x10 as int8
 * @param sternTugDirection degrees, x100 as uint16
 * @param rudder          degrees, x10 as int8 (so at most 12.7 either way)
 * @param speed           knots, x10 as int8
 * @param positionX       metres, north, float32 little-endian
 * @param positionY       metres, east, float32 little-endian
 * @param bowThruster     int8, written as given
 * @param gpsQuality      x100 as uint16
 * @param reserved        the two bytes at offsets 13-14 of the Lady Marie layout, or null
 */
public record Frame(
        String prefix,
        double heading,
        double bowTugDirection,
        double bowTugForce,
        int engine,
        double sternTugForce,
        double sternTugDirection,
        double rudder,
        double speed,
        float positionX,
        float positionY,
        int bowThruster,
        double gpsQuality,
        byte[] reserved) {

    /** The only ship that transmits the 29-byte layout. */
    public static final String LADY_MARIE_PREFIX = "l6";

    public Frame {
        if (prefix == null || prefix.length() != 2 || prefix.chars().anyMatch(c -> c > 127)) {
            throw new IllegalArgumentException("prefix must be two ASCII characters, was: " + prefix);
        }
        if (reserved != null && reserved.length != 2) {
            throw new IllegalArgumentException("reserved must be two bytes or null, was " + reserved.length + " bytes");
        }
    }

    /** Whether this frame uses the 29-byte layout with the reserved block. */
    public boolean isLadyMarieLayout() {
        return LADY_MARIE_PREFIX.equals(prefix.toLowerCase(Locale.ROOT));
    }

    /** The same frame under another prefix - the {@code unknown-prefix} fault. The layout follows the new prefix. */
    public Frame withPrefix(String newPrefix) {
        return new Frame(newPrefix, heading, bowTugDirection, bowTugForce, engine, sternTugForce, sternTugDirection,
                rudder, speed, positionX, positionY, bowThruster, gpsQuality, reserved);
    }

    /** The same frame with another heading - the {@code crlf-in-payload} fault forces 333.8 degrees. */
    public Frame withHeading(double newHeading) {
        return new Frame(prefix, newHeading, bowTugDirection, bowTugForce, engine, sternTugForce, sternTugDirection,
                rudder, speed, positionX, positionY, bowThruster, gpsQuality, reserved);
    }
}
