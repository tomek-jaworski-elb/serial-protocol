package com.jaworski.serialprotocol.service.utils;

import com.jaworski.serialprotocol.dto.ModelTrackDTO;
import com.jaworski.serialprotocol.dto.TugDTO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
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

    private static int getModelId(byte[] delimitedMessage) {
        String modelId = new String(delimitedMessage, 0, 2);
        String modelIdLowerCase = modelId.toLowerCase();
        return MODEL_MAP.getOrDefault(modelIdLowerCase, -1);
    }

    private static Double getSpeed(byte[] message) {
        if (message == null || message.length != MESSAGE_LENGTH) {
            return null;
        } else {
            byte speed = message[12];
            return speed / 10d;
        }
    }

    private static Double getGPSQuality(byte[] message) {
            int headingValue = (message[22] & 0xFF) << 8 | (message[23] & 0xFF);
            return headingValue / 100d;
    }

    private static Double getHeading(byte[] message) {
            int headingValue = (message[2] & 0xFF) << 8 | (message[3] & 0xFF);
            return headingValue / 10d;
    }

    private static Double getEngine(byte[] message) {
            byte speed = message[7];
            return (double) speed;
    }

    private static Double getTugBowForce(byte[] message) {
        byte b = message[6];
        return b / 10d;
    }

    private static Double getTugSternForce(byte[] message) {
        byte b = message[8];
        return b / 10d;
    }

    private static Double getTugBowDirection(byte[] message) {
        int direction = (message[4] & 0xFF) << 8 | (message[5] & 0xFF);
        return direction / 100d;
    }

    private static Double getTugSternDirection(byte[] message) {
            int direction = (message[9] & 0xFF) << 8 | (message[10] & 0xFF);
            return direction / 100d;
    }

    private static Double getRudder(byte[] message) {
        byte b = message[11];
        return b / 10d;
    }

    private static Double getBowThruster(byte[] message) {
        byte b = message[21];
        return (double) b;
    }

    public static ModelTrackDTO getDTO(byte[] message) throws IllegalArgumentException {
        if (message == null || message.length != MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Message to short! " + Arrays.toString(message));
        }
        ModelTrackDTO modelTrackDTO = ModelTrackDTO.builder()
                .modelName(getModelId(message))
                .positionX(getPositionX(message))
                .positionY(getPositionY(message))
                .speed(getSpeed(message))
                .heading(getHeading(message))
                .rudder(getRudder(message))
                .gpsQuality(getGPSQuality(message))
                .engine(getEngine(message))
                .bowTug(TugDTO.builder().tugDirection(getTugBowDirection(message)).tugForce(getTugBowForce(message)).build())
                .sternTug(TugDTO.builder().tugDirection(getTugSternDirection(message)).tugForce(getTugSternForce(message)).build())
                .bowThruster(getBowThruster(message))
                .build();
        LOG.info("Translated message: {}", modelTrackDTO);
        return modelTrackDTO;
    }

    private static Float getPositionY(byte[] message) throws IllegalArgumentException {
        try {
            float aFloat = ByteBuffer.wrap(message, 17, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            return BigDecimal.valueOf(aFloat).setScale(2, RoundingMode.HALF_UP).floatValue();
        } catch (BufferOverflowException | IndexOutOfBoundsException | NumberFormatException e) {
            throw new IllegalArgumentException("Buffer wrap exception for PositionY! " + Arrays.toString(message), e);
        }
    }

    private static Float getPositionX(byte[] message) throws IllegalArgumentException {
        try {
            float aFloat = ByteBuffer.wrap(message, 13, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            return BigDecimal.valueOf(aFloat).setScale(2, RoundingMode.HALF_UP).floatValue();
        } catch (BufferOverflowException | IndexOutOfBoundsException | NumberFormatException e) {
            throw new IllegalArgumentException("Buffer wrap exception for PositionX! " + Arrays.toString(message), e);
        }
    }
}
