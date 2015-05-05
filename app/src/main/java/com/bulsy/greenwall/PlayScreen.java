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
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Represents the main screen of play for the game.
 *
 * There are many ugly things here, partly because game programming lends itself
 * to questionable style.  ...and partly because this is my first venture into Android.
 *
 * Messy manual object recycling, violation of normal object oriented privacy
 * principles, and everything is extremely imperative/procedural, even for Java.
 * But apparently keeping android
 * happy is more important than data hiding and hands-free garbage collection.
 *
 * Created by ugliest on 12/29/14.
 */
public class PlayScreen extends Screen {
    static final float ZSTRETCH = 240; // lower -> more stretched on z axis
    static final float WALL_Z = 1000; // where is the wall, on the z axis?
    static final float WALL_Y_CENTER_FACTOR = 0.38f;  // factor giving y "center" of wall; this is used as infinity on z axis
    static final Rect wallbounds_at_wall_z = new Rect();  // wall bounds AT WALL Z (!!WaLLzeY!!)
    static final Rect wallbounds_at_screen_z = new Rect();  // wall bounds at screen z
    static final float WALLZFACT = 1.0f - (ZSTRETCH / (WALL_Z + ZSTRETCH));
    static final long ONESEC_NANOS = 1000000000L;
    static final int ACC_GRAVITY = 6000;
    static final int INIT_SELECTABLE_SPEED = 150;  // initial speed of selectable fruit at bottom of screen
    static final long MIN_SPAWN_INTERVAL_NANOS = 100000000L;
    static final float INIT_SELECTABLE_Y_FACTOR = 0.9f;
    float selectable_y_play = 0; // pixel distance that displayed selectable fruit wobbles; increases with level.
    int minRoundPassPct;  // pct splatted on wall that we require to advance level
    static final String LINE_SPLIT_MARKER = "#";

    private Paint p;
    private Point effpt = new Point(); // reusable point for rendering, to avoid excessive obj creation.  brutally un-threadsafe, obviously, but we will use it in only the render thread.
    private List<Fruit> fruitsSelectable = Collections.synchronizedList(new LinkedList<Fruit>()); // fruits ready for user to throw
    private List<Fruit> fruitsFlying = Collections.synchronizedList(new LinkedList<Fruit>());  // fruits user has sent flying
    private List<Fruit> fruitsSplatted = new LinkedList<Fruit>(); // fruits that have splatted on wall
    private List<Fruit> fruitsRecycled = new LinkedList<Fruit>(); // fruit objects no longer in use
    private volatile Fruit selectedFruit = null;
    private int maxShownSelectableFruit;
    private long frtime = 0;
    private long possspawntime = 0;
    private Rect scaledDst = new Rect();
    private MainActivity act = null;
    private int selectable_speed;

    private enum State {        RUNNING, STARTROUND, ROUNDSUMMARY, STARTGAME, PLAYERDIED, GAMEOVER    }
    private volatile State gamestate = State.STARTGAME;
    private int fps = 0; // rendering rate, frames per sec

    private int width = 0;
    private int height = 0;
    private int rhstextoffset;
    private int statstextheight, statstextheight2;
    private int wallxcenter = 0;
    private int wallycenter = 0;
    private int inity = 0;
    private int minXbound = 0;
    private int maxXbound = 0;
    private int maxYbound = 0;

    // types of throwables, remaining quantities, values, etc
    private List<Seed> seedsQueued = new LinkedList<Seed>();
    private Bitmap wallbtm, pearbtm[], banbtm[], orangebtm[], milkbtm[], icbtm[], ketbtm[], nutbtm[];
    private Seed pearseed;
    private Seed orangeseed;
    private Seed banseed;
    private Seed milkseed;
    private Seed icseed;
    private Seed nutseed;
    private Seed ketseed;
    private int nWallSplats = 0;
    private int nTotFruit = 0;

    private int round;
    private int score;
    private int lives;
    private int hiscore=0, hilev=1;
    private static final String HISCORE_FILE = "gwhs.dat";
    private static final int START_NUMLIVES = 3;
    private Map<Integer, String> levelmsgMap = new HashMap<Integer, String>();
    private final int LEVEL_ORANGE = 2;  // level where oranges are added
    private final int LEVEL_BANANA = 3;
    private final int LEVEL_MILK = 4;
    private final int LEVEL_KETCHUP = 5;
    private final int LEVEL_DANCING_FRUIT = 6;
    private final int LEVEL_ICECREAM = 8;
    private final int LEVEL_NUT = 10;
    private final int LEVEL_MOREFRUIT1 = 14;
    private final int LEVEL_MOREFRUIT2 = 20;

