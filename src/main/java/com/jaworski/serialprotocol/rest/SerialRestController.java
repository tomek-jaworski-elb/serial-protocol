package com.jaworski.serialprotocol.rest;

import com.fazecast.jSerialComm.SerialPort;
import com.jaworski.serialprotocol.dto.SerialPortDTO;
import com.jaworski.serialprotocol.serial.controller.SerialController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SerialRestController {

    private final SerialController serialController;

    private static SerialPortDTO apply(SerialPort serialPort) {
        return new SerialPortDTO(serialPort.getDescriptivePortName(), serialPort.getBaudRate(), serialPort.getParity());
    }

    @GetMapping("/")
    public ResponseEntity<List<SerialPortDTO>> getPortList() {
        var allPorts = serialController.getAllPorts();
        List<SerialPortDTO> list = allPorts.stream()
                .map(SerialRestController::apply)
                .toList();
        return ResponseEntity.ok(list);
    }
}
