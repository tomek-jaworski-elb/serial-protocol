package com.jaworski.serialprotocol.service.ingest;

/**
 * The single entry point for a delimited frame, whatever transport brought it in.
 *
 * <p>The serial listener and the UDP handler are adapters: they take the bytes off their
 * transport and hand them here. Everything after that - the raw publish on {@code /rs}, the
 * translation, the checksum gate and the JSON publish on {@code /json} - happens once, in
 * {@link FrameIngestService}, so the transports cannot drift apart again. They had: the UDP path
 * skipped {@code /rs} and pushed frames with a bad checksum to every browser.</p>
 *
 * <p>Tests and simulators use this as the injection point above the port: no serial device, no
 * socket, the same pipeline.</p>
 */
public interface FrameIngress {

    /**
     * @param frame  the delimited frame exactly as received, trailing delimiter included
     * @param origin where it came from, for the log only - a port description, a UDP sender
     */
    void ingest(byte[] frame, String origin);
}
