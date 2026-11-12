package com.jaworski.serialprotocol.service.tracks;

/** The request itself is wrong; the message is meant for the user. */
public class InvalidTrackQueryException extends RuntimeException {

    public InvalidTrackQueryException(String message) {
        super(message);
    }
}
