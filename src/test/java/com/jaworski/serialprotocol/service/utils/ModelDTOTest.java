package com.jaworski.serialprotocol.service.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.jaworski.serialprotocol.dto.ModelTrackDTO;
import com.jaworski.serialprotocol.service.utils.impl.MessageCommon;
import com.jaworski.serialprotocol.service.utils.impl.MessageLadyMarie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ModelDTOTest {

    @Autowired
    private JsonMapperService jsonMapperService;
    @Autowired(required = true)
    private MessageTranslator messageTranslator;

    @Autowired(required = true)
    private MessageCommon messageCommon;
    @Autowired(required = true)
    private MessageLadyMarie messageLadyMarie;


    private static Stream<Arguments> provideInputData() {
    return Stream.of(
            Arguments.of(new byte[]{119, 49, 8, -69, 8, -69, 0, 6, 0, 8, -69, -123, 1, 50, 73, -114, 66, 4, 27, 111, 66, 111, 0, 3, 10, 13, 10}, 1),
            Arguments.of(new byte[]{119, 49, 8, -69, 8, -69, 0, 6, 0, 8, -69, -123, 1, 50, 73, -114, 66, 4, 27, 111, 66, 111, 0, 3, 10, 13, 10}, 1),
            Arguments.of(new byte[]{119, 49, 8, -69, 8, -69, 0, 6, 0, 8, -69, -123, 0, -57, 77, -114, 66, -33, 33, 111, 66, 111, 0, 3, -125, 13, 10}, 1),
            Arguments.of(new byte[]{119, 49, 8, -69, 8, -69, 0, 6, 0, 8, -69, -123, 0, 62, 73, -114, 66, 60, 32, 111, 66, 111, 0, 2, 81, 13, 10}, 1),

            Arguments.of(new byte[]{98, 50, 5, 76, 5, 76, 3, -97, 3, 5, 76, 127, 10, 2, 77, 42, -61, 65, -104, -82, 67, -125, 0, 3, 65, 13, 10}, 2),
            Arguments.of(new byte[]{98, 50, 5, 70, 5, 70, 3, -98, 3, 5, 70, -126, 9, 69, 28, 42, -61, -102, 106, -82, 67, -125, 0, 5, 111, 13, 10}, 2),
            Arguments.of(new byte[]{98, 50, 5, 80, 5, 80, 3, -97, 3, 5, 80, -126, 10, 120, 108, 42, -61, 73, -81, -82, 67, -125, 0, 4, 5, 13, 10}, 2),

            Arguments.of(new byte[]{98, 50, 12, 47, 12, 47, 3, 100, 3, 12, 47, -126, 0, 68, 35, -21, 65, -67, 44, -104, 66, 127, 0, 4, 10, 13, 10}, 2),
            Arguments.of(new byte[]{98, 50, 12, 47, 12, 47, 3, 100, 3, 12, 47, -126, 1, -59, 27, -21, 65, 123, 46, -104, 66, 127, 0, 4, 68, 13, 10}, 2),
            Arguments.of(new byte[]{98, 50, 12, 47, 12, 47, 3, 100, 3, 12, 47, -126, 2, -7, 20, -21, 65, 126, 41, -104, 66, 127, 0, 4, 112, 13, 10}, 2),

            Arguments.of(new byte[]{100, 51, 4, -1, 3, 4, 96, 101, 98, 3, -4, 127, 1, 104, -69, -10, 65, 88, 52, -96, 66, 127, 0, 9, -105, 13, 10}, 3),
            Arguments.of(new byte[]{100, 51, 5, 1, 3, 4, 96, 101, 98, 3, -4, 127, 2, -74, -77, -10, 65, 122, 51, -96, 66, -128, 0, 7, 1, 13, 10}, 3),
            Arguments.of(new byte[]{100, 51, 5, 1, 3, 4, 97, 101, 97, 3, -4, 105, 1, -6, -52, -10, 65, 15, 51, -96, 66, -128, 0, 9, -34, 13, 10}, 3),

            // Lady Marie
            Arguments.of(new byte[]{108, 54, 8, -96, 0, 0, 100, 100, 100, -73, 97, 0, 0, 124, 2, 23, -44, 95, 66, 57, -39, 126, 66, -127, 0, -97, -118, 13, 10}, 6)
    );
  }

  @ParameterizedTest
  @MethodSource("provideInputData")
  @DisplayName("Test getModelId with various inputs")
  void realDataMessageToDTOTest(byte[] message, int expected) throws JsonProcessingException {
    ModelTrackDTO dto = messageTranslator.getDTO(message);
    System.out.println(dto);
    System.out.println(jsonMapperService.toJsonString(dto));
    System.out.println("---------");
    assertNotNull(dto);
    assertEquals(expected, dto.getModelName());
  }

  @Test
  void messageToShortTest() {
    byte[] bytes = {100, 51, 5, 1, 3, 4, 97, 101, 97, 3, -4, 105, 1, -6, -52, -10, 65, 15, 51, -96, 0, 9, -34, 13, 10};
    assertThrows(IllegalArgumentException.class, () -> messageTranslator.getDTO(bytes));
  }

  @Test
  void messageToLongTest() {
    byte[] bytes = {100, 51, 5, 1, 3, 4, 97, 101, 97, 3, -4, 105, 1, -6, -52, -10, 65, 15, 51, -96, 0, 9, -34, 13, 10, 10};
    assertThrows(IllegalArgumentException.class, () -> messageTranslator.getDTO(bytes));
  }

  @Test
  void messageNullTest() {
    assertThrows(IllegalArgumentException.class, () -> messageTranslator.getDTO(null));
  }

  @Test
  void messageEmptyTest() {
    assertThrows(IllegalArgumentException.class, () -> messageTranslator.getDTO(new byte[0]));
  }

  @Test
  void ladyMarieMessageToDTOTest() {
    byte[] bytes = {108, 54, 8, -96, 0, 0, 100, 100, 100, -73, 97, 0, 0, 124, 2, 23, -44, 95, 66, 57, -39, 126, 66, -127, 0, -97, -118, 13, 10};
    ModelTrackDTO dto = messageTranslator.getDTO(bytes);
    assertNotNull(dto);
    assertEquals(6, dto.getModelName());
  }

  @Test
  void commonMessageTest() {
    byte[] bytes = {119, 49, 8, -69, 8, -69, 0, 6, 0, 8, -69, -123, 1, 50, 73, -114, 66, 4, 27, 111, 66, 111, 0, 3, 10, 13, 10};
      ModelTrackDTO dto = messageTranslator.getDTO(bytes);
      assertNotNull(dto);
  }
}
