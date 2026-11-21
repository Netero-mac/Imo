package com.netero.magnata.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Ícone de um peão (usado nas escolhas e no placar). */
public class PawnView extends View {
    private int pawn, color;
    private boolean selected;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public PawnView(Context c, int pawn, int color) {
        super(c);
        this.pawn = pawn;
        this.color = color;
    }

    public void set(int pawn, int color) { this.pawn = pawn; this.color = color; invalidate(); }

    public void setChosen(boolean s) { selected = s; invalidate(); }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        if (selected) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x33000000 | (color & 0xFFFFFF));
            c.drawCircle(w / 2f, h / 2f, Math.min(w, h) / 2f - 2, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(3f, w * 0.06f));
            p.setColor(0xFF33302A);
            c.drawCircle(w / 2f, h / 2f, Math.min(w, h) / 2f - p.getStrokeWidth(), p);
        }
        Pawns.draw(c, pawn, color, w / 2f, h * 0.86f, h * 0.72f);
    }
}
