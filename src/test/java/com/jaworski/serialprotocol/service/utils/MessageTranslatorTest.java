package com.jaworski.serialprotocol.service.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageTranslatorTest {

  @Test
  void testGetModelIdWithValidId() {
    byte[] delimitedMessage = "w1".getBytes();
    int expected = 1;
    int actual = MessageTranslator.getModelId(delimitedMessage);
    assertEquals(expected, actual);
  }

  @Test
  void testGetModelIdWithInvalidId() {
    byte[] delimitedMessage = "x1".getBytes();
    int expected = 0;
    int actual = MessageTranslator.getModelId(delimitedMessage);
    assertEquals(expected, actual);
  }

  @Test
  void testGetModelIdWithEmptyMessage() {
    byte[] delimitedMessage = new byte[0];
    int expected = -1;
    int actual = MessageTranslator.getModelId(delimitedMessage);
    assertEquals(expected, actual);
  }

  @Test
  void testGetModelIdWithNullMessage() {
    int expected = -1;
    int actual = MessageTranslator.getModelId(null);
    assertEquals(expected, actual);
  }

  @Test
  void testGetSpeedNullMessage() {
    Double speed = MessageTranslator.getSpeed(null);
    assertNull(speed);
  }

  @Test
  void testGetSpeedEmptyMessage() {
    assertNull(MessageTranslator.getSpeed(new byte[0]));
  }

  @Test
  void testGetSpeedValidMessage() {
    byte[] message = new byte[13];
    message[12] = 50;
    assertEquals(5.0, MessageTranslator.getSpeed(message));
  }

  @Test
  void getHeading_NullMessage_ReturnsNull() {
    byte[] message = null;
    Double result = MessageTranslator.getHeading(message);
    assertNull(result);
  }

  @Test
  void getHeading_EmptyMessage_ReturnsNull() {
    byte[] message = new byte[0];
    Double result = MessageTranslator.getHeading(message);
    assertNull(result);
  }

  @Test
  void getHeading_ValidMessage_ReturnsExpectedHeading() {
    double expected = 232.1;
    int value = (int) (expected * 10);
    byte[] bytes = new byte[2];
    bytes[0] = (byte) ((value >> 8) & 0xFF); // Most significant byte
    bytes[1] = (byte) (value & 0xFF);        // Least significant byte
    byte[] message = new byte[]{0, 0, bytes[0], bytes[1]};
    Double result = MessageTranslator.getHeading(message);
    assertEquals(expected, result);
  }

  @Test
  void getHeading_MessageWithNegativeKurs_ReturnsExpectedHeading() {
    double expected = 32.1;
    int value = (int) (expected * 10);
    byte[] bytes = new byte[2];
    bytes[0] = (byte) ((value >> 8) & 0xFF); // Most significant byte
    bytes[1] = (byte) (value & 0xFF);        // Least significant byte
    byte[] message = new byte[]{0, 0, bytes[0], bytes[1]};
    Double result = MessageTranslator.getHeading(message);
    assertEquals(expected, result);
  }
}