    private List<Combo> combos = new ArrayList<Combo>();  // possible combos
    private List<Fruit> comboFruits = new ArrayList<Fruit>();  // fruits potentially involved in combo
    private Map<Combo, ComboHit> hitCombos = new HashMap<Combo, ComboHit>(); // combos tht have just been hit, and are being displayed to player
    private List<Seed> neededSeeds = new ArrayList<Seed>(); // the list of seeds required when computing whether a combo has been hit.
    private static final int COMBOHIT_SPEED = 300; // how fast the combohit message rises
    private static final long COMBOHIT_DISPLAYTIME = (int) (0.75 * ONESEC_NANOS); // time to display a combo hit

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
            pearbtm[0] = act.getScaledBitmap("pear1.png");
            pearbtm[1] = act.getScaledBitmap("pear2.png");
            pearbtm[2] = act.getScaledBitmap("pear3.png");
            pearbtm[3] = act.getScaledBitmap("pearsplat1.png");

            // banana
            banbtm = new Bitmap[5];
            banbtm[0] = act.getScaledBitmap("ban1.png");
            banbtm[1] = act.getScaledBitmap("ban2.png");
            banbtm[2] = act.getScaledBitmap("ban3.png");
            banbtm[3] = act.getScaledBitmap("ban4.png");
            banbtm[4] = act.getScaledBitmap("bansplat.png");

            // orange
            orangebtm = new Bitmap[4];
            orangebtm[0] = act.getScaledBitmap("orange1.png");
            orangebtm[1] = act.getScaledBitmap("orange2.png");
            orangebtm[2] = act.getScaledBitmap("orange3.png");
            orangebtm[3] = act.getScaledBitmap("orangesplat.png");

            // nutella
            nutbtm = new Bitmap[5];
            nutbtm[0] = act.getScaledBitmap("nut1.png");
            nutbtm[1] = act.getScaledBitmap("nut2.png");
            nutbtm[2] = act.getScaledBitmap("nut3.png");
            nutbtm[3] = act.getScaledBitmap("nut4.png");
            nutbtm[4] = act.getScaledBitmap("nutsplat.png");

            // ice cream
            icbtm = new Bitmap[5];
            icbtm[0] = act.getScaledBitmap("icecream1.png");
            icbtm[1] = act.getScaledBitmap("icecream2.png");
            icbtm[2] = act.getScaledBitmap("icecream3.png");
            icbtm[3] = act.getScaledBitmap("icecream4.png");
            icbtm[4] = act.getScaledBitmap("icecreamsplat.png");

            // milk
            milkbtm = new Bitmap[5];
            milkbtm[0] = act.getScaledBitmap("milk1.png");
            milkbtm[1] = act.getScaledBitmap("milk2.png");
            milkbtm[2] = act.getScaledBitmap("milk3.png");
            milkbtm[3] = act.getScaledBitmap("milk4.png");
            milkbtm[4] = act.getScaledBitmap("milksplat1.png");

            // ketchup
            ketbtm = new Bitmap[2];
            ketbtm[0] = act.getScaledBitmap("ketch1.png");
            ketbtm[1] = act.getScaledBitmap("ketchsplat.png");

            // initialize types of fruit (seeds), point values
            pearseed = new Seed(pearbtm, 10, Sound.WETSPLAT);
            orangeseed = new Seed(orangebtm, 15, Sound.WETSPLAT);
            banseed = new Seed(banbtm, 20, Sound.WETSPLAT);
            milkseed = new Seed(milkbtm, 25, Sound.SPLAT);
            icseed = new Seed(icbtm, 30, Sound.SPLAT);
            nutseed = new Seed(nutbtm, 40, Sound.SPLAT);
            ketseed = new Seed(ketbtm, 0, Sound.KSPLAT);

            // init combos
            ArrayList<Seed> sl = new ArrayList<Seed>();
            sl.add(pearseed);
            sl.add(orangeseed);
            sl.add(banseed);
            combos.add(new Combo(sl, "Fruit Salad!", 100));
            sl = new ArrayList<Seed>();
            sl.add(milkseed);
            sl.add(banseed);
            combos.add(new Combo(sl, "BanaNa MiLKshaKe!", 80));
            sl = new ArrayList<Seed>();
            sl.add(orangeseed);
            sl.add(icseed);
            combos.add(new Combo(sl, "OrangE CreaM!", 80));
            sl = new ArrayList<Seed>();
            sl.add(milkseed);
            sl.add(nutseed);
            combos.add(new Combo(sl, "chOcolaTe MiLK!", 100));
            sl = new ArrayList<Seed>();
            sl.add(banseed);
            sl.add(icseed);
            sl.add(nutseed);
            combos.add(new Combo(sl, "bAnaNA sPLiT!!", 150));

