package com.bulsy.greenwall;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/**
 * Created by ugliest on 12/29/14.
 */
public class EntryScreen extends Screen {
    Paint p = new Paint(Color.GREEN);

    @Override
    public void draw(Canvas c, View v) {
        c.drawRGB(40,40,40);

        c.drawRect(100,100,200,200, p);


    }

    @Override
    public boolean onTouch(MotionEvent e, MainActivity act) {
        if (eventInBounds(e, 100, 100, 200, 200))
            act.startGame();

        return true;
    }


}
