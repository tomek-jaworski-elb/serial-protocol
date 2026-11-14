package com.jaworski.serialprotocol.service.tracks;

/** Too many log scans are running already. */
public class TrackQueryBusyException extends RuntimeException {

    public TrackQueryBusyException() {
        super("The server is busy with another track query, try again in a moment.");
    }
}
