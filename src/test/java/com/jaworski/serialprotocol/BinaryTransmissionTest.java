package com.jaworski.serialprotocol;

import com.jaworski.serialprotocol.service.utils.IEEE754Converter;
import org.firebirdsql.decimal.Decimal32;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
@Disabled
class BinaryTransmissionTest {

  @Test
  void translateMessage() {
    char[] chars0 = new char[]{84, 111, 109, 97, 115, 122, 32, 74, 97, 119, 111, 114, 115, 107, 105, 32, 45, 32, 69, 76, 66, 76, 65, 71, 32, 45, 62, 32, 11, 101};
    char[] chars1 = new char[]{84, 114, 97, 110, 115, 109, 105, 115, 106, 97, 32, 98, 105, 110, 97, 114, 110, 97, 32, 119, 49, 46, 46, 46, 32, 98, 50, 46, 46, 46, 32, 32, 62, 32, 11, 101};
    char[] chars2 = new char[]{75, 111, 110, 105, 101, 99, 32, 108, 105, 110, 105, 32, 110, 101, 120, 116, 32, 119, 105, 101, 114, 115, 122, 46, 13, 10, 75, 111, 110, 105, 101, 99, 32, 108, 105, 110, 105, 32, 110, 101, 120, 116, 32, 119, 105, 101, 114, 115, 122, 46, 13, 10, 84, 111, 109, 97, 115, 122, 32, 74, 97, 119, 111, 114, 115, 107, 105, 32, 45, 32, 69, 76, 66, 76, 65, 71, 32, 45, 62, 32, 11, 101};
    char[] chars3 = new char[]{84, 111, 109, 97, 115, 122, 32, 74, 97, 119, 111, 114, 115, 107, 105, 32, 45, 32, 69, 76, 66, 76, 65, 71, 32, 45, 62, 32, 11, 101};
//    String s = MessageTranslator.fromBinary(chars1);
//    Assertions.assertNotNull(s);
  }

  @Test
  void fileTest() throws IOException {

  }

  byte[] getFile() throws IOException {
    Path path1 = Path.of("./", "_opis_silm_PM_AIS_", "_AIS_Silm_w1_b2_c4_d3_l6_2018-05-04.TXT");
    Assertions.assertNotNull(path1);
    return Files.readAllBytes(path1);
  }

  String getString() throws IOException {
    Path path1 = Path.of("./", "_opis_silm_PM_AIS_", "_AIS_Silm_w1_b2_c4_d3_l6_2018-05-04.TXT");
    Assertions.assertNotNull(path1);
    return Files.readString(path1, StandardCharsets.ISO_8859_1);
  }

  @Test
  void binaryTest() throws IOException {
    byte[] bytes = getFile();
    Assertions.assertNotNull(bytes);
    String readString = getString();
    Assertions.assertNotNull(readString);
    String utf8String = new String(bytes, StandardCharsets.UTF_8);
    String utf16String = new String(bytes, StandardCharsets.UTF_16);
    int i1 = utf16String.indexOf("w1");
    String substring = utf16String.substring(i1, i1 + 27);
    Assertions.assertNotNull(substring);
    var bytes2 = substring.substring(13, 16);
    char CR = 13;
    char LF = 10;
    String newLine = String.valueOf(CR) + String.valueOf(LF);
    char w = 'w';
    char b = 'b';
    char d = 'd';
    char one = '1';
    List<Integer> positionW = new ArrayList<>();
    List<Integer> positionB = new ArrayList<>();
    List<Integer> positionD = new ArrayList<>();
    for (int i = 0; i < bytes.length; i++) {
      if (bytes[i] == w) {
        positionW.add(i);
      }
      if (bytes[i] == b) {
        positionB.add(i);
      }
      if (bytes[i] == d) {
        positionD.add(i);
      }
    }
    System.out.println(positionW);
    System.out.println(positionB);
    System.out.println(positionD);

    byte[] bytes1 = Arrays.copyOfRange(bytes, positionW.get(0), positionW.get(0) + 27);
    Assertions.assertNotNull(bytes1);
    int i = bytes1[13] + bytes1[14] + bytes1[15] + bytes1[16];
    byte[] wx = new byte[]{bytes1[13], bytes1[14], bytes1[15], bytes1[16]};
    Assertions.assertNotNull(wx);
    float bytesToFloat = bytesToFloat(wx);
    float ieee754ToFloat = IEEE754Converter.ieee754ToFloat(IEEE754Converter.floatToIEEE754(bytesToFloat));

    int y = bytes1[17] + bytes1[18] + bytes1[19] + bytes1[20];
    byte[] floatBytes = {0x41, 0x20, 0x00, 0x00}; // This represents 10.0f in IEEE 754
    float f = bytesToFloat(floatBytes);
    float fWx = bytesToFloat(wx);
    boolean[] booleans = convertBytesToBits(floatBytes);
    int[] ints = convertByteArrayToBitArray(wx);
    String binaryString = Integer.toBinaryString(10);
    int i2 = Float.floatToIntBits(10.0f);
    Decimal32 decimal32 = Decimal32.parseBytes(floatBytes);
    System.out.println(decimal32);
    System.out.println(fWx);
    System.out.println(f);
  }

