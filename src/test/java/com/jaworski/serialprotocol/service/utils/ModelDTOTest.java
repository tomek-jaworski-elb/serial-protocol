package com.jaworski.serialprotocol.service.utils;

import com.jaworski.serialprotocol.dto.ModelTrackDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ModelDTOTest {

  private static Stream<Arguments> provideInputData() {
    return Stream.of(
            Arguments.of(new byte[]{119, 49, 3, 103, 3, 103, 0, 2, 0, 3, 103, 112, 108, 54, -89, 70, -61, -66, 15, 54, 67, 111, 0, 4, 99, 13, 10}, 1),
            Arguments.of(new byte[]{119, 49, 3, 112, 3, 112, 0, 2, 0, 3, 112, 104, 108, -103, -12, 70, -61, 59, -105, 58, 67, 111, 0, 3, 46, 13, 10}, 1),
            Arguments.of(new byte[]{119, 49, 3, 116, 3, 116, 0, 2, 0, 3, 116, 83, 110, -33, 59, 71, -61, 6, 1, 62, 67, 111, 0, 5, -16, 13, 10}, 1),

            Arguments.of(new byte[]{98, 50, 5, 76, 5, 76, 3, -97, 3, 5, 76, 127, 10, 2, 77, 42, -61, 65, -104, -82, 67, -125, 0, 3, 65, 13, 10}, 2),
            Arguments.of(new byte[]{98, 50, 5, 70, 5, 70, 3, -98, 3, 5, 70, -126, 9, 69, 28, 42, -61, -102, 106, -82, 67, -125, 0, 5, 111, 13, 10}, 2),
            Arguments.of(new byte[]{98, 50, 5, 80, 5, 80, 3, -97, 3, 5, 80, -126, 10, 120, 108, 42, -61, 73, -81, -82, 67, -125, 0, 4, 5, 13, 10}, 2),

            Arguments.of(new byte[]{100, 51, 4, -1, 3, 4, 96, 101, 98, 3, -4, 127, 1, 104, -69, -10, 65, 88, 52, -96, 66, 127, 0, 9, -105, 13, 10}, 3),
            Arguments.of(new byte[]{100, 51, 5, 1, 3, 4, 96, 101, 98, 3, -4, 127, 2, -74, -77, -10, 65, 122, 51, -96, 66, -128, 0, 7, 1, 13, 10}, 3),
            Arguments.of(new byte[]{100, 51, 5, 1, 3, 4, 97, 101, 97, 3, -4, 105, 1, -6, -52, -10, 65, 15, 51, -96, 66, -128, 0, 9, -34, 13, 10}, 3)
    );
  }

  @ParameterizedTest
  @MethodSource("provideInputData")
  @DisplayName("Test getModelId with various inputs")
  void realDataMessageToDTOTest(byte[] message, int expected) {
    ModelTrackDTO dto = MessageTranslator.getDTO(message);
    System.out.println(dto);
    assertNotNull(dto);
    assertEquals(expected, dto.getModelName());
  }
}
