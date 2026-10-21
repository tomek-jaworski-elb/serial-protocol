package com.jaworski.shipsim.sink;

import java.io.IOException;

/** Where frames go. One call per frame, delimiter included, exactly as the application expects it. */
public interface Sink extends AutoCloseable {

    void send(byte[] frame) throws IOException;

    @Override
    default void close() throws IOException {
    }
}
