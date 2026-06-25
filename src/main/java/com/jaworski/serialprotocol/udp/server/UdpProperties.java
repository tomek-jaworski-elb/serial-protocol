package com.jaworski.serialprotocol.udp.server;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "udp.server")
@Validated
@Getter
@Setter
public class UdpProperties {

    @Min(1024)
    @Max(65535)
    private int port = 1234;

    @Positive
    private int bufferSize = 65535;
}