            levelmsgMap.put(Integer.valueOf(1), "tImE to SPlaT!#fling the fruit at the wall!");
            levelmsgMap.put(Integer.valueOf(LEVEL_ORANGE), "Orange ya glad there's more#fruit to throw?");
            levelmsgMap.put(Integer.valueOf(LEVEL_BANANA), "Splat pear+orange+banana#in the same place...fruit salad!");
            levelmsgMap.put(Integer.valueOf(LEVEL_MILK), "how about Some milk to wash#all that fruit down?");
            levelmsgMap.put(Integer.valueOf(LEVEL_KETCHUP), "Don't pop the keTChup packets!#Nobody likes that.");
            levelmsgMap.put(Integer.valueOf(LEVEL_ICECREAM), "time for ICE CREAM!#And more combinations!");
            levelmsgMap.put(Integer.valueOf(LEVEL_NUT), "Mmm...chocolate sauce!#Hit those COMBOS!");
            levelmsgMap.put(Integer.valueOf(LEVEL_DANCING_FRUIT), "Sometimes...the fruit...#likes to DANCE!");
            levelmsgMap.put(Integer.valueOf(LEVEL_MOREFRUIT1), "more fruit!#fling, fling, fling");
            levelmsgMap.put(Integer.valueOf(LEVEL_MOREFRUIT2), "getting a little crazy now, yes?");

