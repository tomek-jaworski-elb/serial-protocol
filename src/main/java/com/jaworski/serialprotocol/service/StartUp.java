package com.jaworski.serialprotocol.service;

import com.jaworski.serialprotocol.serial.controller.SerialController;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class StartUp {

    private final SerialController serialController;

    public void start() {
        serialController.openAllPorts();
    }
}
