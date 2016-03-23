package com.zyon.zeroid.Util;

/* package */ 
public final class MovingAverage
{
    private final int mNumValues;
    private final long[] mValues;
    private int mEnd = 0;
    private int mLength = 0;
    private long mSum = 0L;

    /* package */ 
    public MovingAverage(final int numValues){
        super();
        mNumValues = numValues;
        mValues = new long[numValues];
    } // constructor()

    /* package */
    public void update(final long value){
        mSum -= mValues[mEnd];
        mValues[mEnd] = value;
        mEnd = (mEnd + 1) % mNumValues;
        if (mLength < mNumValues)
        {
            mLength++;
        } // if
        mSum += value;
    } // update(long)

    /* package */
    public double getAverage(){
        return mSum / (double) mLength;
    } // getAverage()

} // class MovingAverage
