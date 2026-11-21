package com.netero.magnata.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
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
 * Tabuleiro 2.5D (isométrico) que gira, aproxima e arrasta. Desenha casas com o nome
 * pintado dentro, praça central, construções, peões animados, dados 3D, roleta e avisos.
 */
public class BoardView extends View {

    public interface OnSquareTap { void onSquare(int pos); }
    public interface Fx { void sound(String name); }

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

    private static final long DICE_ROLL = 1200, DICE_HOLD = 1800;
    private static final float TH_RATIO = 0.56f;

    private Game game;
    private int me = -1;
    private OnSquareTap tapListener;
    private Fx fx;

    // câmera
    private float baseTW, zoom = 1f, camX, camY;
    private float angle = 0f, angleTarget = Float.NaN;
    private float cosA = 1f, sinA = 0f;
    private boolean camReady;
    private float insetTop, insetBottom;
    private long lastUserTouch;
    private float followGX = -1, followGY = -1;
    private float lastTwist = Float.NaN;

    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;

    // animação
    private final ArrayList<Step> steps = new ArrayList<>();
    private int stepIdx = -1;
    private long stepStart;
    private Runnable onDone;
    private float[][] override;
    private int lastSoundTile = -1, lastTick = -1;
    private final int[] diceFaces = new int[9];
    private long lastFaceChange;
    private final Random rnd = new Random();

    // sobreposições
    private static class Banner { String kind, title, text; long start; }
    private final ArrayList<Banner> banners = new ArrayList<>();
    private static class Floater { int who; String text; int color; long start; boolean emoji; }
    private final ArrayList<Floater> floaters = new ArrayList<>();
    private final RectF bannerRect = new RectF();

    // pincéis
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint aux = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private final Matrix matrix = new Matrix();
    private final float[] src = new float[6], dst = new float[6];
    private LinearGradient sky;
    private int skyH;

    private static final int OUTLINE = 0xFF3A322B;
    private static final int TILE_CREAM = 0xFFFFF6E0;

    // casas no grid 11x11
    private static final int[][] GRID = new int[Board.SIZE][2];
    private static final int[][] POS_AT = new int[11][11];
    private static final float[][] INWARD = new float[Board.SIZE][2];

    static {
        for (int[] row : POS_AT) java.util.Arrays.fill(row, -1);
        for (int p = 0; p < Board.SIZE; p++) {
            int gx, gy;
            float ix, iy;
            if (p <= 10) { gx = 10 - p; gy = 10; ix = 0; iy = -1; }
            else if (p <= 20) { gx = 0; gy = 10 - (p - 10); ix = 1; iy = 0; }
            else if (p <= 30) { gx = p - 20; gy = 0; ix = 0; iy = 1; }
            else { gx = 10; gy = p - 30; ix = -1; iy = 0; }
            if (p % 10 == 0) {
                ix = 5 - gx; iy = 5 - gy;
                float l = (float) Math.sqrt(ix * ix + iy * iy);
                ix /= l; iy /= l;
            }
            GRID[p][0] = gx;
            GRID[p][1] = gy;
            INWARD[p][0] = ix;
            INWARD[p][1] = iy;
            POS_AT[gx][gy] = p;
        }
    }

    // decoração da praça
    private static final float[][] TREES = { // x, y, tipo (0 redonda, 1 pinheiro, 2 arbusto)
            {1.9f, 1.8f, 0}, {3.2f, 1.6f, 1}, {1.6f, 3.1f, 1}, {2.9f, 2.8f, 2}, {3.6f, 3.4f, 2},
            {7.2f, 7.9f, 0}, {8.3f, 7.0f, 1}, {8.2f, 8.3f, 0}, {6.6f, 6.7f, 2}, {7.6f, 6.3f, 2},
            {6.4f, 1.7f, 2}, {8.4f, 3.6f, 0}, {1.7f, 6.4f, 2}, {3.7f, 8.5f, 1}
    };
    private static final float[][] LAMPS = {{4.35f, 3.2f}, {5.65f, 6.8f}, {3.2f, 5.65f}, {6.8f, 4.35f}};
    private static final float[][] FLOWERS = {
            {3.9f, 3.9f, 0xFFE91E63}, {6.1f, 3.9f, 0xFFFFEB3B}, {3.9f, 6.1f, 0xFFFF9800}, {6.1f, 6.1f, 0xFF9C27B0},
            {2.3f, 2.5f, 0xFFFF5252}, {7.6f, 7.4f, 0xFFFFEB3B}, {8.0f, 2.2f, 0xFF7C4DFF}, {2.2f, 8.0f, 0xFFFF4081}
    };
    private static final float[][] CLOUDS = {{0.12f, 0.12f, 1f}, {0.62f, 0.07f, 0.8f}, {0.85f, 0.22f, 1.15f}, {0.35f, 0.3f, 0.7f}};

    public BoardView(Context ctx) {
        super(ctx);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setColor(OUTLINE);
        text.setTextAlign(Paint.Align.CENTER);
        textStroke.setStyle(Paint.Style.STROKE);
        textStroke.setTextAlign(Paint.Align.CENTER);
        textStroke.setColor(0xFFFFFFFF);
        textStroke.setStrokeJoin(Paint.Join.ROUND);
        paintText.setTextAlign(Paint.Align.LEFT);
        paintText.setTextSize(100);
        paintText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

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
                if (e2.getPointerCount() > 1) return true;
                camX -= dx;
                camY -= dy;
                clampCamera();
                lastUserTouch = SystemClock.uptimeMillis();
                invalidate();
                return true;
            }
            @Override public boolean onDoubleTap(MotionEvent e) {
                if (zoom > 1.3f) resetCamera();
                else zoomAt(e.getX(), e.getY(), 2.1f);
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

    public void setGame(Game g, int me) { this.game = g; this.me = me; invalidate(); }
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

    /** Gira o tabuleiro em passos de 90° (animado). */
    public void rotateBy(float deg) {
        float base = Float.isNaN(angleTarget) ? Math.round(angle / 90f) * 90f : angleTarget;
        angleTarget = base + deg;
        lastUserTouch = SystemClock.uptimeMillis();
        invalidate();
    }

    /** Volta a mostrar o tabuleiro inteiro. */
    public void recenter() {
        resetCamera();
        invalidate();
    }

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

    // ================================================================= projeção e câmera

    private float tw() { return baseTW * zoom; }
    private float th() { return baseTW * zoom * TH_RATIO; }
    private float rotX(float gx, float gy) { return (gx - 5) * cosA - (gy - 5) * sinA + 5; }
    private float rotY(float gx, float gy) { return (gx - 5) * sinA + (gy - 5) * cosA + 5; }
    private float sx(float gx, float gy) { return camX + (rotX(gx, gy) - rotY(gx, gy)) * tw() / 2f; }
    private float sy(float gx, float gy) { return camY + (rotX(gx, gy) + rotY(gx, gy)) * th() / 2f; }
    private float depth(float gx, float gy) { return rotX(gx, gy) + rotY(gx, gy); }

    /** Direção (dx, dy) do grid projetada na tela. */
    private float dirX(float dx, float dy) { return ((dx * cosA - dy * sinA) - (dx * sinA + dy * cosA)) * tw() / 2f; }
    private float dirY(float dx, float dy) { return ((dx * cosA - dy * sinA) + (dx * sinA + dy * cosA)) * th() / 2f; }

    private float viewCX() { return getWidth() / 2f; }
    private float viewCY() { return insetTop + (getHeight() - insetTop - insetBottom) / 2f; }

    private void updateTrig() {
        double a = Math.toRadians(angle);
        cosA = (float) Math.cos(a);
        sinA = (float) Math.sin(a);
    }

    private void resetCamera() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        updateTrig();
        float minA = Float.MAX_VALUE, maxA = -Float.MAX_VALUE, minB = Float.MAX_VALUE, maxB = -Float.MAX_VALUE;
        for (float[] cpt : new float[][]{{-0.7f, -0.7f}, {10.7f, -0.7f}, {10.7f, 10.7f}, {-0.7f, 10.7f}}) {
            float a = rotX(cpt[0], cpt[1]) - rotY(cpt[0], cpt[1]);
            float b = rotX(cpt[0], cpt[1]) + rotY(cpt[0], cpt[1]);
            minA = Math.min(minA, a); maxA = Math.max(maxA, a);
            minB = Math.min(minB, b); maxB = Math.max(maxB, b);
        }
        float avail = Math.max(h * 0.3f, h - insetTop - insetBottom);
        float byW = w * 0.98f / ((maxA - minA) / 2f);
        float byH = avail / ((maxB - minB) / 2f * TH_RATIO + 1.2f);
        baseTW = Math.min(byW, byH);
        zoom = 1f;
        centerOn(5, 5);
        camReady = true;
    }

