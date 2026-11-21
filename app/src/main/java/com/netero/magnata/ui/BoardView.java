package com.netero.magnata.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import com.netero.magnata.engine.Board;
import com.netero.magnata.engine.Game;
import com.netero.magnata.engine.Player;

/** Desenha o tabuleiro quadrado com as 40 casas, peões, casas/hotéis e dados. */
public class BoardView extends View {

    public interface OnSquareTap { void onSquare(int pos); }

    private Game game;
    private int myIndex = -1;
    private OnSquareTap tapListener;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    private float w, c, e;

    public static final int BG = 0xFFF6F1E3;
    public static final int CENTER = 0xFFDCEFD8;
    public static final int INK = 0xFF2B2B2B;

    public BoardView(Context ctx) {
        super(ctx);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(0xFF555555);
        text.setColor(INK);
        text.setTextAlign(Paint.Align.CENTER);
    }

    public void setGame(Game g, int myIndex) {
        this.game = g;
        this.myIndex = myIndex;
        invalidate();
    }

    public void setOnSquareTap(OnSquareTap l) { this.tapListener = l; }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int size = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(size, size);
    }

    private void cellRect(int pos, RectF out) {
        if (pos == 0) out.set(w - c, w - c, w, w);
        else if (pos < 10) { float right = w - c - (pos - 1) * e; out.set(right - e, w - c, right, w); }
        else if (pos == 10) out.set(0, w - c, c, w);
        else if (pos < 20) { float bottom = w - c - (pos - 11) * e; out.set(0, bottom - e, c, bottom); }
        else if (pos == 20) out.set(0, 0, c, c);
        else if (pos < 30) { float left = c + (pos - 21) * e; out.set(left, 0, left + e, c); }
        else if (pos == 30) out.set(w - c, 0, w, c);
        else { float top = c + (pos - 31) * e; out.set(w - c, top, w, top + e); }
    }

    /** 0=baixo, 1=esquerda, 2=cima, 3=direita, -1=canto */
    private static int side(int pos) {
        if (pos % 10 == 0) return -1;
        return pos / 10;
    }

    @Override
    protected void onDraw(Canvas cv) {
        w = getWidth();
        e = w / 12.2f;
        c = 1.6f * e;
        cv.drawColor(BG);

        fill.setColor(CENTER);
        cv.drawRect(c, c, w - c, w - c, fill);

        stroke.setStrokeWidth(Math.max(1f, w / 600f));
        for (int i = 0; i < Board.SIZE; i++) drawCell(cv, i);
        stroke.setColor(0xFF333333);
        cv.drawRect(0, 0, w, w, stroke);
        cv.drawRect(c, c, w - c, w - c, stroke);
        stroke.setColor(0xFF555555);

        drawCenter(cv);
        if (game != null) drawTokens(cv);
    }

    private void drawCell(Canvas cv, int pos) {
        cellRect(pos, r);
        fill.setColor(Color.WHITE);
        if (pos % 10 == 0) fill.setColor(0xFFFFF8E1);
        cv.drawRect(r, fill);

        int type = Board.TYPE[pos];
        int sd = side(pos);
        float bar = e * 0.28f;
        RectF content = new RectF(r);

        if (type == Board.PROP) {
            RectF b = new RectF(r);
            switch (sd) {
                case 0: b.bottom = r.top + bar; content.top += bar; break;
                case 1: b.left = r.right - bar; content.right -= bar; break;
                case 2: b.top = r.bottom - bar; content.bottom -= bar; break;
                default: b.right = r.left + bar; content.left += bar; break;
            }
            fill.setColor(Board.GROUP_COLOR[Board.GROUP[pos]]);
            cv.drawRect(b, fill);
            cv.drawRect(b, stroke);
            if (game != null) drawHouses(cv, b, pos);
        }

        cv.drawRect(r, stroke);

        // nome
        float ts = e * 0.2f;
        text.setTypeface(pos % 10 == 0 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        text.setTextSize(pos % 10 == 0 ? ts * 1.05f : ts);
        text.setColor(INK);
        String label = Board.SHORT[pos];
        if (type == Board.DESTINO) label = "? Destino";
        if (type == Board.SURPRESA) label = "★ Surpresa";
        drawWrapped(cv, label, content.centerX(), content.top + content.height() * 0.38f, content.width() * 0.92f);

        if (Board.buyable(pos) || type == Board.TAX) {
            text.setTextSize(ts * 0.9f);
            text.setColor(0xFF555555);
            cv.drawText("$" + Board.PRICE[pos], content.centerX(), content.bottom - ts * 0.5f, text);
        }

        if (game != null && Board.buyable(pos)) {
            int o = game.owner[pos];
            if (o >= 0) {
                // faixa com a cor do dono
                fill.setColor(game.players[o].color);
                float m = e * 0.09f;
                RectF ob = new RectF(content.left + 1, content.bottom - m, content.right - 1, content.bottom - 1);
                cv.drawRect(ob, fill);
                if (game.mortgaged[pos]) {
                    fill.setColor(0x99777777);
                    cv.drawRect(content, fill);
                    text.setColor(Color.WHITE);
                    text.setTextSize(ts);
                    text.setTypeface(Typeface.DEFAULT_BOLD);
                    cv.drawText("HIPOT.", content.centerX(), content.centerY(), text);
                }
            }
        }
    }

    private void drawHouses(Canvas cv, RectF b, int pos) {
        int h = game.houses[pos];
        if (h <= 0) return;
        if (h == 5) {
            fill.setColor(0xFFC62828);
            float s = Math.min(b.width(), b.height()) * 0.7f;
            cv.drawRect(b.centerX() - s, b.centerY() - s / 2, b.centerX() + s, b.centerY() + s / 2, fill);
            return;
        }
        fill.setColor(0xFF1B5E20);
        boolean horiz = b.width() > b.height();
        float s = Math.min(b.width(), b.height()) * 0.55f;
        for (int i = 0; i < h; i++) {
            float cx, cy;
            if (horiz) { cx = b.left + b.width() * (i + 1) / (h + 1); cy = b.centerY(); }
            else { cx = b.centerX(); cy = b.top + b.height() * (i + 1) / (h + 1); }
            cv.drawRect(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2, fill);
        }
    }

    private void drawWrapped(Canvas cv, String s, float cx, float y, float maxW) {
        String[] words = s.split(" ");
        StringBuilder line = new StringBuilder();
        float lh = text.getTextSize() * 1.1f;
        int lines = 0;
        for (String word : words) {
            String test = line.length() == 0 ? word : line + " " + word;
            if (text.measureText(test) > maxW && line.length() > 0) {
                cv.drawText(line.toString(), cx, y + lines * lh, text);
                lines++;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) {
            String l = line.toString();
            while (text.measureText(l) > maxW && l.length() > 2) l = l.substring(0, l.length() - 1);
            cv.drawText(l, cx, y + lines * lh, text);
        }
    }

    private void drawCenter(Canvas cv) {
        float cx = w / 2f;
        text.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        text.setTextSize(e * 0.95f);
        text.setColor(0xFF1B5E20);
        cv.drawText("MAGNATA", cx, c + e * 1.4f, text);
        text.setTypeface(Typeface.DEFAULT);
        text.setTextSize(e * 0.28f);
        text.setColor(0xFF4E6E4E);
        cv.drawText("o jogo dos negócios", cx, c + e * 1.85f, text);

        if (game == null) return;

        // dados
        float ds = e * 1.1f;
        float dy = w / 2f - ds * 0.2f;
        drawDie(cv, cx - ds * 0.65f, dy, ds, game.d1);
        drawDie(cv, cx + ds * 0.65f, dy, ds, game.d2);

        Player p = game.cur();
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextSize(e * 0.36f);
        if (game.phase == Game.PH_OVER) {
            text.setColor(0xFFB8860B);
            String wn = game.winner >= 0 ? game.players[game.winner].name : "?";
            cv.drawText("🏆 " + wn + " venceu!", cx, w - c - e * 1.3f, text);
        } else {
            text.setColor(p.color);
            String who = (game.current == myIndex) ? "Sua vez, " + p.name + "!" : "Vez de " + p.name;
            cv.drawText(who, cx, w - c - e * 1.3f, text);
        }
    }

    private void drawDie(Canvas cv, float cx, float cy, float s, int v) {
        RectF d = new RectF(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2);
        fill.setColor(Color.WHITE);
        cv.drawRoundRect(d, s * 0.18f, s * 0.18f, fill);
        stroke.setColor(0xFF333333);
        cv.drawRoundRect(d, s * 0.18f, s * 0.18f, stroke);
        stroke.setColor(0xFF555555);
        fill.setColor(INK);
        float pr = s * 0.09f, q = s * 0.27f;
        boolean[] pips = new boolean[9];
        switch (v) {
            case 1: pips[4] = true; break;
            case 2: pips[0] = pips[8] = true; break;
            case 3: pips[0] = pips[4] = pips[8] = true; break;
            case 4: pips[0] = pips[2] = pips[6] = pips[8] = true; break;
            case 5: pips[0] = pips[2] = pips[4] = pips[6] = pips[8] = true; break;
            default: pips[0] = pips[2] = pips[3] = pips[5] = pips[6] = pips[8] = true; break;
        }
        for (int i = 0; i < 9; i++) {
            if (!pips[i]) continue;
            float px = cx + (i % 3 - 1) * q, py = cy + (i / 3 - 1) * q;
            cv.drawCircle(px, py, pr, fill);
        }
    }

    private void drawTokens(Canvas cv) {
        int n = game.players.length;
        for (int pos = 0; pos < Board.SIZE; pos++) {
            int count = 0;
            for (Player p : game.players) if (!p.bankrupt && p.pos == pos) count++;
            if (count == 0) continue;
            cellRect(pos, r);
            float rad = e * 0.15f;
            int k = 0;
            for (int i = 0; i < n; i++) {
                Player p = game.players[i];
                if (p.bankrupt || p.pos != pos) continue;
                float cx, cy;
                if (pos == Board.JAIL_POS && p.inJail) {
                    cx = r.right - rad * 1.3f - (k % 3) * rad * 2.1f;
                    cy = r.top + rad * 1.3f + (k / 3) * rad * 2.1f;
                } else {
                    float gx = (k % 3 - (Math.min(count, 3) - 1) / 2f) * rad * 2.1f;
                    float gy = (k / 3) * rad * 2.1f;
                    cx = r.centerX() + gx;
                    cy = r.centerY() + gy + rad * 0.3f;
                }
                fill.setColor(0x55000000);
                cv.drawCircle(cx + rad * 0.15f, cy + rad * 0.2f, rad, fill);
                fill.setColor(p.color);
                cv.drawCircle(cx, cy, rad, fill);
                stroke.setColor(i == game.current ? Color.BLACK : Color.WHITE);
                float old = stroke.getStrokeWidth();
                stroke.setStrokeWidth(rad * 0.3f);
                cv.drawCircle(cx, cy, rad, stroke);
                stroke.setStrokeWidth(old);
                stroke.setColor(0xFF555555);
                k++;
            }
        }
        if (game.cur() != null && !game.cur().bankrupt) {
            // nada extra
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (ev.getAction() == MotionEvent.ACTION_UP && tapListener != null) {
            float x = ev.getX(), y = ev.getY();
            for (int i = 0; i < Board.SIZE; i++) {
                cellRect(i, r);
                if (r.contains(x, y)) {
                    performClick();
                    tapListener.onSquare(i);
                    return true;
                }
            }
        }
        return super.onTouchEvent(ev);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
