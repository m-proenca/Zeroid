package com.zyon.zeroid.Util;

import java.io.IOException;
import java.io.OutputStream;

/* package */ 
public final class MemoryOutputStream extends OutputStream{
    private final byte[] mBuffer;
    private int mLength = 0;
    
    /* package */ 
    public MemoryOutputStream(final int size){
        this(new byte[size]);
    } // constructor(int)
    
    /* package */
    public MemoryOutputStream(final byte[] buffer){
        super();
        mBuffer = buffer;
    } // constructor(byte[])
    
    @Override
    public void write(final byte[] buffer, final int offset, final int count) throws IOException {
        checkSpace(count);
        System.arraycopy(buffer, offset, mBuffer, mLength, count);
        mLength += count;
    } // write(buffer, offset, count)
    
    @Override
    public void write(final byte[] buffer) throws IOException{
        checkSpace(buffer.length);
        System.arraycopy(buffer, 0, mBuffer, mLength, buffer.length);
        mLength += buffer.length;
    } // write(byte[])
    
    @Override
    public void write(final int oneByte) throws IOException{
        checkSpace(1);
        mBuffer[mLength++] = (byte) oneByte;
    } // write(int)
    
    private void checkSpace(final int length) throws IOException{
        if (mLength + length >= mBuffer.length)
        {
            throw new IOException("insufficient space in buffer");
        } // if
    } // checkSpace(int)
    
    /* package */
    public void seek(final int index){
        mLength = index;
    } // seek(int)

    /* package */
    public byte[] getBuffer(){
        return mBuffer;
    } // getBuffer()

    /* package */
    public int getLength(){
        return mLength;
    } // getLength()

} // class MemoryOutputStream
