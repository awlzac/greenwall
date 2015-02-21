package com.bulsy.greenwall;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

import java.io.InputStream;

/**
 * Created by ugliest on 12/29/14.
 */
public class EntryScreen extends Screen {
    MainActivity act;
    Paint p = new Paint(Color.GREEN);
    Bitmap screenbtm, playbtm, exitbtm;
    Rect scaledDst = new Rect(); // generic rect for scaling
    Rect playBtnBounds = null;
    Rect exitBtnBounds = null;

    public EntryScreen(MainActivity act) {
        this.act = act;
        try {
            // load screen bg
            AssetManager assetManager = act.getAssets();
            InputStream inputStream = assetManager.open("entryscreen.png");
            screenbtm = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("playbtn.png");
            playbtm = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("exitbtn.png");
            exitbtm = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
        }
        catch (Exception e) {
            // really tho, what to do with exceptions on android?
        }
    }

    @Override
    public void update(View v) {
      // nothing to update
    }

    @Override
    public void draw(Canvas c, View v) {
        int width = v.getWidth();
        int height = v.getHeight();
        if (playBtnBounds == null) {
            // initialize button locations
            playBtnBounds = new Rect(width/4 - playbtm.getWidth()/2,
                    height * 3/4 - playbtm.getHeight()/2,
                    width/4 + playbtm.getWidth()/2,
                    height * 3/4 + playbtm.getHeight()/2);
            exitBtnBounds = new Rect(width*3/4 - exitbtm.getWidth()/2,
                    height * 3/4 - exitbtm.getHeight()/2,
                    width*3/4 + exitbtm.getWidth()/2,
                    height * 3/4 + exitbtm.getHeight()/2);
        }

        // draw the screen
        scaledDst.set(0, 0, width, height);
        c.drawBitmap(screenbtm, null, scaledDst, p);

        c.drawBitmap(playbtm, null, playBtnBounds, p);
        c.drawBitmap(exitbtm, null, exitBtnBounds, p);
    }

    @Override
    public boolean onTouch(MotionEvent e) {
        if (playBtnBounds.contains((int)e.getX(), (int)e.getY()))
            act.startGame();
        if (exitBtnBounds.contains((int)e.getX(), (int)e.getY()))
            act.exit();

        return true;
    }


}
