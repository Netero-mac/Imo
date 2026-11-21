package com.netero.magnata.ui;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Peões estilo cartoon: pedestal, sombreado com gradiente, contorno grosso,
 * brilho e carinha.
 */
public final class Pawns {
    private Pawns() {}

    public static final int COUNT = 8;
    private static final int OUTLINE = 0xFF2B2622;

    private static final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path path = new Path();
    private static final RectF r = new RectF();

    static {
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setColor(OUTLINE);
    }

    public static int darker(int c, float f) {
        int a = (c >>> 24) & 0xFF;
        int rr = (int) (((c >> 16) & 0xFF) * f);
        int gg = (int) (((c >> 8) & 0xFF) * f);
        int bb = (int) ((c & 0xFF) * f);
        return (a << 24) | (Math.min(255, rr) << 16) | (Math.min(255, gg) << 8) | Math.min(255, bb);
    }

    public static int lighter(int c, float f) {
        int a = (c >>> 24) & 0xFF;
        int rr = (c >> 16) & 0xFF, gg = (c >> 8) & 0xFF, bb = c & 0xFF;
        rr = (int) (rr + (255 - rr) * f);
        gg = (int) (gg + (255 - gg) * f);
        bb = (int) (bb + (255 - bb) * f);
        return (a << 24) | (rr << 16) | (gg << 8) | bb;
    }

