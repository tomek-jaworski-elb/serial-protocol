package com.jaworski.shipsim.motion;

import com.jaworski.shipsim.codec.Frame;
import com.jaworski.shipsim.scenario.MotionParams;
import com.jaworski.shipsim.scenario.ShipSpec;
import com.jaworski.shipsim.scenario.ShipState;

import java.util.Map;

/**
 * How a ship moves between two frames: a fixture, not physics, but one with the inertia of the
 * real models.
 *
 * <ul>
 *   <li>Yaw, Nomoto first order: {@code r' = (K * rudder - r) / T}, {@code heading += r * dt},
 *       heading kept in [0, 360). With {@code T} shorter than a step the yaw rate follows the
 *       rudder at once, which is also what keeps the explicit Euler step from overshooting.</li>
 *   <li>Speed, first-order lag to the scripted setpoint: {@code v' = (v_set - v) / accel-time};
 *       {@code accel-time} shorter than a step means immediate.</li>
 *   <li>Position: {@code x += v * cos(heading) * dt} (north), {@code y += v * sin(heading) * dt}
 *       (east), speed in knots converted to m/s. 0 degrees is north, headings grow clockwise, a
 *       positive rudder turns clockwise - conventions of the simulator, not verified properties
 *       of the models.</li>
 * </ul>
 *
 * <p>Everything goes through {@link StrictMath}, so a committed fixture is bit-identical on every
 * JVM. {@code engine} is scripted and is not coupled to speed, by decision.</p>
 */
public final class ShipMotion {

    public static final double KNOT_IN_METRES_PER_SECOND = 0.514444;

    private final String prefix;
    private final MotionParams params;
    private ShipState state;
    private double speed;
    private double yawRate;

    public ShipMotion(ShipSpec spec, MotionParams params) {
        this.prefix = spec.prefix();
        this.params = params;
        this.state = spec.start();
        this.speed = spec.start().speed();
        this.yawRate = 0;
    }

    /** Applies a timeline entry. */
    public void set(Map<String, Double> changes) {
        state = state.apply(changes);
    }

    /** Advances the ship by {@code dt} seconds. */
    public void step(double dt) {
        double targetYawRate = params.k() * state.rudder();
        if (params.t() < dt) {
            yawRate = targetYawRate;
        } else {
            yawRate += (targetYawRate - yawRate) * dt / params.t();
        }
        double heading = ShipState.normalizeHeading(state.heading() + yawRate * dt);

        if (params.accelTime() < dt) {
            speed = state.speed();
        } else {
            speed += (state.speed() - speed) * dt / params.accelTime();
        }

        double metresPerSecond = speed * KNOT_IN_METRES_PER_SECOND;
        double radians = StrictMath.toRadians(heading);
        double x = state.x() + metresPerSecond * StrictMath.cos(radians) * dt;
        double y = state.y() + metresPerSecond * StrictMath.sin(radians) * dt;
        state = state.movedTo(x, y, heading);
    }

    /** The frame the ship would send now: the current speed, not the setpoint. */
    public Frame frame() {
        return new Frame(prefix, state.heading(), state.bowTugDirection(), state.bowTugForce(),
                (int) Math.round(state.engine()), state.sternTugForce(), state.sternTugDirection(), state.rudder(),
                speed, (float) state.x(), (float) state.y(), (int) Math.round(state.bowThruster()), state.gpsQuality(), null);
    }

    public String prefix() {
        return prefix;
    }

    public ShipState state() {
        return state;
    }

    /** Current speed in knots, lagging behind the setpoint. */
    public double speed() {
        return speed;
    }

    /** Current yaw rate in degrees per second. */
    public double yawRate() {
        return yawRate;
    }
}
