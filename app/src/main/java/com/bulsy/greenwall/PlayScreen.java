package com.bulsy.greenwall;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.support.v4.view.VelocityTrackerCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
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
    static final float ZSTRETCH = 240; // lower -> more stretched on z axis
    static final float WALL_Z = 1000; // where is the wall, on the z axis?
    static final float WALL_Y_CENTER_FACTOR = 0.38f;  // factor giving y "center" of wall; this is used as infinity on z axis
    static final Rect wallbounds_at_z = new Rect();  // wall bounds AT WALL Z (!!WaLLzeY!!)
    static final Rect wallbounds_at_screen = new Rect();  // wall bounds at screen z
    static final float WALLZFACT = 1.0f - (ZSTRETCH/(WALL_Z+ZSTRETCH));
    static final long ONESEC_NANOS = 1000000000L;
    static final int TOUCHVLIMIT = 2;
    static final int ACC_GRAVITY = 5000;
    static final int MAX_SELECTABLE_FRUIT = 2;
    static final int INIT_SELECTABLE_SPEED = 5;  // speed of selectable fruit at bottom of screen
    static final int SELECTABLE_Y_PLAY = 10;  // jiggles fruit up and down
    static final float INIT_SELECTABLE_Y_FACTOR = 0.9f;
    Paint p;
    Point effpt = new Point(); // reusable point for rendering, to avoid excessive obj creation.  brutally un-threadsafe, obviously, but we will use it in only the render thread.
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
    MainActivity act = null;
    int selectable_speed;
    boolean showRoundSummary = false;

    int width = 0;
    int height = 0;
    int wallxcenter = 0;
    int wallycenter = 0;
    int inity = 0;
    int minXbound = 0;
    int maxXbound = 0;
    int maxYbound = 0;

    // types of throwables, remaining quantities, values, etc
    List<Seed> seedsQueued = new LinkedList<Seed>();
    Seed pearseed;
    Seed orangeseed;
    Seed nutseed;
    int nWallSplats = 0;
    int nTotFruit = 0;

    int round = 1;
    int score = 0;

    /**
     * init game for current round
     */
    private void initRound() {
        selectable_speed = INIT_SELECTABLE_SPEED + (round/2);

        for (Fruit f:fruitsSplatted)
          fruitsRecycled.add(f);
        fruitsSplatted.clear();

        // set up fruits to throw
        seedsQueued.clear();
        int npears = 5 + round;
        for (int i = 0; i < npears; i++)
            seedsQueued.add(pearseed);

        int noranges = 0;
        if (round > 1)
            noranges = 4 + round;
        for (int i = 0; i < noranges; i++) {
            int loc = 2 + (int) (Math.random() * seedsQueued.size() - 2);
            seedsQueued.add(loc, orangeseed);
        }

        int nnuts = 0;
        if (round > 2)
            nnuts = round;
        for (int i = 0; i < nnuts; i++) {
            int loc = 2 + (int) (Math.random() * seedsQueued.size() - 2);
            seedsQueued.add(loc, nutseed);
        }

        nWallSplats = 0;
        nTotFruit = seedsQueued.size();
    }

    public PlayScreen(MainActivity act) {
        p = new Paint();
        this.act = act;
        AssetManager assetManager = act.getAssets();
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

            // init combos
            //comboFruitSalad = new Combo({pearseed, orangeseed}, btm, 40);

            p.setTypeface(act.getGameFont());
            round = 1;
            initRound();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * return the effective screen x,y point rendered from the inpassed
     * (x, y, z) point.
     * @param x
     * @param y
     * @param z
     * @return
     */
    private Point renderFromZ(float x, float y, float z, float xc, float yc) {
        float zfact = 1.0f - (ZSTRETCH/(z+ZSTRETCH));
        int effx = (int)(x + (zfact * (xc-x)));
        int effy = (int)(y + (zfact * (yc-y)));
        effpt.set(effx, effy);
        return effpt;
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
        //effpt = renderFromZ(f.x, f.y, f.z, xc, yc);
        //int effx = effpt.x;
        //int effy = effpt.y;
        int effhalfw = (int)(f.seed.halfWidth * (1.0f - zfact));
        int effhalfh = (int)(f.seed.halfHeight * (1.0f - zfact));
        scaledDst.set(effx - effhalfw, effy - effhalfh, effx + effhalfw, effy + effhalfh);
        c.drawBitmap(btm, null, scaledDst, p);
    }

    @Override
    public void update(View v) {
        if (showRoundSummary)
            return;  // nothing to update

        if (width == 0) {
            // set variables that rely on screen size
            width = v.getWidth();
            height = v.getHeight();
            wallxcenter = width / 2;
            wallycenter = (int) (height * WALL_Y_CENTER_FACTOR);

            inity = (int) (INIT_SELECTABLE_Y_FACTOR * height); // initial fruit placement, also bottom of wall.
            minXbound = 8 * -width;
            maxXbound = 8 * width;
            maxYbound = 5 * height;

            // attempt to compute wall bounds from screen size
            wallbounds_at_z.set((int)(-1.5*width), (int)(-height*.9), (int)(2.43*width), inity);  // wall bounds AT WALL Z (!!WaLLzeY!!)

            // compute wall bounds at screen, used for clipping.
            int effl = (int) (wallbounds_at_z.left + (WALLZFACT * (wallxcenter - wallbounds_at_z.left)));
            int efft = (int) (wallbounds_at_z.top + (WALLZFACT * (wallycenter - wallbounds_at_z.top)));
            int effr = (int) (wallbounds_at_z.right + (WALLZFACT * (wallxcenter - wallbounds_at_z.right)));
            int effb = (int) (wallbounds_at_z.bottom + (WALLZFACT * (wallycenter - wallbounds_at_z.bottom)));
            wallbounds_at_screen.set(effl, efft, effr, effb);
        }

        long newtime = System.nanoTime();
        float elapsedsecs = (float)(newtime - frtime) / ONESEC_NANOS;
        frtime = newtime;

        if (fruitsSelectable.size() < MAX_SELECTABLE_FRUIT
                && seedsQueued.size() > 0
                && Math.random() > .9) { // make a fruit available
            Fruit newf = null;
            if (fruitsRecycled.size() > 0) { // recycle a fruit if we can
                newf = fruitsRecycled.get(0);
                fruitsRecycled.remove(0);
            }
            else { // create if needed
                newf = new Fruit();
            }
            int initx = 0;
            int speed = selectable_speed;
            if (Math.random() > .5) {
                initx = width;
                speed = -speed;
            }

            // choose fruit
            Seed s = seedsQueued.get(0);
            seedsQueued.remove(0);
            newf.init(s, initx, inity, 0, speed);
            fruitsSelectable.add(newf);
        }
        else if (fruitsSelectable.size() == 0
                && fruitsFlying.size() == 0
                && seedsQueued.size() == 0) {
            showRoundSummary = true;
            if (nWallSplats*100/nTotFruit >= 50)
                round++;
        }

        // update fruit positions
        synchronized (fruitsFlying) {
            Iterator<Fruit> fit = fruitsFlying.iterator();
            while (fit.hasNext()) {
                Fruit f = fit.next();
                f.x += f.vx * elapsedsecs;
                f.y += f.vy * elapsedsecs;
                f.z += f.vz * elapsedsecs;
                f.vy += ACC_GRAVITY * elapsedsecs;
                if (f.z >= WALL_Z && wallbounds_at_z.contains((int)f.x, (int)f.y)) {
                    // fruit has hit wall
                    fit.remove();
                    fruitsSplatted.add(f);
                    nWallSplats++;
                    score += f.seed.points;
                    //act.getSound

                    // check combo
                    // if combo -> more points and celebration graphic/sound
                }
                else if (f.y > inity
                        && f.y < inity + f.vy * elapsedsecs
                        && f.z > WALL_Z/2) {
                    // fruit has hit ground near wall
                    fit.remove();
                    fruitsSplatted.add(f);
                }
                // here we goofily force java to call the function when we need it
                else if (f.z > WALL_Z
                        && (effpt = renderFromZ(f.x, f.y, f.z, wallxcenter, wallycenter))!=null
                        && wallbounds_at_screen.contains(effpt.x, effpt.y)
                        ) {
                    // wild pitch, behind wall
                    fit.remove();
                    fruitsRecycled.add(f);
                }
                else if (f.y > maxYbound
                        || f.x >= maxXbound
                        || f.x <= minXbound) {
                    // wild pitch, out of bounds
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

    }

    /**
     * draw the screen.
     * @param c
     * @param v
     */
    @Override
    public void draw(Canvas c, View v) {
        try {
            // actually draw the screen
            scaledDst.set(0, 0, width, height);
            c.drawBitmap(wallbtm, null, scaledDst, p);

            // draw wall's bounds, for debugging
            //p.setColor(Color.RED);
            //c.drawRect(wallbounds_at_screen, p);

            // draw fruits
            for (Fruit f : fruitsSplatted){
                drawFruit3Dcoords(c, f, f.getSplatBitmap(), wallxcenter, wallycenter);
            }
            synchronized (fruitsFlying) {
                for (Fruit f : fruitsFlying) {
                    drawFruit3Dcoords(c, f, f.getBitmap(System.nanoTime()), wallxcenter, wallycenter);
                }
            }
            synchronized (fruitsSelectable) {
                for (Fruit f : fruitsSelectable) {
                    // selectable fruit is on z=0, so we can just display normally:
                    c.drawBitmap(f.seed.btm[0], f.x - f.seed.halfWidth, f.y - f.seed.halfHeight, p);
                }
            }
//            c.drawText(
//                        "x:"+touchx+" y:"+touchy+" tvx:"+(int)touchvx+"\ttvy:"+(int)touchvy+
//                    "\tflying:" + fruitsFlying.size()
//                            + " ffz:" + (fruitsFlying.size() > 0 ? fruitsFlying.get(0).z : -1)
//                            + " ffvz:" + (fruitsFlying.size() > 0 ? fruitsFlying.get(0).vz : -1),
//                    0, 100, p);
            p.setColor(Color.WHITE);
            p.setTextSize(45);
            p.setTypeface(act.getGameFont());
            p.setFakeBoldText(true);
            c.drawText("ROUND "+round, width - 300, 60, p);
            c.drawText("SCORE: "+score, 10, 60, p);

            if (showRoundSummary) {
                // round ended, display stats
                int splatPct = (int)(nWallSplats*100/nTotFruit);

                c.drawText(splatPct+"% sPLAttaGe!", width/4, height/3, p);
                if (splatPct < 50)
                    c.drawText("Ooops...", width/4, (int)(height/2.5), p);
                else if (splatPct < 60)
                    c.drawText("Not too bad.", width/4, (int)(height/2.5), p);
                else if (splatPct < 70)
                    c.drawText("Nice!", width*3/4, (int)(height/2.5), p);
                else if (splatPct < 80) {
                    c.drawText("sPAzTIc!", width / 3, (int) (height / 2.5), p);
                    c.drawText("CruDe!!", width / 2, (int) (height / 2.2), p);
                }
                else if (round > 5) {
                    c.drawText("Dude, really?!", width / 4, (int) (height / 2.5), p);
                    c.drawText("That was awesome.", width / 3, (int) (height / 2.2), p);
                }
                else {
                    c.drawText("eEEeEeeEh!! sPAzTIc!", width / 4, (int) (height / 2.5), p);
                }
                c.drawText("Touch to continue", width/4, height*2/3, p);
            }

        } catch (Exception e) {
            Log.e("GW", "exception", e);
            e.printStackTrace();
        }
    }

    VelocityTracker mVelocityTracker = null;
    @Override
    public boolean onTouch(MotionEvent e) {
        long newtime = System.nanoTime();
        float elapsedsecs = (float)(newtime - touchtime) / ONESEC_NANOS;
        touchtime = newtime;
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (showRoundSummary) {
                    showRoundSummary = false; // tapping screen lets us continue game
                    initRound();
                }
                touchx = e.getX();
                touchy = e.getY();
                synchronized (fruitsSelectable) {
                    for (Fruit f : fruitsSelectable) {
                        if (f.hasCollision(touchx, touchy))
                            selectedFruit = f;
                    }
                }
                if(mVelocityTracker == null) {
                    // Retrieve a new VelocityTracker object to watch the velocity of a motion.
                    mVelocityTracker = VelocityTracker.obtain();
                }
                else {
                    // Reset the velocity tracker back to its initial state.
                    mVelocityTracker.clear();
                }
                // Add a user's movement to the tracker.
                mVelocityTracker.addMovement(e);
                break;

            case MotionEvent.ACTION_MOVE:
                touchx = e.getX();
                touchy = e.getY();
                if (selectedFruit != null) {
                    selectedFruit.x = touchx;
                    selectedFruit.y = touchy;
                }
                mVelocityTracker.addMovement(e);
                // When you want to determine the velocity, call
                // computeCurrentVelocity(). Then call getXVelocity()
                // and getYVelocity() to retrieve the velocity for each pointer ID.
                mVelocityTracker.computeCurrentVelocity(1000);
                int pointerId = e.getPointerId(e.getActionIndex());
                // Log velocity of pixels per second
                // Best practice to use VelocityTrackerCompat where possible.
//                Log.d("Greenie", "X velocity: me:" + touchvx+" VT:"+
//                        VelocityTrackerCompat.getXVelocity(mVelocityTracker,
//                                pointerId));
//                Log.d("Greenie", "Y velocity: me:" + touchvy+" VT:"+
//                        VelocityTrackerCompat.getYVelocity(mVelocityTracker,
//                                pointerId));
                touchvx = VelocityTrackerCompat.getXVelocity(mVelocityTracker,
                        pointerId);
                touchvy = VelocityTrackerCompat.getYVelocity(mVelocityTracker,
                        pointerId);
                break;

            case MotionEvent.ACTION_UP:
                float tvx = touchvx;
                float tvy = touchvy;
                touchx = -1;
                touchy = -1;
                touchvx = 0;
                touchvy = 0;
                if (selectedFruit != null) {
                    Fruit f = selectedFruit;
                    selectedFruit = null;
                    if (-tvy > 0) {
                        // there is upward motion at release-- user threw fruit
                        f.throwFruit(tvx, tvy);
                        fruitsSelectable.remove(f);
                        fruitsFlying.add(f);
                    }
                }
                mVelocityTracker.recycle();
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
            thrownTime = System.nanoTime(); // used by animation
            vx = tvx;

            // y vel is faster as we release higher on screen, and z vel
            // is faster if we release lower. so this factor represents
            // how much of the user's actual touchpoint y-velocity is treated as z-velocity.
            float yzfact = y / inity;
            vy = tvy * (1 - yzfact);
            vz = (-tvy * yzfact)/2;
//            if (tvy < -5000)
//                vy = (float)(Math.log(tvy) + (-5000 - Math.log(tvy)));
//            vy += (inity - y);
//            vz = -tvy+Math.abs(tvx)/10;
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