    /** Gradiente horizontal (luz da esquerda) para dar volume. */
    private static void shade(int col, float x0, float x1) {
        fill.setShader(new LinearGradient(x0, 0, x1, 0,
                new int[]{lighter(col, 0.45f), col, darker(col, 0.62f)},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
    }

    private static void flat(int col) {
        fill.setShader(null);
        fill.setColor(col);
    }

    private static void shapeShaded(Canvas c, int col, float x0, float x1) {
        shade(col, x0, x1);
        c.drawPath(path, fill);
        fill.setShader(null);
        c.drawPath(path, line);
    }

    private static void eyes(Canvas c, float cx, float cy, float s) {
        float ex = s * 0.075f, er = s * 0.055f;
        for (int k = -1; k <= 1; k += 2) {
            flat(0xFFFFFFFF);
            r.set(cx + k * ex - er, cy - er * 1.25f, cx + k * ex + er, cy + er * 1.25f);
            c.drawOval(r, fill);
            line.setStrokeWidth(Math.max(1f, s * 0.018f));
            c.drawOval(r, line);
            flat(0xFF222222);
            c.drawCircle(cx + k * ex + er * 0.25f, cy + er * 0.2f, er * 0.58f, fill);
            flat(0xFFFFFFFF);
            c.drawCircle(cx + k * ex + er * 0.05f, cy - er * 0.2f, er * 0.2f, fill);
        }
    }

    private static void smile(Canvas c, float cx, float cy, float s) {
        line.setStrokeWidth(Math.max(1f, s * 0.025f));
        r.set(cx - s * 0.06f, cy - s * 0.05f, cx + s * 0.06f, cy + s * 0.04f);
        c.drawArc(r, 20, 140, false, line);
    }

    private static void gloss(Canvas c, float x, float y, float w, float h) {
        flat(0x99FFFFFF);
        r.set(x - w, y - h, x + w, y + h);
        c.drawOval(r, fill);
    }

    /** Desenha o peão com a base em (cx, by) e altura s. */
    public static void draw(Canvas c, int type, int color, float cx, float by, float s) {
        float lw = Math.max(1.6f, s * 0.045f);
        line.setStrokeWidth(lw);
        // sombra no chão
        flat(0x40000000);
        r.set(cx - s * 0.42f, by - s * 0.09f, cx + s * 0.42f, by + s * 0.1f);
        c.drawOval(r, fill);
        // pedestal
        float pr = s * 0.34f, pt = s * 0.08f, pe = pr * 0.42f;
        int base = darker(color, 0.55f);
        flat(base);
        r.set(cx - pr, by - pe, cx + pr, by + pe);
        c.drawOval(r, fill);
        c.drawRect(cx - pr, by - pt, cx + pr, by, fill);
        line.setStrokeWidth(lw);
        c.drawArc(r, 0, 180, false, line);
        c.drawLine(cx - pr, by - pt, cx - pr, by, line);
        c.drawLine(cx + pr, by - pt, cx + pr, by, line);
        shade(color, cx - pr, cx + pr);
        r.set(cx - pr, by - pt - pe, cx + pr, by - pt + pe);
        c.drawOval(r, fill);
        fill.setShader(null);
        c.drawOval(r, line);
        flat(0x55FFFFFF);
        r.set(cx - pr * 0.75f, by - pt - pe * 0.7f, cx + pr * 0.2f, by - pt - pe * 0.1f);
        c.drawOval(r, fill);

        float b2 = by - pt - pe * 0.3f;
        float s2 = s * 0.92f;
        line.setStrokeWidth(lw);
        switch (((type % COUNT) + COUNT) % COUNT) {
            case 0: pawn(c, color, cx, b2, s2); break;
            case 1: car(c, color, cx, b2, s2); break;
            case 2: hat(c, color, cx, b2, s2); break;
            case 3: rocket(c, color, cx, b2, s2); break;
            case 4: crown(c, color, cx, b2, s2); break;
            case 5: star(c, color, cx, b2, s2); break;
            case 6: boat(c, color, cx, b2, s2); break;
            default: gem(c, color, cx, b2, s2); break;
        }
        fill.setShader(null);
    }

    private static void pawn(Canvas c, int col, float cx, float by, float s) {
        // corpo em sino
        path.reset();
        path.moveTo(cx - s * 0.26f, by);
        path.cubicTo(cx - s * 0.24f, by - s * 0.22f, cx - s * 0.08f, by - s * 0.38f, cx - s * 0.09f, by - s * 0.56f);
        path.lineTo(cx + s * 0.09f, by - s * 0.56f);
        path.cubicTo(cx + s * 0.08f, by - s * 0.38f, cx + s * 0.24f, by - s * 0.22f, cx + s * 0.26f, by);
        path.close();
        shapeShaded(c, col, cx - s * 0.26f, cx + s * 0.26f);
        // colarinho
        r.set(cx - s * 0.17f, by - s * 0.62f, cx + s * 0.17f, by - s * 0.52f);
        shade(darker(col, 0.85f), r.left, r.right);
        c.drawOval(r, fill);
        fill.setShader(null);
        c.drawOval(r, line);
        // cabeça
        float hy = by - s * 0.78f, hr = s * 0.2f;
        fill.setShader(new RadialGradient(cx - hr * 0.35f, hy - hr * 0.35f, hr * 1.4f,
                lighter(col, 0.5f), darker(col, 0.7f), Shader.TileMode.CLAMP));
        c.drawCircle(cx, hy, hr, fill);
        fill.setShader(null);
        c.drawCircle(cx, hy, hr, line);
        eyes(c, cx, hy + s * 0.01f, s);
        smile(c, cx, hy + s * 0.08f, s);
        gloss(c, cx - hr * 0.45f, hy - hr * 0.5f, hr * 0.22f, hr * 0.14f);
    }

    private static void car(Canvas c, int col, float cx, float by, float s) {
        float w = s * 0.46f;
        // cabine
        path.reset();
        path.moveTo(cx - w * 0.6f, by - s * 0.36f);
        path.quadTo(cx - w * 0.45f, by - s * 0.66f, cx - w * 0.1f, by - s * 0.66f);
        path.lineTo(cx + w * 0.25f, by - s * 0.66f);
        path.quadTo(cx + w * 0.5f, by - s * 0.66f, cx + w * 0.62f, by - s * 0.36f);
        path.close();
        shapeShaded(c, lighter(col, 0.15f), cx - w, cx + w);
        // janelas
        flat(0xFFB3E5FC);
        path.reset();
        path.moveTo(cx - w * 0.48f, by - s * 0.4f);
        path.quadTo(cx - w * 0.38f, by - s * 0.6f, cx - w * 0.12f, by - s * 0.6f);
        path.lineTo(cx - w * 0.04f, by - s * 0.6f);
        path.lineTo(cx - w * 0.04f, by - s * 0.4f);
        path.close();
        c.drawPath(path, fill);
        path.reset();
        path.moveTo(cx + w * 0.04f, by - s * 0.4f);
        path.lineTo(cx + w * 0.04f, by - s * 0.6f);
        path.lineTo(cx + w * 0.25f, by - s * 0.6f);
        path.quadTo(cx + w * 0.42f, by - s * 0.6f, cx + w * 0.5f, by - s * 0.4f);
        path.close();
        c.drawPath(path, fill);
        // corpo
        path.reset();
        r.set(cx - w, by - s * 0.42f, cx + w, by - s * 0.1f);
        path.addRoundRect(r, s * 0.13f, s * 0.13f, Path.Direction.CW);
        shapeShaded(c, col, cx - w, cx + w);
        // faróis = olhos
        eyes(c, cx + w * 0.55f, by - s * 0.27f, s * 0.75f);
        // rodas
        for (float k : new float[]{-0.55f, 0.55f}) {
            flat(0xFF2E2E2E);
            c.drawCircle(cx + w * k, by - s * 0.09f, s * 0.13f, fill);
            c.drawCircle(cx + w * k, by - s * 0.09f, s * 0.13f, line);
            flat(0xFFCFD8DC);
            c.drawCircle(cx + w * k, by - s * 0.09f, s * 0.055f, fill);
        }
        gloss(c, cx - w * 0.55f, by - s * 0.36f, s * 0.08f, s * 0.03f);
    }

    private static void hat(Canvas c, int col, float cx, float by, float s) {
        // aba
        r.set(cx - s * 0.4f, by - s * 0.2f, cx + s * 0.4f, by - s * 0.02f);
        shade(darker(col, 0.7f), r.left, r.right);
        c.drawOval(r, fill);
        fill.setShader(null);
        c.drawOval(r, line);
        // copa
        path.reset();
        path.moveTo(cx - s * 0.24f, by - s * 0.12f);
        path.lineTo(cx - s * 0.21f, by - s * 0.84f);
        path.quadTo(cx, by - s * 0.92f, cx + s * 0.21f, by - s * 0.84f);
        path.lineTo(cx + s * 0.24f, by - s * 0.12f);
        path.quadTo(cx, by - s * 0.05f, cx - s * 0.24f, by - s * 0.12f);
        path.close();
        shapeShaded(c, col, cx - s * 0.24f, cx + s * 0.24f);
        // topo
        r.set(cx - s * 0.21f, by - s * 0.9f, cx + s * 0.21f, by - s * 0.8f);
        flat(darker(col, 0.8f));
        c.drawOval(r, fill);
        c.drawOval(r, line);
        // faixa dourada
        flat(0xFFFFC107);
        path.reset();
        path.moveTo(cx - s * 0.232f, by - s * 0.36f);
        path.quadTo(cx, by - s * 0.3f, cx + s * 0.232f, by - s * 0.36f);
        path.lineTo(cx + s * 0.236f, by - s * 0.24f);
        path.quadTo(cx, by - s * 0.18f, cx - s * 0.236f, by - s * 0.24f);
        path.close();
        c.drawPath(path, fill);
        c.drawPath(path, line);
        eyes(c, cx, by - s * 0.55f, s);
        gloss(c, cx - s * 0.13f, by - s * 0.72f, s * 0.03f, s * 0.07f);
    }

    private static void rocket(Canvas c, int col, float cx, float by, float s) {
        // chama
        path.reset();
        path.moveTo(cx - s * 0.1f, by - s * 0.12f);
        path.quadTo(cx - s * 0.06f, by + s * 0.06f, cx, by + s * 0.1f);
        path.quadTo(cx + s * 0.06f, by + s * 0.06f, cx + s * 0.1f, by - s * 0.12f);
        path.close();
        flat(0xFFFF9800);
        c.drawPath(path, fill);
        flat(0xFFFFEB3B);
        c.drawCircle(cx, by - s * 0.06f, s * 0.05f, fill);
        // aletas
        for (int k = -1; k <= 1; k += 2) {
            path.reset();
            path.moveTo(cx + k * s * 0.13f, by - s * 0.46f);
            path.quadTo(cx + k * s * 0.34f, by - s * 0.3f, cx + k * s * 0.3f, by - s * 0.05f);
            path.lineTo(cx + k * s * 0.12f, by - s * 0.15f);
            path.close();
            flat(0xFFE53935);
            c.drawPath(path, fill);
            c.drawPath(path, line);
        }
        // corpo
        path.reset();
        path.moveTo(cx, by - s * 1.0f);
        path.cubicTo(cx + s * 0.3f, by - s * 0.78f, cx + s * 0.2f, by - s * 0.3f, cx + s * 0.14f, by - s * 0.12f);
        path.lineTo(cx - s * 0.14f, by - s * 0.12f);
        path.cubicTo(cx - s * 0.2f, by - s * 0.3f, cx - s * 0.3f, by - s * 0.78f, cx, by - s * 1.0f);
        path.close();
        shapeShaded(c, col, cx - s * 0.22f, cx + s * 0.22f);
        // ponta
        path.reset();
        path.moveTo(cx, by - s * 1.0f);
        path.quadTo(cx + s * 0.16f, by - s * 0.9f, cx + s * 0.19f, by - s * 0.8f);
        path.quadTo(cx, by - s * 0.76f, cx - s * 0.19f, by - s * 0.8f);
        path.quadTo(cx - s * 0.16f, by - s * 0.9f, cx, by - s * 1.0f);
        path.close();
        flat(0xFFE53935);
        c.drawPath(path, fill);
        c.drawPath(path, line);
        // janela com olhos
        flat(0xFFB3E5FC);
        c.drawCircle(cx, by - s * 0.52f, s * 0.13f, fill);
        line.setStrokeWidth(Math.max(1.6f, s * 0.05f));
        flat(0xFFB0BEC5);
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(s * 0.04f);
        ring.setColor(0xFFB0BEC5);
        c.drawCircle(cx, by - s * 0.52f, s * 0.13f, ring);
        line.setStrokeWidth(Math.max(1.2f, s * 0.03f));
        c.drawCircle(cx, by - s * 0.52f, s * 0.15f, line);
        eyes(c, cx, by - s * 0.52f, s * 0.8f);
        gloss(c, cx - s * 0.1f, by - s * 0.75f, s * 0.025f, s * 0.07f);
    }

    private static void crown(Canvas c, int col, float cx, float by, float s) {
        float w = s * 0.36f;
        int gold = 0xFFFFC107;
        path.reset();
        path.moveTo(cx - w, by - s * 0.1f);
        path.lineTo(cx - w * 1.05f, by - s * 0.66f);
        path.lineTo(cx - w * 0.52f, by - s * 0.42f);
        path.lineTo(cx, by - s * 0.82f);
        path.lineTo(cx + w * 0.52f, by - s * 0.42f);
        path.lineTo(cx + w * 1.05f, by - s * 0.66f);
        path.lineTo(cx + w, by - s * 0.1f);
        path.quadTo(cx, by - s * 0.02f, cx - w, by - s * 0.1f);
        path.close();
        shapeShaded(c, gold, cx - w, cx + w);
        // faixa na cor do jogador
        path.reset();
        path.moveTo(cx - w * 1.0f, by - s * 0.28f);
        path.quadTo(cx, by - s * 0.2f, cx + w * 1.0f, by - s * 0.28f);
        path.lineTo(cx + w, by - s * 0.1f);
        path.quadTo(cx, by - s * 0.02f, cx - w, by - s * 0.1f);
        path.close();
        shapeShaded(c, col, cx - w, cx + w);
        // joias
        for (float[] p : new float[][]{{-1.05f, 0.66f}, {0f, 0.82f}, {1.05f, 0.66f}}) {
            float px = cx + w * p[0], py = by - s * p[1];
            flat(col);
            c.drawCircle(px, py, s * 0.065f, fill);
            c.drawCircle(px, py, s * 0.065f, line);
            flat(0xAAFFFFFF);
            c.drawCircle(px - s * 0.02f, py - s * 0.02f, s * 0.02f, fill);
        }
        eyes(c, cx, by - s * 0.42f, s);
        gloss(c, cx - w * 0.6f, by - s * 0.5f, s * 0.025f, s * 0.08f);
    }

    private static void star(Canvas c, int col, float cx, float by, float s) {
        float ccy = by - s * 0.48f, ro = s * 0.46f, ri = s * 0.21f;
        path.reset();
        for (int i = 0; i < 10; i++) {
            double ang = -Math.PI / 2 + i * Math.PI / 5;
            float rad = i % 2 == 0 ? ro : ri;
            float px = cx + (float) Math.cos(ang) * rad, py = ccy + (float) Math.sin(ang) * rad;
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        path.close();
        shapeShaded(c, col, cx - ro, cx + ro);
        eyes(c, cx, ccy + s * 0.01f, s);
        smile(c, cx, ccy + s * 0.09f, s);
        gloss(c, cx - s * 0.12f, ccy - s * 0.18f, s * 0.04f, s * 0.025f);
    }

    private static void boat(Canvas c, int col, float cx, float by, float s) {
        // mastro
        line.setStrokeWidth(Math.max(1.6f, s * 0.05f));
        Paint mast = new Paint(Paint.ANTI_ALIAS_FLAG);
        mast.setColor(0xFF6D4C41);
        mast.setStrokeWidth(s * 0.045f);
        c.drawLine(cx, by - s * 0.26f, cx, by - s * 1.0f, mast);
        // velas
        path.reset();
        path.moveTo(cx + s * 0.03f, by - s * 0.97f);
        path.quadTo(cx + s * 0.38f, by - s * 0.62f, cx + s * 0.36f, by - s * 0.34f);
        path.lineTo(cx + s * 0.03f, by - s * 0.34f);
        path.close();
        shapeShaded(c, 0xFFFFFFFF, cx, cx + s * 0.36f);
        path.reset();
        path.moveTo(cx - s * 0.03f, by - s * 0.84f);
        path.quadTo(cx - s * 0.3f, by - s * 0.55f, cx - s * 0.28f, by - s * 0.36f);
        path.lineTo(cx - s * 0.03f, by - s * 0.36f);
        path.close();
        shapeShaded(c, lighter(col, 0.45f), cx - s * 0.28f, cx);
        // bandeira
        path.reset();
        path.moveTo(cx, by - s * 1.0f);
        path.lineTo(cx - s * 0.16f, by - s * 0.95f);
        path.lineTo(cx, by - s * 0.9f);
        path.close();
        flat(col);
        c.drawPath(path, fill);
        // casco
        path.reset();
        path.moveTo(cx - s * 0.44f, by - s * 0.32f);
        path.lineTo(cx + s * 0.44f, by - s * 0.32f);
        path.quadTo(cx + s * 0.36f, by - s * 0.02f, cx + s * 0.22f, by - s * 0.02f);
        path.lineTo(cx - s * 0.22f, by - s * 0.02f);
        path.quadTo(cx - s * 0.36f, by - s * 0.02f, cx - s * 0.44f, by - s * 0.32f);
        path.close();
        shapeShaded(c, col, cx - s * 0.44f, cx + s * 0.44f);
        flat(0xFFFFFFFF);
        c.drawRect(cx - s * 0.38f, by - s * 0.26f, cx + s * 0.38f, by - s * 0.22f, fill);
        eyes(c, cx, by - s * 0.14f, s * 0.8f);
    }

    private static void gem(Canvas c, int col, float cx, float by, float s) {
        float top = by - s * 0.84f, mid = by - s * 0.6f, w = s * 0.4f, bot = by - s * 0.03f;
        path.reset();
        path.moveTo(cx - w * 0.55f, top);
        path.lineTo(cx + w * 0.55f, top);
        path.lineTo(cx + w, mid);
        path.lineTo(cx, bot);
        path.lineTo(cx - w, mid);
        path.close();
        shapeShaded(c, col, cx - w, cx + w);
        // facetas claras/escuras
        path.reset();
        path.moveTo(cx - w * 0.55f, top);
        path.lineTo(cx - w * 0.2f, top);
        path.lineTo(cx - w * 0.45f, mid);
        path.lineTo(cx - w, mid);
        path.close();
        flat(0x66FFFFFF);
        c.drawPath(path, fill);
        path.reset();
        path.moveTo(cx + w * 0.45f, mid);
        path.lineTo(cx + w, mid);
        path.lineTo(cx, bot);
        path.close();
        flat(0x33000000);
        c.drawPath(path, fill);
        line.setStrokeWidth(Math.max(1f, s * 0.025f));
        c.drawLine(cx - w, mid, cx + w, mid, line);
        c.drawLine(cx - w * 0.2f, top, cx - w * 0.45f, mid, line);
        c.drawLine(cx + w * 0.2f, top, cx + w * 0.45f, mid, line);
        c.drawLine(cx - w * 0.45f, mid, cx, bot, line);
        c.drawLine(cx + w * 0.45f, mid, cx, bot, line);
        eyes(c, cx, by - s * 0.44f, s * 0.9f);
        // brilho
        flat(0xFFFFFFFF);
        path.reset();
        float sx = cx + w * 0.62f, sy = top - s * 0.02f, k = s * 0.07f;
        path.moveTo(sx, sy - k);
        path.lineTo(sx + k * 0.25f, sy - k * 0.25f);
        path.lineTo(sx + k, sy);
        path.lineTo(sx + k * 0.25f, sy + k * 0.25f);
        path.lineTo(sx, sy + k);
        path.lineTo(sx - k * 0.25f, sy + k * 0.25f);
        path.lineTo(sx - k, sy);
        path.lineTo(sx - k * 0.25f, sy - k * 0.25f);
        path.close();
        c.drawPath(path, fill);
    }
}
