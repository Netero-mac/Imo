package com.netero.magnata.ui;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Peças do jogo com aparência de miniaturas envernizadas: volume por gradiente
 * cilíndrico, reflexo especular, contorno fino no tom da peça e pedestal.
 */
public final class Pawns {
    private Pawns() {}

    public static final int COUNT = 8;

    private static final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path path = new Path();
    private static final RectF r = new RectF();
    private static float lw;

    static {
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeCap(Paint.Cap.ROUND);
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

    // ---------------------------------------------------------------- materiais

    /** Volume de cilindro: borda escura, reflexo à esquerda, sombra à direita. */
    private static void cyl(int col, float x0, float x1) {
        fill.setShader(new LinearGradient(x0, 0, x1, 0,
                new int[]{darker(col, 0.62f), lighter(col, 0.18f), lighter(col, 0.62f), col, darker(col, 0.72f), darker(col, 0.45f)},
                new float[]{0f, 0.16f, 0.3f, 0.5f, 0.82f, 1f}, Shader.TileMode.CLAMP));
    }

    /** Volume de esfera. */
    private static void sphere(int col, float cx, float cy, float rad) {
        fill.setShader(new RadialGradient(cx - rad * 0.38f, cy - rad * 0.42f, rad * 1.5f,
                new int[]{lighter(col, 0.75f), lighter(col, 0.15f), darker(col, 0.62f)},
                new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
    }

    /** Volume de cima para baixo (tampa, aba). */
    private static void vgrad(int col, float y0, float y1) {
        fill.setShader(new LinearGradient(0, y0, 0, y1, lighter(col, 0.35f), darker(col, 0.7f), Shader.TileMode.CLAMP));
    }

    private static void flat(int col) {
        fill.setShader(null);
        fill.setColor(col);
    }

    private static void edge(int col) {
        line.setColor(darker(col, 0.38f));
        line.setStrokeWidth(lw);
    }

    private static void drawPath(Canvas c, int col) {
        c.drawPath(path, fill);
        fill.setShader(null);
        edge(col);
        c.drawPath(path, line);
    }

    private static void drawOval(Canvas c, int col) {
        c.drawOval(r, fill);
        fill.setShader(null);
        edge(col);
        c.drawOval(r, line);
    }

    /** Reflexo especular alongado. */
    private static void spec(Canvas c, float x, float y, float w, float h, int alpha) {
        fill.setShader(null);
        fill.setColor((alpha << 24) | 0xFFFFFF);
        r.set(x - w, y - h, x + w, y + h);
        c.drawOval(r, fill);
    }

    // ---------------------------------------------------------------- entrada

    /** Desenha a peça com a base em (cx, by) e altura total s. */
    public static void draw(Canvas c, int type, int color, float cx, float by, float s) {
        lw = Math.max(1.2f, s * 0.022f);
        // sombra no chão
        flat(0x38000000);
        r.set(cx - s * 0.4f, by - s * 0.08f, cx + s * 0.4f, by + s * 0.09f);
        c.drawOval(r, fill);
        // pedestal envernizado
        float pr = s * 0.31f, pt = s * 0.07f, pe = pr * 0.38f;
        int base = darker(color, 0.5f);
        cyl(base, cx - pr, cx + pr);
        r.set(cx - pr, by - pe, cx + pr, by + pe);
        c.drawOval(r, fill);
        c.drawRect(cx - pr, by - pt, cx + pr, by, fill);
        fill.setShader(null);
        edge(base);
        c.drawArc(r, 0, 180, false, line);
        c.drawLine(cx - pr, by - pt, cx - pr, by, line);
        c.drawLine(cx + pr, by - pt, cx + pr, by, line);
        vgrad(lighter(color, 0.15f), by - pt - pe, by - pt + pe);
        r.set(cx - pr, by - pt - pe, cx + pr, by - pt + pe);
        drawOval(c, color);
        // anel dourado
        line.setColor(0xCCFFD54F);
        line.setStrokeWidth(Math.max(1f, s * 0.014f));
        r.set(cx - pr * 0.82f, by - pt - pe * 0.82f, cx + pr * 0.82f, by - pt + pe * 0.82f);
        c.drawOval(r, line);

        float b2 = by - pt;
        float s2 = s * 0.9f;
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

    // ---------------------------------------------------------------- peças

    /** Peão torneado clássico. */
    private static void pawn(Canvas c, int col, float cx, float by, float s) {
        // corpo em perfil de torno
        path.reset();
        path.moveTo(cx - s * 0.25f, by);
        path.cubicTo(cx - s * 0.25f, by - s * 0.08f, cx - s * 0.2f, by - s * 0.1f, cx - s * 0.17f, by - s * 0.13f);
        path.cubicTo(cx - s * 0.1f, by - s * 0.24f, cx - s * 0.07f, by - s * 0.38f, cx - s * 0.075f, by - s * 0.5f);
        path.lineTo(cx + s * 0.075f, by - s * 0.5f);
        path.cubicTo(cx + s * 0.07f, by - s * 0.38f, cx + s * 0.1f, by - s * 0.24f, cx + s * 0.17f, by - s * 0.13f);
        path.cubicTo(cx + s * 0.2f, by - s * 0.1f, cx + s * 0.25f, by - s * 0.08f, cx + s * 0.25f, by);
        path.close();
        cyl(col, cx - s * 0.25f, cx + s * 0.25f);
        drawPath(c, col);
        // anel da base
        cyl(darker(col, 0.85f), cx - s * 0.255f, cx + s * 0.255f);
        r.set(cx - s * 0.255f, by - s * 0.05f, cx + s * 0.255f, by + s * 0.04f);
        drawOval(c, col);
        // colarinho
        cyl(lighter(col, 0.1f), cx - s * 0.16f, cx + s * 0.16f);
        r.set(cx - s * 0.16f, by - s * 0.56f, cx + s * 0.16f, by - s * 0.47f);
        drawOval(c, col);
        // cabeça
        float hy = by - s * 0.71f, hr = s * 0.17f;
        sphere(col, cx, hy, hr);
        c.drawCircle(cx, hy, hr, fill);
        fill.setShader(null);
        edge(col);
        c.drawCircle(cx, hy, hr, line);
        spec(c, cx - hr * 0.38f, hy - hr * 0.45f, hr * 0.22f, hr * 0.14f, 0xDD);
        spec(c, cx - s * 0.1f, by - s * 0.22f, s * 0.018f, s * 0.07f, 0x99);
    }

    /** Carro esportivo em três quartos. */
    private static void car(Canvas c, int col, float cx, float by, float s) {
        float w = s * 0.47f, ground = by - s * 0.02f;
        // teto e vidros
        path.reset();
        path.moveTo(cx - w * 0.55f, ground - s * 0.32f);
        path.cubicTo(cx - w * 0.42f, ground - s * 0.55f, cx - w * 0.2f, ground - s * 0.58f, cx + w * 0.05f, ground - s * 0.58f);
        path.cubicTo(cx + w * 0.3f, ground - s * 0.58f, cx + w * 0.42f, ground - s * 0.5f, cx + w * 0.6f, ground - s * 0.32f);
        path.close();
        vgrad(col, ground - s * 0.58f, ground - s * 0.3f);
        drawPath(c, col);
        flat(0xFF263238);
        path.reset();
        path.moveTo(cx - w * 0.44f, ground - s * 0.33f);
        path.cubicTo(cx - w * 0.36f, ground - s * 0.49f, cx - w * 0.22f, ground - s * 0.52f, cx - w * 0.04f, ground - s * 0.52f);
        path.lineTo(cx - w * 0.04f, ground - s * 0.33f);
        path.close();
        c.drawPath(path, fill);
        path.reset();
        path.moveTo(cx + w * 0.03f, ground - s * 0.33f);
        path.lineTo(cx + w * 0.03f, ground - s * 0.52f);
        path.cubicTo(cx + w * 0.24f, ground - s * 0.52f, cx + w * 0.36f, ground - s * 0.46f, cx + w * 0.48f, ground - s * 0.33f);
        path.close();
        c.drawPath(path, fill);
        // reflexo no vidro
        fill.setColor(0x66B3E5FC);
        path.reset();
        path.moveTo(cx - w * 0.36f, ground - s * 0.34f);
        path.lineTo(cx - w * 0.22f, ground - s * 0.5f);
        path.lineTo(cx - w * 0.12f, ground - s * 0.5f);
        path.lineTo(cx - w * 0.26f, ground - s * 0.34f);
        path.close();
        c.drawPath(path, fill);
        // carroceria
        path.reset();
        path.moveTo(cx - w, ground - s * 0.12f);
        path.cubicTo(cx - w * 1.02f, ground - s * 0.26f, cx - w * 0.9f, ground - s * 0.34f, cx - w * 0.6f, ground - s * 0.34f);
        path.lineTo(cx + w * 0.62f, ground - s * 0.34f);
        path.cubicTo(cx + w * 0.92f, ground - s * 0.33f, cx + w * 1.03f, ground - s * 0.25f, cx + w, ground - s * 0.12f);
        path.close();
        vgrad(col, ground - s * 0.36f, ground - s * 0.08f);
        drawPath(c, col);
        // faixa e friso
        flat(lighter(col, 0.55f));
        c.drawRect(cx - w * 0.9f, ground - s * 0.27f, cx + w * 0.92f, ground - s * 0.245f, fill);
        // farol e lanterna
        flat(0xFFFFF59D);
        r.set(cx + w * 0.84f, ground - s * 0.27f, cx + w * 0.98f, ground - s * 0.2f);
        c.drawOval(r, fill);
        flat(0xFFE53935);
        r.set(cx - w * 0.99f, ground - s * 0.26f, cx - w * 0.9f, ground - s * 0.2f);
        c.drawOval(r, fill);
        // para-choques cromados
        flat(0xFFCFD8DC);
        r.set(cx - w * 1.02f, ground - s * 0.14f, cx - w * 0.72f, ground - s * 0.09f);
        c.drawRoundRect(r, s * 0.02f, s * 0.02f, fill);
        r.set(cx + w * 0.72f, ground - s * 0.14f, cx + w * 1.02f, ground - s * 0.09f);
        c.drawRoundRect(r, s * 0.02f, s * 0.02f, fill);
        // rodas
        for (float k : new float[]{-0.58f, 0.58f}) {
            float wx = cx + w * k, wy = ground - s * 0.1f, wr = s * 0.12f;
            flat(darker(col, 0.35f));
            c.drawCircle(wx, wy - s * 0.01f, wr * 1.12f, fill);
            flat(0xFF212121);
            c.drawCircle(wx, wy, wr, fill);
            fill.setShader(new RadialGradient(wx - wr * 0.2f, wy - wr * 0.2f, wr * 0.6f, 0xFFFFFFFF, 0xFF90A4AE, Shader.TileMode.CLAMP));
            c.drawCircle(wx, wy, wr * 0.52f, fill);
            flat(0xFF78909C);
            c.drawCircle(wx, wy, wr * 0.15f, fill);
        }
        spec(c, cx - w * 0.3f, ground - s * 0.31f, w * 0.35f, s * 0.012f, 0xAA);
    }

    /** Cartola com fita. */
    private static void hat(Canvas c, int col, float cx, float by, float s) {
        int dark = darker(col, 0.6f);
        // aba (parte de trás)
        vgrad(dark, by - s * 0.2f, by);
        r.set(cx - s * 0.4f, by - s * 0.19f, cx + s * 0.4f, by - s * 0.01f);
        drawOval(c, dark);
        // copa
        path.reset();
        path.moveTo(cx - s * 0.235f, by - s * 0.1f);
        path.cubicTo(cx - s * 0.25f, by - s * 0.4f, cx - s * 0.2f, by - s * 0.7f, cx - s * 0.22f, by - s * 0.86f);
        path.lineTo(cx + s * 0.22f, by - s * 0.86f);
        path.cubicTo(cx + s * 0.2f, by - s * 0.7f, cx + s * 0.25f, by - s * 0.4f, cx + s * 0.235f, by - s * 0.1f);
        path.close();
        cyl(col, cx - s * 0.25f, cx + s * 0.25f);
        drawPath(c, col);
        // fita
        path.reset();
        path.moveTo(cx - s * 0.243f, by - s * 0.3f);
        path.quadTo(cx, by - s * 0.24f, cx + s * 0.243f, by - s * 0.3f);
        path.lineTo(cx + s * 0.238f, by - s * 0.17f);
        path.quadTo(cx, by - s * 0.11f, cx - s * 0.238f, by - s * 0.17f);
        path.close();
        cyl(0xFF2B2B2B, cx - s * 0.245f, cx + s * 0.245f);
        drawPath(c, 0xFF555555);
        // fivela dourada
        flat(0xFFFFC107);
        r.set(cx + s * 0.06f, by - s * 0.27f, cx + s * 0.14f, by - s * 0.16f);
        c.drawRect(r, fill);
        flat(0xFF2B2B2B);
        r.set(cx + s * 0.08f, by - s * 0.245f, cx + s * 0.12f, by - s * 0.185f);
        c.drawRect(r, fill);
        // aba (frente)
        path.reset();
        path.moveTo(cx - s * 0.4f, by - s * 0.1f);
        path.quadTo(cx, by + s * 0.06f, cx + s * 0.4f, by - s * 0.1f);
        path.quadTo(cx, by - s * 0.01f, cx - s * 0.4f, by - s * 0.1f);
        path.close();
        vgrad(dark, by - s * 0.1f, by + s * 0.05f);
        drawPath(c, dark);
        // topo
        vgrad(lighter(col, 0.1f), by - s * 0.9f, by - s * 0.82f);
        r.set(cx - s * 0.22f, by - s * 0.9f, cx + s * 0.22f, by - s * 0.82f);
        drawOval(c, col);
        spec(c, cx - s * 0.13f, by - s * 0.6f, s * 0.022f, s * 0.15f, 0x88);
    }

    /** Foguete retrô. */
    private static void rocket(Canvas c, int col, float cx, float by, float s) {
        int fin = darker(col, 0.7f);
        // chama
        path.reset();
        path.moveTo(cx - s * 0.09f, by - s * 0.16f);
        path.cubicTo(cx - s * 0.08f, by - s * 0.02f, cx - s * 0.03f, by + s * 0.04f, cx, by + s * 0.08f);
        path.cubicTo(cx + s * 0.03f, by + s * 0.04f, cx + s * 0.08f, by - s * 0.02f, cx + s * 0.09f, by - s * 0.16f);
        path.close();
        fill.setShader(new LinearGradient(0, by - s * 0.16f, 0, by + s * 0.08f, 0xFFFFF176, 0xFFFF6F00, Shader.TileMode.CLAMP));
        c.drawPath(path, fill);
        fill.setShader(null);
        // aletas laterais
        for (int k = -1; k <= 1; k += 2) {
            path.reset();
            path.moveTo(cx + k * s * 0.15f, by - s * 0.42f);
            path.quadTo(cx + k * s * 0.24f, by - s * 0.3f, cx + k * s * 0.27f, by - s * 0.1f);
            path.lineTo(cx + k * s * 0.25f, by - s * 0.05f);
            path.lineTo(cx + k * s * 0.12f, by - s * 0.17f);
            path.close();
            vgrad(fin, by - s * 0.42f, by - s * 0.05f);
            drawPath(c, fin);
        }
        // bocal
        cyl(0xFF90A4AE, cx - s * 0.12f, cx + s * 0.12f);
        path.reset();
        path.moveTo(cx - s * 0.1f, by - s * 0.2f);
        path.lineTo(cx - s * 0.12f, by - s * 0.13f);
        path.lineTo(cx + s * 0.12f, by - s * 0.13f);
        path.lineTo(cx + s * 0.1f, by - s * 0.2f);
        path.close();
        drawPath(c, 0xFF607D8B);
        // corpo
        path.reset();
        path.moveTo(cx, by - s * 1.02f);
        path.cubicTo(cx + s * 0.24f, by - s * 0.86f, cx + s * 0.2f, by - s * 0.4f, cx + s * 0.13f, by - s * 0.19f);
        path.lineTo(cx - s * 0.13f, by - s * 0.19f);
        path.cubicTo(cx - s * 0.2f, by - s * 0.4f, cx - s * 0.24f, by - s * 0.86f, cx, by - s * 1.02f);
        path.close();
        cyl(0xFFECEFF1, cx - s * 0.2f, cx + s * 0.2f);
        drawPath(c, 0xFF90A4AE);
        // ponta e faixa na cor do jogador
        path.reset();
        path.moveTo(cx, by - s * 1.02f);
        path.cubicTo(cx + s * 0.13f, by - s * 0.94f, cx + s * 0.17f, by - s * 0.86f, cx + s * 0.185f, by - s * 0.78f);
        path.quadTo(cx, by - s * 0.73f, cx - s * 0.185f, by - s * 0.78f);
        path.cubicTo(cx - s * 0.17f, by - s * 0.86f, cx - s * 0.13f, by - s * 0.94f, cx, by - s * 1.02f);
        path.close();
        cyl(col, cx - s * 0.19f, cx + s * 0.19f);
        drawPath(c, col);
        path.reset();
        path.moveTo(cx - s * 0.17f, by - s * 0.33f);
        path.quadTo(cx, by - s * 0.29f, cx + s * 0.17f, by - s * 0.33f);
        path.lineTo(cx + s * 0.145f, by - s * 0.24f);
        path.quadTo(cx, by - s * 0.2f, cx - s * 0.145f, by - s * 0.24f);
        path.close();
        cyl(col, cx - s * 0.17f, cx + s * 0.17f);
        drawPath(c, col);
        // aleta central (de frente)
        path.reset();
        path.moveTo(cx - s * 0.02f, by - s * 0.36f);
        path.lineTo(cx + s * 0.02f, by - s * 0.36f);
        path.lineTo(cx + s * 0.03f, by - s * 0.1f);
        path.lineTo(cx - s * 0.03f, by - s * 0.1f);
        path.close();
        cyl(fin, cx - s * 0.03f, cx + s * 0.03f);
        drawPath(c, fin);
        // escotilha
        float wy = by - s * 0.58f, wr = s * 0.085f;
        flat(0xFFB0BEC5);
        c.drawCircle(cx, wy, wr * 1.3f, fill);
        edge(0xFF90A4AE);
        c.drawCircle(cx, wy, wr * 1.3f, line);
        fill.setShader(new RadialGradient(cx - wr * 0.3f, wy - wr * 0.3f, wr * 1.3f, 0xFFE1F5FE, 0xFF0277BD, Shader.TileMode.CLAMP));
        c.drawCircle(cx, wy, wr, fill);
        fill.setShader(null);
        spec(c, cx - wr * 0.35f, wy - wr * 0.35f, wr * 0.28f, wr * 0.18f, 0xEE);
        // rebites
        flat(0xFF78909C);
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3;
            c.drawCircle(cx + (float) Math.cos(a) * wr * 1.15f, wy + (float) Math.sin(a) * wr * 1.15f, s * 0.008f, fill);
        }
        spec(c, cx - s * 0.1f, by - s * 0.55f, s * 0.018f, s * 0.16f, 0x99);
    }

    /** Coroa real. */
    private static void crown(Canvas c, int col, float cx, float by, float s) {
        int gold = 0xFFFFC107;
        float w = s * 0.33f;
        // interior (veludo na cor do jogador)
        vgrad(darker(col, 0.7f), by - s * 0.6f, by - s * 0.3f);
        r.set(cx - w * 0.92f, by - s * 0.48f, cx + w * 0.92f, by - s * 0.32f);
        c.drawOval(r, fill);
        fill.setShader(null);
        // coroa com pontas
        path.reset();
        path.moveTo(cx - w, by - s * 0.08f);
        path.lineTo(cx - w * 1.04f, by - s * 0.58f);
        path.quadTo(cx - w * 0.85f, by - s * 0.42f, cx - w * 0.55f, by - s * 0.42f);
        path.lineTo(cx - w * 0.4f, by - s * 0.72f);
        path.quadTo(cx - w * 0.2f, by - s * 0.48f, cx, by - s * 0.47f);
        path.quadTo(cx + w * 0.2f, by - s * 0.48f, cx + w * 0.4f, by - s * 0.72f);
        path.lineTo(cx + w * 0.55f, by - s * 0.42f);
        path.quadTo(cx + w * 0.85f, by - s * 0.42f, cx + w * 1.04f, by - s * 0.58f);
        path.lineTo(cx + w, by - s * 0.08f);
        path.quadTo(cx, by + s * 0.01f, cx - w, by - s * 0.08f);
        path.close();
        cyl(gold, cx - w * 1.04f, cx + w * 1.04f);
        drawPath(c, 0xFFB8860B);
        // cruz central
        float tx = cx, ty = by - s * 0.82f;
        cyl(gold, tx - s * 0.03f, tx + s * 0.03f);
        r.set(tx - s * 0.025f, ty - s * 0.06f, tx + s * 0.025f, ty + s * 0.13f);
        c.drawRect(r, fill);
        r.set(tx - s * 0.07f, ty - s * 0.01f, tx + s * 0.07f, ty + s * 0.035f);
        c.drawRect(r, fill);
        fill.setShader(null);
        // bolinhas nas pontas
        for (float[] p : new float[][]{{-1.04f, 0.58f}, {-0.4f, 0.72f}, {0.4f, 0.72f}, {1.04f, 0.58f}}) {
            float px = cx + w * p[0], py = by - s * p[1];
            sphere(0xFFFFE082, px, py, s * 0.04f);
            c.drawCircle(px, py, s * 0.04f, fill);
        }
        // faixa com pedras na cor do jogador
        path.reset();
        path.moveTo(cx - w, by - s * 0.24f);
        path.quadTo(cx, by - s * 0.15f, cx + w, by - s * 0.24f);
        path.lineTo(cx + w, by - s * 0.08f);
        path.quadTo(cx, by + s * 0.01f, cx - w, by - s * 0.08f);
        path.close();
        cyl(0xFFFFCA28, cx - w, cx + w);
        drawPath(c, 0xFFB8860B);
        for (float k : new float[]{-0.6f, 0f, 0.6f}) {
            float gx = cx + w * k, gy = by - s * (0.125f + Math.abs(k) * 0.03f);
            float gr = s * (k == 0 ? 0.055f : 0.04f);
            sphere(col, gx, gy, gr);
            r.set(gx - gr, gy - gr * 0.8f, gx + gr, gy + gr * 0.8f);
            drawOval(c, col);
            spec(c, gx - gr * 0.3f, gy - gr * 0.3f, gr * 0.25f, gr * 0.15f, 0xEE);
        }
        spec(c, cx - w * 0.7f, by - s * 0.35f, s * 0.015f, s * 0.12f, 0xAA);
    }

    /** Estrela com bisel (pontas divididas em luz e sombra). */
    private static void star(Canvas c, int col, float cx, float by, float s) {
        // hastezinha
        path.reset();
        path.moveTo(cx - s * 0.035f, by - s * 0.3f);
        path.lineTo(cx + s * 0.035f, by - s * 0.3f);
        path.lineTo(cx + s * 0.1f, by);
        path.lineTo(cx - s * 0.1f, by);
        path.close();
        cyl(darker(col, 0.75f), cx - s * 0.1f, cx + s * 0.1f);
        drawPath(c, col);
        float ccy = by - s * 0.55f, ro = s * 0.44f, ri = s * 0.19f;
        float[] px = new float[10], py = new float[10];
        for (int i = 0; i < 10; i++) {
            double ang = -Math.PI / 2 + i * Math.PI / 5;
            float rad = i % 2 == 0 ? ro : ri;
            px[i] = cx + (float) Math.cos(ang) * rad;
            py[i] = ccy + (float) Math.sin(ang) * rad;
        }
        // facetas: cada ponta tem duas metades
        for (int i = 0; i < 10; i++) {
            int j = (i + 1) % 10;
            path.reset();
            path.moveTo(cx, ccy);
            path.lineTo(px[i], py[i]);
            path.lineTo(px[j], py[j]);
            path.close();
            double mid = -Math.PI / 2 + (i + 0.5) * Math.PI / 5;
            float light = (float) (0.5 - 0.5 * Math.cos(mid + Math.PI * 0.75));
            int fc = light > 0.5f ? lighter(col, (light - 0.5f) * 1.1f) : darker(col, 0.62f + light * 0.76f);
            flat(fc);
            c.drawPath(path, fill);
        }
        path.reset();
        for (int i = 0; i < 10; i++) {
            if (i == 0) path.moveTo(px[i], py[i]); else path.lineTo(px[i], py[i]);
        }
        path.close();
        fill.setShader(null);
        edge(col);
        c.drawPath(path, line);
        line.setColor(0x40FFFFFF);
        line.setStrokeWidth(Math.max(1f, s * 0.01f));
        for (int i = 0; i < 10; i += 2) c.drawLine(cx, ccy, px[i], py[i], line);
        spec(c, cx - s * 0.08f, ccy - s * 0.16f, s * 0.035f, s * 0.02f, 0xDD);
    }

    /** Veleiro. */
    private static void boat(Canvas c, int col, float cx, float by, float s) {
        // mastro
        cyl(0xFF8D6E63, cx - s * 0.02f, cx + s * 0.02f);
        c.drawRect(cx - s * 0.018f, by - s * 1.0f, cx + s * 0.018f, by - s * 0.25f, fill);
        fill.setShader(null);
        // vela grande (curva, com sombra)
        path.reset();
        path.moveTo(cx + s * 0.03f, by - s * 0.97f);
        path.cubicTo(cx + s * 0.28f, by - s * 0.78f, cx + s * 0.38f, by - s * 0.52f, cx + s * 0.36f, by - s * 0.32f);
        path.lineTo(cx + s * 0.03f, by - s * 0.32f);
        path.close();
        fill.setShader(new LinearGradient(cx, 0, cx + s * 0.38f, 0, 0xFFFFFFFF, 0xFFCFD8DC, Shader.TileMode.CLAMP));
        drawPath(c, 0xFFB0BEC5);
        // listras na cor do jogador
        flat(lighter(col, 0.25f));
        path.reset();
        path.moveTo(cx + s * 0.03f, by - s * 0.62f);
        path.lineTo(cx + s * 0.33f, by - s * 0.62f);
        path.lineTo(cx + s * 0.35f, by - s * 0.54f);
        path.lineTo(cx + s * 0.03f, by - s * 0.54f);
        path.close();
        c.drawPath(path, fill);
        // vela da frente
        path.reset();
        path.moveTo(cx - s * 0.03f, by - s * 0.86f);
        path.cubicTo(cx - s * 0.2f, by - s * 0.68f, cx - s * 0.28f, by - s * 0.48f, cx - s * 0.27f, by - s * 0.34f);
        path.lineTo(cx - s * 0.03f, by - s * 0.34f);
        path.close();
        fill.setShader(new LinearGradient(cx - s * 0.28f, 0, cx, 0, lighter(col, 0.55f), lighter(col, 0.2f), Shader.TileMode.CLAMP));
        drawPath(c, col);
        // flâmula
        path.reset();
        path.moveTo(cx, by - s * 1.02f);
        path.quadTo(cx - s * 0.08f, by - s * 1.04f, cx - s * 0.16f, by - s * 0.98f);
        path.quadTo(cx - s * 0.08f, by - s * 0.96f, cx, by - s * 0.93f);
        path.close();
        flat(col);
        c.drawPath(path, fill);
        // casco
        path.reset();
        path.moveTo(cx - s * 0.45f, by - s * 0.31f);
        path.lineTo(cx + s * 0.45f, by - s * 0.31f);
        path.cubicTo(cx + s * 0.4f, by - s * 0.12f, cx + s * 0.3f, by - s * 0.02f, cx + s * 0.18f, by - s * 0.01f);
        path.lineTo(cx - s * 0.18f, by - s * 0.01f);
        path.cubicTo(cx - s * 0.3f, by - s * 0.02f, cx - s * 0.4f, by - s * 0.12f, cx - s * 0.45f, by - s * 0.31f);
        path.close();
        vgrad(col, by - s * 0.31f, by);
        drawPath(c, col);
        // borda de madeira
        cyl(0xFFA1887F, cx - s * 0.46f, cx + s * 0.46f);
        r.set(cx - s * 0.46f, by - s * 0.34f, cx + s * 0.46f, by - s * 0.285f);
        c.drawRoundRect(r, s * 0.02f, s * 0.02f, fill);
        fill.setShader(null);
        edge(0xFF8D6E63);
        c.drawRoundRect(r, s * 0.02f, s * 0.02f, line);
        // linha d'água e escotilhas
        flat(0xEEFFFFFF);
        c.drawRect(cx - s * 0.36f, by - s * 0.13f, cx + s * 0.36f, by - s * 0.105f, fill);
        for (float k : new float[]{-0.2f, 0f, 0.2f}) {
            flat(0xFFE0F7FA);
            c.drawCircle(cx + s * k, by - s * 0.21f, s * 0.028f, fill);
            edge(col);
            c.drawCircle(cx + s * k, by - s * 0.21f, s * 0.028f, line);
        }
        spec(c, cx - s * 0.25f, by - s * 0.27f, s * 0.12f, s * 0.012f, 0x99);
    }

    /** Diamante lapidado. */
    private static void gem(Canvas c, int col, float cx, float by, float s) {
        float top = by - s * 0.86f, mid = by - s * 0.62f, w = s * 0.42f, bot = by - s * 0.02f;
        float tw = w * 0.55f;
        // pontos da coroa (parte de cima) e do pavilhão (parte de baixo)
        float[][] crownPts = {
                {cx - w, mid}, {cx - tw, top}, {cx - tw * 0.33f, top}, {cx + tw * 0.33f, top}, {cx + tw, top}, {cx + w, mid}
        };
        float[] girdleX = {cx - w, cx - w * 0.5f, cx, cx + w * 0.5f, cx + w};
        // facetas superiores
        int[] topShades = {0, 1, 2, 1, 0};
        float[][] tri = {
                {crownPts[0][0], crownPts[0][1], crownPts[1][0], crownPts[1][1], girdleX[1], mid},
                {crownPts[1][0], crownPts[1][1], crownPts[2][0], crownPts[2][1], girdleX[1], mid},
                {crownPts[2][0], crownPts[2][1], crownPts[3][0], crownPts[3][1], girdleX[2], mid},
                {crownPts[3][0], crownPts[3][1], crownPts[4][0], crownPts[4][1], girdleX[3], mid},
                {crownPts[4][0], crownPts[4][1], crownPts[5][0], crownPts[5][1], girdleX[3], mid},
        };
        int[] shades = {lighter(col, 0.35f), lighter(col, 0.6f), lighter(col, 0.15f), col, darker(col, 0.75f)};
        for (int i = 0; i < tri.length; i++) {
            path.reset();
            path.moveTo(tri[i][0], tri[i][1]);
            path.lineTo(tri[i][2], tri[i][3]);
            path.lineTo(tri[i][4], tri[i][5]);
            path.close();
            flat(shades[i]);
            c.drawPath(path, fill);
        }
        // triângulos entre as facetas superiores
        int[] mids = {lighter(col, 0.45f), lighter(col, 0.25f), darker(col, 0.85f)};
        float[][] tri2 = {
                {crownPts[2][0], crownPts[2][1], girdleX[1], mid, girdleX[2], mid},
                {crownPts[3][0], crownPts[3][1], girdleX[2], mid, girdleX[3], mid},
                {crownPts[1][0], crownPts[1][1], girdleX[0], mid, girdleX[1], mid},
        };
        for (int i = 0; i < tri2.length; i++) {
            path.reset();
            path.moveTo(tri2[i][0], tri2[i][1]);
            path.lineTo(tri2[i][2], tri2[i][3]);
            path.lineTo(tri2[i][4], tri2[i][5]);
            path.close();
            flat(mids[i]);
            c.drawPath(path, fill);
        }
        // pavilhão (de baixo)
        int[] low = {darker(col, 0.8f), lighter(col, 0.2f), darker(col, 0.65f), darker(col, 0.5f)};
        for (int i = 0; i < 4; i++) {
            path.reset();
            path.moveTo(girdleX[i], mid);
            path.lineTo(girdleX[i + 1], mid);
            path.lineTo(cx, bot);
            path.close();
            flat(low[i]);
            c.drawPath(path, fill);
        }
        // contorno e arestas
        path.reset();
        path.moveTo(crownPts[0][0], crownPts[0][1]);
        for (int i = 1; i < crownPts.length; i++) path.lineTo(crownPts[i][0], crownPts[i][1]);
        path.lineTo(cx, bot);
        path.close();
        edge(col);
        c.drawPath(path, line);
        line.setColor(0x55FFFFFF);
        line.setStrokeWidth(Math.max(1f, s * 0.01f));
        c.drawLine(cx - w, mid, cx + w, mid, line);
        for (int i = 1; i < 4; i++) c.drawLine(girdleX[i], mid, cx, bot, line);
        // brilho em cruz
        flat(0xFFFFFFFF);
        float sx = cx + w * 0.55f, sy = top + s * 0.02f, k = s * 0.08f;
        path.reset();
        path.moveTo(sx, sy - k);
        path.quadTo(sx, sy, sx + k, sy);
        path.quadTo(sx, sy, sx, sy + k);
        path.quadTo(sx, sy, sx - k, sy);
        path.quadTo(sx, sy, sx, sy - k);
        path.close();
        c.drawPath(path, fill);
        spec(c, cx - w * 0.45f, top + s * 0.06f, s * 0.05f, s * 0.018f, 0xCC);
    }
}
