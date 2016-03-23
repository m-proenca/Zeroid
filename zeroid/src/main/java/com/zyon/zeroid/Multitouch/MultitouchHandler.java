package com.zyon.zeroid.Multitouch;

import android.graphics.Point;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;

import java.util.ArrayList;

public class MultitouchHandler {
    private static final String TAG = MultitouchHandler.class.getSimpleName();
    private boolean D = false;

    private class previousPoint_t {
        int X;
        int Y;

        previousPoint_t(){
            X = 0;
            Y = 0;
        }
    }

    IMultitouchListener multitouchListener = null;

    SparseArray<View> activePointers;
    ArrayList<previousPoint_t> views_points;
    ArrayList<View> viewsList;

    View popupLeftView;
    int popupLeftViewSize = 0;
    View popupRightView;
    int popupRightViewSize = 0;
    RelativeLayout mLayout;

    int minIntervalToMoveEvent = 0;

    public MultitouchHandler(RelativeLayout layout, int MinDistToMoveEvent){
        mLayout = layout;
        activePointers = new SparseArray<View>();
        views_points = new ArrayList<previousPoint_t>(10);
        for (int i = 0; i < 10; i++) {
            views_points.add(new previousPoint_t());
        }
        minIntervalToMoveEvent = MinDistToMoveEvent;
        viewsList = new ArrayList<View>();
    }

    public void pointersAdd(int pointerId, View eventView, int xyX, int xyY){
        previousPoint_t previousPoint = new previousPoint_t();
        previousPoint.X = xyX;
        previousPoint.Y = xyY;

        activePointers.put(pointerId, eventView);
        views_points.set(pointerId, previousPoint);
    }

    public void pointersRemove(int pointerId){
        activePointers.remove(pointerId);
        previousPoint_t previousPoint_t = views_points.get(pointerId);
        previousPoint_t.X = 0;
        previousPoint_t.Y = 0;
    }

