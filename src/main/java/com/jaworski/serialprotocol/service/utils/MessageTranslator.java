package com.jaworski.serialprotocol.service.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaworski.serialprotocol.dto.ModelTrackDTO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MessageTranslator {
    private static final Logger LOG = LogManager.getLogger(MessageTranslator.class);
    private static final int MESSAGE_LENGTH = 27;

    private MessageTranslator() {
    }

    private static final Map<String, Integer> MODEL_MAP = Map.of("w1", 1,
            "b2", 2,
            "d3", 3,
            "c4", 4,
            "l6", 6,
            "k5", 5);

    public static int getModelId(byte[] delimitedMessage) {
        if (delimitedMessage == null || delimitedMessage.length != MESSAGE_LENGTH) {
            return -1;
        }
        try {
            String modelId = new String(delimitedMessage, 0, 2);
            String modelIdLowerCase = modelId.toLowerCase();
            return MODEL_MAP.getOrDefault(modelIdLowerCase, -1);
        } catch (IndexOutOfBoundsException e) {
            LOG.error("Model name parsing error ", e);
            return -1;
        }
    }

    public static String fromBinary(byte[] delimitedMessage) {
        ModelTrackDTO dto = null;
        try {
            int modelId = getModelId(delimitedMessage);
            Double speed = getSpeed(delimitedMessage);
            Double heading = getHeading(delimitedMessage);
            Double rudder = getRudder(delimitedMessage);
            dto = new ModelTrackDTO(modelId, 0.0f, 0.0f, speed, heading, rudder);
            LOG.info("Received data {}", dto);
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.writeValueAsString(dto);
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException | IllegalArgumentException | NullPointerException |
                 ArrayIndexOutOfBoundsException e) {
            LOG.error("Error while serializing dto {}", dto, e);
            return "";
        }
    }

    private static float bytesToFloat(byte[] bytes) {
        if (bytes.length != 4) {
            throw new IllegalArgumentException("Byte array must be exactly 4 bytes long for a float.");
        }

        // Use ByteBuffer to wrap the byte array
        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        float v = Float.intBitsToFloat(buffer.getInt());
        LOG.info("Bytes {} to float: {}", bytes, v);
        // Convert bytes to float
        return buffer.getFloat();
    }

    public static Double getSpeed(byte[] message) {
        if (message == null || message.length != MESSAGE_LENGTH) {
            return null;
        } else {
            byte speed = message[12];
            return speed / 10d;
        }
    }

    public static Double getHeading(byte[] message) {
        if (message == null || message.length != MESSAGE_LENGTH) {
            return null;
        } else {
            int headingValue = (message[2] & 0xFF) << 8 | (message[3] & 0xFF);
            return headingValue / 10d;
        }
    }

    public static long binaryStringToLong(String binaryString) {
        // Validate the binary string
        if (binaryString == null || binaryString.isEmpty()) {
            throw new NumberFormatException("Binary string is null or empty");
        }

        // Parse the binary string as a long integer
        return Long.parseLong(binaryString, 2);
    }

    public static String byteToBinaryString(byte b) {
        // Convert the byte to a binary string directly
        StringBuilder binaryString = new StringBuilder(8);
        for (int i = 7; i >= 0; i--) {
            binaryString.append((b & (1 << i)) >> i);
        }
        return binaryString.toString();
    }

    public static List<Byte> byteArrayToList(byte[] byteArray) {
        // Create an ArrayList with the same size as the byteArray
        List<Byte> byteList = new ArrayList<>(byteArray.length);
        // Add each byte to the ArrayList
        for (byte b : byteArray) {
            byteList.add(b);
        }

        // Convert Byte array to List
        return byteList;
    }

    public static Double getRudder(byte[] message) {
        if (message == null || message.length != MESSAGE_LENGTH) {
            return null;
        }
        byte b = message[11];
        return b / 10d;
    }

    public static ModelTrackDTO getDTO(byte[] message) {
        return new ModelTrackDTO(getModelId(message), getPositionX(message), getPositionY(message),
                getSpeed(message), getHeading(message), getRudder(message));
    }

    private static Float getPositionX(byte[] message) {
        return 0f;
    }

    private static Float getPositionY(byte[] message) {
        return 0f;
    }
}
