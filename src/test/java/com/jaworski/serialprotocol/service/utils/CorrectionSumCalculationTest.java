package com.jaworski.serialprotocol.service.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CorrectionSumCalculationTest {

    @Test
    public void testEmptyByteArray() {
        byte[] message = new byte[0];
        int result = CorrectionSumCalculation.calculateLowerByte(message);
        assertEquals(0, result);
    }

    @Test
    public void testSingleByteArray() {
        byte[] message = new byte[]{10};
        int result = CorrectionSumCalculation.calculateLowerByte(message);
        assertEquals(10, result);
    }

    @Test
    public void testMultipleByteArrayPositiveValues() {
        byte[] message = new byte[]{10, 20, 30, 105, 124, 111};
        int result = CorrectionSumCalculation.calculateLowerByte(message);
        assertEquals(-112, result);
    }

    @Test
    public void testMultipleByteArrayNegativeValues() {
        byte[] message = new byte[]{-10, -20, -30, -105, -124, -111};
        int result = CorrectionSumCalculation.calculateLowerByte(message);
        assertEquals(112, result);
    }

    @Test
    public void testMultipleByteArrayMixedValues() {
        byte[] message = new byte[]{10, -20, 30, -124};
        int result = CorrectionSumCalculation.calculateLowerByte(message);
        assertEquals(-104, result);
    }

    @Test
    public void testLargeByteArray() {
        byte[] message = new byte[200];
        for (int i = 0; i < 200; i++) {
            message[i] = (byte) i;
        }
        int result = CorrectionSumCalculation.calculateLowerByte(message);
        assertEquals(-68, result);
    }
}