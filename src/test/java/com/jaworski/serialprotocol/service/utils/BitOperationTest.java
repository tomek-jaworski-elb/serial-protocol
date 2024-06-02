package com.jaworski.serialprotocol.service.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

class BitOperationTest {

    @Test
    void bitTest() {
        byte[] byteArray = new byte[4];

        // Assuming the byteArray is initialized with some values
        byteArray[0] = (byte) 0b11001010; // example byte
        byteArray[1] = (byte) 0b11110000; // example byte
        byteArray[2] = (byte) 0b10101010; // example byte
        byteArray[3] = (byte) 0b00001111; // example byte

        // Pick bit from index 0 (which is the MSB of byteArray[0])
        int bit0 = (byteArray[0] >> 7) & 1;

        // Pick bits from index 1 to index 8 (7 bits from byteArray[0] and 1 bit from byteArray[1])
        int bits1to8 = ((byteArray[0] & 0x7F) << 1) | ((byteArray[1] >> 7) & 1);

        // Pick bits from index 9 to the last one (7 bits from byteArray[1], and all bits from byteArray[2] and byteArray[3])
        int bitsFrom9to31 = ((byteArray[1] & 0x7F) << 16) | ((byteArray[2] & 0xFF) << 8) | (byteArray[3] & 0xFF);


        // Print the extracted bits in binary form for verification
        System.out.println("Bit at index 0: " + Integer.toBinaryString(bit0));
        System.out.println("Bits from index 1 to 8: " + Integer.toBinaryString(bits1to8));
        System.out.println("Bits from index 9 to last one: " + Integer.toBinaryString(bitsFrom9to31));
        Assertions.assertNotNull(bitsFrom9to31);
        BigDecimal bigDecimal = BigDecimal.valueOf(bits1to8 - 127);
        System.out.println(bigDecimal);
        BigDecimal decimal = BigDecimal.valueOf(bitsFrom9to31);
        System.out.println(decimal);
        BigDecimal pow = BigDecimal.valueOf(2).pow(bigDecimal.intValue(), MathContext.DECIMAL32);
        System.out.println(pow);
        float parseFloat = Float.parseFloat("0." + decimal);
        System.out.println(parseFloat);
        BigDecimal multiply = BigDecimal.valueOf(parseFloat).multiply(pow, MathContext.DECIMAL32).setScale(2, RoundingMode.HALF_DOWN);
        System.out.println(multiply);
    }
}
