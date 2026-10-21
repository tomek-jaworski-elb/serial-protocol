package com.jaworski.shipsim.sink;

/**
 * A frame with what the generator knows about it, for sinks that record it (the {@code jsonl}
 * fixture sink) - other sinks send only the bytes.
 *
 * @param bytes   the message as sent, delimiter included - possibly two frames glued together
 * @param ship    the prefix of the ship it belongs to
 * @param fault   the fault that produced it, by its YAML name, or null
 * @param seconds when it was emitted, from the start of the scenario
 */
public record TaggedFrame(byte[] bytes, String ship, String fault, double seconds) {

    public TaggedFrame {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("a tagged frame needs bytes");
        }
    }
}
