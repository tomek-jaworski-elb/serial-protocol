package com.jaworski.serialprotocol.serial.controller;

import com.fazecast.jSerialComm.SerialPort;
import com.jaworski.serialprotocol.resources.Resources;
import com.jaworski.serialprotocol.serial.listener.SerialPortListenerImpl;
import com.jaworski.serialprotocol.service.SerialPortService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

@RequiredArgsConstructor
@Component
public class SerialController {
    private static final Logger LOG = LogManager.getLogger(SerialController.class);
    public static final String REGEX = ",";
    private final SerialPortListenerImpl serialPortDataListener;
    private final Resources resources;
    private final SerialPortService serialPortService;

    public void openAllPorts() {
        LOG.info("Opening all ports");
        LOG.info("jSerialComm library version: {}", SerialPort.getVersion());
        LOG.info("Found ports: {}", getAllPorts().size());
        if (serialPortService.getSerialPorts().isEmpty()) {
            LOG.info("No ports found.");
        } else {
            serialPortService.getSerialPorts().stream()
                    .filter(serialPort -> {
                        String portDescription = serialPort.getPortDescription();
                        String descriptivePortName = serialPort.getDescriptivePortName();
                        return StringUtils.containsAnyIgnoreCase(portDescription, resources.getComportsName().split(REGEX)) ||
                                StringUtils.containsAnyIgnoreCase(descriptivePortName, resources.getComportsName().split(REGEX));
                    })
                    .forEach(serialPortInit());
        }
    }

    private Consumer<SerialPort> serialPortInit() {
        return port -> {
            boolean listener = port.addDataListener(serialPortDataListener);
            port.openPort(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);
            LOG.info("On port {} with baud rate {} added listener: {}", port.getPortDescription(), port.getBaudRate(), listener);
        };
    }

    public List<SerialPort> getAllPorts() {
        return serialPortService.getSerialPorts();
    }

    public void closeAllPorts() {
        serialPortService.getSerialPorts().stream()
                .filter(SerialPort::isOpen)
                .forEach(SerialPort::closePort);
    }
}
