package com.jaworski.serialprotocol;

import com.jaworski.serialprotocol.service.StartUp;
import com.jaworski.serialprotocol.udp.server.UdpProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(UdpProperties.class)
public class SerialProtocolApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(SerialProtocolApplication.class, args);
        StartUp startUp = context.getBean(StartUp.class);
        startUp.start();
    }
}