    private void centerOn(float gx, float gy) {
        camX = viewCX() - (rotX(gx, gy) - rotY(gx, gy)) * tw() / 2f;
        camY = viewCY() - (rotX(gx, gy) + rotY(gx, gy)) * th() / 2f;
        clampCamera();
    }

    private void zoomAt(float fx, float fy, float nz) {
        nz = Math.max(0.8f, Math.min(3.4f, nz));
        float a = (fx - camX) / (tw() / 2f), b = (fy - camY) / (th() / 2f);
        zoom = nz;
        camX = fx - a * tw() / 2f;
        camY = fy - b * th() / 2f;
        clampCamera();
        invalidate();
    }

    private void clampCamera() {
        float cx = sx(5, 5), cy = sy(5, 5);
        float halfW = 7.8f * tw() / 2f, halfH = 7.8f * th() / 2f;
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
        float rx = (a + b) / 2f - 5, ry = (b - a) / 2f - 5;
        float gx = rx * cosA + ry * sinA + 5, gy = -rx * sinA + ry * cosA + 5;
        int ix = Math.round(gx), iy = Math.round(gy);
        if (ix < 0 || ix > 10 || iy < 0 || iy > 10) return -1;
        return POS_AT[ix][iy];
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaleDetector.onTouchEvent(e);
        if (e.getPointerCount() >= 2) {
            float tw = (float) Math.toDegrees(Math.atan2(e.getY(1) - e.getY(0), e.getX(1) - e.getX(0)));
            if (!Float.isNaN(lastTwist) && e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float d = tw - lastTwist;
                if (d > 180) d -= 360;
                if (d < -180) d += 360;
                angle += d;
                angleTarget = Float.NaN;
                lastUserTouch = SystemClock.uptimeMillis();
                invalidate();
            }
            lastTwist = tw;
        } else {
            lastTwist = Float.NaN;
        }
        if (e.getActionMasked() == MotionEvent.ACTION_POINTER_UP) lastTwist = Float.NaN;
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
            sound(s.s1.equals("WIN") ? "win" : s.s1.equals("MISSION") ? "coin" : "card");
        }
    }

    private long stepDuration(Step s) {
        switch (s.type) {
            case ST_DICE: return DICE_ROLL + DICE_HOLD;
            case ST_WALK: {
                int k = (s.c - s.b + Board.SIZE) % Board.SIZE;
                return Math.max(1, k) * 210L + 200;
            }
            case ST_JUMP: return 750;
            case ST_ROULETTE: return 4500;
            default: return 350;
        }
    }

    private void sound(String n) { if (fx != null) fx.sound(n); }

    private void updateAnim(long now) {
        if (stepIdx < 0 || game == null) return;
        Step s = steps.get(stepIdx);
        long el = now - stepStart;
        if (override == null || override.length != game.players.length) override = new float[game.players.length][];
        if (s.type == ST_WALK && s.a < override.length) {
            int k = (s.c - s.b + Board.SIZE) % Board.SIZE;
            float t = Math.min(k, el / 210f);
            int i = (int) t;
            float f = t - i;
            if (i >= k) { i = k; f = 0; }
            int p0 = (s.b + i) % Board.SIZE, p1 = (s.b + i + 1) % Board.SIZE;
            float gx = GRID[p0][0] + (GRID[p1][0] - GRID[p0][0]) * f;
            float gy = GRID[p0][1] + (GRID[p1][1] - GRID[p0][1]) * f;
            override[s.a] = new float[]{gx, gy, (float) Math.sin(f * Math.PI) * 0.9f};
            if (i != lastSoundTile && i > 0) { lastSoundTile = i; sound("step"); }
            followGX = gx; followGY = gy;
        } else if (s.type == ST_JUMP && s.a < override.length) {
            float f = Math.min(1f, el / 750f);
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
            if ((s.type == ST_WALK || s.type == ST_JUMP) && s.a < override.length) {
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
        float tx = viewCX() - (rotX(followGX, followGY) - rotY(followGX, followGY)) * tw() / 2f;
        float ty = viewCY() - (rotX(followGX, followGY) + rotY(followGX, followGY)) * th() / 2f;
        camX += (tx - camX) * 0.12f;
        camY += (ty - camY) * 0.12f;
        clampCamera();
    }

    private void updateRotation() {
        if (Float.isNaN(angleTarget)) return;
        float cx = sx(5, 5), cy = sy(5, 5);
        float d = angleTarget - angle;
        if (Math.abs(d) < 0.4f) { angle = angleTarget; angleTarget = Float.NaN; }
        else angle += d * 0.16f;
        updateTrig();
        camX += cx - sx(5, 5);
        camY += cy - sy(5, 5);
    }

    // ================================================================= desenho principal

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        camReady = false;
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        updateTrig();
        if (!camReady) resetCamera();
        updateRotation();
        updateAnim(now);
        followCamera(now);

        drawSky(c, now);
        drawBase(c);
        drawPark(c);
        if (game != null) {
            Integer[] order = new Integer[Board.SIZE];
            for (int i = 0; i < Board.SIZE; i++) order[i] = i;
            java.util.Arrays.sort(order, (a, b) -> Float.compare(depth(GRID[a][0], GRID[a][1]), depth(GRID[b][0], GRID[b][1])));
            for (int p : order) drawTile(c, p);
        }
        drawObjects(c, now);
        drawFloaters(c, now);
        drawHud(c, now);
        postInvalidateOnAnimation();
    }

    private void drawSky(Canvas c, long now) {
        int h = getHeight(), w = getWidth();
        if (sky == null || skyH != h) {
            sky = new LinearGradient(0, 0, 0, h, new int[]{0xFF7EC8F0, 0xFFBFE6F7, 0xFFFFF4DC},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
            skyH = h;
        }
        fill.setShader(sky);
        c.drawRect(0, 0, w, h, fill);
        fill.setShader(null);
        // nuvens
        for (float[] cl : CLOUDS) {
            float x = ((cl[0] * w + now * 0.006f * cl[2]) % (w + dp(160))) - dp(80);
            float y = insetTop + cl[1] * h * 0.6f;
            float s = dp(34) * cl[2];
            fill.setColor(0x22000000);
            c.drawCircle(x + s * 0.1f, y + s * 0.15f, s * 0.6f, fill);
            fill.setColor(0xEEFFFFFF);
            c.drawCircle(x - s * 0.6f, y + s * 0.1f, s * 0.45f, fill);
            c.drawCircle(x, y - s * 0.15f, s * 0.62f, fill);
            c.drawCircle(x + s * 0.65f, y + s * 0.1f, s * 0.48f, fill);
            r.set(x - s * 1.0f, y, x + s * 1.1f, y + s * 0.5f);
            c.drawRoundRect(r, s * 0.25f, s * 0.25f, fill);
        }
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

    /**
     * Prisma de base quadrada [x0,x1]x[y0,y1], do nível z0 até z1 (z em pixels, para cima).
     * Desenha só as faces viradas para a câmera, funciona em qualquer ângulo.
     */
    private void prism(Canvas c, float x0, float y0, float x1, float y1, float z0, float z1, int col, boolean outline) {
        float[][] g = {{x0, y0}, {x1, y0}, {x1, y1}, {x0, y1}};
        float mx = (x0 + x1) / 2f, my = (y0 + y1) / 2f;
        for (int i = 0; i < 4; i++) {
            float[] a = g[i], b = g[(i + 1) % 4];
            float nx = (a[0] + b[0]) / 2f - mx, ny = (a[1] + b[1]) / 2f - my;
            float rnx = nx * cosA - ny * sinA, rny = nx * sinA + ny * cosA;
            if (rnx + rny <= 0.0001f) continue;
            float len = (float) Math.sqrt(rnx * rnx + rny * rny) + 1e-6f;
            float lightSide = (rnx - rny) / len; // >0 virado para a direita
            float f = 0.8f - 0.16f * lightSide;
            int fc = Pawns.darker(col, f);
            float ax = sx(a[0], a[1]), ay = sy(a[0], a[1]), bx = sx(b[0], b[1]), by = sy(b[0], b[1]);
            poly(c, fc, outline, ax, ay - z0, bx, by - z0, bx, by - z1, ax, ay - z1);
        }
        poly(c, col, outline,
                sx(x0, y0), sy(x0, y0) - z1, sx(x1, y0), sy(x1, y0) - z1,
                sx(x1, y1), sy(x1, y1) - z1, sx(x0, y1), sy(x0, y1) - z1);
    }

    private void flatQuad(Canvas c, int col, boolean outline, float z, float... g) {
        float[] pts = new float[g.length];
        for (int i = 0; i < g.length; i += 2) {
            pts[i] = sx(g[i], g[i + 1]);
            pts[i + 1] = sy(g[i], g[i + 1]) - z;
        }
        poly(c, col, outline, pts);
    }

    /** Círculo deitado no chão (elipse), igual em qualquer giro. */
    private void groundCircle(float gx, float gy, float rad, float z, RectF out) {
        float x = sx(gx, gy), y = sy(gx, gy) - z;
        float a = rad * tw() * 0.7071f, b = rad * th() * 0.7071f;
        out.set(x - a, y - b, x + a, y + b);
    }

    private void cylinder(Canvas c, float gx, float gy, float rad, float z0, float z1, int side, int top) {
        float x = sx(gx, gy), y = sy(gx, gy);
        float a = rad * tw() * 0.7071f, b = rad * th() * 0.7071f;
        fill.setShader(new LinearGradient(x - a, 0, x + a, 0,
                new int[]{Pawns.lighter(side, 0.2f), side, Pawns.darker(side, 0.7f)}, null, Shader.TileMode.CLAMP));
        r.set(x - a, y - z0 - b, x + a, y - z0 + b);
        c.drawOval(r, fill);
        c.drawRect(x - a, y - z1, x + a, y - z0, fill);
        fill.setShader(null);
        c.drawArc(r, 0, 180, false, stroke);
        c.drawLine(x - a, y - z1, x - a, y - z0, stroke);
        c.drawLine(x + a, y - z1, x + a, y - z0, stroke);
        r.set(x - a, y - z1 - b, x + a, y - z1 + b);
        fill.setColor(top);
        c.drawOval(r, fill);
        c.drawOval(r, stroke);
    }

    // ---------------------------------------------------------------- base e praça

    private void drawBase(Canvas c) {
        float th = th();
        stroke.setStrokeWidth(Math.max(1.2f, tw() * 0.016f));
        // sombra no "chão"
        groundCircle(5, 5, 7.6f, -th * 0.95f, r);
        fill.setColor(0x16000000);
        c.drawOval(r, fill);
        // moldura de madeira
        prism(c, -0.7f, -0.7f, 10.7f, 10.7f, -th * 0.85f, -th * 0.12f, 0xFFA97142, true);
        prism(c, -0.62f, -0.62f, 10.62f, 10.62f, -th * 0.12f, -th * 0.06f, 0xFFC68A55, false);
    }

    private void drawPark(Canvas c) {
        float th = th();
        float z = -th * 0.05f;
        // gramado com faixas
        flatQuad(c, 0xFF7CB342, true, z, 0.55f, 0.55f, 9.45f, 0.55f, 9.45f, 9.45f, 0.55f, 9.45f);
        for (int i = 0; i < 9; i += 2) {
            float a = 0.55f + i * (8.9f / 9f), b = a + 8.9f / 9f;
            flatQuad(c, 0xFF8BC34A, false, z, a, 0.55f, b, 0.55f, b, 9.45f, a, 9.45f);
        }
        // caminhos de pedra
        int stone = 0xFFE9D8B4;
        flatQuad(c, stone, true, z, 4.62f, 0.6f, 5.38f, 0.6f, 5.38f, 9.4f, 4.62f, 9.4f);
        flatQuad(c, stone, true, z, 0.6f, 4.62f, 9.4f, 4.62f, 9.4f, 5.38f, 0.6f, 5.38f);
        // pedrinhas
        fill.setColor(0x33A1887F);
        for (float t = 1.1f; t < 9.0f; t += 0.55f) {
            if (Math.abs(t - 5) < 1.4f) continue;
            groundCircle(4.85f, t, 0.08f, z, r); c.drawOval(r, fill);
            groundCircle(5.15f, t + 0.25f, 0.07f, z, r); c.drawOval(r, fill);
            groundCircle(t, 4.85f, 0.08f, z, r); c.drawOval(r, fill);
            groundCircle(t + 0.25f, 5.15f, 0.07f, z, r); c.drawOval(r, fill);
        }
        // praça redonda
        groundCircle(5, 5, 1.55f, z, r);
        fill.setColor(stone);
        c.drawOval(r, fill);
        c.drawOval(r, stroke);
        groundCircle(5, 5, 1.32f, z, r);
        fill.setColor(0xFFD7C29A);
        c.drawOval(r, fill);
        // lago
        groundCircle(2.55f, 7.45f, 1.18f, z, r);
        fill.setColor(0xFF8D6E63);
        c.drawOval(r, fill);
        c.drawOval(r, stroke);
        groundCircle(2.55f, 7.45f, 1.02f, z, r);
        float lx = r.centerX(), ly = r.centerY();
        fill.setShader(new RadialGradient(lx - r.width() * 0.2f, ly - r.height() * 0.2f, r.width() * 0.6f,
                0xFF81D4FA, 0xFF0288D1, Shader.TileMode.CLAMP));
        c.drawOval(r, fill);
        fill.setShader(null);
        fill.setColor(0x99FFFFFF);
        groundCircle(2.2f, 7.2f, 0.28f, z, r);
        r.top += r.height() * 0.35f;
        c.drawOval(r, fill);
        // vitórias-régias
        fill.setColor(0xFF43A047);
        groundCircle(2.9f, 7.8f, 0.17f, z, r); c.drawOval(r, fill);
        groundCircle(2.3f, 7.9f, 0.13f, z, r); c.drawOval(r, fill);
        fill.setColor(0xFFF48FB1);
        groundCircle(2.9f, 7.8f, 0.05f, z + th * 0.02f, r); c.drawOval(r, fill);
        // canteiros de flores
        for (float[] f : FLOWERS) {
            groundCircle(f[0], f[1], 0.32f, z, r);
            fill.setColor(0xFF6D4C41);
            c.drawOval(r, fill);
            float cx = r.centerX(), cy = r.centerY(), rw = r.width() / 2f, rh = r.height() / 2f;
            fill.setColor((int) f[2]);
            for (int k = 0; k < 6; k++) {
                double a = k * Math.PI / 3 + 0.3;
                float px = cx + (float) Math.cos(a) * rw * 0.55f, py = cy + (float) Math.sin(a) * rh * 0.55f;
                c.drawCircle(px, py, Math.max(1.5f, tw() * 0.035f), fill);
            }
            fill.setColor(0xFFFFF59D);
            c.drawCircle(cx, cy, Math.max(1.5f, tw() * 0.03f), fill);
        }
    }

    // ---------------------------------------------------------------- casas do tabuleiro

    private int tileTop(int p) {
        if (game != null && game.mortgaged[p]) return 0xFFCFCFCF;
        switch (Board.TYPE[p]) {
            case Board.PROP: return TILE_CREAM;
            case Board.GO: return 0xFFA5D6A7;
            case Board.JAIL: return 0xFFFFCC80;
            case Board.PARKING: return 0xFF81D4FA;
            case Board.GOTOJAIL: return 0xFFEF9A9A;
            case Board.DESTINO: return 0xFFE1BEE7;
            case Board.SURPRESA: return 0xFFFFF59D;
            case Board.TAX: return 0xFFD7CCC8;
            case Board.TRANSPORT: return 0xFFCFD8DC;
            default: return 0xFFB2DFDB;
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
        float h = 0.46f;
        float th = th();
        stroke.setStrokeWidth(Math.max(1.1f, tw() * 0.014f));
        prism(c, gx - h, gy - h, gx + h, gy + h, -th * 0.3f, 0, tileTop(p), true);
        float ix = INWARD[p][0], iy = INWARD[p][1];
        float ex = -iy, ey = ix; // direção ao longo da borda

        if (Board.TYPE[p] == Board.PROP) {
            // faixa colorida do lado de dentro
            int gc = game != null && game.mortgaged[p] ? 0xFF9E9E9E : Board.GROUP_COLOR[Board.GROUP[p]];
            float a0 = 0.18f, a1 = h;
            flatQuad(c, gc, true, 0,
                    gx + ix * a0 + ex * h, gy + iy * a0 + ey * h,
                    gx + ix * a1 + ex * h, gy + iy * a1 + ey * h,
                    gx + ix * a1 - ex * h, gy + iy * a1 - ey * h,
                    gx + ix * a0 - ex * h, gy + iy * a0 - ey * h);
            // brilho da faixa
            flatQuad(c, 0x33FFFFFF, false, 0,
                    gx + ix * 0.36f + ex * h, gy + iy * 0.36f + ey * h,
                    gx + ix * a1 + ex * h, gy + iy * a1 + ey * h,
                    gx + ix * a1 - ex * h, gy + iy * a1 - ey * h,
                    gx + ix * 0.36f - ex * h, gy + iy * 0.36f - ey * h);
        }
        // borda na cor do dono
        if (game != null && game.owner[p] >= 0) {
            float in = 0.4f;
            path.reset();
            path.moveTo(sx(gx - in, gy - in), sy(gx - in, gy - in));
            path.lineTo(sx(gx + in, gy - in), sy(gx + in, gy - in));
            path.lineTo(sx(gx + in, gy + in), sy(gx + in, gy + in));
            path.lineTo(sx(gx - in, gy + in), sy(gx - in, gy + in));
            path.close();
            aux.setStyle(Paint.Style.STROKE);
            aux.setStrokeWidth(Math.max(2f, tw() * 0.05f));
            aux.setColor(game.players[game.owner[p]].color);
            c.drawPath(path, aux);
            aux.setStyle(Paint.Style.FILL);
        }

        // textos pintados no chão da casa
        boolean corner = p % 10 == 0;
        String name = Board.SHORT[p];
        float centerIn = Board.TYPE[p] == Board.PROP ? -0.12f : (corner ? -0.18f : -0.2f);
        float tcx = gx + ix * centerIn, tcy = gy + iy * centerIn;
        int ink = 0xFF3A322B;
        if (corner) {
            paintLabel(c, name, tcx, tcy, ex, ey, ix, iy, 0.92f, 0.17f, ink, 0);
        } else if (Board.TYPE[p] == Board.PROP) {
            boolean showPrice = game != null && game.owner[p] < 0;
            paintLabel(c, name, tcx, tcy, ex, ey, ix, iy, 0.82f, 0.15f, ink, showPrice ? 0.1f : 0);
            if (showPrice) {
                paintLabel(c, "R$ " + game.buyPrice(p), tcx, tcy, ex, ey, ix, iy, 0.7f, 0.12f, 0xFF2E7D32, -0.13f);
            }
        } else {
            paintLabel(c, name, gx - ix * 0.3f, gy - iy * 0.3f, ex, ey, ix, iy, 0.84f, 0.13f, ink, 0);
        }
        // ícone em pé nas casas especiais
        String icon = tileIcon(p);
        if (icon != null) {
            float icx = gx + ix * (corner ? 0.15f : 0.12f), icy = gy + iy * (corner ? 0.15f : 0.12f);
            text.setTextSize(th * (corner ? 0.95f : 0.75f));
            c.drawText(icon, sx(icx, icy), sy(icx, icy) + th * 0.2f, text);
        }
        if (game != null && game.isClosed(p)) {
            text.setTextSize(th * 0.75f);
            c.drawText("🚧", sx(gx, gy), sy(gx, gy) + th * 0.1f, text);
        }
    }

    /**
     * Pinta um texto deitado no chão: centralizado em (cx,cy), correndo ao longo de (ux,uy)
     * e com "para cima" em ±(vx,vy). Escolhe os sentidos para ficar legível em qualquer giro.
     * offset desloca ao longo do "para cima".
     */
    private void paintLabel(Canvas c, String s, float cx, float cy, float ux, float uy, float vx, float vy,
                            float maxW, float maxH, int color, float offset) {
        // sentido de leitura da esquerda para a direita na tela
        float sdx = dirX(ux, uy), sdy = dirY(ux, uy);
        if (sdx < 0 || (Math.abs(sdx) < 1e-3f && sdy < 0)) { ux = -ux; uy = -uy; sdx = -sdx; sdy = -sdy; }
        // "para cima" coerente (sem espelhar)
        float upx = sdy, upy = -sdx;
        if (dirX(vx, vy) * upx + dirY(vx, vy) * upy < 0) { vx = -vx; vy = -vy; }
        float w = paintText.measureText(s);
        float capH = 72f;
        float k = Math.min(maxW / w, maxH / capH);
        float gw = w * k, gh = capH * k;
        // altura projetada em pixels: se muito pequena, nem desenha
        float pixH = (float) Math.hypot(dirX(vx, vy), dirY(vx, vy)) * gh;
        if (pixH < 3.5f) return;
        float bx = cx - ux * gw / 2f - vx * (gh / 2f - offset);
        float by = cy - uy * gw / 2f - vy * (gh / 2f - offset);
        src[0] = 0; src[1] = 0;
        src[2] = w; src[3] = 0;
        src[4] = 0; src[5] = -capH;
        dst[0] = sx(bx, by); dst[1] = sy(bx, by);
        dst[2] = sx(bx + ux * gw, by + uy * gw); dst[3] = sy(bx + ux * gw, by + uy * gw);
        dst[4] = sx(bx + vx * gh, by + vy * gh); dst[5] = sy(bx + vx * gh, by + vy * gh);
        matrix.setPolyToPoly(src, 0, dst, 0, 3);
        c.save();
        c.concat(matrix);
        paintText.setColor(color);
        c.drawText(s, 0, 0, paintText);
        c.restore();
    }

    // ---------------------------------------------------------------- objetos com profundidade

    private interface Drawer { void draw(Canvas c); }

    private static class Obj implements Comparable<Obj> {
        final float depth; final Drawer d;
        Obj(float depth, Drawer d) { this.depth = depth; this.d = d; }
        @Override public int compareTo(Obj o) { return Float.compare(depth, o.depth); }
    }

    private void drawObjects(Canvas c, long now) {
        ArrayList<Obj> objs = new ArrayList<>();
        for (float[] t : TREES) {
            final float tx = t[0], ty = t[1];
            final int kind = (int) t[2];
            objs.add(new Obj(depth(tx, ty), cv -> tree(cv, tx, ty, kind)));
        }
        for (float[] l : LAMPS) {
            final float lx = l[0], ly = l[1];
            objs.add(new Obj(depth(lx, ly), cv -> lamp(cv, lx, ly)));
        }
        objs.add(new Obj(depth(5, 5), cv -> fountain(cv, now)));
        objs.add(new Obj(depth(7.3f, 2.6f), cv -> sign(cv, 7.3f, 2.6f)));
        objs.add(new Obj(depth(3.0f, 7.0f) + 0.5f, cv -> duck(cv, 2.2f, 7.6f, now)));

        if (game != null) {
            for (int p = 0; p < Board.SIZE; p++) {
                final int pos = p;
                final float gx = GRID[p][0], gy = GRID[p][1];
                final float ix = INWARD[p][0], iy = INWARD[p][1];
                final float ex = -iy, ey = ix;
                int hs = game.houses[p];
                if (Board.TYPE[p] == Board.PROP && hs > 0) {
                    final float bx = gx + ix * 0.32f, by = gy + iy * 0.32f;
                    if (hs == 5) {
                        objs.add(new Obj(depth(bx, by), cv -> hotel(cv, bx, by)));
                    } else {
                        for (int i = 0; i < hs; i++) {
                            float t = (i - (hs - 1) / 2f) * 0.23f;
                            final float hx = bx + ex * t, hy = by + ey * t;
                            objs.add(new Obj(depth(hx, hy), cv -> house(cv, hx, hy)));
                        }
                    }
                }
                if (game.owner[p] >= 0) {
                    final int col = game.players[game.owner[p]].color;
                    final float fx = gx - ix * 0.32f + ex * 0.32f, fy = gy - iy * 0.32f + ey * 0.32f;
                    objs.add(new Obj(depth(fx, fy), cv -> flag(cv, fx, fy, col)));
                }
                if (game.eventId == Rules.E_FESTA && game.eventArg == pos) {
                    objs.add(new Obj(depth(gx, gy) + 0.9f, cv -> {
                        text.setTextSize(th() * 0.9f);
                        cv.drawText("🎉", sx(gx, gy), sy(gx, gy) - th() * 0.9f, text);
                    }));
                }
            }
            for (int i = 0; i < game.players.length; i++) {
                if (game.players[i].bankrupt) continue;
                final float[] pp = pawnPos(i);
                final int idx = i;
                objs.add(new Obj(depth(pp[0], pp[1]) + 0.3f, cv -> drawPawn(cv, idx, pp, now)));
            }
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
        float[][] off = {{0, 0}, {-0.18f, 0.12f}, {0.12f, -0.18f}, {0.18f, 0.18f}, {-0.2f, -0.14f}, {0.05f, 0.26f}};
        float ox = 0, oy = 0;
        if (count > 1) { ox = off[(k + 1) % off.length][0]; oy = off[(k + 1) % off.length][1]; }
        if (pos == Board.JAIL_POS && pl.inJail) { ox = 0.15f + (k % 2) * 0.12f; oy = -0.2f + (k / 2) * 0.12f; }
        return new float[]{GRID[pos][0] + ox, GRID[pos][1] + oy, 0};
    }

    private void drawPawn(Canvas c, int i, float[] pp, long now) {
        Player pl = game.players[i];
        float x = sx(pp[0], pp[1]), y = sy(pp[0], pp[1]);
        float size = tw() * 0.66f;
        float hop = pp[2] * th();
        if (i == game.current && game.phase != Game.PH_OVER && stepIdx < 0) {
            float pulse = (float) (0.5 + 0.5 * Math.sin(now / 220.0));
            aux.setStyle(Paint.Style.STROKE);
            aux.setStrokeWidth(tw() * 0.035f);
            aux.setColor((((int) (110 + 145 * pulse)) << 24) | (pl.color & 0xFFFFFF));
            groundCircle(pp[0], pp[1], 0.36f + 0.05f * pulse, 0, r);
            c.drawOval(r, aux);
            aux.setStyle(Paint.Style.FILL);
        }
        Pawns.draw(c, pl.pawn, pl.color, x, y - hop, size);
        if (pl.inJail && pl.pos == Board.JAIL_POS && override == null) {
            aux.setColor(0xDD37474F);
            aux.setStrokeWidth(size * 0.05f);
            for (int k = -2; k <= 2; k++) {
                float bx = x + k * size * 0.12f;
                c.drawLine(bx, y - size * 1.0f, bx, y, aux);
            }
            c.drawLine(x - size * 0.3f, y - size * 1.0f, x + size * 0.3f, y - size * 1.0f, aux);
        }
    }

    private void house(Canvas c, float gx, float gy) {
        float th = th();
        float hw = 0.1f, hgt = th * 0.3f;
        stroke.setStrokeWidth(Math.max(1f, tw() * 0.012f));
        prism(c, gx - hw, gy - hw, gx + hw, gy + hw, 0, hgt, 0xFF66BB6A, true);
        // telhado (pirâmide): faces viradas para a câmera
        float apx = sx(gx, gy), apy = sy(gx, gy) - hgt - th * 0.3f;
        float[][] g = {{gx - hw, gy - hw}, {gx + hw, gy - hw}, {gx + hw, gy + hw}, {gx - hw, gy + hw}};
        for (int i = 0; i < 4; i++) {
            float[] a = g[i], b = g[(i + 1) % 4];
            float nx = (a[0] + b[0]) / 2f - gx, ny = (a[1] + b[1]) / 2f - gy;
            float rnx = nx * cosA - ny * sinA, rny = nx * sinA + ny * cosA;
            if (rnx + rny <= 0) continue;
            float f = rnx - rny > 0 ? 0.75f : 1f;
            poly(c, Pawns.darker(0xFFE53935, f), true, sx(a[0], a[1]), sy(a[0], a[1]) - hgt,
                    sx(b[0], b[1]), sy(b[0], b[1]) - hgt, apx, apy);
        }
    }

    private void hotel(Canvas c, float gx, float gy) {
        float th = th();
        float hw = 0.2f, hgt = th * 1.3f;
        stroke.setStrokeWidth(Math.max(1f, tw() * 0.014f));
        prism(c, gx - hw, gy - hw, gx + hw, gy + hw, 0, hgt, 0xFFE53935, true);
        prism(c, gx - hw * 1.08f, gy - hw * 1.08f, gx + hw * 1.08f, gy + hw * 1.08f, hgt, hgt + th * 0.1f, 0xFF8E24AA, true);
        // janelas nas faces visíveis
        fill.setColor(0xFFFFF59D);
        float[][] g = {{gx - hw, gy - hw}, {gx + hw, gy - hw}, {gx + hw, gy + hw}, {gx - hw, gy + hw}};
        for (int i = 0; i < 4; i++) {
            float[] a = g[i], b = g[(i + 1) % 4];
            float nx = (a[0] + b[0]) / 2f - gx, ny = (a[1] + b[1]) / 2f - gy;
            float rnx = nx * cosA - ny * sinA, rny = nx * sinA + ny * cosA;
            if (rnx + rny <= 0) continue;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 2; col++) {
                    float u = (col + 0.5f) / 2f;
                    float wx = a[0] + (b[0] - a[0]) * u, wy = a[1] + (b[1] - a[1]) * u;
                    float px = sx(wx, wy), py = sy(wx, wy) - hgt * (0.18f + row * 0.28f);
                    c.drawRect(px - tw() * 0.025f, py - th * 0.13f, px + tw() * 0.025f, py, fill);
                }
            }
        }
        text.setTextSize(th * 0.45f);
        c.drawText("⭐", sx(gx, gy), sy(gx, gy) - hgt - th * 0.15f, text);
    }

    private void flag(Canvas c, float gx, float gy, int col) {
        float th = th();
        float x = sx(gx, gy), y = sy(gx, gy);
        aux.setColor(0xFF5D4037);
        aux.setStrokeWidth(Math.max(2f, tw() * 0.022f));
        c.drawLine(x, y, x, y - th * 1.2f, aux);
        stroke.setStrokeWidth(Math.max(1f, tw() * 0.012f));
        path.reset();
        path.moveTo(x, y - th * 1.2f);
        path.quadTo(x + tw() * 0.12f, y - th * 1.3f, x + tw() * 0.24f, y - th * 1.12f);
        path.quadTo(x + tw() * 0.12f, y - th * 0.95f, x, y - th * 0.85f);
        path.close();
        fill.setColor(col);
        c.drawPath(path, fill);
        c.drawPath(path, stroke);
        fill.setColor(0xFFFFD54F);
        c.drawCircle(x, y - th * 1.22f, Math.max(1.5f, tw() * 0.025f), fill);
    }

    private void tree(Canvas c, float gx, float gy, int kind) {
        float th = th(), tw = tw();
        float x = sx(gx, gy), y = sy(gx, gy);
        stroke.setStrokeWidth(Math.max(1f, tw * 0.012f));
        groundCircle(gx, gy, 0.32f, 0, r);
        fill.setColor(0x33000000);
        c.drawOval(r, fill);
        if (kind == 2) { // arbusto
            float rad = tw * 0.13f;
            for (float[] o : new float[][]{{-0.7f, 0.1f}, {0.7f, 0.1f}, {0f, -0.35f}}) {
                fill.setShader(new RadialGradient(x + o[0] * rad - rad * 0.3f, y - rad * 0.6f + o[1] * rad - rad * 0.3f, rad * 1.4f,
                        0xFF9CCC65, 0xFF388E3C, Shader.TileMode.CLAMP));
                c.drawCircle(x + o[0] * rad, y - rad * 0.6f + o[1] * rad, rad, fill);
                fill.setShader(null);
                c.drawCircle(x + o[0] * rad, y - rad * 0.6f + o[1] * rad, rad, stroke);
            }
            fill.setColor(0xFFFF8A80);
            c.drawCircle(x - rad * 0.4f, y - rad * 0.9f, rad * 0.16f, fill);
            c.drawCircle(x + rad * 0.6f, y - rad * 0.5f, rad * 0.16f, fill);
            return;
        }
        // tronco
        fill.setColor(0xFF795548);
        r.set(x - tw * 0.035f, y - th * 0.75f, x + tw * 0.035f, y);
        c.drawRect(r, fill);
        c.drawRect(r, stroke);
        if (kind == 1) { // pinheiro
            for (int k = 0; k < 3; k++) {
                float base = y - th * (0.55f + k * 0.42f), wid = tw * (0.26f - k * 0.06f), hgt = th * 0.8f;
                path.reset();
                path.moveTo(x - wid, base);
                path.lineTo(x, base - hgt);
                path.lineTo(x + wid, base);
                path.close();
                fill.setShader(new LinearGradient(x - wid, 0, x + wid, 0, 0xFF66BB6A, 0xFF1B5E20, Shader.TileMode.CLAMP));
                c.drawPath(path, fill);
                fill.setShader(null);
                c.drawPath(path, stroke);
            }
            return;
        }
        // árvore redonda
        float rad = tw * 0.22f, cy = y - th * 1.15f;
        fill.setShader(new RadialGradient(x - rad * 0.4f, cy - rad * 0.4f, rad * 1.6f, 0xFFAED581, 0xFF2E7D32, Shader.TileMode.CLAMP));
        c.drawCircle(x - rad * 0.55f, cy + rad * 0.25f, rad * 0.7f, fill);
        c.drawCircle(x + rad * 0.55f, cy + rad * 0.25f, rad * 0.7f, fill);
        c.drawCircle(x, cy - rad * 0.2f, rad, fill);
        fill.setShader(null);
        c.drawCircle(x, cy - rad * 0.2f, rad, stroke);
        fill.setColor(0xFFE53935);
        c.drawCircle(x - rad * 0.35f, cy + rad * 0.1f, rad * 0.11f, fill);
        c.drawCircle(x + rad * 0.4f, cy - rad * 0.3f, rad * 0.11f, fill);
        c.drawCircle(x + rad * 0.05f, cy + rad * 0.4f, rad * 0.11f, fill);
        fill.setColor(0x66FFFFFF);
        c.drawCircle(x - rad * 0.4f, cy - rad * 0.55f, rad * 0.2f, fill);
    }

    private void lamp(Canvas c, float gx, float gy) {
        float th = th(), tw = tw();
        float x = sx(gx, gy), y = sy(gx, gy);
        aux.setColor(0xFF37474F);
        aux.setStrokeWidth(Math.max(1.5f, tw * 0.02f));
        c.drawLine(x, y, x, y - th * 1.5f, aux);
        fill.setColor(0x55FFF59D);
        c.drawCircle(x, y - th * 1.55f, tw * 0.12f, fill);
        fill.setColor(0xFFFFF176);
        c.drawCircle(x, y - th * 1.55f, tw * 0.05f, fill);
        stroke.setStrokeWidth(Math.max(1f, tw * 0.01f));
        c.drawCircle(x, y - th * 1.55f, tw * 0.05f, stroke);
    }

    private void fountain(Canvas c, long now) {
        float th = th();
        stroke.setStrokeWidth(Math.max(1.1f, tw() * 0.014f));
        cylinder(c, 5, 5, 1.0f, 0, th * 0.22f, 0xFFB0BEC5, 0xFFCFD8DC);
        // água da base
        groundCircle(5, 5, 0.86f, th * 0.22f, r);
        fill.setShader(new RadialGradient(r.centerX(), r.centerY(), r.width() / 2f, 0xFF81D4FA, 0xFF039BE5, Shader.TileMode.CLAMP));
        c.drawOval(r, fill);
        fill.setShader(null);
        // ondinhas
        float wave = (now % 1600) / 1600f;
        aux.setStyle(Paint.Style.STROKE);
        aux.setStrokeWidth(Math.max(1f, tw() * 0.01f));
        aux.setColor(((int) ((1 - wave) * 160)) << 24 | 0xFFFFFF);
        groundCircle(5, 5, 0.3f + wave * 0.5f, th * 0.22f, r);
        c.drawOval(r, aux);
        aux.setStyle(Paint.Style.FILL);
        // coluna e taça
        cylinder(c, 5, 5, 0.14f, th * 0.22f, th * 0.9f, 0xFFB0BEC5, 0xFFCFD8DC);
        cylinder(c, 5, 5, 0.42f, th * 0.9f, th * 1.02f, 0xFFB0BEC5, 0xFF4FC3F7);
        cylinder(c, 5, 5, 0.06f, th * 1.02f, th * 1.35f, 0xFFB0BEC5, 0xFFCFD8DC);
        // jatos de água
        float x = sx(5, 5), y = sy(5, 5);
        fill.setColor(0xCCB3E5FC);
        for (int k = 0; k < 8; k++) {
            float t = ((now / 900f) + k / 8f) % 1f;
            double ang = k * Math.PI / 4 + angle * Math.PI / 180;
            float dx = (float) Math.cos(ang) * tw() * 0.28f * t;
            float dy = (float) Math.sin(ang) * th * 0.28f * t;
            float hgt = th * 1.35f + th * 0.6f * (float) Math.sin(t * Math.PI) - th * 0.45f * t;
            c.drawCircle(x + dx, y + dy - hgt, Math.max(1.5f, tw() * 0.025f), fill);
        }
    }

    private void sign(Canvas c, float gx, float gy) {
        float th = th(), tw = tw();
        float x = sx(gx, gy), y = sy(gx, gy);
        float w = tw * 1.1f, h = th * 0.75f, top = y - th * 1.55f;
        aux.setColor(0xFF6D4C41);
        aux.setStrokeWidth(Math.max(2f, tw * 0.035f));
        c.drawLine(x - w * 0.35f, y, x - w * 0.35f, top + h, aux);
        c.drawLine(x + w * 0.35f, y, x + w * 0.35f, top + h, aux);
        r.set(x - w / 2f, top, x + w / 2f, top + h);
        fill.setColor(0xFF2E7D32);
        c.drawRoundRect(r, h * 0.2f, h * 0.2f, fill);
        stroke.setStrokeWidth(Math.max(1.5f, tw * 0.02f));
        c.drawRoundRect(r, h * 0.2f, h * 0.2f, stroke);
        float ts = h * 0.55f;
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setTextSize(ts);
        textStroke.setTypeface(text.getTypeface());
        textStroke.setTextSize(ts);
        textStroke.setStrokeWidth(ts * 0.2f);
        textStroke.setColor(0xFF1B5E20);
        c.drawText("MAGNATA", x, top + h * 0.7f, textStroke);
        text.setColor(0xFFFFEB3B);
        c.drawText("MAGNATA", x, top + h * 0.7f, text);
        textStroke.setColor(0xFFFFFFFF);
        text.setTypeface(Typeface.DEFAULT);
        textStroke.setTypeface(Typeface.DEFAULT);
    }

    private void duck(Canvas c, float gx, float gy, long now) {
        float t = (now % 12000) / 12000f;
        double a = t * Math.PI * 2;
        float dgx = 2.55f + (float) Math.cos(a) * 0.55f, dgy = 7.45f + (float) Math.sin(a) * 0.55f;
        float x = sx(dgx, dgy), y = sy(dgx, dgy) - th() * 0.03f;
        float s = tw() * 0.12f;
        boolean faceRight = Math.cos(a + Math.PI / 2) * cosA - Math.sin(a + Math.PI / 2) * sinA > 0;
        float dir = faceRight ? 1 : -1;
        fill.setColor(0xFFFFEB3B);
        r.set(x - s, y - s * 0.6f, x + s, y + s * 0.2f);
        c.drawOval(r, fill);
        stroke.setStrokeWidth(Math.max(1f, tw() * 0.008f));
        c.drawOval(r, stroke);
        c.drawCircle(x + dir * s * 0.7f, y - s * 0.8f, s * 0.42f, fill);
        c.drawCircle(x + dir * s * 0.7f, y - s * 0.8f, s * 0.42f, stroke);
        fill.setColor(0xFFFF9800);
        c.drawCircle(x + dir * s * 1.12f, y - s * 0.75f, s * 0.17f, fill);
        fill.setColor(0xFF222222);
        c.drawCircle(x + dir * s * 0.82f, y - s * 0.9f, s * 0.07f, fill);
    }

    // ---------------------------------------------------------------- textos flutuantes e emojis

    private void drawFloaters(Canvas c, long now) {
        if (game == null) { floaters.clear(); return; }
        for (int i = floaters.size() - 1; i >= 0; i--) {
            Floater f = floaters.get(i);
            long el = now - f.start;
            long dur = f.emoji ? 2400 : 1800;
            if (el > dur || f.who < 0 || f.who >= game.players.length) { floaters.remove(i); continue; }
            float[] pp = pawnPos(f.who);
            float x = sx(pp[0], pp[1]);
            float y = sy(pp[0], pp[1]) - tw() * 0.85f - el / (float) dur * tw() * 0.6f;
            float alpha = el < dur * 0.7f ? 1f : 1f - (el - dur * 0.7f) / (dur * 0.3f);
            if (f.emoji) {
                float sc = el < 200 ? el / 200f * 1.3f : el < 350 ? 1.3f - (el - 200) / 150f * 0.3f : 1f;
                float sz = Math.max(tw() * 0.6f, dp(36)) * sc;
                fill.setColor(((int) (alpha * 235) << 24) | 0xFFFFFF);
                r.set(x - sz * 0.62f, y - sz * 0.95f, x + sz * 0.62f, y + sz * 0.25f);
                c.drawRoundRect(r, sz * 0.4f, sz * 0.4f, fill);
                text.setTextSize(sz * 0.8f);
                text.setAlpha((int) (alpha * 255));
                c.drawText(f.text, x, y, text);
                text.setAlpha(255);
            } else {
                float ts = Math.max(dp(17), tw() * 0.24f);
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
        boolean rolling = false;
        if (stepIdx >= 0) {
            Step s = steps.get(stepIdx);
            long el = now - stepStart;
            if (s.type == ST_DICE) { drawDiceRoll(c, s, el, now); rolling = true; }
            else if (s.type == ST_ROULETTE) drawRoulette(c, s.a, el);
        }
        if (!rolling) drawLastRoll(c);
        drawBanner(c, now);
    }

    /** Últimos dados sempre visíveis no canto. */
    private void drawLastRoll(Canvas c) {
        if (game == null || game.rollSeq == 0) return;
        float size = dp(34);
        float x = dp(14) + size / 2f, y = getHeight() - insetBottom - dp(20);
        r.set(x - size * 0.7f, y - size * 1.05f, x + size * (game.d3 > 0 ? 3.3f : 2.15f), y + size * 0.75f);
        fill.setColor(0xCCFFFFFF);
        c.drawRoundRect(r, dp(12), dp(12), fill);
        stroke.setStrokeWidth(dp(1.5f));
        c.drawRoundRect(r, dp(12), dp(12), stroke);
        die(c, x, y, size, game.d1, (game.d1 % 6) + 1, ((game.d1 + 2) % 6) + 1, 0xFFFFFFFF);
        die(c, x + size * 1.3f, y, size, game.d2, (game.d2 % 6) + 1, ((game.d2 + 2) % 6) + 1, 0xFFFFFFFF);
        if (game.d3 > 0) die(c, x + size * 2.6f, y, size, game.d3, (game.d3 % 6) + 1, ((game.d3 + 2) % 6) + 1, 0xFFFFE082);
    }

    private void drawDiceRoll(Canvas c, Step s, long el, long now) {
        int n = s.c > 0 ? 3 : 2;
        int[] vals = {s.a, s.b, s.c};
        float size = Math.min(getWidth(), getHeight()) * 0.2f;
        boolean rolling = el < DICE_ROLL;
        if (rolling && now - lastFaceChange > 85) {
            lastFaceChange = now;
            for (int i = 0; i < diceFaces.length; i++) diceFaces[i] = 1 + rnd.nextInt(6);
        }
        float cy = viewCY();
        fill.setColor(0x44000000);
        c.drawRect(0, 0, getWidth(), getHeight(), fill);
        for (int i = 0; i < n; i++) {
            float targetX = getWidth() / 2f + (i - (n - 1) / 2f) * size * 1.35f;
            float t = Math.min(1f, el / (float) DICE_ROLL);
            float ease = 1 - (1 - t) * (1 - t);
            float startX = -size + i * size * 0.3f;
            float x = startX + (targetX - startX) * ease;
            float bounce = (float) Math.abs(Math.sin(t * Math.PI * 3.2 + i)) * size * 1.1f * (1 - t);
            float y = cy - bounce;
            float rot = rolling ? (1 - t) * 720f * (i % 2 == 0 ? 1 : -1) : 0f;
            int top = rolling ? diceFaces[i * 3] : vals[i];
            int left = rolling ? diceFaces[i * 3 + 1] : (vals[i] % 6) + 1;
            int right = rolling ? diceFaces[i * 3 + 2] : ((vals[i] + 2) % 6) + 1;
            float pop = 1f;
            if (!rolling) {
                long h = el - DICE_ROLL;
                pop = h < 160 ? 1f + 0.18f * (h / 160f) : h < 320 ? 1.18f - 0.18f * ((h - 160) / 160f) : 1f;
            }
            c.save();
            c.rotate(rot, x, y);
            die(c, x, y, size * pop, top, left, right, i == 2 ? 0xFFFFE082 : 0xFFFFFFFF);
            c.restore();
        }
        if (!rolling) {
            int sum = s.a + s.b + s.c;
            String t = (s.c > 0 ? s.a + " + " + s.b + " + " + s.c : s.a + " + " + s.b) + " = " + sum;
            float ts = dp(32);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(ts);
            textStroke.setTypeface(Typeface.DEFAULT_BOLD);
            textStroke.setTextSize(ts);
            textStroke.setStrokeWidth(ts * 0.25f);
            textStroke.setColor(0xFF3A322B);
            float ty = cy + size * 1.35f;
            c.drawText(t, getWidth() / 2f, ty, textStroke);
            text.setColor(0xFFFFFFFF);
            c.drawText(t, getWidth() / 2f, ty, text);
            if (s.a == s.b) {
                c.drawText("DUPLA! 🎉", getWidth() / 2f, ty + ts * 1.25f, textStroke);
                text.setColor(0xFFFFEB3B);
                c.drawText("DUPLA! 🎉", getWidth() / 2f, ty + ts * 1.25f, text);
            }
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
        if (right == top) right = (right % 6) + 1;
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
        fill.setColor(0xFF5D4037);
        c.drawCircle(cx, cy, R * 1.07f, fill);
        c.drawCircle(cx, cy, R * 1.07f, stroke);
        fill.setColor(0xFFFFD54F);
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            c.drawCircle(cx + (float) Math.cos(a) * R * 1.035f, cy + (float) Math.sin(a) * R * 1.035f, R * 0.022f, fill);
        }
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
        textStroke.setColor(0xFF3A322B);
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
        poly(c, 0xFFFFEB3B, true, cx - R * 0.09f, cy - R - R * 0.14f, cx + R * 0.09f, cy - R - R * 0.14f, cx, cy - R + R * 0.1f);
        if (el > 3300) {
            String res = Rules.ROULETTE[result];
            float ts = dp(28);
            text.setTextSize(ts);
            textStroke.setTextSize(ts);
            textStroke.setStrokeWidth(ts * 0.25f);
            textStroke.setColor(0xFF3A322B);
            c.drawText(res, cx, cy + R + ts * 1.5f, textStroke);
            text.setColor(0xFFFFEB3B);
            c.drawText(res, cx, cy + R + ts * 1.5f, text);
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
        long dur = 4500;
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
