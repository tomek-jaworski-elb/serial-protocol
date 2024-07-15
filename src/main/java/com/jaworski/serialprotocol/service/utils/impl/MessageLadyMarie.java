package com.jaworski.serialprotocol.service.utils.impl;

import com.jaworski.serialprotocol.service.utils.SerialMessageTranslator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static com.jaworski.serialprotocol.service.utils.MessageTranslator.MODEL_MAP;

@Component
public class MessageLadyMarie implements SerialMessageTranslator {

    private static final int POSITION_OFFSET = 2;

    @Override
    public int getModelId(byte[] delimitedMessage) {
        String modelId = new String(delimitedMessage, 0, 2);
        String modelIdLowerCase = modelId.toLowerCase();
        return MODEL_MAP.getOrDefault(modelIdLowerCase, -1);
    }

    @Override
    public Double getSpeed(byte[] message) {
        byte speed = message[12];
        return speed / 10d;
    }

    @Override
    public Double getGPSQuality(byte[] message) {
        int headingValue = (message[22 + POSITION_OFFSET] & 0xFF) << 8 | (message[23 + POSITION_OFFSET] & 0xFF);
        return headingValue / 100d;
    }

    @Override
    public Double getHeading(byte[] message) {
        int headingValue = (message[2] & 0xFF) << 8 | (message[3] & 0xFF);
        return headingValue / 10d;
    }

    @Override
    public Double getEngine(byte[] message) {
        byte speed = message[7];
        return (double) speed;
    }

    @Override
    public Double getTugBowForce(byte[] message) {
        byte b = message[6];
        return b / 10d;
    }

    @Override
    public Double getTugSternForce(byte[] message) {
        byte b = message[8];
        return b / 10d;
    }

    @Override
    public Double getTugBowDirection(byte[] message) {
        int direction = (message[4] & 0xFF) << 8 | (message[5] & 0xFF);
        return direction / 100d;
    }

    @Override
    public Double getTugSternDirection(byte[] message) {
        int direction = (message[9] & 0xFF) << 8 | (message[10] & 0xFF);
        return direction / 100d;
    }

    @Override
    public Double getRudder(byte[] message) {
        byte b = message[11];
        return b / 10d;
    }

    @Override
    public Double getBowThruster(byte[] message) {
        byte b = message[21 + POSITION_OFFSET];
        return (double) b;
    }

    @Override
    public Float getPositionY(byte[] message) throws IllegalArgumentException {
        try {
            float aFloat = ByteBuffer.wrap(message, 17 + POSITION_OFFSET, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            return BigDecimal.valueOf(aFloat).setScale(2, RoundingMode.HALF_UP).floatValue();
        } catch (BufferOverflowException | IndexOutOfBoundsException | NumberFormatException e) {
            throw new IllegalArgumentException("Buffer wrap exception for PositionY! " + Arrays.toString(message), e);
        }    }

    @Override
    public Float getPositionX(byte[] message) throws IllegalArgumentException {
        try {
            float aFloat = ByteBuffer.wrap(message, 13 + POSITION_OFFSET, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            return BigDecimal.valueOf(aFloat).setScale(2, RoundingMode.HALF_UP).floatValue();
        } catch (BufferOverflowException | IndexOutOfBoundsException | NumberFormatException e) {
            throw new IllegalArgumentException("Buffer wrap exception for PositionX! " + Arrays.toString(message), e);
        }    }
}
