package com.netero.magnata.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.netero.magnata.engine.Board;
import com.netero.magnata.engine.Game;
import com.netero.magnata.engine.Player;
import com.netero.magnata.engine.Rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

/**
 * Tabuleiro em 2.5D (isométrico) com câmera móvel, construções em relevo,
 * peões animados, dados 3D, roleta e avisos.
 */
public class BoardView extends View {

    public interface OnSquareTap { void onSquare(int pos); }
    public interface Fx { void sound(String name); }

    // ---------------------------------------------------------------- passos de animação
    public static final int ST_DICE = 0, ST_WALK = 1, ST_JUMP = 2, ST_ROULETTE = 3, ST_BANNER = 4;

    public static class Step {
        int type, a, b, c;
        String s1, s2, s3;
        public static Step dice(int d1, int d2, int d3) { Step s = new Step(); s.type = ST_DICE; s.a = d1; s.b = d2; s.c = d3; return s; }
        public static Step move(int who, int from, int to, boolean walk) {
            Step s = new Step(); s.type = walk ? ST_WALK : ST_JUMP; s.a = who; s.b = from; s.c = to; return s;
        }
        public static Step roulette(int r) { Step s = new Step(); s.type = ST_ROULETTE; s.a = r; return s; }
        public static Step banner(String kind, String title, String text) {
            Step s = new Step(); s.type = ST_BANNER; s.s1 = kind; s.s2 = title; s.s3 = text; return s;
        }
    }

    private Game game;
    private int me = -1;
    private OnSquareTap tapListener;
    private Fx fx;

    // câmera
    private float baseTW, zoom = 1f, camX, camY;
    private boolean camReady;
    private float insetTop, insetBottom;
    private long lastUserTouch;
    private float followGX = -1, followGY = -1;

    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;

    // animação
    private final ArrayList<Step> steps = new ArrayList<>();
    private int stepIdx = -1;
    private long stepStart;
    private Runnable onDone;
    private float[][] override; // por jogador: {gx, gy, hop}
    private int lastSoundTile = -1;
    private int lastTick = -1;
    private final int[] diceFaces = new int[9];
    private long lastFaceChange;
    private final Random rnd = new Random();

    // sobreposições
    private static class Banner { String kind, title, text; long start; }
    private final ArrayList<Banner> banners = new ArrayList<>();
    private static class Floater { int who; String text; int color; long start; boolean emoji; }
    private final ArrayList<Floater> floaters = new ArrayList<>();
    private RectF bannerRect = new RectF();

    // pincéis
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private LinearGradient sky;
    private int skyH;

    private static final int OUTLINE = 0xFF33302A;

    // posições no grid 11x11
    private static final int[][] GRID = new int[Board.SIZE][2];
    private static final int[][] POS_AT = new int[11][11];

    static {
        for (int[] row : POS_AT) java.util.Arrays.fill(row, -1);
        for (int p = 0; p < Board.SIZE; p++) {
            int gx, gy;
            if (p <= 10) { gx = 10 - p; gy = 10; }
            else if (p <= 20) { gx = 0; gy = 10 - (p - 10); }
            else if (p <= 30) { gx = p - 20; gy = 0; }
            else { gx = 10; gy = p - 30; }
            GRID[p][0] = gx;
            GRID[p][1] = gy;
            POS_AT[gx][gy] = p;
        }
    }

    private static final float[][] TREES = {
            {2.2f, 2.0f}, {1.6f, 3.0f}, {2.0f, 7.8f}, {1.7f, 6.6f}, {7.8f, 1.8f}, {8.6f, 2.6f},
            {3.2f, 8.4f}, {4.4f, 8.6f}, {8.5f, 4.6f}, {6.2f, 1.6f}, {3.6f, 1.7f}
    };

