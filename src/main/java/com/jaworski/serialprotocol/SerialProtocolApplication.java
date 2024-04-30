package com.jaworski.serialprotocol;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class SerialProtocolApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(SerialProtocolApplication.class, args);
        SerialController serialController = context.getBean(SerialController.class);
        serialController.run();
        serialController.listener();
        serialController.list();

    }

}
