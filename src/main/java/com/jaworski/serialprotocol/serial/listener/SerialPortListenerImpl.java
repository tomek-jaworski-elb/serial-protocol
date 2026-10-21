package com.jaworski.serialprotocol.serial.listener;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.fazecast.jSerialComm.SerialPortMessageListener;
import com.jaworski.serialprotocol.resources.Resources;
import com.jaworski.serialprotocol.service.ingest.FrameIngress;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Serial-port adapter: frames the byte stream on the configured delimiter and hands every
 * message to {@link FrameIngress}. Nothing is published from here.
 */
@RequiredArgsConstructor
@Component
public class SerialPortListenerImpl implements SerialPortMessageListener {

    private static final Logger LOG = LoggerFactory.getLogger(SerialPortListenerImpl.class);
    private final Resources resources;
    private final FrameIngress frameIngress;

    @Override
    public byte[] getMessageDelimiter() {
        LOG.info("Set message delimiter: {}", resources.getMessageDelimiter());
        return resources.getMessageDelimiter();
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
        // This log line is an API: the simulator's replay parses "Received delimited message: [...]".
        LOG.info("On port {} Received delimited message: {}", serialPort.getPortDescription(), delimitedMessage);
        frameIngress.ingest(delimitedMessage, serialPort.getPortDescription());
    }
}
