package com.jaworski.serialprotocol.udp.server;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@Getter
@Setter
public class UdpProperties {

    @Value("${udp.server.port:1234}")
    @Min(1024)
    @Max(65535)
    private int port;

    @Value("${udp.server.bufferSize:65535}")
    @Positive
    private int bufferSize;
}

