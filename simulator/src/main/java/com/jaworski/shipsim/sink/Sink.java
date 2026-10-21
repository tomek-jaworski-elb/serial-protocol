package com.jaworski.shipsim.sink;

import java.io.IOException;

/** Where frames go. One call per message, delimiter included, exactly as the application expects it. */
public interface Sink extends AutoCloseable {

    void send(byte[] frame) throws IOException;

    /** A frame with its provenance; only recording sinks care, the rest send the bytes. */
    default void send(TaggedFrame frame) throws IOException {
        send(frame.bytes());
    }

    /**
     * Whether the sender should wait between ticks. A live transport is paced at the scenario
     * rate; a file is written at once.
     */
    default boolean paced() {
        return true;
    }

    @Override
    default void close() throws IOException {
    }
}
