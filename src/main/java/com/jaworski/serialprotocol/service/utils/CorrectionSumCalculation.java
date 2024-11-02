package com.jaworski.serialprotocol.service.utils;

public  class CorrectionSumCalculation {

    public static int calculateLowByte(byte[] message) {
        int sum = 0;
        for (byte b : message) {
            sum += b;
        }
        return sum & 0xFF;
    }
}