            p.setTypeface(act.getGameFont());
            round = 1;

        } catch (IOException e) {
            Log.d(act.LOG_ID, "wha?", e);
        }
    }

    /**
     * clear fruit lists
     */
    private void clearLists() {
        fruitsRecycled.addAll(fruitsSplatted);
        fruitsSplatted.clear();
        fruitsRecycled.addAll(fruitsSelectable);
        fruitsSelectable.clear();
        fruitsRecycled.addAll(fruitsFlying);
        fruitsFlying.clear();
    }

    /**
     * initialize and start a game
     */
    void initGame() {
        score = 0;
        round = 1;
        lives = START_NUMLIVES;
        hiscore = 0;

        clearLists();
        try {
            BufferedReader f = new BufferedReader(new FileReader(act.getFilesDir() + HISCORE_FILE));
            hiscore = Integer.parseInt(f.readLine());
            hilev = Integer.parseInt(f.readLine());
            f.close();
        } catch (Exception e) {
            Log.d(MainActivity.LOG_ID, "ReadHiScore", e);
        }
        gamestate = State.STARTGAME;
    }

    /**
     * add the specified number of the specified fruit seed to the specified list.
     * seeds are added at random locations in the list.
     *
     * @param list
     * @param n
     */
    private void addFruitSeed(List<Seed> list, Seed s, int n) {
        for (int i = 0; i < n; i++) {
            int loc = (int) (Math.random() * list.size());
            list.add(loc, s);
        }
    }

    /**
     * init game for current round
     */
    private void initRound() {
        selectable_speed = (int)(INIT_SELECTABLE_SPEED * act.sizescalefactor + (round * 10));

        if (round < 2)
            maxShownSelectableFruit = 1;
        else if (round < 8)
            maxShownSelectableFruit = 2;
        else if (round < LEVEL_MOREFRUIT1)
            maxShownSelectableFruit = 3;
        else if (round < LEVEL_MOREFRUIT2)
            maxShownSelectableFruit = 4;
        else
            maxShownSelectableFruit = 5;

        // how much do we need to get on the wall, to pass?
        if (round == 1)
            minRoundPassPct = 60;
        else if (round < 4)
            minRoundPassPct = 70;
        else
            minRoundPassPct = 75;

        clearLists();

        // set up fruits to throw
        seedsQueued.clear();
        addFruitSeed(seedsQueued, pearseed, 5 + round / 2);
        if (round >= LEVEL_ORANGE)
            addFruitSeed(seedsQueued, orangeseed, 4 + round / 2);
        if (round >= LEVEL_BANANA)
            addFruitSeed(seedsQueued, banseed, round / 2);
        if (round >= LEVEL_MILK)
            addFruitSeed(seedsQueued, milkseed, 1 + round / 3);
        if (round >= LEVEL_ICECREAM)
            addFruitSeed(seedsQueued, icseed, round / 3);
        if (round >= LEVEL_NUT)
            addFruitSeed(seedsQueued, nutseed, round / 3);

        if (round >= LEVEL_DANCING_FRUIT)
          selectable_y_play = 8+round/2;
        else
          selectable_y_play = 0;

        int nket=0;
        if (round >= LEVEL_KETCHUP) {
            nket = round / 2;
            addFruitSeed(seedsQueued, ketseed, nket);
        }

        nWallSplats = 0;
        nTotFruit = seedsQueued.size() - nket; // don't count ketchups toward throwable fruit total

        gamestate = State.RUNNING;
    }

    /**
     * player lost a life
     */
    private void loseLife() {
        lives--;
        act.playSound(Sound.DEATH);
        if (lives == 0) {
            // game over!  wrap things up and write hi score file
            gamestate = State.GAMEOVER;
            try {
                BufferedWriter f = new BufferedWriter(new FileWriter(act.getFilesDir() + HISCORE_FILE));
                f.write(Integer.toString(hiscore)+"\n");
                f.write(Integer.toString(hilev)+"\n");
                f.close();
            } catch (Exception e) { // if we can't write the hi score file...oh well.
                Log.d(MainActivity.LOG_ID, "WriteHiScore", e);
            }
        } else
            gamestate = State.PLAYERDIED;
    }

    /**
     * Represents a combination of splats
     */
    private class Combo {
        List<Seed> seeds;
        String name;
        int points;

        public Combo(List<Seed> seeds, String name, int points) {
            this.seeds = seeds;
            this.name = name;
            this.points = points;
        }
    }

    private class ComboHit {
        float x = 0;
        float y = 0;
        long hitTime = 0;
        int alpha = 0; // translucence
    }

    /**
     * return the effective screen x,y point rendered from the inpassed
     * (x, y, z) point.
     *
     * @param x
     * @param y
     * @param z
     * @return
     */
    private Point renderFromZ(float x, float y, float z, float xc, float yc) {
        float zfact = 1.0f - (ZSTRETCH / (z + ZSTRETCH));
        int effx = (int) (x + (zfact * (xc - x)));
        int effy = (int) (y + (zfact * (yc - y)));
        effpt.set(effx, effy);
        return effpt;
    }


    /**
     * Draw the inpassed fruit, using the inpassed bitmap, rendering the
     * fruit's x/y/z position to the
     * actual x/y screen coords.
     *
     * @param c
     * @param f
     * @param btm
     * @param xc  the x center. to which the "z axis" points
     * @param yc  the y center, to which the "z axis" points
     */
    private void drawFruit3Dcoords(Canvas c, Fruit f, Bitmap btm, float xc, float yc) {
        // render effective x and y, from x y z
        // DRY says this should call renderFromZ, but that creates even more
        // ugliness than this duplication of code, and this code isn't going to change.
        float zfact = 1.0f - (ZSTRETCH / (f.z + ZSTRETCH));
        int effx = (int) (f.x + (zfact * (xc - f.x)));
        int effy = (int) (f.y + (zfact * (yc - f.y)));
        int effhalfw = (int) (f.seed.halfWidth * (1.0f - zfact));
        int effhalfh = (int) (f.seed.halfHeight * (1.0f - zfact));
        scaledDst.set(effx - effhalfw, effy - effhalfh, effx + effhalfw, effy + effhalfh);
        c.drawBitmap(btm, null, scaledDst, p);
    }

    @Override
    public void update(View v) {
        long newtime = System.nanoTime();
        float elapsedsecs = (float) (newtime - frtime) / ONESEC_NANOS;
        frtime = newtime;
        fps = (int) (1 / elapsedsecs);

        // update combo hits
        Iterator<Combo> hcit = hitCombos.keySet().iterator();
        while (hcit.hasNext()) {
            Combo combo = hcit.next();
            ComboHit ch = hitCombos.get(combo);
            ch.y -= COMBOHIT_SPEED * elapsedsecs;
            float chtime = frtime - ch.hitTime;
            ch.alpha = (int) (255 * (1.0f - chtime / COMBOHIT_DISPLAYTIME));
            if (frtime - ch.hitTime > COMBOHIT_DISPLAYTIME / 3)
                fps = 0; // excuse to put a breakpoint here
            if (frtime - ch.hitTime > COMBOHIT_DISPLAYTIME)
                hcit.remove();
        }

        if (gamestate == State.STARTROUND) {
            // this goofy construction is to make sure we initialize the round from
            // the update/draw thread, not from the UI thread.
            initRound();
            return;
        }

        if (width == 0) {
            // set variables that rely on screen size
            width = v.getWidth();
            height = v.getHeight();
            wallxcenter = width / 2;
            wallycenter = (int) (height * WALL_Y_CENTER_FACTOR);

            inity = (int) (INIT_SELECTABLE_Y_FACTOR * height); // initial fruit placement, also bottom of wall.

            // attempt to compute wall bounds at wall z  from screen size.  constants are pure
            // magic, found thru trial and error iterations.
            // if the background picture changes, they will need to be recalibrated.
            wallbounds_at_wall_z.set((int) (-1.5 * width), (int) (-height * .94), (int) (2.43 * width), inity);  // wall bounds AT WALL Z (!!WaLLzeY!!)

            // magic trial and error world-bounds contants, based on screen image size
            minXbound = 8 * -width;
            maxXbound = 8 * width;
            maxYbound = 5 * height;

            // compute wall bounds at screen z, used for clipping.
            int effl = (int) (wallbounds_at_wall_z.left + (WALLZFACT * (wallxcenter - wallbounds_at_wall_z.left)));
            int efft = (int) (wallbounds_at_wall_z.top + (WALLZFACT * (wallycenter - wallbounds_at_wall_z.top)));
            int effr = (int) (wallbounds_at_wall_z.right + (WALLZFACT * (wallxcenter - wallbounds_at_wall_z.right)));
            int effb = (int) (wallbounds_at_wall_z.bottom + (WALLZFACT * (wallycenter - wallbounds_at_wall_z.bottom)));
            wallbounds_at_screen_z.set(effl, efft, effr, effb);
            p.setTextSize(act.TS_NORMAL);
            p.setTypeface(act.getGameFont());
            String t = "SCORE: 999999";
            rhstextoffset = (int)p.measureText(t);
            p.getTextBounds(t, 0, t.length()-1, scaledDst);
            statstextheight = (int)(scaledDst.height() +5);
            statstextheight2 = statstextheight * 2;
        }

        if (gamestate == State.RUNNING
                && fruitsSelectable.size() < maxShownSelectableFruit
                && seedsQueued.size() > 0
                && frtime > possspawntime + MIN_SPAWN_INTERVAL_NANOS) {
            possspawntime = frtime;
            // "every now and then" make a fruit available
            if (Math.random() > .6) {
                Fruit newf = null;
                if (fruitsRecycled.size() > 0) { // recycle a fruit if we can
                    newf = fruitsRecycled.get(0);
                    fruitsRecycled.remove(0);
                } else { // create if needed
                    newf = new Fruit();
                }

                // choose fruit
                Seed s = seedsQueued.get(0);
                seedsQueued.remove(0);

                int initx = (int)-s.halfWidth;
                int speed = selectable_speed;
                if (Math.random() > .5) {
                    initx = (int)(width + s.halfWidth);
                    speed = -speed;
                }

                newf.init(s, initx, inity, 0, speed);
                fruitsSelectable.add(newf);
            }
        } else if (gamestate == State.RUNNING
                && fruitsSelectable.size() == 0
                && fruitsFlying.size() == 0
                && seedsQueued.size() == 0) {
            // round is complete
            if (nWallSplats * 100 / nTotFruit >= minRoundPassPct) {
                act.playSound(Sound.PASSLEVEL);
                round++;
                gamestate = State.ROUNDSUMMARY;
            } else
                loseLife();
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
                if (f.z >= WALL_Z && wallbounds_at_wall_z.contains((int) f.x, (int) f.y)) {
                    // fruit has hit wall
                    fit.remove();
                    fruitsSplatted.add(f);
                    nWallSplats++;
                    score += f.seed.points;
                    act.playSound(f.getSplatSound());

                    // check combo
                    for (Combo c : combos) {
                        neededSeeds.clear();
                        neededSeeds.addAll(c.seeds);
                        neededSeeds.remove(f.seed);
                        comboFruits.clear();
                        comboFruits.add(f);
                        for (Fruit spf : fruitsSplatted) {
                            if (neededSeeds.contains(spf.seed)) {
                                if (spf.getBounds().intersect(f.getBounds())) {
                                    neededSeeds.remove(spf.seed);
                                    comboFruits.add(spf);
                                }
                                if (neededSeeds.size() == 0)
                                    break;
                            }
                        }
                        if (neededSeeds.size() == 0) {
                            // combo is hit
                            score += c.points;
                            for (Fruit spf : comboFruits) {
                                fruitsSplatted.remove(spf);
                            }


                            // display combo hit message "somewhere next to" combo hit
                            effpt = renderFromZ(f.x, f.y, f.z, wallxcenter, wallycenter);
                            ComboHit ch = new ComboHit();
                            ch.x = effpt.x + (float) Math.random() * 100 - 50;
                            ch.y = effpt.y + (float) Math.random() * 100 - 80;

                            // play sound
                            act.playSound(Sound.COMBO);

                            // ensure combo display is fully onscreen
                            p.getTextBounds(c.name, 0, c.name.length(), scaledDst);
                            if (ch.x < 0)
                                ch.x = 0;
                            else if (ch.x > width - scaledDst.width())
                                ch.x = width - scaledDst.width();

                            ch.hitTime = System.nanoTime();
                            hitCombos.put(c, ch);
                        }
                    }
                } else if (f.y > inity
                        && f.y < inity + f.vy * elapsedsecs
                        && f.z > WALL_Z / 2) {
                    // fruit has hit ground near wall
                    fit.remove();
                    fruitsSplatted.add(f);
                } else if (f.z > WALL_Z
                        // here we goofily force java to call render function when we need it
                        && (effpt = renderFromZ(f.x, f.y, f.z, wallxcenter, wallycenter)) != null
                        && wallbounds_at_screen_z.contains(effpt.x, effpt.y)
                        ) {
                    // wild pitch, behind wall
                    fit.remove();
                    fruitsRecycled.add(f);
                } else if (f.y > maxYbound
                        || f.x >= maxXbound
                        || f.x <= minXbound) {
                    // wild pitch, out of bounds
                    fit.remove();
                    fruitsRecycled.add(f);
                }
            }
        }

        if (gamestate == State.RUNNING) {
            synchronized (fruitsSelectable) {
                Iterator<Fruit> fit = fruitsSelectable.iterator();
                while (fit.hasNext()) {
                    Fruit f = fit.next();
                    if (f != selectedFruit) {
                        f.x += f.vx * elapsedsecs;

                        // wobble displayable fruit up and down, and return them to regular line when let go
                        int targy = inity + (int)(Math.sin(f.x/15)* selectable_y_play);
                        f.y += (targy - f.y) / SELECTABLE_FRUIT_BRAKING_FACTOR;
                    }
                    if (f.x < -f.seed.halfWidth || f.x > width + f.seed.halfWidth) {
                        // we floated off screen
                        fit.remove();
                        fruitsRecycled.add(f);
                    }
                }
            }
        }
    }

    private static final double SELECTABLE_FRUIT_BRAKING_FACTOR = 3.0;
    /**
     * draw the screen.
     *
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
            //c.drawRect(wallbounds_at_screen_z, p);

            // draw fruits
            for (Fruit f : fruitsSplatted) {
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
                    c.drawBitmap(f.getBitmap(), f.x - f.seed.halfWidth, f.y - f.seed.halfHeight, p);
                }
            }

            // draw combo hits
            for (Combo combo : hitCombos.keySet()) {
                ComboHit ch = hitCombos.get(combo);
                //p.setColor(Color.YELLOW);
                p.setARGB(ch.alpha, 190 + (int) (Math.random() * 60), 190 + (int) (Math.random() * 60), (int) (Math.random() * 60));
                p.setTypeface(act.getGameFont());
                p.setTextSize(act.TS_NORMAL);
                c.drawText(combo.name, ch.x, ch.y, p);
            }

//            c.drawText("fps: "+fps
//                    +" w:"+width + " h:" +height
//                        +"x:"+touchx+" y:"+touchy+" tvx:"+(int)touchvx+"\ttvy:"+(int)touchvy+
//                    "\tflying:" + fruitsFlying.size()
//                            + "\nff vz:" + (fruitsFlying.size() > 0 ? fruitsFlying.get(0).vz : -1)
//                            + "\nff vy:" + (fruitsFlying.size() > 0 ? fruitsFlying.get(0).vy : -1)
//                            + " ffvz:" + (fruitsFlying.size() > 0 ? fruitsFlying.get(0).vz : -1),
//                    , 0, 200, p);
            p.setColor(Color.WHITE);
            p.setTextSize(act.TS_NORMAL);
            p.setTypeface(act.getGameFont());
            p.setFakeBoldText(true);
            // drawtext draws bottom-aligned?
            c.drawText("ROUND: " + round, width - rhstextoffset, statstextheight, p);
            c.drawText("LIVES: " + lives, width - rhstextoffset, statstextheight2, p);
            c.drawText("SCORE: " + score, 10, statstextheight, p);
            if (score >= hiscore) {
                hiscore = score;
                hilev = round;
            }
            c.drawText("HIGH: " + hiscore +", r"+hilev, 10, statstextheight2, p);

            // game programming!  pure and constant state manipulation!
            // this is like fingernails on a chalkboard for the functional programming crowd
            if (gamestate == State.ROUNDSUMMARY
                    || gamestate == State.STARTGAME
                    || gamestate == State.PLAYERDIED
                    || gamestate == State.GAMEOVER) {
                if (gamestate != State.STARTGAME) {
                    // round ended, by completion or player death, display stats
                    int splatPct = (int) (nWallSplats * 100 / nTotFruit);

                    drawCenteredText(c, splatPct + "% sPLAttaGe! ("+ minRoundPassPct +"% required)", height / 3, p, 0);
                    if (gamestate == State.ROUNDSUMMARY) {
                        if (splatPct < 80)
                            drawCenteredText(c, "not too bad.", (int) (height / 2.5), p, 0);
                        else if (splatPct < 85)
                            drawCenteredText(c, "nice!", (int) (height / 2.5), p, 0);
                        else if (splatPct <= 95) {
                            drawCenteredText(c, "cRudE!", (int) (height / 2.5), p, 0);
                        } else if (round > 10) {
                            c.drawText("Dude, really?!", width / 4, (int) (height / 2.5), p);
                            c.drawText("Awesome.", width / 3, (int) (height / 2.2), p);
                        } else {
                            drawCenteredText(c, "eEEeEeeEh!! sPAzMiC!", (int) (height / 2.5), p, 0);
                        }
                    } else if (gamestate == State.PLAYERDIED
                            || gamestate == State.GAMEOVER)
                        c.drawText("...Ooops.", width / 3, (int) (height / 2.5), p);
                }

                if (gamestate != State.PLAYERDIED
                        && gamestate != State.GAMEOVER) {
                    String msg = levelmsgMap.get(Integer.valueOf(round));
                    if (msg != null) {
                        if (msg.contains(LINE_SPLIT_MARKER)) {
                            drawCenteredText(c, msg.substring(0, msg.indexOf(LINE_SPLIT_MARKER)), height * 3 / 5, p, 0);
                            drawCenteredText(c, msg.substring(msg.indexOf(LINE_SPLIT_MARKER) + 1), height * 2 / 3, p, 0);
                        } else
                            drawCenteredText(c, msg, height * 3 / 5, p, 0);
                    }
                }

                if (gamestate != State.GAMEOVER) {
                    p.setTextSize(act.TS_BIG);
                    p.setColor(Color.RED);
                    drawCenteredText(c, "Touch to continue", height * 4/5, p, -2);
                    p.setColor(Color.WHITE);
                    drawCenteredText(c, "Touch to continue", height * 4 / 5, p, 0);
                }
            }
            if (gamestate == State.GAMEOVER) {
                p.setTextSize(act.TS_BIG);
                p.setColor(Color.RED);
                drawCenteredText(c, "GamE oVeR!", height /2, p, -2);
                drawCenteredText(c, "Touch to end game", height * 4 /5, p, -2);
                p.setColor(Color.WHITE);
                drawCenteredText(c, "GamE oVeR!", height /2, p, 0);
                drawCenteredText(c, "Touch to end game", height * 4 /5, p, 0);
            }

        } catch (Exception e) {
            Log.e(MainActivity.LOG_ID, "draw", e);
            e.printStackTrace();
        }
    }

    /**
     * draw text onscreen, centered, at the inpassed height.  last param can be used to shift
     * text horizontally.
     *
     * @param shift pixels to horizontally shift text.  normally 0
     */
    private void drawCenteredText(Canvas c, String msg, int height, Paint p, int shift) {
        c.drawText(msg, (width - p.measureText(msg)) / 2 + shift, height, p);
    }

    VelocityTracker mVelocityTracker = null;
    DisplayMetrics dm = new DisplayMetrics();
    @Override
    public boolean onTouch(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (gamestate == State.ROUNDSUMMARY
                        || gamestate == State.STARTGAME
                        || gamestate == State.PLAYERDIED) {
                    gamestate = State.STARTROUND; // prep and start round
                    return false; // no followup msgs
                }
                else if (gamestate == State.GAMEOVER) {
                    act.leaveGame(); // user touched after gameover -> back to entry screen
                    return false;  // no followup msgs
                }
                else {
                    synchronized (fruitsSelectable) {
                        Iterator<Fruit> itf = fruitsSelectable.iterator();
                        while (itf.hasNext()) {
                            Fruit f = itf.next();
                            if (f.hasCollision(e.getX(), e.getY()))
                                if (f.seed == ketseed) {
                                    // user popped ketchup
                                    act.playSound(Sound.KSPLAT);
                                    f.burst();
                                    loseLife();
                                    return false; // no followup msgs
                                } else {
                                    //user picked up a fruit
                                    selectedFruit = f;
                                }
                        }
                    }
                    if (mVelocityTracker == null) {
                        // Retrieve a new VelocityTracker object to watch the velocity of a motion.
                        mVelocityTracker = VelocityTracker.obtain();
                    } else {
                        // Reset the velocity tracker back to its initial state.
                        mVelocityTracker.clear();
                    }
                    // Add a user's movement to the tracker.
                    mVelocityTracker.addMovement(e);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (selectedFruit != null) {
                    selectedFruit.x = e.getX();
                    selectedFruit.y = e.getY();
                }
                mVelocityTracker.addMovement(e);
                break;

            case MotionEvent.ACTION_UP:
                if (selectedFruit != null) {
                    Fruit f = selectedFruit;
                    selectedFruit = null;

                    mVelocityTracker.computeCurrentVelocity(1000);
                    int pointerId = e.getPointerId(e.getActionIndex());
                    float tvx = VelocityTrackerCompat.getXVelocity(mVelocityTracker,
                            pointerId);
                    float tvy = VelocityTrackerCompat.getYVelocity(mVelocityTracker,
                            pointerId);

                    if (-tvy > 10) {
                        // there is upward motion at release-- user threw fruit

                        // scale throw speed for display size/density
                        tvx = tvx / act.densityscalefactor;
                        tvy = tvy / act.densityscalefactor;

                        // help ease perspective problem when we release fruit away from the horizontal center of the screen
                        tvx += (e.getX()-width/2)*3.5*act.densityscalefactor;

                        f.throwFruit(tvx, tvy);
                        synchronized (fruitsFlying) {
                            fruitsFlying.add(f);
                            fruitsSelectable.remove(f);
                        }

                        // attempting to adjust sound for how hard fruit was thrown horizontally.
                        // hardness == 0 --> not thrown with any force
                        // hardness == 1 --> thrown as hard as possible
                        // assume that 5000 represents "really fast"; z vel should sound "harder" than y-vel
                        float hardness = (f.vz - f.vy/2)/5000;  // vy: up is negative.
                        if (hardness >= 1f)
                            hardness = 1.0f;
                        if (hardness < .3f)
                            hardness = .3f;
                        act.playSound(Sound.THROW, hardness * .9f, hardness * 2);
                    }
                }
                mVelocityTracker.recycle();
                // seems to be a bug here on android 4.4, causing IllegalStateException - not addressing, since it doesn't affect game play, and may be resolved in later versions
                break;
        }

        return true;
    }

    /**
     * A Seed is more or less a template for a Fruit.
     */
    private class Seed {
        int points; // points this type of Fruit is worth, if it hits the wall.
        Sound splatsound;
        Bitmap btm[]; // bitmap for animating this type of throwable
        float width=0; // width onscreen
        float height=0;  // height onscreen
        float halfWidth = 0;  // convenience
        float halfHeight = 0;
        final float HALF_DIVISOR = 1.9f;  // we fudge "half" a little, results are more comfortable.

        public Seed(Bitmap bitmaps[], int points, Sound splatsound) {
            this.btm = bitmaps;
            this.width = bitmaps[0].getWidth();
            this.height = bitmaps[0].getHeight();
            this.halfWidth = width/HALF_DIVISOR;
            this.halfHeight = height/HALF_DIVISOR;
            this.points = points;
            this.splatsound = splatsound;
        }
    }

    /**
     * "Throwable" would be a better name here, but as that's taken, "Fruit" it is.
     * A fruit is something that the player is presented with, usually to throw at the wall.
     */
    private class Fruit {
        final int APS = 4; // number of animation cycles per second

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
        boolean isBurst = false;

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
            isBurst = false;
        }

        /**
         * looks for a collision with an inpossed point -=- z axis ignored..
         * @param collx
         * @param colly
         */
        public boolean hasCollision(float collx, float colly) {
            return getBounds().contains((int) collx, (int) colly);
        }

        public Rect getBounds() {
            bounds.set((int)(this.x - seed.halfWidth), (int)(this.y-seed.halfHeight),
                    (int)(this.x+seed.halfWidth), (int)(this.y+seed.halfHeight));
            return bounds;
        }

        public void throwFruit(float tvx, float tvy) {
            thrownTime = System.nanoTime(); // used by animation
            vx = tvx;

            // to simulate throwing into the screen,
            // y velocity ("up") is faster as we release higher on the touchscreen,
            // and z velocity ("into the screen") is faster if we release lower.
            // yzfact represents how much of the user's actual touchpoint y-velocity
            // is treated as z-velocity.
            float yzfact = y / inity;
            vy = tvy * (1 - yzfact);
            vz = (-tvy * yzfact)/2;
        }

        public void burst() {
            isBurst = true;
        }

        public Bitmap getBitmap() {
            if (isBurst)
                return getSplatBitmap();
            else
                return seed.btm[0];
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

        public Sound getSplatSound() { return seed.splatsound;}
    }
}
