package com.jaworski.serialprotocol.serial.listener;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.fazecast.jSerialComm.SerialPortMessageListener;
import com.jaworski.serialprotocol.resources.Resources;
import com.jaworski.serialprotocol.service.WSSessionManager;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;

import java.io.IOException;

@RequiredArgsConstructor
@Component
public class SerialPortListenerImpl implements SerialPortMessageListener {

    private static final Logger LOG = LogManager.getLogger(SerialPortListenerImpl.class);
    private final Resources resources;
    private final WSSessionManager wsSessionManager;

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
        LOG.info("On port {}: Received the following delimited message: {}", serialPort.getPortDescription(), delimitedMessage);
        wsSessionManager.getWebSocketSessions().forEach(session -> {
            try {
                session.sendMessage(new TextMessage(delimitedMessage));
                LOG.info("Message sent to client {}: {}", session.getId(), new String(delimitedMessage));
            } catch (IOException e) {
                LOG.error("Error sending message to client {}", session.getId(), e);
            }
        });
    }
}
