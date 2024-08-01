package com.jaworski.serialprotocol.service;

import com.fazecast.jSerialComm.SerialPort;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class SerialPortService {

    private static final List<SerialPort> serialPorts = Arrays.asList(SerialPort.getCommPorts()); // <SerialPort>

    public List<SerialPort> getSerialPorts() {
        return serialPorts;
    }
}
