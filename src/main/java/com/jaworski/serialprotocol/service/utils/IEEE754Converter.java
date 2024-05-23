package com.jaworski.serialprotocol.service.utils;

import java.nio.ByteBuffer;

public class IEEE754Converter {

  // Method to convert a float to IEEE 754 binary representation
  public static String floatToIEEE754(float value) {
    int intBits = Float.floatToIntBits(value);
    return String.format("%32s", Integer.toBinaryString(intBits)).replace(' ', '0');
  }

  // Method to convert IEEE 754 binary representation to a float
  public static float ieee754ToFloat(String binary) {
    if (binary.length() != 32) {
      throw new IllegalArgumentException("Binary string must be 32 bits long.");
    }
    int intBits = Integer.parseUnsignedInt(binary, 2);
    return Float.intBitsToFloat(intBits);
  }

  public static float bytesToFloat(byte[] bytes) {
    // Check if the byte array length is 4, which is the size of a float in bytes
    if (bytes.length != 4) {
      throw new IllegalArgumentException("Byte array must be 4 bytes long");
    }

    // Wrap the byte array in a ByteBuffer
    ByteBuffer buffer = ByteBuffer.wrap(bytes);

    // Optionally, you can set the byte order if you know it's different from the default
    // buffer.order(ByteOrder.BIG_ENDIAN); // Big-endian is the default order

    // Convert the bytes to float
    return buffer.getFloat();
  }
}
