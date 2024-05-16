package com.jaworski.serialprotocol.service.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaworski.serialprotocol.dto.ModelTrackDTO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;

public class MessageTranslator {
    private static final Logger LOG = LogManager.getLogger(MessageTranslator.class);

    private MessageTranslator() {}

    public static String fromBinary(char[] bytes) {
        String message = new String(Arrays.copyOfRange(bytes, 0, 2));
        int modelId = getModelId(message);
        int i = bytes[20];
        int j = bytes[21];
        return String.valueOf(i + j * 256);
    }

    private static ModelTrackDTO toDTO(String message) {
        return new ModelTrackDTO(message, 12.21, 123.123);
    }

    private static int getModelId(String modelString) {
        if (modelString.equalsIgnoreCase("01")) {
            return 1;
        } else if (modelString.equalsIgnoreCase("02")) {
            return 2;
        } else if (modelString.equalsIgnoreCase("03")) {
            return 3;
        } else {
            return 0;
        }

    }

    public static String fromBinary(byte[] delimitedMessage) {
        int x = delimitedMessage[10] + delimitedMessage[11];
        int y = delimitedMessage[13] + delimitedMessage[14];
        ModelTrackDTO dto = new ModelTrackDTO("w1", x, y);
        LOG.info("Received {}, {}, {}", dto.getModelName(), dto.getPositionX(), dto.getPositionY());
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            objectMapper.writeValueAsString(dto);
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            LOG.error("Error while serializing dto {}", dto, e);
            return "";
        }
    }
}
