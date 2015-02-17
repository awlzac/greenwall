package com.bulsy.greenwall;

import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

/**
 * Created by ugliest on 12/28/14.
 */
public abstract class Screen {
    public abstract void draw(Canvas c, View v);

    public abstract boolean onTouch(MotionEvent e, MainActivity act);

    boolean eventInBounds(MotionEvent event, int x, int y, int width, int height) {
        float ex = event.getX();
        float ey = event.getY();
        return (ex > x && ex < x + width - 1 &&
                ey > y && ey < y + height - 1);
    }
}
