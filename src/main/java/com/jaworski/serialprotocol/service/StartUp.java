package com.jaworski.serialprotocol.service;

import com.jaworski.serialprotocol.resources.Resources;
import com.jaworski.serialprotocol.restclient.RestNameService;
import com.jaworski.serialprotocol.serial.controller.SerialController;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class StartUp {

    private final SerialController serialController;
    private final RestNameService restNameService;
    private final Resources resources;

    public void start() {
        serialController.openAllPorts();
        if (resources.isRestServiceEnabled()) {
          restNameService.checkConnection();
        }
    }
}
