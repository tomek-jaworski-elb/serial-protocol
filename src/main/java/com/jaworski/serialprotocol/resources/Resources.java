package com.jaworski.serialprotocol.resources;

import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Resources {

    private static final Logger LOG = LogManager.getLogger(Resources.class);
    public static final int DEFAULT_BAUD_RATE = 9600;

    @Value("${rs.baud_rate}")
    private String rsBaudRate;

    @Value("${rs.comport}")
    @Getter
    private String comportName;

    @Value("${rs.message_delimiter}")
    @Getter
    private byte[] messageDelimiter;

    @Getter
    @Value("${ws.endpoint}")
    private String wsEndpoint;

    public Integer getBaudRate() {
        try {
            return Integer.parseInt(rsBaudRate);
        } catch (NumberFormatException e) {
            LOG.error("Could not parse baud rate: {}", rsBaudRate);
            return DEFAULT_BAUD_RATE;
        }
    }
}
