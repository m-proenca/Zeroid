package com.zyon.zeroid;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;

import com.zyon.zeroid.Util.zMath;

public class JoystickView extends View {
    private static final String TAG = JoystickView.class.getSimpleName();
    boolean D = false;

    IJoyTouchListener joyTouchListener = null;

    Paint paint = new Paint();
    Bitmap background;
    Bitmap stick;

    int originX = 0;
    int originY = 0;

    int pointX = 0;
    int pointY = 0;

    int distance = 0;
    int angle = 0;

    int outX = 0;
    int outY = 0;
    int outDistance = 0;

    public int minDistance = 10;
    public int maxDistance = 127;
    int beetleSize = 100;
    int joystickSize = 255;

    boolean touch_state = true;
    boolean wasDeadZone = false;
    RelativeLayout mRelativeLayout;

    public JoystickView(Context context, RelativeLayout relativeLayout, int BeetleSize, int JoystickSize, int MinDistance) {
        super(context);

        beetleSize = BeetleSize;

        joystickSize = JoystickSize;

        minDistance = MinDistance;
        maxDistance = JoystickSize / 2;

        distance = minDistance;

        mRelativeLayout = relativeLayout;

        background = BitmapFactory.decodeResource(context.getResources(), R.drawable.image_button_bg);
        stick = BitmapFactory.decodeResource(context.getResources(), R.drawable.beetle);

        background = getResizedBitmap(background, joystickSize, joystickSize);
        stick = getResizedBitmap(stick, beetleSize, beetleSize);
    }

    public Bitmap getResizedBitmap(Bitmap bm, float newHeight, float newWidth) {
        int width = bm.getWidth();
        int height = bm.getHeight();

        float scaleWidth = (newWidth) / width;
        float scaleHeight = (newHeight) / height;

        // create a matrix for the manipulation
        Matrix matrix = new Matrix();
        // resize the bit map
        matrix.postScale(scaleWidth, scaleHeight);
        // recreate the new Bitmap
        Bitmap resizedBitmap = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, false);

