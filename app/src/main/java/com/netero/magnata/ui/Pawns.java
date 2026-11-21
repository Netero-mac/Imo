package com.netero.magnata.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Desenha os 8 peões em estilo cartoon (cor sólida, contorno escuro e brilho). */
public final class Pawns {
    private Pawns() {}

    public static final int COUNT = 8;
    private static final int OUTLINE = 0xFF2A2A2A;

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

    /** Desenha o peão com a base em (cx, by) e altura s. */
    public static void draw(Canvas c, int type, int color, float cx, float by, float s) {
        line.setStrokeWidth(Math.max(1.5f, s * 0.055f));
        // sombra
        fill.setColor(0x44000000);
        r.set(cx - s * 0.36f, by - s * 0.08f, cx + s * 0.36f, by + s * 0.08f);
        c.drawOval(r, fill);

        switch (((type % COUNT) + COUNT) % COUNT) {
            case 0: pawn(c, color, cx, by, s); break;
            case 1: car(c, color, cx, by, s); break;
            case 2: hat(c, color, cx, by, s); break;
            case 3: rocket(c, color, cx, by, s); break;
            case 4: crown(c, color, cx, by, s); break;
            case 5: star(c, color, cx, by, s); break;
            case 6: boat(c, color, cx, by, s); break;
            default: gem(c, color, cx, by, s); break;
        }
    }

    private static void shape(Canvas c, int color) {
        fill.setColor(color);
        c.drawPath(path, fill);
        c.drawPath(path, line);
    }

    private static void shine(Canvas c, float x, float y, float rad) {
        fill.setColor(0x99FFFFFF);
        c.drawCircle(x, y, rad, fill);
    }

    private static void pawn(Canvas c, int col, float cx, float by, float s) {
        // base
        r.set(cx - s * 0.32f, by - s * 0.2f, cx + s * 0.32f, by);
        fill.setColor(darker(col, 0.8f));
        c.drawRoundRect(r, s * 0.1f, s * 0.1f, fill);
        c.drawRoundRect(r, s * 0.1f, s * 0.1f, line);
        // corpo
        path.reset();
        path.moveTo(cx - s * 0.22f, by - s * 0.18f);
        path.quadTo(cx - s * 0.1f, by - s * 0.5f, cx - s * 0.08f, by - s * 0.66f);
        path.lineTo(cx + s * 0.08f, by - s * 0.66f);
        path.quadTo(cx + s * 0.1f, by - s * 0.5f, cx + s * 0.22f, by - s * 0.18f);
        path.close();
        shape(c, col);
        // cabeça
        fill.setColor(col);
        c.drawCircle(cx, by - s * 0.78f, s * 0.18f, fill);
        c.drawCircle(cx, by - s * 0.78f, s * 0.18f, line);
        shine(c, cx - s * 0.06f, by - s * 0.84f, s * 0.05f);
    }

    private static void car(Canvas c, int col, float cx, float by, float s) {
        float w = s * 0.5f;
        // cabine
        path.reset();
        path.moveTo(cx - w * 0.55f, by - s * 0.42f);
        path.lineTo(cx - w * 0.35f, by - s * 0.7f);
        path.lineTo(cx + w * 0.3f, by - s * 0.7f);
        path.lineTo(cx + w * 0.55f, by - s * 0.42f);
        path.close();
        shape(c, lighter(col, 0.25f));
        fill.setColor(0xFFBBDEFB);
        r.set(cx - w * 0.38f, by - s * 0.64f, cx + w * 0.32f, by - s * 0.46f);
        c.drawRoundRect(r, s * 0.04f, s * 0.04f, fill);
        // corpo
        r.set(cx - w, by - s * 0.46f, cx + w, by - s * 0.16f);
        fill.setColor(col);
        c.drawRoundRect(r, s * 0.12f, s * 0.12f, fill);
        c.drawRoundRect(r, s * 0.12f, s * 0.12f, line);
        // rodas
        for (float k : new float[]{-0.55f, 0.55f}) {
            fill.setColor(0xFF333333);
            c.drawCircle(cx + w * k, by - s * 0.14f, s * 0.13f, fill);
            fill.setColor(0xFFBDBDBD);
            c.drawCircle(cx + w * k, by - s * 0.14f, s * 0.05f, fill);
        }
        shine(c, cx - w * 0.6f, by - s * 0.38f, s * 0.04f);
    }

