package com.zyon.zeroid;

public class ControlCode_t {
    public int Size = 10;
    /*
    java has no unsigned int 16
    so instead uint16_t we can use char(?)

    another soluction
    short b0 = (buffer[0] & 255); // trick converts to unsigned
    short b1 = (buffer[1] & 255);
    int val = b0 | (b1 << 8);

    // or just put it all inline:
    int val = (buffer[0]&255) | ((buffer[1]&255) << 8)
    */

    public byte headerType;
    public byte Type;
    public byte b1;
    public byte b2;
    public byte b3;
    public byte b4;
    public byte b5;
    public byte b6;
    public char CRC;

    public byte[] toBytes() {
        byte[] myBuffer = new byte[Size];

        myBuffer[0] = headerType;
        myBuffer[1] = Type;
        myBuffer[2] = b1;
        myBuffer[3] = b2;
        myBuffer[4] = b3;
        myBuffer[5] = b4;
        myBuffer[6] = b5;
        myBuffer[7] = b6;
        myBuffer[8] = (byte) ((CRC & 0xFF00) >> 8);
        myBuffer[9] = (byte) (CRC & 0x00FF);
        return myBuffer;
    }

    public void fromBytes(byte[] Bytes) {
        headerType = Bytes[0];
        Type = Bytes[1];
        b1 = Bytes[2];
        b2 = Bytes[3];
        b3 = Bytes[4];
        b4 = Bytes[5];
        b5 = Bytes[6];
        b6 = Bytes[7];
        CRC = (char) ((Bytes[8] << 8) + (Bytes[9] & 0xFF));
    }

    public void Clear() {
        headerType = 0x00;
        Type = 0x00;
        b1 = 0x00;
        b2 = 0x00;
        b3 = 0x00;
        b4 = 0x00;
        b5 = 0x00;
        b6 = 0x00;
        CRC = 0x0000;
    }
}
