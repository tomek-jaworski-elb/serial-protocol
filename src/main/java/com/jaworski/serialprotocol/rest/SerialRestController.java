package com.jaworski.serialprotocol.rest;

import com.jaworski.serialprotocol.serial.controller.SerialController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SerialRestController {

    private final SerialController serialController;

    public SerialRestController(SerialController serialController) {
        this.serialController = serialController;
    }

    @GetMapping("/")
    public ResponseEntity<String> getPortList() {
        List<String> allPorts = serialController.getAllPorts();
        return ResponseEntity.ok(allPorts.stream().
                reduce((s, s2) -> s + System.lineSeparator() + s2)
                .orElse("No ports!")
        );
    }
}