  public static float bytesToFloat(byte[] bytes) {
    if (bytes.length != 4) {
      throw new IllegalArgumentException("Byte array must be exactly 4 bytes long for a float.");
    }

    // Use ByteBuffer to wrap the byte array
    ByteBuffer buffer = ByteBuffer.wrap(bytes);


    float v = Float.intBitsToFloat(buffer.getInt());
    // Convert bytes to float
    return v;
  }

  public static boolean[] convertBytesToBits(byte[] byteArray) {
    int totalBits = byteArray.length * 8;
    boolean[] bitArray = new boolean[totalBits];

    for (int i = 0; i < byteArray.length; i++) {
      for (int bit = 0; bit < 8; bit++) {
        bitArray[i * 8 + bit] = (byteArray[i] & (1 << (7 - bit))) != 0;
      }
    }

    return bitArray;
  }

  public static int[] convertByteArrayToBitArray(byte[] byteArray) {
    int[] bitArray = new int[byteArray.length * 8];

    for (int i = 0; i < byteArray.length; i++) {
      for (int j = 0; j < 8; j++) {
        // Extract the j-th bit of byteArray[i]
        bitArray[i * 8 + j] = (byteArray[i] >> (7 - j)) & 1;
      }
    }

    return bitArray;
  }

  @Test
  void floatToBytesTest() {
    String s = IEEE754Converter.floatToIEEE754(10.0f);
    Assertions.assertNotNull(s);
  }

  @Test
  void bytesToFloatTest() {
    float value = -10.0f;
    byte[] floatBytes = {0x41, 0x20, 0x00, 0x00}; // This represents 10.0f in IEEE 754
    float v = bytesToFloat(floatBytes);
    boolean[] converted = convertBytesToBits(floatBytes);
    int[] byteArrayToBitArray = convertByteArrayToBitArray(floatBytes);
    String ieee754 = IEEE754Converter.floatToIEEE754(value);
    var s = IEEE754Converter.ieee754ToFloat(ieee754);
    Assertions.assertNotNull(s);
  }

  @Test
  void bytesTest() {
//    byte[] floatBytes = {0x41, 0x20, 0x00, 0x00}; // This represents 10.0f in IEEE 754

    byte[] floatBytes = new byte[]{(byte) 255, 0x20, 0x00, 0x00};
    float aFloat = IEEE754Converter.bytesToFloat(floatBytes);
    Assertions.assertNotNull(floatBytes);
    Assertions.assertNotNull(aFloat);

  }

