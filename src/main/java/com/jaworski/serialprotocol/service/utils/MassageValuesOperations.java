package com.jaworski.serialprotocol.service.utils;

import org.springframework.stereotype.Component;

@Component
public class MassageValuesOperations {

    private static final int HEADING_CORRECTION = 0;

    public Double headingAlignment(Double heading) {
        return (heading + HEADING_CORRECTION) % 360;
    }
}
