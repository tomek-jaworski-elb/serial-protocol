package com.jaworski.serialprotocol;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class SerialController {
    private static final Logger LOG = LogManager.getLogger(SerialController.class);
    private final SerialPortDataListener serialPortDataListener;

    public SerialController(SerialPortDataListener serialPortDataListener) {
        this.serialPortDataListener = serialPortDataListener;
    }

    public void run() {
        SerialPort[] commPorts = SerialPort.getCommPorts();
        LOG.info("Ports count: {}",commPorts.length);
        SerialPort port = SerialPort.getCommPorts()[0];
        port.setBaudRate(9600);
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
                LOG.info("Read {} bytes.", numRead);
            }
        });
    }
}
