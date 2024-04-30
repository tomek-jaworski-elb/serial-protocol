package com.jaworski.serialprotocol;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.fazecast.jSerialComm.SerialPortMessageListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class SerialPortListenerImpl implements SerialPortMessageListener {

    private static final Logger LOG = LogManager.getLogger(SerialPortListenerImpl.class);

    @Override
    public byte[] getMessageDelimiter() {
        return new byte[] { (byte)0x0B, (byte)0x65 };
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
        byte[] delimitedMessage = event.getReceivedData();
        LOG.info("Received the following delimited message: {}", delimitedMessage);
    }
}
