package com.jaworski.serialprotocol.service.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaworski.serialprotocol.dto.ModelTrackDTO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class MessageTranslator {
  private static final Logger LOG = LogManager.getLogger(MessageTranslator.class);

  private MessageTranslator() {
  }

  private static ModelTrackDTO toDTO(String message) {
    return new ModelTrackDTO(message, 12.21f, 123.123f);
  }

  private static int getModelId(byte[] delimitedMessage) {
    Map<String, Integer> modelMap = new HashMap<>();
    modelMap.put("w1", 1);
    modelMap.put("b2", 2);
    modelMap.put("d3", 3);
    modelMap.put("c4", 4);
    modelMap.put("l6", 6);
    modelMap.put("k5", 5);
    String modelId = new String(delimitedMessage, 0, 2);
    String modelIdLowerCase = modelId.toLowerCase();
    return modelMap.getOrDefault(modelIdLowerCase, 0);
  }

  public static String fromBinary(byte[] delimitedMessage) {
    ModelTrackDTO dto = null;
    try {
      byte[] bytesX = Arrays.copyOfRange(delimitedMessage, 13, 16);
      byte[] bytesY = Arrays.copyOfRange(delimitedMessage, 17, 20);
      float x = bytesToFloat(bytesX);
      float y = bytesToFloat(bytesY);
      int modelId = getModelId(delimitedMessage);
      dto = new ModelTrackDTO(Integer.toString(modelId), x, y);
      LOG.info("Received {}, {}, {}", dto.getModelName(), dto.getPositionX(), dto.getPositionY());
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
}
