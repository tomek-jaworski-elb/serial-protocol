package com.jaworski.serialprotocol.resources;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Resources {

    public static final int DEFAULT_BAUD_RATE = 9600;

    @Value("${rs.baud_rate}")
    private String rsBaudRate;

    @Value("${rs.comport}")
    @Getter
    private String comportName;

    @Value("${rs.message_delimiter}")
    @Getter
    private byte[] messageDelimiter;

    public Integer getBaudRate() {
        try {
            return Integer.parseInt(rsBaudRate);
        } catch (NumberFormatException e) {
            return DEFAULT_BAUD_RATE;
        }
    }
}
