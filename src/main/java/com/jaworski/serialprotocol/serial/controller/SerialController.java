package com.jaworski.serialprotocol.serial.controller;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.jaworski.serialprotocol.resources.Resources;
import com.jaworski.serialprotocol.serial.listener.SerialPortListenerImpl;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@RequiredArgsConstructor
@Component
public class SerialController {
    private static final Logger LOG = LogManager.getLogger(SerialController.class);
    private final SerialPortListenerImpl serialPortDataListener;
    private final Resources resources;

    public void run() {
        SerialPort[] commPorts = SerialPort.getCommPorts();
        LOG.info("Ports count: {}",commPorts.length);
        SerialPort port = SerialPort.getCommPorts()[0];
        port.setBaudRate(resources.getBaudRate());
        port.openPort(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);
    }


    public void list() {
        SerialPort port = SerialPort.getCommPorts()[0];
        port.addDataListener(serialPortDataListener);
    }

    public void listener() {
        SerialPort port = SerialPort.getCommPorts()[0];
        port.addDataListener(new SerialPortDataListener() {
            @Override
            public int getListeningEvents() {
                return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
            }

            @Override
            public void serialEvent(SerialPortEvent event) {
                if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE)
                    return;
                byte[] newData = new byte[port.bytesAvailable()];
                int numRead = port.readBytes(newData, newData.length);
                LOG.info("On port {} Read {} bytes:{}{}", port.getPortDescription(), numRead, System.lineSeparator(), newData);
            }
        });
    }

    public void openAllPorts() {
        LOG.debug("SerialPort version: {}", SerialPort.getVersion());
        SerialPort[] commPorts = SerialPort.getCommPorts();
        if (commPorts.length == 0) {
            LOG.info("No ports found.");
        } else {
            Arrays.stream(commPorts)
                    .map(SerialPort::getPortDescription)
                    .reduce((serialPort, serialPort2) -> serialPort + "," + serialPort2)
                    .ifPresent(message -> LOG.info("Found {} ports: ({})", commPorts.length, message));
            for (SerialPort port : commPorts) {
                port.openPort(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);
                boolean added = port.addDataListener(serialPortDataListener);
                LOG.info("On port {} with baud rate {} added listener: {}", port.getPortDescription(), port.getBaudRate(), added);
            }
        }
    }

    public List<SerialPort> getAllPorts() {
        return Arrays.stream(SerialPort.getCommPorts())
                .toList();
    }
}
