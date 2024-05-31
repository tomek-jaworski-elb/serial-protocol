package com.jaworski.serialprotocol.serial.listener;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.fazecast.jSerialComm.SerialPortMessageListener;
import com.jaworski.serialprotocol.dto.ModelTrackDTO;
import com.jaworski.serialprotocol.resources.Resources;
import com.jaworski.serialprotocol.service.impl.WebSocketJsonPublisher;
import com.jaworski.serialprotocol.service.impl.WebSocketRawPublisher;
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
    private final WebSocketRawPublisher webSocketRawPublisher;
    private final WebSocketJsonPublisher webSocketJsonPublisher;

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
        webSocketRawPublisher.publishForAllClients(Arrays.toString(delimitedMessage));
        ModelTrackDTO dto = MessageTranslator.getDTO(delimitedMessage);
        webSocketJsonPublisher.publishForAllClients(dto.toString());
    }
}
