package com.jaworski.serialprotocol;

import com.jaworski.serialprotocol.service.StartUp;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class SerialProtocolApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(SerialProtocolApplication.class, args);
        StartUp startUp = context.getBean(StartUp.class);
        startUp.start();
    }
}
