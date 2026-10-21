package com.jaworski.shipsim.sink;

import java.io.PrintStream;
import java.util.Arrays;

/**
 * Prints each frame as a Java array literal, one per line - the same notation the application
 * logs, so the output can be pasted into a test or replayed with {@code replay --from}.
 */
public final class StdoutSink implements Sink {

    private final PrintStream out;

    public StdoutSink(PrintStream out) {
        this.out = out;
    }

    @Override
    public void send(byte[] frame) {
        out.println(Arrays.toString(frame));
    }

    @Override
    public boolean paced() {
        return false;
    }

    @Override
    public String toString() {
        return "stdout";
    }
}
