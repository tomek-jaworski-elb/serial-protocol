package com.jaworski.shipsim.codec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Writes a {@link Frame} in the wire layout the application's {@code MessageCommon} and
 * {@code MessageLadyMarie} decode. The golden file in the application's test resources is the
 * contract: {@code FrameEncoderTest} must reproduce every recorded frame byte for byte.
 *
 * <p>Scaled fields are rounded with {@link Math#round} because a value such as
 * {@code 256.94 * 100} is not an exact double. The checksum is the low byte of the signed sum
 * of every byte before it, which is what {@code CorrectionSumCalculation} computes on the other
 * side. The frame ends with CR LF, the delimiter the serial listener splits on.</p>
 */
public final class FrameEncoder {

    public static final int COMMON_LENGTH = 27;
    public static final int LADY_MARIE_LENGTH = 29;
    public static final byte CR = 13;
    public static final byte LF = 10;

    private static final int RESERVED_OFFSET = 13;

    public byte[] encode(Frame frame) {
        boolean ladyMarie = frame.isLadyMarieLayout();
        byte[] out = new byte[ladyMarie ? LADY_MARIE_LENGTH : COMMON_LENGTH];
        out[0] = (byte) frame.prefix().charAt(0);
        out[1] = (byte) frame.prefix().charAt(1);
        putU16(out, 2, scaled(frame.heading(), 10, "heading"));
        putU16(out, 4, scaled(frame.bowTugDirection(), 100, "bowTugDirection"));
        out[6] = i8(scaled(frame.bowTugForce(), 10, "bowTugForce"), "bowTugForce");
        out[7] = i8(frame.engine(), "engine");
        out[8] = i8(scaled(frame.sternTugForce(), 10, "sternTugForce"), "sternTugForce");
        putU16(out, 9, scaled(frame.sternTugDirection(), 100, "sternTugDirection"));
        out[11] = i8(scaled(frame.rudder(), 10, "rudder"), "rudder");
        out[12] = i8(scaled(frame.speed(), 10, "speed"), "speed");

        int position = RESERVED_OFFSET;
        if (ladyMarie) {
            byte[] reserved = frame.reserved() == null ? new byte[2] : frame.reserved();
            out[RESERVED_OFFSET] = reserved[0];
            out[RESERVED_OFFSET + 1] = reserved[1];
            position += 2;
        }
        putFloatLittleEndian(out, position, frame.positionX());
        putFloatLittleEndian(out, position + 4, frame.positionY());
        out[position + 8] = i8(frame.bowThruster(), "bowThruster");
        putU16(out, position + 9, scaled(frame.gpsQuality(), 100, "gpsQuality"));
        int checksumIndex = position + 11;
        out[checksumIndex] = checksum(out, checksumIndex);
        out[checksumIndex + 1] = CR;
        out[checksumIndex + 2] = LF;
        return out;
    }

    /** Low byte of the signed sum of {@code out[0..length)}, as the application recomputes it. */
    static byte checksum(byte[] out, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += out[i];
        }
        return (byte) sum;
    }

    private static long scaled(double value, int scale, String field) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(field + " must be a finite number, was " + value);
        }
        return Math.round(value * scale);
    }

    private static byte i8(long value, String field) {
        if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
            throw new IllegalArgumentException(field + " does not fit the frame: scaled value " + value
                    + " is outside " + Byte.MIN_VALUE + ".." + Byte.MAX_VALUE);
        }
        return (byte) value;
    }

    private static void putU16(byte[] out, int offset, long value) {
        if (value < 0 || value > 0xFFFF) {
            throw new IllegalArgumentException("value at offset " + offset + " does not fit the frame: scaled value "
                    + value + " is outside 0..65535");
        }
        out[offset] = (byte) (value >> 8);
        out[offset + 1] = (byte) value;
    }

    private static void putFloatLittleEndian(byte[] out, int offset, float value) {
        ByteBuffer.wrap(out, offset, 4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value);
    }
}
