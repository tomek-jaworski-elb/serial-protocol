package com.jaworski.serialprotocol.service.utils;

import org.springframework.stereotype.Component;

@Component
public class MassageValuesOperations {

    public Double headingAlignment(Double heading) {
        return heading % 360;
    }
}
