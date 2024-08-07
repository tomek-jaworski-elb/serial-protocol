package com.jaworski.serialprotocol;

import com.jaworski.serialprotocol.serial.controller.SerialController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class SerialProtocolApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(SerialProtocolApplication.class, args);
        SerialController serialController = context.getBean(SerialController.class);
        serialController.openAllPorts();

    }

}
