package com.jaworski.serialprotocol.serial.listener;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.fazecast.jSerialComm.SerialPortMessageListener;
import com.jaworski.serialprotocol.resources.Resources;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class SerialPortListenerImpl implements SerialPortMessageListener {

    private static final Logger LOG = LogManager.getLogger(SerialPortListenerImpl.class);
    private final Resources resources;

    @Override
    public byte[] getMessageDelimiter() {
        LOG.info("Set message delimiter: {}", resources.getMessageDelimiter());
        return resources.getMessageDelimiter();
//        return new byte[]{(byte) 0x0B, (byte) 0x65};
    }

    @Override
    public boolean delimiterIndicatesEndOfMessage() {
        return true;
    }

    @Override
    public int getListeningEvents() {
        return SerialPort.LISTENING_EVENT_DATA_RECEIVED;
    }

    @Override
    public void serialEvent(SerialPortEvent event) {
        SerialPort serialPort = event.getSerialPort();
        byte[] delimitedMessage = event.getReceivedData();
        LOG.info("On port {}: Received the following delimited message: {}", serialPort.getPortDescription(), delimitedMessage);
    }
}
