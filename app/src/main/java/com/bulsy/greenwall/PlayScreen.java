package com.bulsy.greenwall;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Represents the main screen of play for the game.
 * There are many ugly things here, as game programming lends itself
 * to questionable style.  Primarily object reuse and violation of
 * normal object oriented privacy principles.  But keeping android happy is
 * more important than data hiding and hands-free garbage collection, in this case.
 *
 * Created by ugliest on 12/29/14.
 */
public class PlayScreen extends Screen {
    static final float ZSTRETCH = 300; // lower -> more stretched on z axis
    static final float WALL_Z = 1000; // where is the wall, on the z axis?
    static final float WALL_Y_CENTER = 0.25f;  // factor giving y "center" of wall; this is used as infinity on z axis
    static final long ONESEC_NANOS = 1000000000L;
    static final int TOUCHVLIMIT = 2;
    static final int ACC_GRAVITY = 5000;
    static final int MAX_SELECTABLE_FRUIT = 2;
    static final int SELECTABLE_SPEED = 10;  // speed of selectable fruit at bottom of screen
    static final int SELECTABLE_Y_PLAY = 10;  // jiggles fruit up and down
    static final float INIT_SELECTABLE_Y_FACTOR = 0.9f;
    Paint p;
    List<Fruit> fruitsSelectable = Collections.synchronizedList(new LinkedList<Fruit>()); // fruits ready for user to throw
    List<Fruit> fruitsFlying = Collections.synchronizedList(new LinkedList<Fruit>());  // fruits user has sent flying
    List<Fruit> fruitsSplatted = new LinkedList<Fruit>(); // fruits that have splatted on wall
    List<Fruit> fruitsRecycled = new LinkedList<Fruit>(); // fruit objects no longer in use
    volatile Fruit selectedFruit = null;
    volatile float touchx, touchy;  // touchpoint location
    float touchvx, touchvy;  // touchpoint's velocity
    Bitmap wallbtm, pearbtm[], banbtm[], orangebtm[], nutbtm[];
    long touchtime = 0, frtime = 0;
    Rect scaledDst = new Rect();

    // types of throwables, initial quantities, values, etc
    int npears = 0, noranges = 0, nchoc = 0;
    Seed pearseed;
    Seed orangeseed;
    Seed nutseed;

    int round = 1;
    int score = 0;

