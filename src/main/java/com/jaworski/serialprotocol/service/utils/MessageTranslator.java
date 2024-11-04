package com.jaworski.serialprotocol.service.utils;

import com.jaworski.serialprotocol.dto.ModelTrackDTO;
import com.jaworski.serialprotocol.dto.Models;
import com.jaworski.serialprotocol.dto.TugDTO;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MessageTranslator {
    private static final Logger LOG = LogManager.getLogger(MessageTranslator.class);
    public static final int MESSAGE_LENGTH = 27;
    public static final int MESSAGE_LENGTH_LADY_MARIE = 29;
    private static final int HEADING_CORRECTION = 9; //0;
    @Qualifier("messageCommon")
    private final SerialMessageTranslator messageCommon;

    @Qualifier("messageLadyMarie")
    private final SerialMessageTranslator messageLadyMarie;

    public static final Map<String, Integer> MODEL_MAP = Map.of("w1", Models.WARTA.getId(),
            "b2", Models.BLUE_LADY.getId(),
            "d3", Models.DORCHERTER_LADY.getId(),
            "c4", Models.CHERRY_LADY.getId(),
            "l6", Models.LADY_MARIE.getId(),
            "k5", Models.KOLOBRZEG.getId());

    public ModelTrackDTO getDTO(byte[] message) throws IllegalArgumentException {
        if (message == null) {
            throw new IllegalArgumentException("Message is null!");
        } else if (message.length == MESSAGE_LENGTH_LADY_MARIE) {
            messageLadyMarie.getModelId(message);
            ModelTrackDTO modelTrackDTO = ModelTrackDTO.builder()
                    .modelName(messageLadyMarie.getModelId(message))
                    .positionX(messageLadyMarie.getPositionX(message))
                    .positionY(messageLadyMarie.getPositionY(message))
                    .speed(messageLadyMarie.getSpeed(message))
                    .heading(messageLadyMarie.getHeading(message) + HEADING_CORRECTION)
                    .rudder(messageLadyMarie.getRudder(message))
                    .gpsQuality(messageLadyMarie.getGPSQuality(message))
                    .engine(messageLadyMarie.getEngine(message))
                    .bowTug(TugDTO.builder()
                            .tugDirection(messageLadyMarie.getTugBowDirection(message))
                            .tugForce(messageLadyMarie.getTugBowForce(message))
                            .build())
                    .sternTug(TugDTO.builder()
                            .tugDirection(messageLadyMarie.getTugSternDirection(message))
                            .tugForce(messageLadyMarie.getTugSternForce(message))
                            .build())
                    .bowThruster(messageLadyMarie.getBowThruster(message))
                    .isCRCValid(messageLadyMarie.isDataValid(message))
                    .build();
            LOG.info("Translated message: {}", modelTrackDTO);
            return modelTrackDTO;
        } else if (message.length == MESSAGE_LENGTH) {
            ModelTrackDTO modelTrackDTO = ModelTrackDTO.builder()
                    .modelName(messageCommon.getModelId(message))
                    .positionX(messageCommon.getPositionX(message))
                    .positionY(messageCommon.getPositionY(message))
                    .speed(messageCommon.getSpeed(message))
                    .heading(messageCommon.getHeading(message) + HEADING_CORRECTION)
                    .rudder(messageCommon.getRudder(message))
                    .gpsQuality(messageCommon.getGPSQuality(message))
                    .engine(messageCommon.getEngine(message))
                    .bowTug(TugDTO.builder()
                            .tugDirection(messageCommon.getTugBowDirection(message))
                            .tugForce(messageCommon.getTugBowForce(message))
                            .build())
                    .sternTug(TugDTO.builder()
                            .tugDirection(messageCommon.getTugSternDirection(message))
                            .tugForce(messageCommon.getTugSternForce(message))
                            .build())
                    .bowThruster(messageCommon.getBowThruster(message))
                    .isCRCValid(messageCommon.isDataValid(message))
                    .build();
            LOG.info("Translated message: {}", modelTrackDTO);
            return modelTrackDTO;
        } else if (message.length > MESSAGE_LENGTH) {
            LOG.info("Received override message with length {}. Trying to parse...", message.length);
            byte[] trimmedMessage = Arrays.copyOfRange(message, message.length - MESSAGE_LENGTH, message.length);
            ModelTrackDTO dto = getDTO(trimmedMessage);
            if (dto.getModelName() != -1) {
                return ModelTrackDTO.builder()
                        .modelName(dto.getModelName())
                        .isCRCValid(false)
                        .build();
            } else if (message.length > MESSAGE_LENGTH_LADY_MARIE) {
                trimmedMessage = Arrays.copyOfRange(message, message.length - MESSAGE_LENGTH_LADY_MARIE, message.length);
                dto = getDTO(trimmedMessage);
                if (dto.getModelName() != -1) {
                    return dto;
                } else {
                    LOG.warn("Message length not supported! {} ", Arrays.toString(message));
                    throw new IllegalArgumentException("Message length not supported! " + Arrays.toString(message));
                }
            } else {
                LOG.warn("Message length not supported! {} ", Arrays.toString(message));
                throw new IllegalArgumentException("Message length not supported! " + Arrays.toString(message));
            }
        } else {
            throw new IllegalArgumentException("Message length not supported! " + Arrays.toString(message));
        }
    }

    @Scheduled(fixedDelayString = "10")
    private void trackLog() {
        long currentTimeMillis = System.currentTimeMillis();
        LOG.info("Test. Timestamp: {}", currentTimeMillis);
    }
}
