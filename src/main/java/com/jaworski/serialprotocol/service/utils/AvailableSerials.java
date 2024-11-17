package com.jaworski.serialprotocol.service.utils;

import com.fazecast.jSerialComm.SerialPort;
import com.jaworski.serialprotocol.service.SerialPortService;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

@Component
@Order(value = Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class AvailableSerials implements CommandLineRunner {

    private static final Logger LOG = LogManager.getLogger(AvailableSerials.class);
    private final SerialPortService serialPortService;

    @Override
    public void run(String... args) throws Exception {
        List<SerialPort> serialPorts = serialPortService.getSerialPorts();
        serialPorts.stream()
                .map(getSerialPortDescription())
                .forEach(str -> LOG.info("Available serial ports: {}", str));
    }

    private static Function<SerialPort, String> getSerialPortDescription() {
        return serialPort -> serialPort.getDescriptivePortName() + ", " +
                serialPort.getSystemPortName() + ", " +
                serialPort.getPortDescription() + ", " +
                serialPort.getBaudRate() + System.lineSeparator();
    }
}