    public PlayScreen(Context c) {
        p = new Paint();
        AssetManager assetManager = c.getAssets();
        try {
            // wall
            InputStream inputStream = assetManager.open("wall1_800x1104_16.png");
            wallbtm = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            // pear
            pearbtm = new Bitmap[4];
            inputStream = assetManager.open("pear1.png");
            pearbtm[0] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("pear2.png");
            pearbtm[1] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("pear3.png");
            pearbtm[2] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("pearsplat1.png");
            pearbtm[3] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            // banana
            banbtm = new Bitmap[4];
            inputStream = assetManager.open("ban1.png");
            banbtm[0] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("ban2.png");
            banbtm[1] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("ban3.png");
            banbtm[2] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("pearsplat1.png");
            banbtm[3] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            // orange
            orangebtm = new Bitmap[4];
            inputStream = assetManager.open("orange1.png");
            orangebtm[0] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("orange2.png");
            orangebtm[1] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("orange3.png");
            orangebtm[2] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("orangesplat.png");
            orangebtm[3] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            // nutella
            nutbtm = new Bitmap[5];
            inputStream = assetManager.open("nut1.png");
            nutbtm[0] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("nut2.png");
            nutbtm[1] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("nut3.png");
            nutbtm[2] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("nut4.png");
            nutbtm[3] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            inputStream = assetManager.open("nutsplat.png");
            nutbtm[4] = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            // initialize types of fruit (seeds), point values
            pearseed = new Seed(pearbtm, 10);
            orangeseed = new Seed(orangebtm, 15);
            nutseed = new Seed(nutbtm, 30);


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Draw the inpassed fruit, using the inpassed bitmap, rendering the
     * fruit's x/y/z position to the
     * actual x/y screen coords.
     * @param c
     * @param f
     * @param btm
     * @param xc  the x center. to which the "z axis" points
     * @param yc  the y center, to which the "z axis" points
     */
    void drawFruit3Dcoords(Canvas c, Fruit f, Bitmap btm, float xc, float yc) {
        // render effective x and y, from x y z
        float zfact = 1.0f - (ZSTRETCH/(f.z+ZSTRETCH));
        int effx = (int)(f.x + (zfact * (xc-f.x)));
        int effy = (int)(f.y + (zfact * (yc-f.y)));
        int effhalfw = (int)(f.seed.halfWidth * (1.0f - zfact));
        int effhalfh = (int)(f.seed.halfHeight * (1.0f - zfact));
        scaledDst.set(effx - effhalfw, effy - effhalfh, effx + effhalfw, effy + effhalfh);
        c.drawBitmap(btm, null, scaledDst, p);
    }

    /**
     * draw the screen.  we also update state here, before drawing.  this is technically
     * gluttonous of us, because we're using locked canvas time.
     * @param c
     * @param v
     */
    @Override
    public void draw(Canvas c, View v) {
        try {
            int width = v.getWidth();
            int height = v.getHeight();
            int inity = (int)(INIT_SELECTABLE_Y_FACTOR * height);
            int wallxcenter = width / 2;
            int wallycenter = (int)(height * WALL_Y_CENTER);
            final int minXbound = -width;
            final int maxXbound = 2*width;
            final int maxYbound = 2*height;

            if (fruitsSelectable.size() < MAX_SELECTABLE_FRUIT && Math.random() > .9) { // make a fruit available
                Fruit newf = null;
                if (fruitsRecycled.size() > 0) { // recycle a fruit if we can
                    newf = fruitsRecycled.get(0);
                    fruitsRecycled.remove(0);
                }
                else { // create if needed
                    newf = new Fruit();
                }
                int initx = 0;
                int speed = SELECTABLE_SPEED;
                if (Math.random() > .5) {
                    initx = width;
                    speed = -speed;
                }

                // choose fruit
                Seed s;
                double fruitchoice = Math.random();
                if (fruitchoice < .5)
                    s = pearseed;
                else if (fruitchoice < .9)
                    s = orangeseed;
                else
                    s = nutseed;
                newf.init(s, initx, inity, 0, speed);
                fruitsSelectable.add(newf);
            }
            else if (fruitsSelectable.size() == 0) {
                // round over?
            }

            long newtime = System.nanoTime();
            float elapsedsecs = (float)(newtime - frtime) / ONESEC_NANOS;
            frtime = newtime;

            // update fruit positions
            synchronized (fruitsFlying) {
                Iterator<Fruit> fit = fruitsFlying.iterator();
                while (fit.hasNext()) {
                    Fruit f = fit.next();
                    f.x += f.vx * elapsedsecs;
                    f.y += f.vy * elapsedsecs;
                    f.z += f.vz * elapsedsecs;
                    f.vy += ACC_GRAVITY * elapsedsecs;
                    if (f.z >= WALL_Z) { // fruit has hit wall
                        fit.remove();
                        fruitsSplatted.add(f);
                        score += f.seed.points;
                        // if combo -> more points and celebration graphic/sound
                    }
                    if (f.y > inity && f.z > WALL_Z/2) { // fruit has hit ground near wall
                        fit.remove();
                        fruitsSplatted.add(f);
                    }
                    if (f.y > maxYbound || f.x >= maxXbound || f.x <= minXbound) { //wild pitch
                        fit.remove();
                        fruitsRecycled.add(f);
                    }

                }
            }
            synchronized (fruitsSelectable) {
                Iterator<Fruit> fit = fruitsSelectable.iterator();
                while (fit.hasNext()) {
                    Fruit f = fit.next();
                    if (f != selectedFruit) {
                        f.x += f.vx;
                        f.y += SELECTABLE_Y_PLAY;
                        f.y += (inity - f.y) / 3;
                    }
                    if (f.x < -f.seed.halfWidth || f.x > width + f.seed.halfWidth) {
                        // we floated off screen
                        fit.remove();
                        fruitsRecycled.add(f);
                    }
                }
            }

            // actually draw the screen
            scaledDst.set(0, 0, width, height);
            c.drawBitmap(wallbtm, null, scaledDst, p);
            for (Fruit f : fruitsSplatted){
                drawFruit3Dcoords(c, f, f.getSplatBitmap(), wallxcenter, wallycenter);
            }
            synchronized (fruitsFlying) {
                for (Fruit f : fruitsFlying) {
                    drawFruit3Dcoords(c, f, f.getBitmap(newtime), wallxcenter, wallycenter);
                }
            }
            synchronized (fruitsSelectable) {
                for (Fruit f : fruitsSelectable) {
                    // selectable fruit is on z=0, so we can just display normally:
                    c.drawBitmap(f.seed.btm[0], f.x - f.seed.halfWidth, f.y - f.seed.halfHeight, p);
                }
            }
            p.setTextSize(40);
//            c.drawText("tvx:"+(int)touchvx+"\ttvy:"+(int)touchvy+ "\tflying:"+fruitsFlying.size()
//                            +" ffz:"+(fruitsFlying.size()>0?fruitsFlying.get(0).z:-1)
//                    +" ffvz:"+(fruitsFlying.size()>0?fruitsFlying.get(0).vz:-1),
//                    0, 100, p);
            p.setColor(Color.WHITE);
            c.drawText("ROUND "+round, 0, 50, p);
            c.drawText("SCORE: "+score, width - 200, 50, p);
        } catch (Exception e) {
            Log.e("GW", "exception", e);
            e.printStackTrace();
        }
    }

    @Override
    public boolean onTouch(MotionEvent e, MainActivity act) {
        long newtime = System.nanoTime();
        float elapsedsecs = (float)(newtime - touchtime) / ONESEC_NANOS;
        touchtime = newtime;
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchx = e.getX();
                touchy = e.getY();
                synchronized (fruitsSelectable) {
                    for (Fruit f : fruitsSelectable) {
                        if (f.hasCollision(touchx, touchy))
                            selectedFruit = f;
                    }
                }
                break;

            case MotionEvent.ACTION_MOVE:
                float newx = e.getX();
                float newy = e.getY();
                touchvx = (newx - touchx)/elapsedsecs;
                touchvy = (newy - touchy)/elapsedsecs;
                touchx = newx;
                touchy = newy;
                if (selectedFruit != null) {
                    selectedFruit.x = touchx;
                    selectedFruit.y = touchy;
                }
                break;

            case MotionEvent.ACTION_UP:
                if (selectedFruit != null) {
                    Fruit f = selectedFruit;
                    selectedFruit = null;
                    if (Math.abs(touchvx) - touchvy > TOUCHVLIMIT) {
                        // user threw fruit
                        f.throwFruit(touchvx, touchvy);
                        fruitsSelectable.remove(f);
                        fruitsFlying.add(f);
                    }
                }
                touchx = -1;
                touchy = -1;
                touchvx = 0;
                touchvy = 0;
                break;
        }

        return true;
    }

    /**
     * A Seed is more or less a template for a Fruit.
     */
    private class Seed {
        int points; // points this type of Fruit is worth, if it hits the wall.
        Bitmap btm[]; // bitmap for animating this type of throwable
        float width=0; // width onscreen
        float height=0;  // height onscreen
        float halfWidth = 0;  // convenience
        float halfHeight = 0;
        final float HALF_DIVISOR = 1.9f;  // we fudge "half" a little, results are more comfortable.

        public Seed(Bitmap bitmaps[], int points) {
            this.btm = bitmaps;
            this.width = bitmaps[0].getWidth();
            this.height = bitmaps[0].getHeight();
            this.halfWidth = width/HALF_DIVISOR;
            this.halfHeight = height/HALF_DIVISOR;
            this.points = points;
        }
    }

    /**
     * "Throwable" would be a better name here, but as that's taken, "Fruit" it is.
     * A fruit is something that the player is presented with, usually to throw at the wall.
     */
    private class Fruit {
        final int APS = 3; // number of animation cycles per second

        // position
        int initx=0;
        int inity=0;
        int initz=0;
        float x=0;
        float y=0;
        float z=0;

        // speed
        float vx=0;
        float vy=0;
        float vz=0;

        long thrownTime = 0; // when this fruit was thrown; 0 = not yet
        Seed seed=null; // the core information about this throwable fruit

        Rect bounds = new Rect();

        /**
         * initialize a fruit, at initial location.
         * @param initx
         * @param inity
         * @param initz
         */
        public void init (Seed s, int initx, int inity, int initz, int initxspeed) {
            this.initx = initx;
            this.inity = inity;
            this.initz = initz;
            this.vx = initxspeed;
            this.x = initx;
            this.y = inity;
            this.z = initz;
            this.seed = s;
        }

        /**
         * looks for a collision with an inpossed point -=- z axis ignored..
         * @param collx
         * @param colly
         */
        public boolean hasCollision(float collx, float colly) {
            bounds.set((int)(this.x - seed.halfWidth), (int)(this.y-seed.halfHeight),
                    (int)(this.x+seed.halfWidth), (int)(this.y+seed.halfHeight));
            return bounds.contains((int)collx, (int)colly);
        }

        public void throwFruit(float tvx, float tvy) {
            thrownTime = System.nanoTime();
            vx = tvx;
            vy = tvy/3;
            if (tvy < -5000)
                vy = (float)(Math.log(tvy) + (-5000 - Math.log(tvy)));
            vz = -tvy+Math.abs(tvx)/10;
        }

        public Bitmap getBitmap(long t) {
            // cycle through the images, over half a sec
            int nframes = seed.btm.length - 1;
            int idx =(int)((t - thrownTime) / (ONESEC_NANOS / (APS * nframes))) % nframes;
            return seed.btm[idx];
        }

        public Bitmap getSplatBitmap() {
            return seed.btm[seed.btm.length - 1];
        }
    }
}
