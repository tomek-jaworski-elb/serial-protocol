package com.jaworski.shipsim.scenario;

/**
 * The per-ship parameters of the motion model (Nomoto first order plus a speed lag), see
 * {@code ShipMotion}. They come from a scenario's {@code model} block or from {@code ships.yaml}.
 *
 * @param k         steady yaw rate in degrees per second per degree of rudder
 * @param t         yaw time constant in seconds; 0 makes the yaw rate follow the rudder at once
 * @param accelTime time constant of the speed response in seconds; 0 makes speed changes immediate
 */
public record MotionParams(double k, double t, double accelTime) {

    public MotionParams {
        require(k, "K");
        require(t, "T");
        require(accelTime, "accel-time");
    }

    private static void require(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be a finite number >= 0, was " + value);
        }
    }
}
