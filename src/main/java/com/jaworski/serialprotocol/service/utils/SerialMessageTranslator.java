package com.jaworski.serialprotocol.service.utils;

public interface SerialMessageTranslator {

    int getModelId(byte[] delimitedMessage);
    Double getSpeed(byte[] message);
    Double getGPSQuality(byte[] message);
    Double getHeading(byte[] message);
    Double getEngine(byte[] message);
    Double getTugBowForce(byte[] message);
    Double getTugSternForce(byte[] message);
    Double getTugBowDirection(byte[] message);
    Double getTugSternDirection(byte[] message);
    Double getRudder(byte[] message);
    Double getBowThruster(byte[] message);
    Float getPositionY(byte[] message) throws IllegalArgumentException;
    Float getPositionX(byte[] message) throws IllegalArgumentException;

}
