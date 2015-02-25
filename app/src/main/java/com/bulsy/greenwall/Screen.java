package com.bulsy.greenwall;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

/**
 * Created by ugliest on 12/28/14.
 */
public abstract class Screen {

    // recommended to have a screen's constructor accept the main activity, so calls back to
    // game-common things there may be made in the below methods.

    /**
     * Update the screen's state.
     */
    public abstract void update(View v);

    /**
     * draw the screen to the inpassed canvas.
     * @param c
     * @param v
     */
    public abstract void draw(Canvas c, View v);

    /**
     * handle touch event.
     * @param e
     * @return
     */
    public abstract boolean onTouch(MotionEvent e);

    /**
     * returns tru if the inpassed event location is within the inpassed rectangle bounds.
     * @param event
     * @param x
     * @param y
     * @param width
     * @param height
     * @return
     */
    boolean eventInBounds(MotionEvent event, int x, int y, int width, int height) {
        float ex = event.getX();
        float ey = event.getY();
        return (ex > x && ex < x + width - 1 &&
                ey > y && ey < y + height - 1);
    }
}
