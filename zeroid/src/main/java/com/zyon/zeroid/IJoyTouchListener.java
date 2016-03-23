package com.zyon.zeroid;

import java.util.EventListener;

public interface IJoyTouchListener extends EventListener {
    void onJoyTouch(int Degrees, int Radius, int X, int Y);
}