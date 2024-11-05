package com.jaworski.serialprotocol.service.utils;

import org.springframework.lang.NonNull;

public  class CorrectionSumCalculation {

    public static int calculateLowerByte(@NonNull byte[] message) {
        int sum = 0;
        for (byte b : message) {
            sum += b;
        }
        return (byte) (sum & 0xFF);
    }
}