  @Test
  void realDataTest() {
  byte[] bytes =new byte[]{119, 49, 12, 100, 12, 100, 5, 5, 5, 12, 100, 27, 12, 110, 18, 28, -61, -14, 126, 47, 67, 111, 0, 2, -32, 13, 10};
    String s = new String(bytes);
    Assertions.assertNotNull(s);
    byte[] wx = new byte[]{bytes[13], bytes[14], bytes[15], bytes[16]};
    byte[] wy = new byte[]{bytes[17], bytes[18],bytes[19], bytes[20]};
    float x = bytesToFloat(wx);
    float y = bytesToFloat(wy);
    Assertions.assertNotNull(x);
    Assertions.assertNotNull(y);
    Decimal32 decimal32x = Decimal32.parseBytes(wx);
    Decimal32 decimal32y = Decimal32.parseBytes(wy);
    double v1 = decimal32x.doubleValue();

    List<Byte> list = byteArrayToList(wx);
    List<Byte> negateList = list.stream()
            .map(aByte -> byteToBinaryString(aByte))
            .map(s1 -> negateBinaryString(s1))
            .map(s1 -> binaryStringToByte(s1))
            .toList();
    byte[] negateBytes = listToByteArray(negateList);

    String s1 = byteToBinaryString(bytes[13]);
    String s2 = negateBinaryString(s1);
    byte negateByte = binaryStringToByte(s2);
    s1.length();
  }

  public static String byteToBinaryString(byte b) {
    // Mask the byte to only get the last 8 bits and convert to a binary string
    String binaryString = Integer.toBinaryString(b & 0xFF);
    // Format the string to ensure it is 8 bits long
    while (binaryString.length() < 8) {
      binaryString = "0" + binaryString;
    }
    return binaryString;
  }

  public static String negateBinaryString(String binaryString) {
    StringBuilder negatedBinaryString = new StringBuilder();

    for (int i = 0; i < binaryString.length(); i++) {
      char bit = binaryString.charAt(i);
      if (bit == '0') {
        negatedBinaryString.append('1');
      } else if (bit == '1') {
        negatedBinaryString.append('0');
      } else {
        throw new IllegalArgumentException("Invalid binary string");
      }
    }
    return negatedBinaryString.toString();
  }

  public static byte binaryStringToByte(String binaryString) {
    if (binaryString == null || binaryString.length() == 0 || binaryString.length() > 8) {
      throw new IllegalArgumentException("Binary string must be 1 to 8 characters long");
    }

    // Validate the binary string to ensure it only contains '0' and '1'
    for (char c : binaryString.toCharArray()) {
      if (c != '0' && c != '1') {
        throw new IllegalArgumentException("Binary string contains invalid characters");
      }
    }

    // Parse the binary string as an integer
    int intValue = Integer.parseInt(binaryString, 2);

    // Cast the integer to a byte
    return (byte) intValue;
  }

  public static List<Byte> byteArrayToList(byte[] byteArray) {
    // Convert byte array to Byte array
    Byte[] byteObjects = new Byte[byteArray.length];
    for (int i = 0; i < byteArray.length; i++) {
      byteObjects[i] = byteArray[i];
    }

    // Convert Byte array to List
    return new ArrayList<>(Arrays.asList(byteObjects));
  }

  public static byte[] listToByteArray(List<Byte> byteList) {
    byte[] byteArray = new byte[byteList.size()];
    for (int i = 0; i < byteList.size(); i++) {
      byteArray[i] = byteList.get(i);
    }
    return byteArray;
  }

  @Test
  void kursTest() {
    byte[] bytes =new byte[]{119, 49, 12, 100, 12, 100, 5, 5, 5, 12, 100, 27, 12, 110, 18, 28, -61, -14, 126, 47, 67, 111, 0, 2, -32, 13, 10};
    byte[] kurs = new byte[]{bytes[2], bytes[3]};
    List<Byte> bytes1 = byteArrayToList(kurs);
    String s1 = bytes1.stream().map(BinaryTransmissionTest::byteToBinaryString)
            .reduce((s, s2) -> s + s2)
            .orElse("");
    long l = binaryStringToLong(s1);
    double v = l / 10d;

    System.out.println(v);
  }

  public static long binaryStringToLong(String binaryString) {
    // Validate the binary string
    if (binaryString == null || binaryString.isEmpty()) {
      throw new NumberFormatException("Binary string is null or empty");
    }

    // Parse the binary string as a long integer
    return Long.parseLong(binaryString, 2);
  }

  @Test
  void speedTest() {
    byte[] bytes =new byte[]{119, 49, 12, 100, 12, 100, 5, 5, 5, 12, 100, 27, 12, 110, 18, 28, -61, -14, 126, 47, 67, 111, 0, 2, -32, 13, 10};
    byte speed = bytes[12];
    double v = speed / 10d;
    System.out.println(v);
  }
}