    private static void hat(Canvas c, int col, float cx, float by, float s) {
        // aba
        r.set(cx - s * 0.4f, by - s * 0.22f, cx + s * 0.4f, by - s * 0.04f);
        fill.setColor(darker(col, 0.75f));
        c.drawOval(r, fill);
        c.drawOval(r, line);
        // copa
        path.reset();
        path.moveTo(cx - s * 0.24f, by - s * 0.14f);
        path.lineTo(cx - s * 0.22f, by - s * 0.82f);
        path.quadTo(cx, by - s * 0.9f, cx + s * 0.22f, by - s * 0.82f);
        path.lineTo(cx + s * 0.24f, by - s * 0.14f);
        path.quadTo(cx, by - s * 0.08f, cx - s * 0.24f, by - s * 0.14f);
        path.close();
        shape(c, col);
        // faixa
        fill.setColor(0xFF2A2A2A);
        r.set(cx - s * 0.235f, by - s * 0.34f, cx + s * 0.235f, by - s * 0.24f);
        c.drawRect(r, fill);
        shine(c, cx - s * 0.12f, by - s * 0.7f, s * 0.05f);
    }

    private static void rocket(Canvas c, int col, float cx, float by, float s) {
        // aletas
        path.reset();
        path.moveTo(cx - s * 0.14f, by - s * 0.42f);
        path.lineTo(cx - s * 0.32f, by - s * 0.08f);
        path.lineTo(cx - s * 0.12f, by - s * 0.16f);
        path.close();
        shape(c, darker(col, 0.75f));
        path.reset();
        path.moveTo(cx + s * 0.14f, by - s * 0.42f);
        path.lineTo(cx + s * 0.32f, by - s * 0.08f);
        path.lineTo(cx + s * 0.12f, by - s * 0.16f);
        path.close();
        shape(c, darker(col, 0.75f));
        // chama
        path.reset();
        path.moveTo(cx - s * 0.09f, by - s * 0.14f);
        path.lineTo(cx, by + s * 0.02f);
        path.lineTo(cx + s * 0.09f, by - s * 0.14f);
        path.close();
        fill.setColor(0xFFFFA726);
        c.drawPath(path, fill);
        // corpo
        path.reset();
        path.moveTo(cx, by - s * 0.98f);
        path.quadTo(cx + s * 0.24f, by - s * 0.6f, cx + s * 0.14f, by - s * 0.14f);
        path.lineTo(cx - s * 0.14f, by - s * 0.14f);
        path.quadTo(cx - s * 0.24f, by - s * 0.6f, cx, by - s * 0.98f);
        path.close();
        shape(c, col);
        // janela
        fill.setColor(0xFFE3F2FD);
        c.drawCircle(cx, by - s * 0.58f, s * 0.08f, fill);
        c.drawCircle(cx, by - s * 0.58f, s * 0.08f, line);
        shine(c, cx - s * 0.07f, by - s * 0.8f, s * 0.035f);
    }

    private static void crown(Canvas c, int col, float cx, float by, float s) {
        float w = s * 0.38f;
        path.reset();
        path.moveTo(cx - w, by - s * 0.12f);
        path.lineTo(cx - w, by - s * 0.66f);
        path.lineTo(cx - w * 0.5f, by - s * 0.4f);
        path.lineTo(cx, by - s * 0.8f);
        path.lineTo(cx + w * 0.5f, by - s * 0.4f);
        path.lineTo(cx + w, by - s * 0.66f);
        path.lineTo(cx + w, by - s * 0.12f);
        path.close();
        shape(c, col);
        r.set(cx - w, by - s * 0.24f, cx + w, by - s * 0.08f);
        fill.setColor(darker(col, 0.8f));
        c.drawRect(r, fill);
        c.drawRect(r, line);
        fill.setColor(0xFFFFFFFF);
        c.drawCircle(cx, by - s * 0.16f, s * 0.045f, fill);
        for (float k : new float[]{-1f, 0f, 1f}) {
            float px = cx + w * k, py = k == 0 ? by - s * 0.82f : by - s * 0.68f;
            fill.setColor(0xFFFFEB3B);
            c.drawCircle(px, py, s * 0.06f, fill);
            c.drawCircle(px, py, s * 0.06f, line);
        }
    }