    public BoardView(Context ctx) {
        super(ctx);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(OUTLINE);
        text.setTextAlign(Paint.Align.CENTER);
        textStroke.setStyle(Paint.Style.STROKE);
        textStroke.setTextAlign(Paint.Align.CENTER);
        textStroke.setColor(0xFFFFFFFF);
        textStroke.setStrokeJoin(Paint.Join.ROUND);

        scaleDetector = new ScaleGestureDetector(ctx, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                zoomAt(d.getFocusX(), d.getFocusY(), zoom * d.getScaleFactor());
                lastUserTouch = SystemClock.uptimeMillis();
                return true;
            }
        });
        gestureDetector = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                camX -= dx;
                camY -= dy;
                clampCamera();
                lastUserTouch = SystemClock.uptimeMillis();
                invalidate();
                return true;
            }
            @Override public boolean onDoubleTap(MotionEvent e) {
                if (zoom > 1.3f) resetCamera();
                else {
                    zoomAt(e.getX(), e.getY(), 2f);
                    if (game != null && me >= 0 && me < game.players.length) centerOn(GRID[game.players[me].pos][0], GRID[game.players[me].pos][1]);
                }
                lastUserTouch = SystemClock.uptimeMillis();
                invalidate();
                return true;
            }
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                if (!banners.isEmpty() && bannerRect.contains(e.getX(), e.getY())) {
                    banners.remove(0);
                    if (!banners.isEmpty()) banners.get(0).start = SystemClock.uptimeMillis();
                    invalidate();
                    return true;
                }
                int pos = tileAt(e.getX(), e.getY());
                if (pos >= 0 && tapListener != null) tapListener.onSquare(pos);
                return true;
            }
        });
    }

    // ================================================================= API

    public void setGame(Game g, int me) {
        this.game = g;
        this.me = me;
        invalidate();
    }

    public void setOnSquareTap(OnSquareTap l) { this.tapListener = l; }
    public void setFx(Fx f) { this.fx = f; }

    public void setInsets(float top, float bottom) {
        if (top == insetTop && bottom == insetBottom) return;
        insetTop = top;
        insetBottom = bottom;
        camReady = false;
        invalidate();
    }

    public boolean isAnimating() { return stepIdx >= 0; }

    /** Toca os passos em ordem e chama done no fim. */
    public void play(ArrayList<Step> list, Runnable done) {
        steps.clear();
        steps.addAll(list);
        onDone = done;
        override = null;
        if (steps.isEmpty()) {
            stepIdx = -1;
            if (done != null) post(done);
            return;
        }
        stepIdx = 0;
        startStep();
        invalidate();
    }

    public void floatText(int who, String s, int color) {
        Floater f = new Floater();
        f.who = who; f.text = s; f.color = color; f.start = SystemClock.uptimeMillis();
        floaters.add(f);
        invalidate();
    }

    public void emoji(int who, String e) {
        Floater f = new Floater();
        f.who = who; f.text = e; f.emoji = true; f.start = SystemClock.uptimeMillis();
        floaters.add(f);
        invalidate();
    }

    public void banner(String kind, String title, String body) {
        Banner b = new Banner();
        b.kind = kind; b.title = title; b.text = body;
        b.start = SystemClock.uptimeMillis();
        banners.add(b);
        invalidate();
    }

    // ================================================================= câmera

    private float tw() { return baseTW * zoom; }
    private float th() { return baseTW * zoom * 0.5f; }
    private float sx(float gx, float gy) { return camX + (gx - gy) * tw() / 2f; }
    private float sy(float gx, float gy) { return camY + (gx + gy) * th() / 2f; }

    private float viewCX() { return getWidth() / 2f; }
    private float viewCY() { return insetTop + (getHeight() - insetTop - insetBottom) / 2f; }

    private void resetCamera() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float avail = Math.max(h * 0.3f, h - insetTop - insetBottom);
        baseTW = Math.min(w / 11.4f, avail / (5.5f + 1.4f));
        zoom = 1f;
        centerOn(5, 5);
        camReady = true;
    }

    private void centerOn(float gx, float gy) {
        camX = viewCX() - (gx - gy) * tw() / 2f;
        camY = viewCY() - (gx + gy) * th() / 2f;
        clampCamera();
    }

    private void zoomAt(float fx, float fy, float nz) {
        nz = Math.max(0.85f, Math.min(3.2f, nz));
        float a = (fx - camX) / (tw() / 2f), b = (fy - camY) / (th() / 2f);
        zoom = nz;
        camX = fx - a * tw() / 2f;
        camY = fy - b * th() / 2f;
        clampCamera();
        invalidate();
    }

    private void clampCamera() {
        // mantém o centro do tabuleiro dentro da tela
        float cx = sx(5, 5), cy = sy(5, 5);
        float halfW = 5.5f * tw(), halfH = 5.5f * th();
        float minX = getWidth() - halfW, maxX = halfW;
        float minY = getHeight() - insetBottom - halfH, maxY = insetTop + halfH;
        if (minX > maxX) { float m = getWidth() / 2f; minX = maxX = m; }
        if (minY > maxY) { float m = viewCY(); minY = maxY = m; }
        float ncx = Math.max(minX, Math.min(maxX, cx));
        float ncy = Math.max(minY, Math.min(maxY, cy));
        camX += ncx - cx;
        camY += ncy - cy;
    }

    private int tileAt(float x, float y) {
        float a = (x - camX) / (tw() / 2f), b = (y - camY) / (th() / 2f);
        float gx = (a + b) / 2f, gy = (b - a) / 2f;
        int ix = Math.round(gx), iy = Math.round(gy);
        if (ix < 0 || ix > 10 || iy < 0 || iy > 10) return -1;
        return POS_AT[ix][iy];
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaleDetector.onTouchEvent(e);
        gestureDetector.onTouchEvent(e);
        if (e.getAction() == MotionEvent.ACTION_UP) performClick();
        return true;
    }

    @Override
    public boolean performClick() { return super.performClick(); }

    // ================================================================= animação

    private void startStep() {
        stepStart = SystemClock.uptimeMillis();
        lastSoundTile = -1;
        lastTick = -1;
        Step s = steps.get(stepIdx);
        if (s.type == ST_DICE) sound("dice");
        if (s.type == ST_JUMP) sound("jump");
        if (s.type == ST_ROULETTE) sound("spin");
        if (s.type == ST_BANNER) {
            banner(s.s1, s.s2, s.s3);
            sound(s.s1.equals("CARD") ? "card" : s.s1.equals("WIN") ? "win" : s.s1.equals("MISSION") ? "coin" : "card");
        }
    }

    private long stepDuration(Step s) {
        switch (s.type) {
            case ST_DICE: return 1750;
            case ST_WALK: {
                int k = (s.c - s.b + Board.SIZE) % Board.SIZE;
                return Math.max(1, k) * 200L + 150;
            }
            case ST_JUMP: return 700;
            case ST_ROULETTE: return 4400;
            default: return 350;
        }
    }

    private void sound(String n) { if (fx != null) fx.sound(n); }

    private void ensureOverride() {
        if (game == null) return;
        if (override == null || override.length != game.players.length) {
            override = new float[game.players.length][];
        }
    }

    private void updateAnim(long now) {
        if (stepIdx < 0) return;
        Step s = steps.get(stepIdx);
        long el = now - stepStart;
        ensureOverride();
        if (s.type == ST_WALK && override != null && s.a < override.length) {
            int k = (s.c - s.b + Board.SIZE) % Board.SIZE;
            float t = Math.min(k, el / 200f);
            int i = (int) t;
            float f = t - i;
            if (i >= k) { i = k; f = 0; }
            int p0 = (s.b + i) % Board.SIZE, p1 = (s.b + i + 1) % Board.SIZE;
            float gx = GRID[p0][0] + (GRID[p1][0] - GRID[p0][0]) * f;
            float gy = GRID[p0][1] + (GRID[p1][1] - GRID[p0][1]) * f;
            override[s.a] = new float[]{gx, gy, (float) Math.sin(f * Math.PI) * 0.9f};
            if (i != lastSoundTile && i > 0) { lastSoundTile = i; sound("step"); }
            followGX = gx; followGY = gy;
        } else if (s.type == ST_JUMP && override != null && s.a < override.length) {
            float f = Math.min(1f, el / 700f);
            float e = f * f * (3 - 2 * f);
            float gx = GRID[s.b][0] + (GRID[s.c][0] - GRID[s.b][0]) * e;
            float gy = GRID[s.b][1] + (GRID[s.c][1] - GRID[s.b][1]) * e;
            override[s.a] = new float[]{gx, gy, (float) Math.sin(f * Math.PI) * 3.5f};
            followGX = gx; followGY = gy;
        } else if (s.type == ST_ROULETTE) {
            int tick = (int) (rouletteAngle(el, s.a) / 45f);
            if (tick != lastTick && el < 3300) { lastTick = tick; sound("tick"); }
        }
        if (el >= stepDuration(s)) {
            if ((s.type == ST_WALK || s.type == ST_JUMP) && override != null && s.a < override.length) {
                override[s.a] = new float[]{GRID[s.c][0], GRID[s.c][1], 0};
                if (s.type == ST_WALK) sound("land");
            }
            stepIdx++;
            if (stepIdx >= steps.size()) {
                stepIdx = -1;
                followGX = followGY = -1;
                final Runnable d = onDone;
                onDone = null;
                override = null;
                if (d != null) post(d);
            } else {
                startStep();
            }
        }
    }

    private void followCamera(long now) {
        if (followGX < 0 || zoom < 1.15f || now - lastUserTouch < 2500) return;
        float tx = viewCX() - (followGX - followGY) * tw() / 2f;
        float ty = viewCY() - (followGX + followGY) * th() / 2f;
        camX += (tx - camX) * 0.12f;
        camY += (ty - camY) * 0.12f;
        clampCamera();
    }

    // ================================================================= desenho

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        camReady = false;
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        if (!camReady) resetCamera();
        updateAnim(now);
        followCamera(now);

        drawSky(c);
        drawGround(c);
        if (game != null) {
            for (int d = 0; d <= 20; d++) {
                for (int p = 0; p < Board.SIZE; p++) {
                    if (GRID[p][0] + GRID[p][1] == d) drawTile(c, p);
                }
            }
            drawObjects(c, now);
            if (zoom >= 1.3f) drawLabels(c);
        }
        drawFloaters(c, now);
        drawHud(c, now);

        boolean busy = stepIdx >= 0 || !floaters.isEmpty() || !banners.isEmpty() || followGX >= 0;
        if (busy || (game != null && game.phase != Game.PH_OVER)) postInvalidateOnAnimation();
    }

    private void drawSky(Canvas c) {
        int h = getHeight();
        if (sky == null || skyH != h) {
            sky = new LinearGradient(0, 0, 0, h, 0xFF8FD3F4, 0xFFFDF6E3, Shader.TileMode.CLAMP);
            skyH = h;
        }
        fill.setShader(sky);
        c.drawRect(0, 0, getWidth(), h, fill);
        fill.setShader(null);
    }

    private void poly(Canvas c, int color, boolean outline, float... pts) {
        path.reset();
        path.moveTo(pts[0], pts[1]);
        for (int i = 2; i < pts.length; i += 2) path.lineTo(pts[i], pts[i + 1]);
        path.close();
        fill.setColor(color);
        c.drawPath(path, fill);
        if (outline) c.drawPath(path, stroke);
    }

    /** Laje isométrica com altura (face de cima + duas laterais). */
    private void slab(Canvas c, float x0, float y0, float x1, float y1, float z, float thick, int top) {
        float tAx = sx(x0, y0), tAy = sy(x0, y0) - z;
        float tBx = sx(x1, y0), tBy = sy(x1, y0) - z;
        float tCx = sx(x1, y1), tCy = sy(x1, y1) - z;
        float tDx = sx(x0, y1), tDy = sy(x0, y1) - z;
        poly(c, Pawns.darker(top, 0.78f), true, tDx, tDy, tCx, tCy, tCx, tCy + thick, tDx, tDy + thick);
        poly(c, Pawns.darker(top, 0.62f), true, tCx, tCy, tBx, tBy, tBx, tBy + thick, tCx, tCy + thick);
        poly(c, top, true, tAx, tAy, tBx, tBy, tCx, tCy, tDx, tDy);
    }

    private void drawGround(Canvas c) {
        stroke.setStrokeWidth(Math.max(1.2f, tw() * 0.018f));
        float th = th();
        // base de madeira embaixo de tudo
        slab(c, -0.62f, -0.62f, 10.62f, 10.62f, -th * 0.18f, th * 0.55f, 0xFFB98A5A);
        // gramado do centro
        slab(c, 0.55f, 0.55f, 9.45f, 9.45f, -th * 0.05f, th * 0.1f, 0xFF8BC34A);
        // lago
        float lx = sx(6.6f, 6.4f), ly = sy(6.6f, 6.4f);
        r.set(lx - tw() * 0.95f, ly - th * 0.9f, lx + tw() * 0.95f, ly + th * 0.9f);
        fill.setColor(0xFF4FC3F7);
        c.drawOval(r, fill);
        c.drawOval(r, stroke);
        fill.setColor(0x88FFFFFF);
        r.set(lx - tw() * 0.45f, ly - th * 0.25f, lx + tw() * 0.05f, ly);
        c.drawOval(r, fill);
        // logo pintado no chão
        drawLogo(c);
    }

    private void drawLogo(Canvas c) {
        String s = "MAGNATA";
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setTextSize(100);
        text.setTextAlign(Paint.Align.LEFT);
        float w = text.measureText(s);
        float[] src = {0, -80, w, -80, 0, 10};
        float gy = 3.6f;
        float[] dst = {sx(1.4f, gy), sy(1.4f, gy), sx(8.4f, gy), sy(8.4f, gy), sx(1.4f, gy + 1.7f), sy(1.4f, gy + 1.7f)};
        Matrix m = new Matrix();
        m.setPolyToPoly(src, 0, dst, 0, 3);
        c.save();
        c.concat(m);
        textStroke.setTypeface(text.getTypeface());
        textStroke.setTextSize(100);
        textStroke.setTextAlign(Paint.Align.LEFT);
        textStroke.setStrokeWidth(14);
        textStroke.setColor(0xFF33691E);
        c.drawText(s, 0, 0, textStroke);
        text.setColor(0xFFFFEB3B);
        c.drawText(s, 0, 0, text);
        c.restore();
        text.setTextAlign(Paint.Align.CENTER);
        textStroke.setTextAlign(Paint.Align.CENTER);
        textStroke.setColor(0xFFFFFFFF);
    }

    private int tileColor(int p) {
        if (game != null && game.mortgaged[p]) return 0xFFB8B8B8;
        switch (Board.TYPE[p]) {
            case Board.PROP: return Pawns.lighter(Board.GROUP_COLOR[Board.GROUP[p]], 0.12f);
            case Board.GO: return 0xFF81C784;
            case Board.JAIL: return 0xFFFFB74D;
            case Board.PARKING: return 0xFF4FC3F7;
            case Board.GOTOJAIL: return 0xFFE57373;
            case Board.DESTINO: return 0xFFBA68C8;
            case Board.SURPRESA: return 0xFFFFD54F;
            case Board.TAX: return 0xFFBCAAA4;
            case Board.TRANSPORT: return 0xFF90A4AE;
            default: return 0xFF80CBC4;
        }
    }

    private static String tileIcon(int p) {
        switch (p) {
            case 0: return "🏁";
            case 4: return "💰";
            case 5: return "🚌";
            case 10: return "🚔";
            case 12: return "⚡";
            case 15: return "🚆";
            case 20: return "🎡";
            case 25: return "✈️";
            case 28: return "💧";
            case 30: return "👮";
            case 35: return "🚢";
            case 38: return "💎";
            default:
                if (Board.TYPE[p] == Board.DESTINO) return "❓";
                if (Board.TYPE[p] == Board.SURPRESA) return "⭐";
                return null;
        }
    }

    private void drawTile(Canvas c, int p) {
        float gx = GRID[p][0], gy = GRID[p][1];
        float h = 0.45f;
        float th = th();
        int col = tileColor(p);
        slab(c, gx - h, gy - h, gx + h, gy + h, 0, th * 0.32f, col);
        // borda do dono
        if (game.owner[p] >= 0) {
            float in = 0.36f;
            path.reset();
            path.moveTo(sx(gx - in, gy - in), sy(gx - in, gy - in));
            path.lineTo(sx(gx + in, gy - in), sy(gx + in, gy - in));
            path.lineTo(sx(gx + in, gy + in), sy(gx + in, gy + in));
            path.lineTo(sx(gx - in, gy + in), sy(gx - in, gy + in));
            path.close();
            Paint ps = new Paint(Paint.ANTI_ALIAS_FLAG);
            ps.setStyle(Paint.Style.STROKE);
            ps.setStrokeWidth(tw() * 0.055f);
            ps.setColor(game.players[game.owner[p]].color);
            c.drawPath(path, ps);
        }
        // ícone
        String icon = tileIcon(p);
        float cx = sx(gx, gy), cy = sy(gx, gy);
        if (icon != null) {
            text.setTextSize(th * 0.75f);
            c.drawText(icon, cx, cy + th * 0.25f, text);
        }
        if (game.isClosed(p)) {
            text.setTextSize(th * 0.7f);
            c.drawText("🚧", cx, cy + th * 0.25f, text);
        }
    }

    // ---------------------------------------------------------------- objetos (construções, árvores, peões)

    private interface Drawer { void draw(Canvas c); }

    private static class Obj implements Comparable<Obj> {
        float depth; Drawer d;
        Obj(float depth, Drawer d) { this.depth = depth; this.d = d; }
        @Override public int compareTo(Obj o) { return Float.compare(depth, o.depth); }
    }

    private void drawObjects(Canvas c, long now) {
        ArrayList<Obj> objs = new ArrayList<>();
        for (float[] t : TREES) {
            final float tx = t[0], ty = t[1];
            objs.add(new Obj(tx + ty, cv -> tree(cv, tx, ty)));
        }
        for (int p = 0; p < Board.SIZE; p++) {
            final int pos = p;
            final float gx = GRID[p][0], gy = GRID[p][1];
            int hs = game.houses[p];
            if (Board.TYPE[p] == Board.PROP && hs > 0) {
                if (hs == 5) {
                    objs.add(new Obj(gx + gy + 0.1f, cv -> hotel(cv, gx, gy)));
                } else {
                    float[][] spots = {{-0.2f, -0.2f}, {0.2f, -0.2f}, {-0.2f, 0.2f}, {0.2f, 0.2f}};
                    for (int i = 0; i < hs; i++) {
                        final float hx = gx + spots[i][0], hy = gy + spots[i][1];
                        objs.add(new Obj(hx + hy, cv -> house(cv, hx, hy)));
                    }
                }
            }
            if (game.owner[p] >= 0 && hs < 5) {
                final int col = game.players[game.owner[p]].color;
                objs.add(new Obj(gx + gy - 0.6f, cv -> flag(cv, gx - 0.3f, gy - 0.3f, col)));
            }
            if (game.eventId == Rules.E_FESTA && game.eventArg == pos) {
                objs.add(new Obj(gx + gy + 0.9f, cv -> {
                    text.setTextSize(th() * 0.8f);
                    cv.drawText("🎉", sx(gx, gy), sy(gx, gy) - th() * 0.9f, text);
                }));
            }
        }
        // peões
        int n = game.players.length;
        for (int i = 0; i < n; i++) {
            Player pl = game.players[i];
            if (pl.bankrupt) continue;
            final float[] pp = pawnPos(i);
            final int idx = i;
            objs.add(new Obj(pp[0] + pp[1] + 0.5f, cv -> drawPawn(cv, idx, pp, now)));
        }
        Collections.sort(objs);
        for (Obj o : objs) o.d.draw(c);
    }

    private float[] pawnPos(int i) {
        if (override != null && i < override.length && override[i] != null) return override[i];
        Player pl = game.players[i];
        int pos = pl.pos;
        int k = 0, count = 0;
        for (int j = 0; j < game.players.length; j++) {
            Player q = game.players[j];
            if (q.bankrupt || q.pos != pos) continue;
            if (override != null && j < override.length && override[j] != null) continue;
            if (j < i) k++;
            count++;
        }
        float[][] off = {{0, 0}, {-0.2f, 0.12f}, {0.12f, -0.2f}, {0.2f, 0.2f}, {-0.22f, -0.12f}, {0.05f, 0.28f}};
        float ox = 0, oy = 0;
        if (count > 1) { ox = off[(k + 1) % off.length][0]; oy = off[(k + 1) % off.length][1]; }
        if (pos == Board.JAIL_POS && pl.inJail) { ox = 0.15f + (k % 2) * 0.12f; oy = -0.2f + (k / 2) * 0.12f; }
        return new float[]{GRID[pos][0] + ox, GRID[pos][1] + oy, 0};
    }

    private void drawPawn(Canvas c, int i, float[] pp, long now) {
        Player pl = game.players[i];
        float x = sx(pp[0], pp[1]), y = sy(pp[0], pp[1]);
        float size = tw() * 0.62f;
        float hop = pp[2] * th();
        if (i == game.current && game.phase != Game.PH_OVER && stepIdx < 0) {
            float pulse = (float) (0.5 + 0.5 * Math.sin(now / 220.0));
            Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(tw() * 0.03f);
            ring.setColor((((int) (120 + 135 * pulse)) << 24) | (pl.color & 0xFFFFFF));
            r.set(x - tw() * 0.32f, y - th() * 0.32f, x + tw() * 0.32f, y + th() * 0.32f);
            c.drawOval(r, ring);
        }
        Pawns.draw(c, pl.pawn, pl.color, x, y - hop, size);
        if (pl.inJail && pl.pos == Board.JAIL_POS && override == null) {
            Paint bars = new Paint(Paint.ANTI_ALIAS_FLAG);
            bars.setColor(0xDD424242);
            bars.setStrokeWidth(size * 0.05f);
            for (int k = -2; k <= 2; k++) {
                float bx = x + k * size * 0.12f;
                c.drawLine(bx, y - size * 0.95f, bx, y, bars);
            }
        }
    }

    private void box(Canvas c, float gx, float gy, float hx, float hy, float z, float height, int col) {
        float th = th();
        float aX = sx(gx - hx, gy - hy), aY = sy(gx - hx, gy - hy) - z;
        float bX = sx(gx + hx, gy - hy), bY = sy(gx + hx, gy - hy) - z;
        float cX = sx(gx + hx, gy + hy), cY = sy(gx + hx, gy + hy) - z;
        float dX = sx(gx - hx, gy + hy), dY = sy(gx - hx, gy + hy) - z;
        poly(c, Pawns.darker(col, 0.85f), true, dX, dY, cX, cY, cX, cY - height, dX, dY - height);
        poly(c, Pawns.darker(col, 0.68f), true, cX, cY, bX, bY, bX, bY - height, cX, cY - height);
        poly(c, Pawns.lighter(col, 0.15f), true, aX, aY - height, bX, bY - height, cX, cY - height, dX, dY - height);
    }

    private void house(Canvas c, float gx, float gy) {
        float th = th();
        float hw = 0.13f, h = th * 0.42f, z = 0;
        box(c, gx, gy, hw, hw, z, h, 0xFFFFF3E0);
        // telhado (pirâmide)
        float apexX = sx(gx, gy), apexY = sy(gx, gy) - h - th * 0.42f;
        float bX = sx(gx + hw, gy - hw), bY = sy(gx + hw, gy - hw) - h;
        float cX = sx(gx + hw, gy + hw), cY = sy(gx + hw, gy + hw) - h;
        float dX = sx(gx - hw, gy + hw), dY = sy(gx - hw, gy + hw) - h;
        poly(c, 0xFFE53935, true, dX, dY, cX, cY, apexX, apexY);
        poly(c, 0xFFB71C1C, true, cX, cY, bX, bY, apexX, apexY);
        // porta
        fill.setColor(0xFF6D4C41);
        float px = (dX + cX) / 2f, py = (dY + cY) / 2f + h;
        c.drawRect(px - tw() * 0.025f, py - h * 0.55f, px + tw() * 0.025f, py - h * 0.05f, fill);
    }

    private void hotel(Canvas c, float gx, float gy) {
        float th = th();
        float hw = 0.3f, h = th * 1.5f;
        box(c, gx, gy, hw, hw, 0, h, 0xFFEF5350);
        // janelas
        fill.setColor(0xFFFFF59D);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                float u = (col + 0.5f) / 3f;
                float wx = sx(gx - hw + u * 2 * hw, gy + hw), wy = sy(gx - hw + u * 2 * hw, gy + hw);
                float yy = wy - h * (0.2f + row * 0.28f);
                c.drawRect(wx - tw() * 0.03f, yy - th * 0.12f, wx + tw() * 0.03f, yy, fill);
                float vx = sx(gx + hw, gy + hw - u * 2 * hw), vy = sy(gx + hw, gy + hw - u * 2 * hw);
                float yv = vy - h * (0.2f + row * 0.28f);
                c.drawRect(vx - tw() * 0.03f, yv - th * 0.12f, vx + tw() * 0.03f, yv, fill);
            }
        }
        // letreiro
        float tx = sx(gx - hw, gy - hw), ty = sy(gx - hw, gy - hw) - h;
        float bx = sx(gx + hw, gy + hw);
        text.setTextSize(th * 0.38f);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        textStroke.setTextSize(th * 0.38f);
        textStroke.setStrokeWidth(th * 0.08f);
        textStroke.setColor(0xFF4A148C);
        float mx = (tx + bx) / 2f;
        c.drawText("HOTEL", mx, ty + th * 0.15f, textStroke);
        text.setColor(0xFFFFEB3B);
        c.drawText("HOTEL", mx, ty + th * 0.15f, text);
        textStroke.setColor(0xFFFFFFFF);
        text.setTypeface(Typeface.DEFAULT);
    }

    private void flag(Canvas c, float gx, float gy, int col) {
        float th = th();
        float x = sx(gx, gy), y = sy(gx, gy);
        Paint pole = new Paint(Paint.ANTI_ALIAS_FLAG);
        pole.setColor(0xFF5D4037);
        pole.setStrokeWidth(Math.max(2f, tw() * 0.025f));
        c.drawLine(x, y, x, y - th * 1.25f, pole);
        poly(c, col, true, x, y - th * 1.25f, x + tw() * 0.24f, y - th * 1.05f, x, y - th * 0.85f);
    }

    private void tree(Canvas c, float gx, float gy) {
        float th = th();
        float x = sx(gx, gy), y = sy(gx, gy);
        fill.setColor(0x33000000);
        r.set(x - tw() * 0.22f, y - th * 0.18f, x + tw() * 0.22f, y + th * 0.18f);
        c.drawOval(r, fill);
        fill.setColor(0xFF795548);
        c.drawRect(x - tw() * 0.04f, y - th * 0.7f, x + tw() * 0.04f, y, fill);
        float rad = tw() * 0.2f;
        fill.setColor(0xFF43A047);
        c.drawCircle(x - rad * 0.5f, y - th * 0.95f, rad * 0.8f, fill);
        c.drawCircle(x + rad * 0.5f, y - th * 0.95f, rad * 0.8f, fill);
        fill.setColor(0xFF66BB6A);
        c.drawCircle(x, y - th * 1.25f, rad, fill);
        stroke.setStrokeWidth(Math.max(1f, tw() * 0.012f));
        c.drawCircle(x, y - th * 1.25f, rad, stroke);
        fill.setColor(0x66FFFFFF);
        c.drawCircle(x - rad * 0.35f, y - th * 1.4f, rad * 0.25f, fill);
    }

    private void drawLabels(Canvas c) {
        float th = th();
        text.setTypeface(Typeface.DEFAULT_BOLD);
        float ts = Math.min(th * 0.3f, tw() * 0.13f);
        text.setTextSize(ts);
        textStroke.setTextSize(ts);
        textStroke.setTypeface(Typeface.DEFAULT_BOLD);
        textStroke.setStrokeWidth(ts * 0.3f);
        for (int p = 0; p < Board.SIZE; p++) {
            float gx = GRID[p][0], gy = GRID[p][1];
            float x = sx(gx, gy), y = sy(gx, gy) + th * 0.45f;
            String name = Board.SHORT[p];
            c.drawText(name, x, y, textStroke);
            text.setColor(0xFF263238);
            c.drawText(name, x, y, text);
            if (Board.buyable(p) && game.owner[p] < 0) {
                String pr = "R$ " + game.buyPrice(p);
                c.drawText(pr, x, y + ts * 1.1f, textStroke);
                text.setColor(0xFF2E7D32);
                c.drawText(pr, x, y + ts * 1.1f, text);
            }
        }
        text.setTypeface(Typeface.DEFAULT);
        textStroke.setTypeface(Typeface.DEFAULT);
    }

    // ---------------------------------------------------------------- textos flutuantes e emojis

    private void drawFloaters(Canvas c, long now) {
        if (game == null) { floaters.clear(); return; }
        for (int i = floaters.size() - 1; i >= 0; i--) {
            Floater f = floaters.get(i);
            long el = now - f.start;
            long dur = f.emoji ? 2400 : 1600;
            if (el > dur || f.who < 0 || f.who >= game.players.length) { floaters.remove(i); continue; }
            float[] pp = pawnPos(f.who);
            float x = sx(pp[0], pp[1]);
            float y = sy(pp[0], pp[1]) - tw() * 0.75f - el / (float) dur * tw() * 0.6f;
            float alpha = el < dur * 0.7f ? 1f : 1f - (el - dur * 0.7f) / (dur * 0.3f);
            if (f.emoji) {
                float sc = el < 200 ? el / 200f * 1.3f : el < 350 ? 1.3f - (el - 200) / 150f * 0.3f : 1f;
                float sz = Math.max(tw() * 0.6f, dp(34)) * sc;
                fill.setColor(((int) (alpha * 235) << 24) | 0xFFFFFF);
                r.set(x - sz * 0.62f, y - sz * 0.95f, x + sz * 0.62f, y + sz * 0.25f);
                c.drawRoundRect(r, sz * 0.4f, sz * 0.4f, fill);
                text.setTextSize(sz * 0.8f);
                text.setAlpha((int) (alpha * 255));
                c.drawText(f.text, x, y, text);
                text.setAlpha(255);
            } else {
                float ts = Math.max(dp(16), tw() * 0.24f);
                text.setTypeface(Typeface.DEFAULT_BOLD);
                text.setTextSize(ts);
                textStroke.setTypeface(Typeface.DEFAULT_BOLD);
                textStroke.setTextSize(ts);
                textStroke.setStrokeWidth(ts * 0.22f);
                textStroke.setAlpha((int) (alpha * 255));
                c.drawText(f.text, x, y, textStroke);
                text.setColor(f.color);
                text.setAlpha((int) (alpha * 255));
                c.drawText(f.text, x, y, text);
                text.setAlpha(255);
                textStroke.setAlpha(255);
                text.setTypeface(Typeface.DEFAULT);
                textStroke.setTypeface(Typeface.DEFAULT);
            }
        }
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    // ---------------------------------------------------------------- HUD: dados, roleta, avisos

    private void drawHud(Canvas c, long now) {
        if (stepIdx >= 0) {
            Step s = steps.get(stepIdx);
            long el = now - stepStart;
            if (s.type == ST_DICE) drawDiceRoll(c, s, el, now);
            else if (s.type == ST_ROULETTE) drawRoulette(c, s.a, el);
        }
        drawBanner(c, now);
    }

    private void drawDiceRoll(Canvas c, Step s, long el, long now) {
        int n = s.c > 0 ? 3 : 2;
        int[] vals = {s.a, s.b, s.c};
        float size = Math.min(getWidth(), getHeight()) * 0.19f;
        float rollT = 1150f;
        boolean rolling = el < rollT;
        if (rolling && now - lastFaceChange > 85) {
            lastFaceChange = now;
            for (int i = 0; i < diceFaces.length; i++) diceFaces[i] = 1 + rnd.nextInt(6);
        }
        float cy = viewCY();
        // escurece o fundo de leve
        fill.setColor(0x33000000);
        c.drawRect(0, 0, getWidth(), getHeight(), fill);
        for (int i = 0; i < n; i++) {
            float targetX = getWidth() / 2f + (i - (n - 1) / 2f) * size * 1.35f;
            float t = Math.min(1f, el / rollT);
            float ease = 1 - (1 - t) * (1 - t);
            float startX = -size + i * size * 0.3f;
            float x = startX + (targetX - startX) * ease;
            float bounce = (float) Math.abs(Math.sin(t * Math.PI * 3.2 + i)) * size * 1.1f * (1 - t);
            float y = cy - bounce;
            float rot = rolling ? (1 - t) * 720f * (i % 2 == 0 ? 1 : -1) : 0f;
            int top = rolling ? diceFaces[i * 3] : vals[i];
            int left = rolling ? diceFaces[i * 3 + 1] : (vals[i] % 6) + 1;
            int right = rolling ? diceFaces[i * 3 + 2] : ((vals[i] + 2) % 6) + 1;
            if (!rolling && right == top) right = (right % 6) + 1;
            c.save();
            c.rotate(rot, x, y);
            die(c, x, y, size, top, left, right, i == 2 ? 0xFFFFE082 : 0xFFFFFFFF);
            c.restore();
        }
        if (!rolling) {
            int sum = s.a + s.b + s.c;
            String t = (s.c > 0 ? s.a + " + " + s.b + " + " + s.c : s.a + " + " + s.b) + " = " + sum + (s.a == s.b ? "  DUPLA!" : "");
            float ts = dp(24);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(ts);
            textStroke.setTypeface(Typeface.DEFAULT_BOLD);
            textStroke.setTextSize(ts);
            textStroke.setStrokeWidth(ts * 0.25f);
            textStroke.setColor(0xFF33302A);
            c.drawText(t, getWidth() / 2f, cy + size * 1.25f, textStroke);
            text.setColor(s.a == s.b ? 0xFFFFEB3B : 0xFFFFFFFF);
            c.drawText(t, getWidth() / 2f, cy + size * 1.25f, text);
            textStroke.setColor(0xFFFFFFFF);
            text.setTypeface(Typeface.DEFAULT);
            textStroke.setTypeface(Typeface.DEFAULT);
        }
    }

    private void die(Canvas c, float cx, float cy, float s, int top, int left, int right, int color) {
        stroke.setStrokeWidth(Math.max(2f, s * 0.04f));
        float t0x = cx, t0y = cy - s * 0.5f;
        float t1x = cx + s * 0.5f, t1y = cy - s * 0.25f;
        float t2x = cx, t2y = cy;
        float t3x = cx - s * 0.5f, t3y = cy - s * 0.25f;
        float dh = s * 0.55f;
        poly(c, Pawns.darker(color, 0.86f), true, t3x, t3y, t2x, t2y, t2x, t2y + dh, t3x, t3y + dh);
        poly(c, Pawns.darker(color, 0.72f), true, t2x, t2y, t1x, t1y, t1x, t1y + dh, t2x, t2y + dh);
        poly(c, color, true, t0x, t0y, t1x, t1y, t2x, t2y, t3x, t3y);
        float pr = s * 0.065f;
        pips(c, top, t0x, t0y, t1x - t0x, t1y - t0y, t3x - t0x, t3y - t0y, pr, 0.6f, 0xFFD32F2F);
        pips(c, left, t3x, t3y, t2x - t3x, t2y - t3y, 0, dh, pr, 0.75f, 0xFF333333);
        pips(c, right, t2x, t2y, t1x - t2x, t1y - t2y, 0, dh, pr, 0.75f, 0xFF333333);
    }

    private void pips(Canvas c, int v, float ox, float oy, float ux, float uy, float vx, float vy, float pr, float squash, int col) {
        float[][] pts;
        float a = 0.25f, m = 0.5f, b = 0.75f;
        switch (v) {
            case 1: pts = new float[][]{{m, m}}; break;
            case 2: pts = new float[][]{{a, a}, {b, b}}; break;
            case 3: pts = new float[][]{{a, a}, {m, m}, {b, b}}; break;
            case 4: pts = new float[][]{{a, a}, {b, a}, {a, b}, {b, b}}; break;
            case 5: pts = new float[][]{{a, a}, {b, a}, {m, m}, {a, b}, {b, b}}; break;
            default: pts = new float[][]{{a, a}, {b, a}, {a, m}, {b, m}, {a, b}, {b, b}}; break;
        }
        fill.setColor(v == 1 ? col : (col == 0xFFD32F2F ? 0xFF333333 : col));
        for (float[] p : pts) {
            float x = ox + ux * p[0] + vx * p[1];
            float y = oy + uy * p[0] + vy * p[1];
            r.set(x - pr, y - pr * squash, x + pr, y + pr * squash);
            c.drawOval(r, fill);
        }
    }

    private float rouletteAngle(long el, int result) {
        float spin = 3300f;
        float total = 360f * 5 + result * 45f + 22.5f;
        float t = Math.min(1f, el / spin);
        float e = 1 - (float) Math.pow(1 - t, 3);
        return total * e;
    }

    private void drawRoulette(Canvas c, int result, long el) {
        fill.setColor(0x66000000);
        c.drawRect(0, 0, getWidth(), getHeight(), fill);
        float cx = getWidth() / 2f, cy = viewCY();
        float R = Math.min(getWidth(), getHeight()) * 0.36f;
        float ang = rouletteAngle(el, result);
        stroke.setStrokeWidth(dp(3));
        c.save();
        c.rotate(-ang, cx, cy);
        r.set(cx - R, cy - R, cx + R, cy + R);
        for (int i = 0; i < Rules.ROULETTE.length; i++) {
            fill.setColor(Rules.ROULETTE_COLORS[i]);
            float startA = -90 + i * 45f;
            c.drawArc(r, startA, 45f, true, fill);
            c.drawArc(r, startA, 45f, true, stroke);
        }
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextSize(R * 0.1f);
        text.setColor(0xFFFFFFFF);
        textStroke.setTypeface(Typeface.DEFAULT_BOLD);
        textStroke.setTextSize(R * 0.1f);
        textStroke.setStrokeWidth(R * 0.025f);
        textStroke.setColor(0xFF33302A);
        for (int i = 0; i < Rules.ROULETTE.length; i++) {
            c.save();
            c.rotate(i * 45f + 22.5f, cx, cy);
            c.drawText(Rules.ROULETTE[i], cx, cy - R * 0.62f, textStroke);
            c.drawText(Rules.ROULETTE[i], cx, cy - R * 0.62f, text);
            c.restore();
        }
        c.restore();
        textStroke.setColor(0xFFFFFFFF);
        fill.setColor(0xFFFFFFFF);
        c.drawCircle(cx, cy, R * 0.14f, fill);
        c.drawCircle(cx, cy, R * 0.14f, stroke);
        text.setTextSize(R * 0.16f);
        c.drawText("🎡", cx, cy + R * 0.06f, text);
        // ponteiro
        poly(c, 0xFFFFEB3B, true, cx - R * 0.09f, cy - R - R * 0.12f, cx + R * 0.09f, cy - R - R * 0.12f, cx, cy - R + R * 0.1f);
        if (el > 3300) {
            String res = Rules.ROULETTE[result];
            float ts = dp(26);
            text.setTextSize(ts);
            textStroke.setTextSize(ts);
            textStroke.setStrokeWidth(ts * 0.25f);
            textStroke.setColor(0xFF33302A);
            c.drawText(res, cx, cy + R + ts * 1.4f, textStroke);
            text.setColor(0xFFFFEB3B);
            c.drawText(res, cx, cy + R + ts * 1.4f, text);
            textStroke.setColor(0xFFFFFFFF);
        }
        text.setTypeface(Typeface.DEFAULT);
        textStroke.setTypeface(Typeface.DEFAULT);
    }

    private int bannerColor(String kind) {
        switch (kind) {
            case "CARD": return 0xFF7E57C2;
            case "EVENT": return 0xFFFF8F00;
            case "ROULETTE": return 0xFF1E88E5;
            case "POWER": return 0xFF00897B;
            case "MISSION": return 0xFF43A047;
            case "WIN": return 0xFFF9A825;
            default: return 0xFF455A64;
        }
    }

    private void drawBanner(Canvas c, long now) {
        if (banners.isEmpty()) { bannerRect.setEmpty(); return; }
        Banner b = banners.get(0);
        long el = now - b.start;
        long dur = 4200;
        if (el > dur) {
            banners.remove(0);
            if (!banners.isEmpty()) banners.get(0).start = now;
            bannerRect.setEmpty();
            return;
        }
        float w = getWidth() * 0.9f;
        float pad = dp(14);
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(0xFFFFFFFF);
        tp.setTextSize(dp(15));
        StaticLayout body = StaticLayout.Builder.obtain(b.text, 0, b.text.length(), tp, (int) (w - pad * 2))
                .setAlignment(Layout.Alignment.ALIGN_CENTER).build();
        float titleH = dp(26);
        float h = pad * 2 + titleH + body.getHeight();
        float slide = el < 250 ? (1 - el / 250f) : el > dur - 250 ? (el - (dur - 250)) / 250f : 0f;
        float x = (getWidth() - w) / 2f;
        float y = insetTop + dp(8) - slide * (h + insetTop + dp(16));
        bannerRect.set(x, y, x + w, y + h);
        fill.setColor(0x55000000);
        r.set(x + dp(3), y + dp(4), x + w + dp(3), y + h + dp(4));
        c.drawRoundRect(r, dp(18), dp(18), fill);
        fill.setColor(bannerColor(b.kind));
        c.drawRoundRect(bannerRect, dp(18), dp(18), fill);
        stroke.setStrokeWidth(dp(2.5f));
        c.drawRoundRect(bannerRect, dp(18), dp(18), stroke);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextSize(dp(19));
        text.setColor(0xFFFFFFFF);
        c.drawText(b.title, x + w / 2f, y + pad + dp(19), text);
        text.setTypeface(Typeface.DEFAULT);
        c.save();
        c.translate(x + pad, y + pad + titleH);
        body.draw(c);
        c.restore();
    }
}
