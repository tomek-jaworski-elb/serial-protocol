package com.jaworski.shipsim.motion;

import com.jaworski.shipsim.scenario.MotionParams;
import com.jaworski.shipsim.scenario.ShipSpec;
import com.jaworski.shipsim.scenario.ShipState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipMotionTest {

    private static final MotionParams IMMEDIATE = new MotionParams(0.1, 0, 0);
    private static final double EXACT = 1e-9;

    @Test
    void aNorthboundRunIncreasesXAndLeavesYAlone() {
        ShipMotion ship = ship(0, 2.0, 0, IMMEDIATE);

        steps(ship, 10);

        assertEquals(10 * 2.0 * ShipMotion.KNOT_IN_METRES_PER_SECOND, ship.state().x(), EXACT);
        assertEquals(0.0, ship.state().y(), 0.0);
        assertEquals(0.0, ship.state().heading(), 0.0);
    }

    @Test
    void anEastboundRunIncreasesY() {
        ShipMotion ship = ship(90, 2.0, 0, IMMEDIATE);

        steps(ship, 10);

        assertEquals(10 * 2.0 * ShipMotion.KNOT_IN_METRES_PER_SECOND, ship.state().y(), EXACT);
        assertEquals(0.0, ship.state().x(), EXACT);
    }

    @Test
    void theYawRateApproachesKTimesRudderWithTimeConstantT() {
        MotionParams params = new MotionParams(0.05, 8.0, 0);
        ShipMotion ship = ship(0, 0, 10, params);
        double steady = 0.05 * 10;

        steps(ship, 8);
        // Explicit Euler with dt = 1: r_n = r_ss * (1 - (1 - dt/T)^n).
        assertEquals(steady * (1 - Math.pow(1 - 1.0 / 8.0, 8)), ship.yawRate(), EXACT);

        steps(ship, 400);
        assertEquals(steady, ship.yawRate(), 1e-6);
    }

    @Test
    void theSpeedApproachesTheSetpointWithAccelTime() {
        MotionParams params = new MotionParams(0.05, 0, 20.0);
        ShipMotion ship = ship(0, 0, 0, params);
        ship.set(Map.of("speed", 4.0));

        steps(ship, 20);

        assertEquals(4.0 * (1 - Math.pow(1 - 1.0 / 20.0, 20)), ship.speed(), EXACT);
        assertEquals(ship.speed(), ship.frame().speed(), 0.0, "the frame carries the current speed, not the setpoint");
    }

    @Test
    void aConstantRudderWithAnImmediateResponseClosesACircle() {
        // K = 0.1 and rudder 10 give exactly 1 degree per second: 360 steps are one full turn.
        ShipMotion ship = ship(0, 2.0, 10, IMMEDIATE);

        steps(ship, 360);

        assertEquals(0.0, ship.state().x(), 1e-6);
        assertEquals(0.0, ship.state().y(), 1e-6);
        assertEquals(0.0, ship.state().heading(), 1e-9);
    }

    @Test
    void theRadiusOfTheCircleFollowsK() {
        // Diameter of the turning circle = the east extent of a full clockwise turn from heading 0.
        double speedMetresPerSecond = 2.0 * ShipMotion.KNOT_IN_METRES_PER_SECOND;
        double diameterAtK01 = eastExtent(ship(0, 2.0, 10, new MotionParams(0.1, 0, 0)), 360);
        double diameterAtK02 = eastExtent(ship(0, 2.0, 10, new MotionParams(0.2, 0, 0)), 180);

        // r = K * rudder = 1 deg/s: R = v / omega with omega in rad/s.
        assertEquals(2 * speedMetresPerSecond / Math.toRadians(1.0), diameterAtK01, 0.01 * diameterAtK01);
        assertEquals(diameterAtK01 / 2, diameterAtK02, 0.01 * diameterAtK02, "doubling K halves the circle");
    }

    @Test
    void theHeadingStaysWithinTheCircle() {
        ShipMotion ship = ship(359.5, 0, 10, IMMEDIATE);

        ship.step(1.0);

        assertTrue(ship.state().heading() < 360.0 && ship.state().heading() >= 0.0, "heading " + ship.state().heading());
        assertEquals(0.5, ship.state().heading(), EXACT);
    }

    @Test
    void aTimelineCannotTeleportAShip() {
        ShipMotion ship = ship(0, 0, 0, IMMEDIATE);

        IllegalArgumentException e = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> ship.set(Map.of("x", 100.0)));

        assertTrue(e.getMessage().contains("teleport"), e.getMessage());
    }

    private static ShipMotion ship(double heading, double speed, double rudder, MotionParams params) {
        ShipState start = ShipState.fromMap(Map.of("heading", heading, "speed", speed, "rudder", rudder));
        return new ShipMotion(new ShipSpec("w1", params, start, List.of()), params);
    }

    private static void steps(ShipMotion ship, int count) {
        for (int i = 0; i < count; i++) {
            ship.step(1.0);
        }
    }

    private static double eastExtent(ShipMotion ship, int count) {
        double maxY = 0;
        for (int i = 0; i < count; i++) {
            ship.step(1.0);
            maxY = Math.max(maxY, ship.state().y());
        }
        return maxY;
    }
}
