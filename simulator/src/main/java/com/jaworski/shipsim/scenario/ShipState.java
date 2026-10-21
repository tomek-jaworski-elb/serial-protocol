package com.jaworski.shipsim.scenario;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a ship reports, in engineering units: its pose and every scripted field of the frame.
 * Immutable; {@link #apply(Map)} returns the state after a timeline entry.
 *
 * <p>Ranges are those of the wire format and are checked here, so a scenario with an impossible
 * value fails when it is read, naming the field, rather than when a frame is encoded. {@code speed}
 * is the setpoint the ship is ordered to; the motion model lags behind it.</p>
 */
public record ShipState(
        double x,
        double y,
        double heading,
        double speed,
        double rudder,
        double engine,
        double bowThruster,
        double bowTugForce,
        double bowTugDirection,
        double sternTugForce,
        double sternTugDirection,
        double gpsQuality) {

    /** The keys a scenario may use in {@code start} and in a timeline {@code set}. */
    public static final List<String> FIELDS = List.of("x", "y", "heading", "speed", "rudder", "engine",
            "bow-thruster", "bow-tug-force", "bow-tug-direction", "stern-tug-force", "stern-tug-direction", "gps-quality");
    /** Keys that describe where the ship is; a timeline may not teleport it. */
    public static final Set<String> POSE_FIELDS = Set.of("x", "y", "heading");

    private static final double INT8_TENTHS_MAX = 12.7;
    private static final double UINT16_HUNDREDTHS_MAX = 655.35;

    public ShipState {
        finite(x, "x");
        finite(y, "y");
        heading = normalizeHeading(finite(heading, "heading"));
        within(speed, -INT8_TENTHS_MAX, INT8_TENTHS_MAX, "speed");
        within(rudder, -INT8_TENTHS_MAX, INT8_TENTHS_MAX, "rudder");
        within(engine, Byte.MIN_VALUE, Byte.MAX_VALUE, "engine");
        within(bowThruster, Byte.MIN_VALUE, Byte.MAX_VALUE, "bow-thruster");
        within(bowTugForce, -INT8_TENTHS_MAX, INT8_TENTHS_MAX, "bow-tug-force");
        within(bowTugDirection, 0, UINT16_HUNDREDTHS_MAX, "bow-tug-direction");
        within(sternTugForce, -INT8_TENTHS_MAX, INT8_TENTHS_MAX, "stern-tug-force");
        within(sternTugDirection, 0, UINT16_HUNDREDTHS_MAX, "stern-tug-direction");
        within(gpsQuality, 0, UINT16_HUNDREDTHS_MAX, "gps-quality");
    }

    /** A state built from a scenario's {@code start} map; every field defaults to 0. */
    public static ShipState fromMap(Map<String, Double> values) {
        ShipState zero = new ShipState(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        return zero.apply(values, true);
    }

    /** The state after a timeline entry; pose fields are rejected, a timeline cannot teleport a ship. */
    public ShipState apply(Map<String, Double> changes) {
        return apply(changes, false);
    }

    /** The state after moving: the motion model's output. */
    public ShipState movedTo(double newX, double newY, double newHeading) {
        return new ShipState(newX, newY, newHeading, speed, rudder, engine, bowThruster,
                bowTugForce, bowTugDirection, sternTugForce, sternTugDirection, gpsQuality);
    }

    public static double normalizeHeading(double degrees) {
        double h = degrees % 360.0;
        return h < 0 ? h + 360.0 : h;
    }

    private ShipState apply(Map<String, Double> changes, boolean allowPose) {
        Set<String> unknown = new LinkedHashSet<>(changes.keySet());
        FIELDS.forEach(unknown::remove);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("unknown field(s) " + unknown + "; allowed: " + FIELDS);
        }
        if (!allowPose) {
            for (String pose : POSE_FIELDS) {
                if (changes.containsKey(pose)) {
                    throw new IllegalArgumentException("'" + pose + "' can only be set in 'start': a timeline cannot teleport a ship");
                }
            }
        }
        return new ShipState(
                changes.getOrDefault("x", x),
                changes.getOrDefault("y", y),
                changes.getOrDefault("heading", heading),
                changes.getOrDefault("speed", speed),
                changes.getOrDefault("rudder", rudder),
                changes.getOrDefault("engine", engine),
                changes.getOrDefault("bow-thruster", bowThruster),
                changes.getOrDefault("bow-tug-force", bowTugForce),
                changes.getOrDefault("bow-tug-direction", bowTugDirection),
                changes.getOrDefault("stern-tug-force", sternTugForce),
                changes.getOrDefault("stern-tug-direction", sternTugDirection),
                changes.getOrDefault("gps-quality", gpsQuality));
    }

    private static double finite(double value, String field) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(field + " must be a finite number, was " + value);
        }
        return value;
    }

    private static void within(double value, double min, double max, String field) {
        finite(value, field);
        if (value < min || value > max) {
            throw new IllegalArgumentException(field + " must be within " + min + ".." + max + ", was " + value);
        }
    }
}
