package org.astrobotics.ds2017.io;


/******************************************************************************
 * Compilation:  javac CRC16CCITT.java
 * Execution:    java CRC16CCITT s
 * Dependencies:
 * <p/>
 * Reads in a sequence of bytes and prints out its 16 bit
 * Cylcic Redundancy Check (CRC-CCIIT 0xFFFF).
 * <p/>
 * 1 + x + x^5 + x^12 + x^16 is irreducible polynomial.
 * <p/>
 * Copyright (c) 2000-2011, Robert Sedgewick and Kevin Wayne.
 ******************************************************************************/

public class CRC16CCITT {
    public static final int POLY = 0x1021; // 0001 0000 0010 0001  (0, 5, 12)
    public static final int DEF_CRC = 0xFFFF;

    public static int crc16(byte[] bytes) {
        return crc16(bytes, DEF_CRC);
    }

    public static int crc16(byte[] bytes, int crc) {
        for (byte b : bytes) {
            for (int i = 0; i < 8; i++) {
                boolean bit = ((b >> (7 - i) & 1) == 1);
                boolean c15 = ((crc >> 15 & 1) == 1);
                crc <<= 1;
                if (c15 ^ bit) crc ^= POLY;
            }
        }

        crc &= 0xffff;
        return crc;
    }
}
