package com.jaworski.serialprotocol.exception;

public class CustomRestException extends Exception {
    public CustomRestException(String message) {
        super(message);
    }

    public CustomRestException(String message, Exception e) {
        super(message, e);
    }
}