        return resizedBitmap;
    }

    public boolean onTouch(int maskedAction, int PointX, int PointY) {
        boolean sendEvent = false;

        switch (maskedAction) {
            //if (arg1.getAction() == MotionEvent.ACTION_DOWN) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                originX = PointX;
                originY = PointY;

                pointX = PointX;
                pointY = PointY;

                distance = minDistance;

                touch_state = true;
                wasDeadZone = true;
                sendEvent = false;

                mRelativeLayout.addView(this);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (touch_state) {
                    XYToDegrees(PointX, PointY, originX, originY); //got angle
                    distance = getDistance(PointX, PointY, originX, originY); //got distance
                    DegreesToXY(angle,distance,originX, originY); //got pointX pointY

                    if (distance < minDistance) {
                        pointX = originX;
                        pointY = originY;
                        distance = minDistance;

                        //fire event only when join to dead zone
                        if (!wasDeadZone) {
                            sendEvent = true;
                            wasDeadZone = true;
                        }

                        if(D) Log.i(TAG, "MOVE D<MC: " + String.valueOf(PointX) + ", Y: " + String.valueOf(PointY) + ", posX: " + String.valueOf(pointX) + ", posY: " + String.valueOf(pointY) + ", angle: " + String.valueOf(angle) + ", distance: " + String.valueOf(distance));
                    } else {
                        wasDeadZone = false;
                        sendEvent = true;

                        if(D) Log.i(TAG, "MOVE X: " + String.valueOf(PointX) + ", Y: " + String.valueOf(PointY) + ", posX: " + String.valueOf(pointX) + ", posY: " + String.valueOf(pointY) + ", angle: " + String.valueOf(angle) + ", distance: " + String.valueOf(distance));
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (touch_state) {
                    touch_state = false;
                    //back to center forcing distance to zero
                    pointX = originX;
                    pointY = originY;
                    distance = minDistance;
                    sendEvent = true;

                    //if(mRelativeLayout.findViewById(draw.getId()) != null)
                    mRelativeLayout.removeView(this);
                    invalidate();
                }
            }
            break;
        }
        invalidate();

        if (sendEvent) {
            outX = zMath.map(pointX, originX - maxDistance, originX + maxDistance, 0, 255);
            outY = zMath.map(pointY, originY - maxDistance, originY + maxDistance, 0, 255);
            outDistance = zMath.map(distance, minDistance, maxDistance, 0, 127);

            if(D) Log.i(TAG, "Distance: " + String.valueOf(distance) + ", Min: " + String.valueOf(minDistance) + ", Max: " + String.valueOf(maxDistance) + ", out: " + String.valueOf(outDistance));

            if(D) Log.i(TAG, "Event PointX: " + String.valueOf(PointX) + ", PointY: " + String.valueOf(PointY) + ", outX: " + String.valueOf(outX) + ", outY: " + String.valueOf(outY) + ", angle: " + String.valueOf(angle) + ", outDistance: " + String.valueOf(outDistance));

            this.joyTouchListener.onJoyTouch(angle, outDistance, outX, outY);
        }
        return true;
    }

    double XYToDegrees(int xyX, int xyY, int OriginX, int OriginY) {
        //double angle = 0;
//		int OriginX = 127;
//		int originY = 127;

        if (xyY <= OriginY) {
            if (xyX > OriginX) {
                //up right
                //angle = (double)(xyX - OriginX) / (double)(originY - xyY);
                //angle = atan(-angle);
                //angle = 270 - angle * 180 / 3.14159 /*PI*/;

                angle = (int) (270.0 - Math.atan(-(double) (xyX - OriginX) / (double) (OriginY - xyY)) * 180.0 / 3.14159);
            } else if (xyX <= OriginX) {
                //up left
                //angle = (double)(OriginX - xyX) / (double)(originY - xyY);
                //angle = atan(angle);
                //angle = 270.0f - angle * 180.0f / 3.14159f /*PI*/;
                angle = (int) (270.0 - Math.atan((double) (OriginX - xyX) / (double) (OriginY - xyY)) * 180.0 / 3.14159) /*PI*/;
            }
        } else if (xyY > OriginY) {
            if (xyX > OriginX) {
                //down right
                //angle = (double)(xyX - OriginX) / (double)(xyY - originY);
                //angle = atan(angle);
                //angle = 90.0f - angle * 180.0f / 3.14159f /*PI*/;
                angle = (int) (90.0 - Math.atan((double) (xyX - OriginX) / (double) (xyY - OriginY)) * 180.0 / 3.14159 /*PI*/);
            } else if (xyX <= OriginX) {
                //down left
                //angle = (double)(OriginX - xyX) / (double)(xyY - originY);
                //angle = atan(-angle);
                //angle = 90.0f - angle * 180.0f / 3.14159f /*PI*/;
                angle = (int) (90.0 - Math.atan(-(double) (OriginX - xyX) / (double) (xyY - OriginY)) * 180.0 / 3.14159f /*PI*/);
            }
        }
        return angle;
        //if (angle > 180) angle -= 360; //Optional. Keeps values between -180 and 180
    }

    private int getDistance(int xyX, int xyY, int OriginX, int OriginY){
        int Radius =  (int) Math.sqrt(Math.pow((xyX - OriginX), 2) + Math.pow((xyY - OriginY), 2));

        if(Radius > maxDistance)
            Radius = maxDistance;

        return Radius;
    }

    private void DegreesToXY(int degrees, int radius, int originX, int originY) {
        double radians = (double)degrees * Math.PI / 180.0;

        pointX = (int) (Math.cos(-radians) * (double)radius + (double) originX);
        pointY = (int) (Math.sin(radians) * (double)radius + (double) originY);
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (touch_state) {
            double stickDistance = ((double)distance - (((double) beetleSize / 2) * ((double)distance / (double)maxDistance)));

            double radians = angle * Math.PI / 180.0;
            int stickX = (int) (Math.cos(-radians) * stickDistance + (double) originX);
            int stickY = (int) (Math.sin(radians) * stickDistance + (double) originY);

            canvas.drawBitmap(background, originX - (joystickSize / 2), originY - (joystickSize / 2), paint);
            canvas.drawBitmap(stick, stickX - (beetleSize / 2), stickY - (beetleSize / 2), paint);
        }
    }

    public void setListener(IJoyTouchListener listener) {
        this.joyTouchListener = listener;
    }
}