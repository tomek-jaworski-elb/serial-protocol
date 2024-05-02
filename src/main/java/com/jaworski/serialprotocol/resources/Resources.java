package com.jaworski.serialprotocol.resources;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Resources {

    public static final int DEFAULT_BAUD_RATE = 9600;
    @Value("${rs.baud_rate}")
    private String f8Port;

    public Integer getBaudRate() {
        try {
            return Integer.parseInt(f8Port);
        } catch (NumberFormatException e) {
            return DEFAULT_BAUD_RATE;
        }
    }
}
