package com.zyon.zeroid.Multitouch;

import android.view.MotionEvent;
import android.view.View;

import java.util.EventListener;

public interface IMultitouchListener extends EventListener {
    void onMultiTouch(View view, int maskedAction, int xyX, int xyY);
}