    public void onTouchEvent(View view, MotionEvent event) {
        // get pointer index from the event object
        int pointerIndex = event.getActionIndex();
        // get pointer ID
        int pointerId = event.getPointerId(pointerIndex);
        // get masked (not specific to a pointer) action
        int maskedAction = event.getActionMasked();
        int eventMaskedAction = maskedAction;
        // get view location
        final int original_location[] = {0, 0};
        view.getLocationOnScreen(original_location);
        // check for action
        switch (maskedAction) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                float rawX = (int) event.getX(pointerIndex) + original_location[0];
                float rawY = (int) event.getY(pointerIndex) + original_location[1];

                // get view from array list
                View eventView = viewsList_getViewByLocation((int) rawX, (int) rawY);

                if(eventView == null){
                    if(rawX <= mLayout.getWidth()/2) {
                        if (popupLeftView != null) {
                            if((rawX > (popupLeftViewSize / 2)) && (rawY > (popupLeftViewSize / 2)) && (rawX < ((mLayout.getWidth()/2) - (popupLeftViewSize / 2))) && (rawY < (mLayout.getHeight() - (popupLeftViewSize / 2))))
                                eventView = popupLeftView;
                        }
                    } else {
                        if (popupRightView != null) {
                            if((rawX > ((popupRightViewSize / 2) + (mLayout.getWidth()/2))) && (rawY > (popupRightViewSize / 2)) && (rawX < (mLayout.getWidth() - (popupRightViewSize / 2))) && (rawY < (mLayout.getHeight() - (popupRightViewSize / 2))))
                                eventView = popupRightView;
                        }
                    }
                }

                // check if activePointers already has this View
                View pointerView = activePointer_getView(eventView);

                // if not found in array list and not in activePointer -> attach it
                if(pointerView == null && eventView != null) {
                    pointersAdd(pointerId, eventView, (int) rawX, (int) rawY);
                    if(D) Log.i(TAG, "pointerId: " + String.valueOf(pointerId) + " view " + String.valueOf(eventView.getId()) + " xy: " + String.valueOf(rawX) + "x" + String.valueOf(rawY) + " attached.");
                    eventMaskedAction = MotionEvent.ACTION_DOWN;
                    this.multitouchListener.onMultiTouch(eventView, eventMaskedAction, (int) rawX, (int) rawY);
                } else {
                    Log.i(TAG, "pointerId: " + String.valueOf(pointerId) + " pointerView is null.");
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                // loop all pointers
                for (int size = event.getPointerCount(), i = 0; i < size; i++) {
                    View eventView = activePointers.get(event.getPointerId(i));
                    if (eventView != null) {
                        // get XY
                        int rawX = (int) event.getX(i) + original_location[0];
                        int rawY = (int) event.getY(i) + original_location[1];

                        // check if current point is different from previous by minimun distance and fire event
                        previousPoint_t previousPoint = views_points.get(i);
                        if(previousPoint.X - rawX > minIntervalToMoveEvent | rawX - previousPoint.X > minIntervalToMoveEvent
                                | previousPoint.Y - rawY > minIntervalToMoveEvent | rawY - previousPoint.Y > minIntervalToMoveEvent) {

                            if(D) Log.i(TAG, "pointerId: " + String.valueOf(i) + " view: " + String.valueOf(eventView.getId()) + " previousPoint: " + String.valueOf(previousPoint.X) + "x" + String.valueOf(previousPoint.Y) + " xy: " + String.valueOf(rawX) + "x" + String.valueOf(rawY) + " Move");

                            previousPoint.X = rawX;
                            previousPoint.Y = rawY;
                            eventMaskedAction = MotionEvent.ACTION_MOVE;
                            this.multitouchListener.onMultiTouch(eventView, eventMaskedAction, rawX, rawY);
                        }
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL: {
                View eventView = activePointers.get(pointerId);
                if (eventView != null) {
                    if(D) Log.i(TAG, "pointerId: " + String.valueOf(pointerId) + " view: " + String.valueOf(eventView.getId()) + " Removed");

                    pointersRemove(pointerId);
                    eventMaskedAction = MotionEvent.ACTION_UP;
                    this.multitouchListener.onMultiTouch(eventView, eventMaskedAction, 0, 0);
                } else {
                    Log.i(TAG, "pointerId: " + String.valueOf(pointerId) + " view no found in cancel");
                }
                break;
            }
        }
    }

    public void setPopupLeftView(View v, int size){
        popupLeftView = v;
        popupLeftViewSize = size;
    }

    public void setPopupRightView(View v, int size){
        popupRightView = v;
        popupRightViewSize = size;
    }

    public View viewsList_getViewByLocation(int x, int y){
        for(int key=0; key!= viewsList.size(); key++){
            View v = viewsList.get(key);
            //GET BUTTON ABSOLUTE POSITION ON SCREEN
            int[] v_location = { 0, 0 };
            v.getLocationOnScreen(v_location);

            //FIND THE BOUNDS
            Point min = new Point(v_location[0], v_location[1]);
            Point max = new Point(min.x + v.getWidth(), min.y + v.getHeight());

            if(x>=min.x && x<=max.x && y>=min.y && y<=max.y){
                //Log.d("mylog", "***Found a view: " + v.getId());
                return v;
            }
        }

        //Log.d("mylog", "Searching: " + x +", " + y + " but not found!");
        return null;
    }

    public View activePointer_getView(View view){
        for(int i = 0; i < activePointers.size(); i++) {
            int key = activePointers.keyAt(i);
            // get the object by the key.
            Object obj = activePointers.get(key);
            if((View)obj == view){
                return (View)obj;
            }
        }

        //Log.d("mylog", "Searching: " + x +", " + y + " but not found!");
        return null;
    }

    public void addMultiTouchView(View v){
        viewsList.add(v);
    }

    public void removeMultiTouchView(View v){
        viewsList.remove(v);
    }

    public void setListener(IMultitouchListener listener) {
        this.multitouchListener = listener;
    }
}