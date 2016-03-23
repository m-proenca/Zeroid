package com.zyon.zeroid.Util;

public class ByteExt {
    public static final char getCRCMODBUS(byte[] buf, int len) {
        char crc = 0xFFFF;
        //ushort val = 0;

        for (int pos = 0; pos < len; pos++) {
            crc ^= (char)(0x00ff & buf[pos]); // FIX HERE -- XOR byte into least sig. byte of crc

            for (int i = 8; i != 0; i--) {    // Loop over each bit
                if ((crc & 0x0001) != 0) {    // If the LSB is set
                    crc >>= 1;                // Shift right and XOR 0xA001
                    crc ^= 0xA001;
                }
                else                          // Else LSB is not set
                    crc >>= 1;                // Just shift right
            }
        }
        // Note, crc has low and high bytes swapped, so use it accordingly (or swap bytes)
        //val = (ushort)((crc & 0xff) << 8);
        //val = (ushort)(val + ((crc >> 8) & 0xff));
        //System.out.printf("Calculated a CRC of 0x%x, swapped: 0x%x\n", crc, val);
        //return val;
        return crc;
    }

    public static final char getCRCMODBUS2(byte[] data, int byteStart, int len) {
        char crc = 0xFFFF;
        //char val = 0xFFFF;

        int End = len + byteStart;

        for (int pos = byteStart; pos < End; pos++) {
            crc ^= (char)data[pos];          // XOR byte into least sig. byte of crc

            for (byte i = 8; i != 0; i--) {    // Loop over each bit
                if ((crc & 0x0001) != 0) {      // If the LSB is set
                    crc >>= 1;                    // Shift right and XOR 0xA001
                    crc ^= 0xA001;
                }
                else                            // Else LSB is not set
                    crc >>= 1;                    // Just shift right
            }
        }
        // Note, crc has low and high bytes swapped, so use it accordingly (or swap bytes)
        //val = (char)((crc & 0xff) << 8);
        //val = (char)(val + ((crc >> 8) & 0xff));
        //return val;
        return crc;
    }

    public static int getCRCMODBUS(byte[] data, int byteStart, int len) {
        int crc =  0xFFFF;
        int val = 0;
        int End = len + byteStart;

        for (int pos = byteStart; pos < End; pos++) {
            crc ^= (int)(0x00ff & data[pos]);  // FIX HERE -- XOR byte into least sig. byte of crc

            for (int i = 8; i != 0; i--) {    // Loop over each bit
                if ((crc & 0x0001) != 0) {      // If the LSB is set
                    crc >>= 1;                    // Shift right and XOR 0xA001
                    crc ^= 0xA001;
                }
                else                            // Else LSB is not set
                    crc >>= 1;                    // Just shift right
            }
        }
        // Note, crc has low and high bytes swapped, so use it accordingly (or swap bytes)
        //val =  (crc & 0xff) << 8;
        //val =  val + ((crc >> 8) & 0xff);
        //System.out.printf("Calculated a CRC of 0x%x, swapped: 0x%x\n", crc, val);
        //return val;
        return crc;
    }   // end GetCRC

    public static byte SetBit(byte self, int index) {
        byte mask = (byte)(1 << index);
        self = (byte)(self | mask);
        return self;
    }

    public static byte ClearBit(byte self, int index) {
        byte mask = (byte)(1 << index);
        self = (byte)(self & ~mask);
        return self;
    }

    public static boolean GetBit(byte self, int index, boolean value) {
        byte mask = (byte)(1 << index);
        return (self & mask) != 0;
    }

    // Writes provided 4-byte integer to a 4 element byte array in Little-Endian order.
    public static final byte[] intToByteArray(int value) {
        return new byte[] {
                (byte)(value & 0xff),
                (byte)(value >> 8 & 0xff),
                (byte)(value >> 16 & 0xff),
                (byte)(value >>> 24)
        };
    }

    // Writes provided 4-byte array containing a little-endian integer to a big-endian integer.
    public static final int byteArrayToInt(byte[] value) {
        int ret = ((value[0] & 0xFF) << 24) | ((value[1] & 0xFF) << 16) |
                ((value[2] & 0xFF) << 8) | (value[3] & 0xFF);

        return ret;
    }

    public static String toString(byte[] bytes) {
        return new String(bytes);
    }

    public static String printBits(byte myByte) {
        return Integer.toBinaryString(myByte & 0xFF).replace(' ', '0');
    }
}