    private static void star(Canvas c, int col, float cx, float by, float s) {
        r.set(cx - s * 0.18f, by - s * 0.14f, cx + s * 0.18f, by);
        fill.setColor(darker(col, 0.7f));
        c.drawRoundRect(r, s * 0.05f, s * 0.05f, fill);
        c.drawRoundRect(r, s * 0.05f, s * 0.05f, line);
        float ccy = by - s * 0.5f, ro = s * 0.4f, ri = s * 0.17f;
        path.reset();
        for (int i = 0; i < 10; i++) {
            double ang = -Math.PI / 2 + i * Math.PI / 5;
            float rad = i % 2 == 0 ? ro : ri;
            float px = cx + (float) Math.cos(ang) * rad, py = ccy + (float) Math.sin(ang) * rad;
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        path.close();
        shape(c, col);
        shine(c, cx - s * 0.08f, ccy - s * 0.12f, s * 0.05f);
    }

    private static void boat(Canvas c, int col, float cx, float by, float s) {
        // mastro
        line.setStrokeWidth(Math.max(1.5f, s * 0.05f));
        c.drawLine(cx, by - s * 0.24f, cx, by - s * 0.95f, line);
        // vela
        path.reset();
        path.moveTo(cx + s * 0.03f, by - s * 0.92f);
        path.lineTo(cx + s * 0.34f, by - s * 0.32f);
        path.lineTo(cx + s * 0.03f, by - s * 0.32f);
        path.close();
        shape(c, 0xFFFFFFFF);
        path.reset();
        path.moveTo(cx - s * 0.03f, by - s * 0.8f);
        path.lineTo(cx - s * 0.26f, by - s * 0.34f);
        path.lineTo(cx - s * 0.03f, by - s * 0.34f);
        path.close();
        shape(c, lighter(col, 0.5f));
        // casco
        path.reset();
        path.moveTo(cx - s * 0.42f, by - s * 0.3f);
        path.lineTo(cx + s * 0.42f, by - s * 0.3f);
        path.lineTo(cx + s * 0.26f, by - s * 0.04f);
        path.lineTo(cx - s * 0.26f, by - s * 0.04f);
        path.close();
        shape(c, col);
        shine(c, cx - s * 0.28f, by - s * 0.24f, s * 0.035f);
    }

    private static void gem(Canvas c, int col, float cx, float by, float s) {
        float top = by - s * 0.82f, mid = by - s * 0.58f, w = s * 0.38f;
        path.reset();
        path.moveTo(cx - w * 0.55f, top);
        path.lineTo(cx + w * 0.55f, top);
        path.lineTo(cx + w, mid);
        path.lineTo(cx, by - s * 0.04f);
        path.lineTo(cx - w, mid);
        path.close();
        shape(c, col);
        // facetas
        line.setStrokeWidth(Math.max(1f, s * 0.03f));
        c.drawLine(cx - w, mid, cx + w, mid, line);
        c.drawLine(cx - w * 0.3f, top, cx - w * 0.45f, mid, line);
        c.drawLine(cx + w * 0.3f, top, cx + w * 0.45f, mid, line);
        c.drawLine(cx - w * 0.45f, mid, cx, by - s * 0.04f, line);
        c.drawLine(cx + w * 0.45f, mid, cx, by - s * 0.04f, line);
        path.reset();
        path.moveTo(cx - w * 0.5f, top + s * 0.03f);
        path.lineTo(cx - w * 0.1f, top + s * 0.03f);
        path.lineTo(cx - w * 0.3f, mid - s * 0.03f);
        path.close();
        fill.setColor(0x88FFFFFF);
        c.drawPath(path, fill);
    }
}
