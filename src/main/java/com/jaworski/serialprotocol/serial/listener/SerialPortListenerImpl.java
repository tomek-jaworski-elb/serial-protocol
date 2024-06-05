package com.jaworski.serialprotocol.serial.listener;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.fazecast.jSerialComm.SerialPortMessageListener;
import com.jaworski.serialprotocol.dto.ModelTrackDTO;
import com.jaworski.serialprotocol.resources.Resources;
import com.jaworski.serialprotocol.serial.SessionType;
import com.jaworski.serialprotocol.service.WebSocketPublisher;
import com.jaworski.serialprotocol.service.utils.MessageTranslator;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@RequiredArgsConstructor
@Component
public class SerialPortListenerImpl implements SerialPortMessageListener {

    private static final Logger LOG = LogManager.getLogger(SerialPortListenerImpl.class);
    private final Resources resources;
    private final WebSocketPublisher webSocketPublisher;

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
        LOG.info("On port {} Received delimited message: {}", serialPort.getPortDescription(), delimitedMessage);
        webSocketPublisher.publishForAllClients(Arrays.toString(delimitedMessage), SessionType.RS);
        try {
            ModelTrackDTO dto = MessageTranslator.getDTO(delimitedMessage);
            webSocketPublisher.publishForAllClients(dto.toString(), SessionType.JSON);
        } catch (IllegalArgumentException e) {
            LOG.error("Failed to translate message: {}", Arrays.toString(delimitedMessage));
        }
    }
}
