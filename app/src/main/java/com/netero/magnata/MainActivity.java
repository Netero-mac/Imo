package com.netero.magnata;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.netero.magnata.engine.Board;
import com.netero.magnata.engine.Game;
import com.netero.magnata.engine.Player;
import com.netero.magnata.engine.Rules;
import com.netero.magnata.net.Bt;
import com.netero.magnata.net.Link;
import com.netero.magnata.net.Msg;
import com.netero.magnata.net.Wifi;
import com.netero.magnata.ui.BoardView;
import com.netero.magnata.ui.PawnView;
import com.netero.magnata.ui.Pawns;
import com.netero.magnata.ui.Sound;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuppressLint("MissingPermission")
public class MainActivity extends Activity {

    private static final int MODE_NONE = 0, MODE_LOCAL = 1, MODE_HOST = 2, MODE_CLIENT = 3;
    private static final int REQ_PERM = 1, REQ_ENABLE = 2, REQ_DISCOVERABLE = 3;
    private static final int MAX_PLAYERS = 6;
    private static final long AUCTION_MS = 10000;

    // paleta cartoon
    private static final int BG = 0xFFFDF6E3;
    private static final int INK = 0xFF33302A;
    private static final int GREEN = 0xFF2E7D32;
    private static final int GREEN_L = 0xFF43A047;
    private static final int BLUE = 0xFF1E88E5;
    private static final int GOLD = 0xFFF9A825;
    private static final int ORANGE = 0xFFFB8C00;
    private static final int RED = 0xFFE53935;
    private static final int PURPLE = 0xFF8E24AA;
    private static final int GREY = 0xFF90A4AE;
    private static final int BROWN = 0xFF8D6E63;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private BluetoothAdapter bt;
    private Sound sound;

    private int mode = MODE_NONE;
    private Game game;      // estado verdadeiro (anfitrião/local)
    private Game display;   // estado desenhado (depois das animações)
    private final ArrayDeque<Game> queue = new ArrayDeque<>();
    private boolean animating;
    private int myIndex = 0;
    private boolean leaving;
    private int maxRounds;

    // anfitrião
    private Bt.Server server;
    private Wifi.Server wifiServer;
    private boolean hostWifi;
    private final List<Link> clients = new ArrayList<>();
    private boolean gameStarted;

    // cliente
    private Link hostLink;
    private List<Player.Setup> lobbySetups = new ArrayList<>();
    private int lobbyRounds;
    private Wifi.Finder finder;
    private WifiManager.MulticastLock multicastLock;
    private final List<String[]> rooms = new ArrayList<>();

    // modo local
    private final List<Player.Setup> localPlayers = new ArrayList<>();

    // telas
    private String screen = "";
    private LinearLayout listBox;
    private TextView statusText;
    private final List<BluetoothDevice> found = new ArrayList<>();

    // jogo
    private BoardView board;
    private LinearLayout chipsRow, panel, actionsBox;
    private TextView eventPill, gameStatus, auctionLabel;
    private Button powersBtn;
    private int shownAuctionSeq = -1;
    private long auctionSeenAt;
    private final ArrayDeque<String[]> tipQueue = new ArrayDeque<>();
    private boolean tipShowing;
    private boolean endShown;

    private Runnable afterPermission;

    // ================================================================= ciclo de vida

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("magnata", MODE_PRIVATE);
        BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bt = bm != null ? bm.getAdapter() : null;
        sound = new Sound(this);
        sound.enabled = prefs.getBoolean("sound", true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(0xFF5BAFD6);

        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothDevice.ACTION_FOUND);
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(receiver, f);

        ui.post(ticker);
        showMenu();
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(receiver); } catch (Exception ignored) { }
        ui.removeCallbacks(ticker);
        shutdownNetwork();
        sound.release();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (screen.equals("menu")) { super.onBackPressed(); return; }
        if (screen.equals("game")) { confirmExit(); return; }
        shutdownNetwork();
        showMenu();
    }

    // ================================================================= helpers de interface

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private GradientDrawable rounded(int color, float radiusDp, int strokeColor, float strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    private ScrollView page(LinearLayout content) {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        content.setPadding(dp(16), dp(16), dp(16), dp(28));
        sv.addView(content);
        return sv;
    }

    private TextView title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(32);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        t.setTextColor(0xFFFFB300);
        t.setShadowLayer(dp(2), dp(2), dp(2), INK);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(8), 0, dp(6));
        return t;
    }

    private TextView label(String s, float size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(INK);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private TextView bold(String s, float size) {
        TextView t = label(s, size);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String s, int color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.WHITE);
        b.setBackground(rounded(color, 14, Pawns.darker(color, 0.65f), 2.5f));
        b.setShadowLayer(dp(1), 0, dp(1), 0x66000000);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> { sound.play("pop"); l.onClick(v); });
        return b;
    }

    /** Botões lado a lado, dividindo a largura. */
    private LinearLayout buttonRow(Button... bs) {
        LinearLayout r = row();
        for (int i = 0; i < bs.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(50), 1f);
            lp.topMargin = dp(6);
            if (i > 0) lp.leftMargin = dp(6);
            bs[i].setLayoutParams(lp);
            bs[i].setTextSize(14);
            r.addView(bs[i]);
        }
        return r;
    }

    private EditText input(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(true);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(14)});
        e.setTextColor(INK);
        return e;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private TextView card(String s, int bg) {
        TextView t = label(s, 15);
        t.setBackground(rounded(bg, 14, INK, 2));
        t.setPadding(dp(14), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    private AlertDialog dialog(String titleText, View content) {
        ScrollView sv = new ScrollView(this);
        LinearLayout wrap = column();
        wrap.setPadding(dp(18), dp(6), dp(18), dp(8));
        wrap.addView(content);
        sv.addView(wrap);
        return new AlertDialog.Builder(this).setTitle(titleText).setView(sv).setPositiveButton("Fechar", null).show();
    }

    // ================================================================= perfil

    private Player.Setup myProfile() {
        String n = prefs.getString("name", "").trim();
        int ci = prefs.getInt("color", 0);
        return new Player.Setup(n.isEmpty() ? "Jogador" : n, Rules.COLORS[Math.max(0, Math.min(7, ci))],
                prefs.getInt("pawn", 0), prefs.getInt("char", 0));
    }

    private void saveProfile(Player.Setup s) {
        int ci = 0;
        for (int i = 0; i < Rules.COLORS.length; i++) if (Rules.COLORS[i] == s.color) ci = i;
        prefs.edit().putString("name", s.name).putInt("color", ci).putInt("pawn", s.pawn).putInt("char", s.character).apply();
    }

    /** Editor de nome, cor, peão e personagem. */
    private View profileEditor(final Player.Setup s, final Set<Integer> takenColors) {
        final LinearLayout box = column();
        final EditText name = input("Nome", s.name);
        name.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence a, int b, int c, int d) { }
            @Override public void onTextChanged(CharSequence a, int b, int c, int d) { }
            @Override public void afterTextChanged(Editable e) {
                String v = e.toString().trim();
                s.name = v.isEmpty() ? "Jogador" : v;
            }
        });
        box.addView(bold("Nome", 15));
        box.addView(name);

        box.addView(bold("Cor", 15));
        final LinearLayout colors = row();
        box.addView(colors);
        box.addView(bold("Peão", 15));
        final LinearLayout pawns1 = row(), pawns2 = row();
        box.addView(pawns1);
        box.addView(pawns2);
        box.addView(bold("Personagem", 15));
        final LinearLayout chars = column();
        box.addView(chars);

        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            colors.removeAllViews();
            for (int i = 0; i < Rules.COLORS.length; i++) {
                final int col = Rules.COLORS[i];
                boolean taken = takenColors != null && takenColors.contains(col) && col != s.color;
                TextView sw = new TextView(this);
                sw.setGravity(Gravity.CENTER);
                sw.setText(taken ? "✕" : (col == s.color ? "✓" : ""));
                sw.setTextColor(Color.WHITE);
                sw.setTypeface(Typeface.DEFAULT_BOLD);
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(taken ? Pawns.lighter(col, 0.6f) : col);
                g.setStroke(dp(col == s.color ? 4 : 2), INK);
                sw.setBackground(g);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(36), 1f);
                lp.setMargins(dp(3), dp(4), dp(3), dp(4));
                sw.setLayoutParams(lp);
                if (!taken) sw.setOnClickListener(v -> { s.color = col; refresh[0].run(); });
                colors.addView(sw);
            }
            pawns1.removeAllViews();
            pawns2.removeAllViews();
            for (int i = 0; i < Pawns.COUNT; i++) {
                final int pi = i;
                LinearLayout cell = column();
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                PawnView pv = new PawnView(this, i, s.color);
                pv.setChosen(i == s.pawn);
                cell.addView(pv, new LinearLayout.LayoutParams(dp(54), dp(54)));
                TextView l = label(Rules.PAWN_NAMES[i], 11);
                l.setGravity(Gravity.CENTER);
                cell.addView(l);
                cell.setOnClickListener(v -> { s.pawn = pi; refresh[0].run(); });
                (i < 4 ? pawns1 : pawns2).addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            chars.removeAllViews();
            for (int i = 0; i < Rules.CHAR_NAMES.length; i++) {
                final int ci = i;
                TextView t = card(Rules.CHAR_ICONS[i] + "  " + Rules.CHAR_NAMES[i] + "\n" + Rules.CHAR_DESC[i],
                        i == s.character ? Pawns.lighter(s.color, 0.6f) : 0xFFFFFFFF);
                t.setTextSize(14);
                t.setOnClickListener(v -> { s.character = ci; refresh[0].run(); });
                chars.addView(t);
            }
        };
        refresh[0].run();
        return box;
    }

    // ================================================================= menu

    private void showMenu() {
        screen = "menu";
        mode = MODE_NONE;
        game = null;
        display = null;
        queue.clear();
        animating = false;
        LinearLayout c = column();
        c.addView(title("🎲 MAGNATA 🏠"));
        TextView sub = label("Compre cidades, use poderes, cumpra missões e seja o maior magnata!", 15);
        sub.setGravity(Gravity.CENTER);
        c.addView(sub);

        Player.Setup me = myProfile();
        LinearLayout prof = row();
        prof.setBackground(rounded(0xFFFFFFFF, 16, INK, 2));
        prof.setPadding(dp(10), dp(8), dp(10), dp(8));
        PawnView pv = new PawnView(this, me.pawn, me.color);
        prof.addView(pv, new LinearLayout.LayoutParams(dp(60), dp(60)));
        LinearLayout info = column();
        info.setPadding(dp(10), 0, 0, 0);
        TextView nm = bold(me.name, 18);
        nm.setTextColor(me.color);
        info.addView(nm);
        info.addView(label(Rules.CHAR_ICONS[me.character] + " " + Rules.CHAR_NAMES[me.character] + " · " + Rules.PAWN_NAMES[me.pawn], 14));
        TextView edit = label("Toque para mudar cor, peão e personagem", 12);
        edit.setTextColor(0xFF757575);
        info.addView(edit);
        prof.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        prof.setOnClickListener(v -> editMyProfile());
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(10);
        plp.bottomMargin = dp(6);
        c.addView(prof, plp);

        c.addView(button("📶  Criar sala", GREEN, v -> chooseTransport(true)));
        c.addView(button("🔗  Entrar numa sala", BLUE, v -> chooseTransport(false)));
        c.addView(button("📱  Jogar no mesmo celular", ORANGE, v -> showLocalSetup()));
        c.addView(button("📖  Guia do jogo", PURPLE, v -> showGuide()));
        c.addView(button(sound.enabled ? "🔊  Som: ligado" : "🔇  Som: desligado", GREY, v -> {
            sound.enabled = !sound.enabled;
            prefs.edit().putBoolean("sound", sound.enabled).apply();
            showMenu();
        }));
        setContentView(page(c));
    }

    private void editMyProfile() {
        final Player.Setup s = myProfile();
        ScrollView sv = new ScrollView(this);
        LinearLayout w = column();
        w.setPadding(dp(18), dp(6), dp(18), dp(6));
        w.addView(profileEditor(s, null));
        sv.addView(w);
        new AlertDialog.Builder(this).setTitle("Seu perfil").setView(sv)
                .setPositiveButton("Salvar", (d, x) -> { saveProfile(s); showMenu(); })
                .setNegativeButton("Cancelar", null).show();
    }

    private void chooseTransport(final boolean host) {
        String[] opts = {"📡  Wi-Fi (todos na mesma rede)", "🔵  Bluetooth"};
        new AlertDialog.Builder(this)
                .setTitle(host ? "Criar sala por..." : "Entrar na sala por...")
                .setItems(opts, (d, w) -> {
                    if (w == 0) { if (host) startHostWifi(); else showJoinWifi(); }
                    else { if (host) startHost(); else startJoin(); }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ================================================================= guia

    private View guideContent() {
        LinearLayout c = column();
        c.addView(label("Um resumo rápido de cada mecânica. Na primeira vez que cada uma aparecer na partida, o jogo também mostra uma dica.", 14));
        for (String[] g : Rules.GUIDE) {
            TextView t = card(g[0] + "\n" + g[1], 0xFFFFFFFF);
            c.addView(t);
        }
        c.addView(bold("\n🧑 Personagens", 17));
        for (int i = 0; i < Rules.CHAR_NAMES.length; i++)
            c.addView(label(Rules.CHAR_ICONS[i] + " " + Rules.CHAR_NAMES[i] + ": " + Rules.CHAR_DESC[i], 14));
        c.addView(bold("\n🃏 Cartas de poder", 17));
        for (int i = 0; i < Rules.POWER_NAMES.length; i++)
            c.addView(label(Rules.POWER_ICONS[i] + " " + Rules.POWER_NAMES[i] + ": " + Rules.POWER_DESC[i], 14));
        c.addView(bold("\n🌎 Eventos globais", 17));
        for (int i = 0; i < Rules.EVENT_NAMES.length; i++)
            c.addView(label(Rules.EVENT_ICONS[i] + " " + Rules.EVENT_NAMES[i] + ": " + Rules.EVENT_DESC[i], 14));
        c.addView(bold("\n🎡 Roleta", 17));
        c.addView(label(String.join(" · ", Rules.ROULETTE), 14));
        return c;
    }

    private void showGuide() {
        screen = "guide";
        LinearLayout c = column();
        c.addView(title("📖 Guia"));
        c.addView(guideContent());
        c.addView(button("Voltar", GREY, v -> showMenu()));
        setContentView(page(c));
    }

    // ================================================================= modo local

    private void showLocalSetup() {
        screen = "local";
        if (localPlayers.isEmpty()) {
            localPlayers.add(myProfile());
            addLocalPlayer();
        }
        LinearLayout c = column();
        c.addView(title("📱 Mesmo celular"));
        c.addView(label("De 2 a 6 jogadores passando o celular. Toque num jogador para escolher nome, cor, peão e personagem.", 15));
        for (int i = 0; i < localPlayers.size(); i++) {
            final int idx = i;
            c.addView(playerRow(localPlayers.get(i), v -> editLocalPlayer(idx),
                    localPlayers.size() > 2 ? v -> { localPlayers.remove(idx); showLocalSetup(); } : null));
        }
        if (localPlayers.size() < MAX_PLAYERS) c.addView(button("➕  Adicionar jogador", BLUE, v -> { addLocalPlayer(); showLocalSetup(); }));
        c.addView(modeSelector());
        c.addView(button("▶  Começar", GREEN, v -> {
            mode = MODE_LOCAL;
            myIndex = -1;
            game = new Game(localPlayers.toArray(new Player.Setup[0]), maxRounds, new SecureRandom());
            showGame();
        }));
        c.addView(button("Voltar", GREY, v -> showMenu()));
        setContentView(page(c));
    }

    private void addLocalPlayer() {
        Set<Integer> used = new HashSet<>();
        Set<Integer> usedPawns = new HashSet<>();
        for (Player.Setup s : localPlayers) { used.add(s.color); usedPawns.add(s.pawn); }
        int col = Rules.COLORS[0];
        for (int cc : Rules.COLORS) if (!used.contains(cc)) { col = cc; break; }
        int pw = 0;
        for (int i = 0; i < Pawns.COUNT; i++) if (!usedPawns.contains(i)) { pw = i; break; }
        localPlayers.add(new Player.Setup("Jogador " + (localPlayers.size() + 1), col, pw, localPlayers.size() % Rules.CHAR_NAMES.length));
    }

    private void editLocalPlayer(int idx) {
        final Player.Setup s = localPlayers.get(idx);
        final Player.Setup copy = new Player.Setup(s.name, s.color, s.pawn, s.character);
        Set<Integer> taken = new HashSet<>();
        for (int i = 0; i < localPlayers.size(); i++) if (i != idx) taken.add(localPlayers.get(i).color);
        ScrollView sv = new ScrollView(this);
        LinearLayout w = column();
        w.setPadding(dp(18), dp(6), dp(18), dp(6));
        w.addView(profileEditor(copy, taken));
        sv.addView(w);
        new AlertDialog.Builder(this).setTitle("Jogador " + (idx + 1)).setView(sv)
                .setPositiveButton("OK", (d, x) -> {
                    s.name = copy.name; s.color = copy.color; s.pawn = copy.pawn; s.character = copy.character;
                    showLocalSetup();
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private View playerRow(Player.Setup s, View.OnClickListener onTap, View.OnClickListener onRemove) {
        LinearLayout r = row();
        r.setBackground(rounded(0xFFFFFFFF, 14, INK, 2));
        r.setPadding(dp(8), dp(6), dp(8), dp(6));
        r.addView(new PawnView(this, s.pawn, s.color), new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout info = column();
        info.setPadding(dp(8), 0, 0, 0);
        TextView n = bold(s.name, 16);
        n.setTextColor(s.color);
        info.addView(n);
        info.addView(label(Rules.CHAR_ICONS[s.character] + " " + Rules.CHAR_NAMES[s.character], 13));
        r.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (onRemove != null) {
            TextView x = bold("✕", 20);
            x.setPadding(dp(12), 0, dp(6), 0);
            x.setOnClickListener(onRemove);
            r.addView(x);
        }
        if (onTap != null) r.setOnClickListener(onTap);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        r.setLayoutParams(lp);
        return r;
    }

    private View modeSelector() {
        LinearLayout box = column();
        box.addView(bold("\nModo de jogo", 16));
        int sel = prefs.getInt("roundsMode", 0);
        maxRounds = Rules.ROUND_OPTIONS[sel];
        for (int i = 0; i < Rules.ROUND_NAMES.length; i++) {
            final int idx = i;
            TextView t = card((i == sel ? "◉  " : "○  ") + Rules.ROUND_NAMES[i], i == sel ? 0xFFFFF59D : 0xFFFFFFFF);
            t.setOnClickListener(v -> {
                prefs.edit().putInt("roundsMode", idx).apply();
                maxRounds = Rules.ROUND_OPTIONS[idx];
                if (screen.equals("local")) showLocalSetup();
                else if (screen.equals("hostLobby")) { showHostLobby(); broadcastLobby(); }
            });
            box.addView(t);
        }
        return box;
    }

    // ================================================================= permissões Bluetooth

    private void ensureBluetooth(Runnable next) {
        if (bt == null) { toast("Este celular não tem Bluetooth."); return; }
        String[] perms = Build.VERSION.SDK_INT >= 31
                ? new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE}
                : new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        List<String> missing = new ArrayList<>();
        for (String p : perms) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        afterPermission = next;
        if (!missing.isEmpty()) {
            requestPermissions(missing.toArray(new String[0]), REQ_PERM);
            return;
        }
        enableThen();
    }

    private void enableThen() {
        if (!bt.isEnabled()) {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQ_ENABLE);
            return;
        }
        Runnable r = afterPermission;
        afterPermission = null;
        if (r != null) r.run();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code != REQ_PERM) return;
        for (int r : results) {
            if (r != PackageManager.PERMISSION_GRANTED) {
                toast("O jogo precisa da permissão de Bluetooth para jogar com amigos.");
                afterPermission = null;
                return;
            }
        }
        enableThen();
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == REQ_ENABLE) {
            if (bt.isEnabled()) enableThen();
            else { toast("Ligue o Bluetooth para continuar."); afterPermission = null; }
        }
    }

    // ================================================================= anfitrião

    private void startHost() {
        ensureBluetooth(() -> {
            shutdownNetwork();
            prepareHost(false);
            server = new Bt.Server();
            server.start(bt, hostServerCallback);
            requestDiscoverable();
            showHostLobby();
        });
    }

    private void startHostWifi() {
        shutdownNetwork();
        if (Wifi.localIps().isEmpty()) {
            toast("Conecte este celular a uma rede Wi-Fi (ou ligue o roteador do celular).");
            return;
        }
        prepareHost(true);
        wifiServer = new Wifi.Server();
        wifiServer.start(myProfile().name, hostServerCallback);
        if (wifiServer.port < 0) {
            wifiServer = null;
            mode = MODE_NONE;
            return;
        }
        showHostLobby();
    }

    private void prepareHost(boolean wifi) {
        mode = MODE_HOST;
        hostWifi = wifi;
        myIndex = 0;
        gameStarted = false;
        leaving = false;
        clients.clear();
        maxRounds = Rules.ROUND_OPTIONS[prefs.getInt("roundsMode", 0)];
    }

    private void requestDiscoverable() {
        try {
            Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            i.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivityForResult(i, REQ_DISCOVERABLE);
        } catch (Exception e) {
            toast("Não foi possível ficar visível.");
        }
    }

    private final Bt.ServerCallback hostServerCallback = new Bt.ServerCallback() {
        @Override public void onClient(final Link link) {
            ui.post(() -> {
                if (mode != MODE_HOST || gameStarted || clients.size() >= MAX_PLAYERS - 1) {
                    link.close();
                    return;
                }
                clients.add(link);
                link.start(hostLinkListener);
            });
        }
        @Override public void onServerError(final String msg) {
            ui.post(() -> {
                if (gameStarted) return;
                new AlertDialog.Builder(MainActivity.this).setTitle("Problema na sala").setMessage(msg)
                        .setPositiveButton("OK", null).show();
            });
        }
    };

    private final Link.Listener hostLinkListener = new Link.Listener() {
        @Override public void onMessage(final Link link, final byte type, final byte[] data) {
            ui.post(() -> hostHandle(link, type, data));
        }
        @Override public void onClosed(final Link link) {
            ui.post(() -> hostClientGone(link));
        }
    };

    private void hostHandle(Link link, byte type, byte[] data) {
        if (mode != MODE_HOST) return;
        if (type == Link.HELLO) {
            Player.Setup s = Msg.readSetup(data);
            if (s == null) {
                link.send(Link.ERROR, Msg.str("Versão do jogo diferente. Instale a mesma versão do anfitrião."));
                ui.postDelayed(link::close, 500);
                return;
            }
            if (gameStarted) { link.close(); return; }
            if (s.name.trim().isEmpty()) s.name = "Amigo";
            link.setup = s;
            link.name = s.name;
            assignColors();
            broadcastLobby();
            if (screen.equals("hostLobby")) showHostLobby();
        } else if (type == Link.ACTION) {
            if (game == null || link.tag < 0) return;
            int[] v = Msg.readAction(data);
            int[] args = new int[v.length - 1];
            System.arraycopy(v, 1, args, 0, args.length);
            String err = game.apply(link.tag, v[0], args);
            if (err != null) link.send(Link.ERROR, Msg.str(err));
            else authoritativeChanged();
        }
    }

    private void hostClientGone(Link link) {
        if (!clients.remove(link)) return;
        if (mode != MODE_HOST) return;
        if (!gameStarted) {
            assignColors();
            broadcastLobby();
            if (screen.equals("hostLobby")) showHostLobby();
        } else if (game != null && link.tag >= 0 && game.phase != Game.PH_OVER && !game.players[link.tag].bankrupt) {
            toast(link.name + " desconectou e saiu da partida.");
            game.apply(link.tag, Game.A_BANKRUPT);
            authoritativeChanged();
        }
    }

    /** Garante cores diferentes: quem entrou depois troca se a cor já estiver em uso. */
    private void assignColors() {
        Set<Integer> used = new HashSet<>();
        used.add(myProfile().color);
        for (Link l : clients) {
            if (l.setup == null) continue;
            if (used.contains(l.setup.color)) {
                for (int c : Rules.COLORS) if (!used.contains(c)) { l.setup.color = c; break; }
            }
            used.add(l.setup.color);
        }
    }

    private List<Player.Setup> lobbyList() {
        List<Player.Setup> list = new ArrayList<>();
        list.add(myProfile());
        for (Link l : clients) if (l.setup != null) list.add(l.setup);
        return list;
    }

    private void broadcastLobby() {
        if (mode != MODE_HOST) return;
        byte[] d = Msg.lobby(lobbyList(), maxRounds);
        for (Link l : clients) if (l.setup != null) l.send(Link.LOBBY, d);
    }

    private void showHostLobby() {
        screen = "hostLobby";
        LinearLayout c = column();
        c.addView(title("🏠 Sala aberta"));
        Player.Setup me = myProfile();
        if (hostWifi) {
            c.addView(label("Sala por Wi-Fi. Os amigos precisam estar na mesma rede, tocar em \"Entrar numa sala\" > Wi-Fi "
                    + "e escolher a sala de " + me.name + ".", 15));
            List<String> addrs = new ArrayList<>();
            int port = wifiServer != null ? wifiServer.port : Wifi.PORT;
            for (String ip : Wifi.localIps()) addrs.add(port == Wifi.PORT ? ip : ip + ":" + port);
            TextView ipT = bold("Se a sala não aparecer, digitem: " + String.join("  ou  ", addrs), 15);
            c.addView(ipT);
        } else {
            String btName = null;
            try { btName = bt != null ? bt.getName() : null; } catch (SecurityException ignored) { }
            c.addView(label("Sala por Bluetooth. Os amigos tocam em \"Entrar numa sala\" > Bluetooth e escolhem este celular"
                    + (btName != null ? " (\"" + btName + "\")" : "") + ".", 15));
        }
        c.addView(bold("\nJogadores na sala", 16));
        List<Player.Setup> list = lobbyList();
        for (int i = 0; i < list.size(); i++) c.addView(playerRow(list.get(i), null, null));
        if (list.size() < 2) c.addView(label("Aguardando amigos se conectarem...", 14));
        c.addView(modeSelector());
        if (!hostWifi) c.addView(button("👁  Ficar visível de novo", BLUE, v -> requestDiscoverable()));
        c.addView(button("▶  Começar partida", GREEN, v -> hostStartGame()));
        c.addView(button("Cancelar", GREY, v -> { shutdownNetwork(); showMenu(); }));
        setContentView(page(c));
    }

    private void hostStartGame() {
        List<Link> ready = new ArrayList<>();
        for (Link l : new ArrayList<>(clients)) {
            if (l.setup == null) { l.close(); continue; }
            ready.add(l);
        }
        if (ready.isEmpty()) { toast("Espere pelo menos 1 amigo entrar na sala."); return; }
        assignColors();
        List<Player.Setup> setups = new ArrayList<>();
        setups.add(myProfile());
        for (int i = 0; i < ready.size(); i++) {
            Link l = ready.get(i);
            l.tag = i + 1;
            setups.add(l.setup);
            l.send(Link.WELCOME, Msg.ints(l.tag));
        }
        gameStarted = true;
        if (server != null) server.stop();
        if (wifiServer != null) wifiServer.stop();
        game = new Game(setups.toArray(new Player.Setup[0]), maxRounds, new SecureRandom());
        showGame();
    }

    /** O estado verdadeiro mudou: anima localmente e manda para todos. */
    private void authoritativeChanged() {
        if (game == null) return;
        byte[] s = game.toBytes();
        if (mode == MODE_HOST) for (Link l : clients) if (l.tag >= 0) l.send(Link.STATE, s);
        try { pushState(Game.fromBytes(s)); } catch (Exception ignored) { }
    }

    // ================================================================= cliente Bluetooth

    private void startJoin() {
        ensureBluetooth(() -> {
            shutdownNetwork();
            showJoin();
        });
    }

    private void showJoin() {
        screen = "join";
        found.clear();
        LinearLayout c = column();
        c.addView(title("🔵 Entrar"));
        c.addView(label("Toque no celular de quem criou a sala:", 15));
        statusText = label("", 14);
        c.addView(statusText);
        listBox = column();
        c.addView(listBox);
        c.addView(button("🔍  Procurar aparelhos", BLUE, v -> startDiscovery()));
        c.addView(button("Voltar", GREY, v -> { stopDiscovery(); showMenu(); }));
        setContentView(page(c));
        Set<BluetoothDevice> bonded = null;
        try { bonded = bt.getBondedDevices(); } catch (SecurityException ignored) { }
        if (bonded != null) found.addAll(bonded);
        refreshDeviceList();
        startDiscovery();
    }

    private void startDiscovery() {
        try {
            if (bt.isDiscovering()) bt.cancelDiscovery();
            bt.startDiscovery();
            if (statusText != null) statusText.setText("Procurando aparelhos próximos...");
        } catch (SecurityException e) {
            toast("Sem permissão para procurar aparelhos.");
        }
    }

    private void stopDiscovery() {
        try { if (bt != null && bt.isDiscovering()) bt.cancelDiscovery(); } catch (SecurityException ignored) { }
    }

    private String deviceName(BluetoothDevice d) {
        String n = null;
        try { n = d.getName(); } catch (SecurityException ignored) { }
        return n == null || n.isEmpty() ? d.getAddress() : n;
    }

    private void refreshDeviceList() {
        if (listBox == null || !screen.equals("join")) return;
        listBox.removeAllViews();
        if (found.isEmpty()) listBox.addView(label("Nenhum aparelho ainda.", 14));
        for (final BluetoothDevice d : found) listBox.addView(button("📱  " + deviceName(d), GREEN, v -> connectTo(d)));
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent i) {
            String a = i.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(a)) {
                BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (d == null) return;
                for (BluetoothDevice x : found) if (x.getAddress().equals(d.getAddress())) return;
                String n = null;
                try { n = d.getName(); } catch (SecurityException ignored) { }
                if (n == null) return;
                found.add(d);
                refreshDeviceList();
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(a)) {
                if (statusText != null && screen.equals("join")) statusText.setText("Busca concluída. Não achou? Toque em Procurar de novo.");
            }
        }
    };

    private void connectTo(BluetoothDevice d) {
        stopDiscovery();
        if (statusText != null) statusText.setText("Conectando a " + deviceName(d) + "...");
        Bt.connect(bt, d, new Bt.ConnectCallback() {
            @Override public void onConnected(final Link link) { ui.post(() -> onJoined(link)); }
            @Override public void onConnectFailed(final String msg) {
                ui.post(() -> { if (statusText != null) statusText.setText(msg); });
            }
        });
    }

    private void onJoined(Link link) {
        if (!screen.equals("join") && !screen.equals("joinWifi")) { link.close(); return; }
        stopFinder();
        mode = MODE_CLIENT;
        leaving = false;
        hostLink = link;
        myIndex = -1;
        lobbySetups = new ArrayList<>();
        link.start(clientListener);
        link.send(Link.HELLO, Msg.setup(myProfile()));
        showWaiting();
    }

    // ================================================================= cliente Wi-Fi

    private void showJoinWifi() {
        shutdownNetwork();
        screen = "joinWifi";
        rooms.clear();
        LinearLayout c = column();
        c.addView(title("📡 Entrar"));
        c.addView(label("Salas encontradas na sua rede:", 15));
        statusText = label("Procurando salas...", 14);
        c.addView(statusText);
        listBox = column();
        c.addView(listBox);
        c.addView(label("Ou digite o endereço que aparece no celular do anfitrião:", 15));
        final EditText ip = input("Ex.: 192.168.0.10", prefs.getString("lastIp", ""));
        ip.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        c.addView(ip);
        c.addView(button("Conectar neste endereço", BLUE, v -> {
            String a = ip.getText().toString().trim();
            if (a.isEmpty()) { toast("Digite o endereço."); return; }
            prefs.edit().putString("lastIp", a).apply();
            String host = a;
            int[] ports = {Wifi.PORT, Wifi.PORT + 1, Wifi.PORT + 2, Wifi.PORT + 3};
            int ci = a.lastIndexOf(':');
            if (ci > 0) {
                try { ports = new int[]{Integer.parseInt(a.substring(ci + 1).trim())}; host = a.substring(0, ci); }
                catch (NumberFormatException ignored) { }
            }
            List<String> one = new ArrayList<>();
            one.add(host);
            connectWifi(one, ports);
        }));
        c.addView(button("Voltar", GREY, v -> { stopFinder(); showMenu(); }));
        setContentView(page(c));
        refreshRooms();
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                multicastLock = wm.createMulticastLock("magnata");
                multicastLock.setReferenceCounted(false);
                multicastLock.acquire();
            }
        } catch (Exception ignored) { }
        finder = new Wifi.Finder();
        finder.start((name, addr, port, ips) -> ui.post(() -> {
            if (!screen.equals("joinWifi")) return;
            String alts = String.join(",", ips);
            for (String[] r : rooms) {
                if (r[1].equals(addr)) {
                    if (!r[0].equals(name) || !r[2].equals(String.valueOf(port))) { r[0] = name; r[2] = String.valueOf(port); r[3] = alts; refreshRooms(); }
                    return;
                }
            }
            rooms.add(new String[]{name, addr, String.valueOf(port), alts});
            refreshRooms();
        }));
    }

    private void refreshRooms() {
        if (listBox == null || !screen.equals("joinWifi")) return;
        listBox.removeAllViews();
        if (rooms.isEmpty()) listBox.addView(label("Nenhuma sala ainda. Confira se o anfitrião criou a sala por Wi-Fi e se vocês estão na mesma rede.", 14));
        for (final String[] r : rooms) {
            listBox.addView(button("🎲  Entrar na sala de " + r[0], GREEN, v -> {
                List<String> ips = new ArrayList<>();
                ips.add(r[1]);
                for (String x : r[3].split(",")) if (!x.trim().isEmpty()) ips.add(x.trim());
                int port = Wifi.PORT;
                try { port = Integer.parseInt(r[2]); } catch (NumberFormatException ignored) { }
                connectWifi(ips, new int[]{port});
            }));
        }
    }

    private void connectWifi(List<String> ips, int[] ports) {
        if (statusText != null) statusText.setText("Conectando... (pode levar alguns segundos)");
        Wifi.connect(ips, ports, new Bt.ConnectCallback() {
            @Override public void onConnected(final Link link) { ui.post(() -> onJoined(link)); }
            @Override public void onConnectFailed(final String msg) {
                ui.post(() -> { if (statusText != null && screen.equals("joinWifi")) statusText.setText(msg); });
            }
        });
    }

    private void stopFinder() {
        if (finder != null) { finder.stop(); finder = null; }
        if (multicastLock != null) {
            try { multicastLock.release(); } catch (Exception ignored) { }
            multicastLock = null;
        }
    }

    private final Link.Listener clientListener = new Link.Listener() {
        @Override public void onMessage(Link link, final byte type, final byte[] data) {
            ui.post(() -> clientHandle(type, data));
        }
        @Override public void onClosed(Link link) {
            ui.post(() -> {
                if (mode != MODE_CLIENT || leaving) return;
                boolean over = display != null && display.phase == Game.PH_OVER;
                mode = MODE_NONE;
                hostLink = null;
                if (over) return;
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Conexão perdida")
                        .setMessage("A conexão com o anfitrião caiu.")
                        .setCancelable(false)
                        .setPositiveButton("Voltar ao menu", (dl, w) -> showMenu())
                        .show();
            });
        }
    };

    private void clientHandle(byte type, byte[] data) {
        if (mode != MODE_CLIENT) return;
        switch (type) {
            case Link.WELCOME:
                myIndex = Msg.readInts(data, 1)[0];
                break;
            case Link.LOBBY: {
                int[] out = new int[1];
                lobbySetups = Msg.readLobby(data, out);
                lobbyRounds = out[0];
                if (screen.equals("waiting")) showWaiting();
                break;
            }
            case Link.STATE: {
                Game g;
                try { g = Game.fromBytes(data); } catch (Exception e) { return; }
                if (!screen.equals("game")) showGame();
                pushState(g);
                break;
            }
            case Link.ERROR: {
                String msg = Msg.readStr(data);
                if (!screen.equals("game")) {
                    leaving = true;
                    new AlertDialog.Builder(this).setTitle("Não foi possível entrar").setMessage(msg)
                            .setCancelable(false)
                            .setPositiveButton("OK", (d, w) -> { shutdownNetwork(); showMenu(); }).show();
                } else {
                    toast(msg);
                }
                break;
            }
            default:
                break;
        }
    }

    private void showWaiting() {
        screen = "waiting";
        LinearLayout c = column();
        c.addView(title("✅ Na sala!"));
        c.addView(label("Conectado. Aguarde o anfitrião começar a partida.", 15));
        for (Player.Setup s : lobbySetups) c.addView(playerRow(s, null, null));
        int mi = 0;
        for (int i = 0; i < Rules.ROUND_OPTIONS.length; i++) if (Rules.ROUND_OPTIONS[i] == lobbyRounds) mi = i;
        c.addView(label("\nModo: " + Rules.ROUND_NAMES[mi], 15));
        c.addView(button("Sair da sala", GREY, v -> { shutdownNetwork(); showMenu(); }));
        setContentView(page(c));
    }

    private void shutdownNetwork() {
        leaving = true;
        stopDiscovery();
        stopFinder();
        if (server != null) { server.stop(); server = null; }
        if (wifiServer != null) { wifiServer.stop(); wifiServer = null; }
        for (Link l : new ArrayList<>(clients)) l.close();
        clients.clear();
        if (hostLink != null) { hostLink.close(); hostLink = null; }
        gameStarted = false;
    }

    // ================================================================= tela do jogo

    private void showGame() {
        screen = "game";
        display = null;
        queue.clear();
        animating = false;
        endShown = false;
        shownAuctionSeq = -1;

        FrameLayout root = new FrameLayout(this);
        board = new BoardView(this);
        board.setOnSquareTap(this::showSquare);
        board.setFx(n -> sound.play(n));
        board.setInsets(dp(100), dp(225));
        root.addView(board, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // topo: placar + evento
        LinearLayout top = column();
        top.setPadding(dp(6), dp(6), dp(6), 0);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        chipsRow = row();
        hs.addView(chipsRow);
        top.addView(hs);
        eventPill = label("", 13);
        eventPill.setBackground(rounded(0xEEFFFFFF, 12, INK, 1.5f));
        eventPill.setPadding(dp(10), dp(4), dp(10), dp(4));
        eventPill.setOnClickListener(v -> {
            if (display != null && display.eventId >= 0) tipNow("🌎 Eventos globais", Rules.GUIDE[8][1]);
        });
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.topMargin = dp(4);
        top.addView(eventPill, elp);
        root.addView(top, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        // baixo: painel
        panel = column();
        panel.setBackground(rounded(0xF2FFFFFF, 22, INK, 2.5f));
        panel.setPadding(dp(12), dp(8), dp(12), dp(8));
        gameStatus = bold("", 15);
        gameStatus.setGravity(Gravity.CENTER);
        panel.addView(gameStatus);
        actionsBox = column();
        panel.addView(actionsBox);
        LinearLayout tools = row();
        tools.setPadding(0, dp(6), 0, 0);
        tools.addView(tool("🏠", "Bens", v -> showMyProperties(viewer())));
        powersBtn = tool("🃏", "Poderes", v -> showPowers());
        tools.addView(powersBtn);
        tools.addView(tool("🤝", "Negociar", v -> showDeals()));
        tools.addView(tool("🎯", "Missão", v -> showMission()));
        tools.addView(tool("☰", "Menu", v -> showGameMenu()));
        panel.addView(tools);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        plp.setMargins(dp(6), 0, dp(6), dp(6));
        root.addView(panel, plp);

        // controles do tabuleiro (girar, centralizar, emoji)
        LinearLayout ctrls = column();
        ctrls.addView(roundBtn("⟲", v -> board.rotateBy(-90)));
        ctrls.addView(roundBtn("⟳", v -> board.rotateBy(90)));
        ctrls.addView(roundBtn("⛶", v -> board.recenter()));
        ctrls.addView(roundBtn("😀", v -> showEmojis()));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL);
        clp.setMargins(0, 0, dp(8), dp(60));
        root.addView(ctrls, clp);

        setContentView(root);
        if (mode == MODE_LOCAL || mode == MODE_HOST) authoritativeChanged();
    }

    private Button tool(String icon, String name, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(icon + "\n" + name);
        b.setAllCaps(false);
        b.setTextSize(11);
        b.setTextColor(INK);
        b.setPadding(dp(4), dp(2), dp(4), dp(2));
        b.setBackground(rounded(0xFFFFF8E1, 12, INK, 1.5f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(54), 1f);
        lp.leftMargin = dp(2);
        lp.rightMargin = dp(2);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> { sound.play("pop"); l.onClick(v); });
        return b;
    }

    private TextView roundBtn(String icon, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(icon);
        t.setTextSize(20);
        t.setTextColor(INK);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0xEEFFFFFF);
        g.setStroke(dp(2), INK);
        t.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(46), dp(46));
        lp.topMargin = dp(8);
        t.setLayoutParams(lp);
        t.setOnClickListener(v -> { sound.play("pop"); l.onClick(v); });
        return t;
    }

    /** Coisas que não são da partida em si. */
    private void showGameMenu() {
        boolean tips = prefs.getBoolean("tips", true);
        String[] items = {
                "📜  Histórico da partida",
                "📖  Guia do jogo",
                sound.enabled ? "🔇  Desligar som" : "🔊  Ligar som",
                tips ? "💡  Desligar dicas" : "💡  Ligar dicas",
                "🚪  Sair da partida"
        };
        new AlertDialog.Builder(this).setTitle("☰ Menu")
                .setItems(items, (d, w) -> {
                    switch (w) {
                        case 0: showLog(); break;
                        case 1: dialog("📖 Guia", guideContent()); break;
                        case 2:
                            sound.enabled = !sound.enabled;
                            prefs.edit().putBoolean("sound", sound.enabled).apply();
                            break;
                        case 3:
                            prefs.edit().putBoolean("tips", !tips).apply();
                            break;
                        default: confirmExit(); break;
                    }
                })
                .setNegativeButton("Fechar", null).show();
    }

    private void showDeals() {
        String[] items = {"🤝  Trocar com outro jogador", "🏦  Banco (empréstimo)"};
        new AlertDialog.Builder(this).setTitle("🤝 Negociar")
                .setItems(items, (d, w) -> { if (w == 0) startTrade(); else showBank(); })
                .setNegativeButton("Fechar", null).show();
    }

    /** Quem está agindo neste celular. */
    private int viewer() {
        if (display == null) return -1;
        return mode == MODE_LOCAL ? display.current : myIndex;
    }

    private void doAction(int a, int... x) { doActionAs(viewer(), a, x); }

    private void doActionAs(int who, int a, int... x) {
        if (mode == MODE_CLIENT) {
            if (hostLink != null) hostLink.send(Link.ACTION, Msg.action(a, x));
            return;
        }
        if (game == null) return;
        int p = mode == MODE_HOST ? 0 : who;
        String err = game.apply(p, a, x);
        if (err != null) { toast(err); sound.play("bad"); return; }
        authoritativeChanged();
    }

    // ---------------------------------------------------------------- fila de animação

    private void pushState(Game g) {
        queue.add(g);
        if (!animating) nextState();
    }

    private void nextState() {
        final Game g = queue.poll();
        if (g == null) {
            animating = false;
            refreshPanels();
            return;
        }
        if (display == null) {
            display = g;
            board.setGame(g, viewer());
            afterStateShown(null, g);
            nextState();
            return;
        }
        final Game prev = display;
        animating = true;
        ArrayList<BoardView.Step> steps = new ArrayList<>();
        if (g.version != prev.version) {
            if (g.rollSeq != prev.rollSeq) steps.add(BoardView.Step.dice(g.d1, g.d2, g.d3));
            for (int[] m : g.moves) steps.add(BoardView.Step.move(m[0], m[1], m[2], m[3] == Game.MV_WALK));
            if (g.rouletteSeq != prev.rouletteSeq && g.rouletteResult >= 0) steps.add(BoardView.Step.roulette(g.rouletteResult));
            for (String p : g.popups) {
                String[] parts = p.split("\u001f", -1);
                if (parts.length == 3) steps.add(BoardView.Step.banner(parts[0], parts[1], parts[2]));
            }
        }
        refreshPanels();
        board.play(steps, () -> {
            display = g;
            board.setGame(g, viewer());
            afterStateShown(prev, g);
            nextState();
        });
    }

    private void afterStateShown(Game prev, Game g) {
        if (prev == null) {
            welcome(g);
            return;
        }
        // dinheiro flutuando
        boolean gain = false, loss = false;
        for (int i = 0; i < g.players.length && i < prev.players.length; i++) {
            int d = g.players[i].money - prev.players[i].money;
            if (d == 0 || g.players[i].bankrupt) continue;
            board.floatText(i, (d > 0 ? "+R$ " : "-R$ ") + Math.abs(d), d > 0 ? 0xFF2E7D32 : 0xFFC62828);
            if (d > 0) gain = true; else loss = true;
        }
        boolean bought = false;
        for (int i = 0; i < Board.SIZE; i++) if (g.owner[i] != prev.owner[i] && g.owner[i] >= 0) bought = true;
        boolean built = false;
        for (int i = 0; i < Board.SIZE; i++) if (g.houses[i] > prev.houses[i]) built = true;
        if (bought || built) sound.play("buy");
        else if (gain) sound.play("coin");
        else if (loss) sound.play("bad");
        // emoji
        if (g.emojiSeq != prev.emojiSeq && g.emojiWho >= 0) {
            board.emoji(g.emojiWho, Rules.EMOJIS[g.emojiIdx]);
            sound.play("pop");
        }
        // troca de vez
        if (g.current != prev.current && g.phase != Game.PH_OVER) {
            if (mode == MODE_LOCAL) {
                sound.play("turn");
                toast("Vez de " + g.cur().name + " — passe o celular!");
            } else if (g.current == myIndex) {
                sound.play("turn");
                vibrate();
                toast("🎲 Sua vez!");
            }
        }
        // proposta de troca recebida
        if (g.tradeSeq != prev.tradeSeq && g.tradeFrom >= 0 && (mode == MODE_LOCAL || g.tradeTo == myIndex)) {
            showTradeOffer(g);
        }
        // dicas na primeira vez
        if (g.phase == Game.PH_AUCTION) tip("auction", 4);
        if (g.phase == Game.PH_ROULETTE) tip("roulette", 10);
        if (g.eventId >= 0) tip("events", 8);
        if (g.phase == Game.PH_TRAVEL) tip("chars", 3);
        int v = viewer();
        if (v >= 0 && v < g.players.length) {
            Player me = g.players[v];
            if (me.money < 0) tip("loan", 6);
            for (int grp = 0; grp < 8; grp++) if (g.ownsGroup(v, grp)) tip("build", 1);
            if (me.inJail) tip("jail", 2);
        }
        // fim de jogo
        if (g.phase == Game.PH_OVER && !endShown) {
            endShown = true;
            sound.play("win");
            ui.postDelayed(this::showEndScreen, 1800);
        }
    }

    private void vibrate() {
        try {
            Vibrator vb = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vb != null) vb.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Exception ignored) { }
    }

    // ---------------------------------------------------------------- dicas

    private void tip(String key, int guideIdx) {
        if (!prefs.getBoolean("tips", true)) return;
        if (prefs.getBoolean("tip_" + key, false)) return;
        prefs.edit().putBoolean("tip_" + key, true).apply();
        tipQueue.add(new String[]{"💡 " + Rules.GUIDE[guideIdx][0], Rules.GUIDE[guideIdx][1]});
        showNextTip();
    }

    private void tipNow(String t, String body) {
        tipQueue.add(new String[]{t, body});
        showNextTip();
    }

    private void showNextTip() {
        if (tipShowing || tipQueue.isEmpty() || isFinishing()) return;
        String[] t = tipQueue.poll();
        tipShowing = true;
        new AlertDialog.Builder(this).setTitle(t[0]).setMessage(t[1])
                .setPositiveButton("Entendi", null)
                .setNegativeButton("Não mostrar dicas", (d, w) -> { prefs.edit().putBoolean("tips", false).apply(); tipQueue.clear(); })
                .setOnDismissListener(d -> { tipShowing = false; ui.postDelayed(this::showNextTip, 300); })
                .show();
    }

    private void welcome(Game g) {
        StringBuilder sb = new StringBuilder();
        sb.append(Rules.GUIDE[0][1]).append("\n\n");
        int v = viewer();
        if (mode != MODE_LOCAL && v >= 0 && v < g.players.length) {
            Player me = g.players[v];
            sb.append("Seu personagem: ").append(Rules.CHAR_ICONS[me.character]).append(" ").append(Rules.CHAR_NAMES[me.character])
                    .append(" — ").append(Rules.CHAR_DESC[me.character]).append("\n\n");
            sb.append("🎯 Sua missão secreta: ").append(Rules.MISSIONS[me.mission]).append(" (vale R$ ").append(Rules.MISSION_REWARD).append(" + carta)\n\n");
            if (!me.powers.isEmpty()) {
                int c = me.powers.get(0);
                sb.append("🃏 Você começa com a carta ").append(Rules.POWER_ICONS[c]).append(" ").append(Rules.POWER_NAMES[c])
                        .append(": ").append(Rules.POWER_DESC[c]).append("\n\n");
            }
        } else {
            sb.append("Cada jogador tem um personagem, uma missão secreta (botão 🎯) e começa com uma carta de poder (botão 🃏).\n\n");
        }
        if (g.maxRounds > 0) sb.append("⏱️ Partida de ").append(g.maxRounds).append(" rodadas: no fim vence o maior patrimônio.\n\n");
        sb.append("Arraste o tabuleiro com o dedo, faça pinça para zoom e toque duas vezes para aproximar. Toque numa casa para ver detalhes.");
        tipNow("🎲 Bem-vindo ao Magnata!", sb.toString());
    }

    // ---------------------------------------------------------------- painéis

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            ui.postDelayed(this, 400);
            if (!screen.equals("game") || display == null) return;
            long now = SystemClock.uptimeMillis();
            if (display.phase == Game.PH_AUCTION && !animating) {
                if (display.auctionSeq != shownAuctionSeq) {
                    shownAuctionSeq = display.auctionSeq;
                    auctionSeenAt = now;
                }
                long left = AUCTION_MS - (now - auctionSeenAt);
                if (auctionLabel != null) auctionLabel.setText("⏱️ " + Math.max(0, (left + 999) / 1000) + "s para encerrar");
                if (left <= 0 && (mode == MODE_HOST || mode == MODE_LOCAL) && game != null
                        && game.phase == Game.PH_AUCTION && game.auctionSeq == display.auctionSeq) {
                    if (game.closeAuctionTimeout()) authoritativeChanged();
                }
            }
        }
    };

    private void refreshPanels() {
        if (display == null || !screen.equals("game")) return;
        Game g = display;
        int me = viewer();

        // placar
        chipsRow.removeAllViews();
        for (int i = 0; i < g.players.length; i++) {
            final int idx = i;
            Player p = g.players[i];
            LinearLayout chip = row();
            boolean cur = i == g.current && g.phase != Game.PH_OVER;
            chip.setBackground(rounded(cur ? Pawns.lighter(p.color, 0.75f) : 0xEEFFFFFF, 14, cur ? p.color : INK, cur ? 3.5f : 1.5f));
            chip.setPadding(dp(6), dp(3), dp(10), dp(3));
            chip.addView(new PawnView(this, p.pawn, p.color), new LinearLayout.LayoutParams(dp(34), dp(34)));
            LinearLayout col = column();
            col.setPadding(dp(4), 0, 0, 0);
            TextView n = bold(p.name + (mode != MODE_LOCAL && i == myIndex ? " (você)" : ""), 13);
            n.setPadding(0, 0, 0, 0);
            n.setTextColor(Pawns.darker(p.color, 0.8f));
            col.addView(n);
            String st = p.bankrupt ? "💀 faliu" : "R$ " + p.money + "  " + Rules.CHAR_ICONS[p.character]
                    + (p.powers.isEmpty() ? "" : " 🃏" + p.powers.size()) + (p.inJail ? " 🔒" : "") + (p.loanOwed > 0 ? " 🏦" : "");
            TextView m = label(st, 12);
            m.setPadding(0, 0, 0, 0);
            col.addView(m);
            chip.addView(col);
            if (p.bankrupt) chip.setAlpha(0.45f);
            chip.setOnClickListener(v -> showPlayerInfo(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(5);
            chipsRow.addView(chip, lp);
        }

        // evento e rodada
        String rd = "Rodada " + g.round + (g.maxRounds > 0 ? "/" + g.maxRounds : "");
        if (g.eventId >= 0) {
            eventPill.setText(Rules.EVENT_ICONS[g.eventId] + " " + Rules.EVENT_NAMES[g.eventId] + ": " + g.eventText() + "  ·  " + rd);
            eventPill.setBackground(rounded(0xEEFFE0B2, 12, ORANGE, 2));
        } else {
            eventPill.setText("🗓️ " + rd);
            eventPill.setBackground(rounded(0xEEFFFFFF, 12, INK, 1.5f));
        }

        int pc = me >= 0 && me < g.players.length ? g.players[me].powers.size() : 0;
        powersBtn.setText("🃏\nPoderes" + (pc > 0 ? " " + pc : ""));

        // ações
        actionsBox.removeAllViews();
        auctionLabel = null;
        if (animating) {
            gameStatus.setText("🎬 ...");
            return;
        }
        if (g.phase == Game.PH_OVER) {
            String wn = g.winner >= 0 ? g.players[g.winner].name : "?";
            gameStatus.setText("🏆 " + wn + " venceu a partida!");
            actionsBox.addView(buttonRow(button("📊 Resultado", GOLD, v -> showEndScreen()),
                    button("Menu", GREY, v -> { shutdownNetwork(); showMenu(); })));
            return;
        }
        Player cur = g.cur();
        Player mp = me >= 0 && me < g.players.length ? g.players[me] : null;

        if (g.phase == Game.PH_AUCTION) {
            buildAuctionPanel(g, me);
            return;
        }
        if (mp != null && mp.bankrupt && mode != MODE_LOCAL) {
            gameStatus.setText("Você faliu. Pode continuar assistindo e mandando emojis 😉");
            return;
        }
        if (me != g.current) {
            gameStatus.setText("⏳ Vez de " + cur.name + "...");
            if (g.tradeFrom >= 0 && g.tradeTo == me) {
                actionsBox.addView(button("🤝 Ver proposta de " + g.players[g.tradeFrom].name, PURPLE, v -> showTradeOffer(g)));
            }
            return;
        }

        String who = mode == MODE_LOCAL ? cur.name + ": " : "";
        if (g.tradeFrom == me) {
            gameStatus.setText(who + "Aguardando " + g.players[g.tradeTo].name + " responder a troca...");
            actionsBox.addView(button("Cancelar proposta", GREY, v -> doAction(Game.A_TRADE_CANCEL)));
            return;
        }
        if (mode == MODE_LOCAL && g.tradeFrom >= 0 && g.tradeTo != me) {
            actionsBox.addView(button("🤝 Proposta para " + g.players[g.tradeTo].name, PURPLE, v -> showTradeOffer(g)));
        }
        switch (g.phase) {
            case Game.PH_ROLL: {
                String extra = (g.turbo ? " 🚀Turbo" : "") + (g.shield ? " 🛡️Escudo" : "");
                if (cur.money < 0) gameStatus.setText(who + "Você está devendo R$ " + (-cur.money) + "! Resolva antes de rolar.");
                else gameStatus.setText(who + (cur.inJail ? "Você está na prisão 🔒" : "Role os dados! 🎲") + extra);
                if (g.validate(me, Game.A_ROLL) == null)
                    actionsBox.addView(button(cur.inJail ? "🎲 Tentar a dupla" : "🎲  Rolar dados", GREEN, v -> doAction(Game.A_ROLL)));
                if (cur.inJail) {
                    ArrayList<Button> bs = new ArrayList<>();
                    if (g.validate(me, Game.A_BAIL) == null) bs.add(button("Fiança R$ " + g.bail(me), GOLD, v -> doAction(Game.A_BAIL)));
                    if (g.validate(me, Game.A_CARD) == null) bs.add(button("Usar carta 🎫", BLUE, v -> doAction(Game.A_CARD)));
                    if (!bs.isEmpty()) actionsBox.addView(buttonRow(bs.toArray(new Button[0])));
                }
                if (cur.money < 0) addDebtButtons();
                break;
            }
            case Game.PH_TRAVEL: {
                int s = g.pendingSteps;
                gameStatus.setText(who + "🧭 Viajante: quantas casas andar?");
                actionsBox.addView(buttonRow(
                        button((s - 1) + " casas", BLUE, v -> doAction(Game.A_TRAVEL, -1)),
                        button(s + " casas", GREEN, v -> doAction(Game.A_TRAVEL, 0)),
                        button((s + 1) + " casas", BLUE, v -> doAction(Game.A_TRAVEL, 1))));
                break;
            }
            case Game.PH_BUY: {
                int pos = g.pendingBuy;
                gameStatus.setText(who + "Comprar " + Board.NAME[pos] + "?  Saldo R$ " + cur.money);
                actionsBox.addView(buttonRow(
                        button("✅ Comprar R$ " + g.buyPrice(pos), GREEN, v -> doAction(Game.A_BUY)),
                        button("🔨 Leiloar", ORANGE, v -> doAction(Game.A_DECLINE))));
                int di = cur.powers.indexOf(Rules.P_DESCONTO);
                if (di >= 0 && g.validate(me, Game.A_POWER, di, -1) == null) {
                    final int fdi = di;
                    actionsBox.addView(button("🏷️ Comprar com Desconto (R$ " + g.buyPrice(pos) / 2 + ")", PURPLE,
                            v -> doAction(Game.A_POWER, fdi, -1)));
                }
                actionsBox.addView(button("ℹ️ Ver " + Board.NAME[pos], GREY, v -> showSquare(pos)));
                break;
            }
            case Game.PH_ROULETTE:
                gameStatus.setText(who + "🎡 Você chegou ao Descanso!");
                actionsBox.addView(button("🎡  Girar a roleta", BLUE, v -> doAction(Game.A_SPIN)));
                break;
            default: {
                if (cur.money < 0) {
                    gameStatus.setText(who + "Você está devendo R$ " + (-cur.money) + "!");
                    addDebtButtons();
                } else {
                    gameStatus.setText(who + "Saldo R$ " + cur.money + ". Construa, troque ou encerre a vez.");
                    actionsBox.addView(button("⏭  Encerrar vez", GREEN_L, v -> doAction(Game.A_END)));
                }
                break;
            }
        }
    }

    private void addDebtButtons() {
        actionsBox.addView(buttonRow(
                button("🏠 Vender/Hipotecar", BROWN, v -> showMyProperties(viewer())),
                button("🏦 Empréstimo", BLUE, v -> showBank()),
                button("💀 Falência", RED, v -> confirmBankrupt())));
    }

    private void buildAuctionPanel(Game g, int me) {
        int pos = g.auctionPos;
        String hb = g.highBidder >= 0 ? "R$ " + g.highBid + " de " + g.players[g.highBidder].name : "nenhum lance ainda";
        gameStatus.setText("🔨 Leilão de " + Board.NAME[pos] + " — maior lance: " + hb);
        auctionLabel = label("⏱️ ...", 13);
        auctionLabel.setGravity(Gravity.CENTER);
        actionsBox.addView(auctionLabel);
        if (mode == MODE_LOCAL) {
            for (int i = 0; i < g.players.length; i++) {
                Player p = g.players[i];
                if (p.bankrupt || g.auctionPassed[i]) continue;
                final int pi = i;
                LinearLayout r = row();
                TextView n = bold(p.name, 13);
                n.setTextColor(p.color);
                r.addView(n, new LinearLayout.LayoutParams(dp(78), ViewGroup.LayoutParams.WRAP_CONTENT));
                if (i == g.highBidder) {
                    r.addView(label("👑 maior lance", 13));
                } else {
                    LinearLayout br = buttonRow(
                            button("+10", GREEN, v -> doActionAs(pi, Game.A_BID, 10)),
                            button("+50", GREEN, v -> doActionAs(pi, Game.A_BID, 50)),
                            button("+100", GREEN, v -> doActionAs(pi, Game.A_BID, 100)),
                            button("Sair", GREY, v -> doActionAs(pi, Game.A_PASS)));
                    r.addView(br, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                }
                actionsBox.addView(r);
            }
            return;
        }
        if (me < 0 || me >= g.players.length || g.players[me].bankrupt) return;
        if (g.auctionPassed[me]) { actionsBox.addView(label("Você saiu deste leilão.", 14)); return; }
        if (me == g.highBidder) { actionsBox.addView(label("👑 Seu lance é o maior! Espere os outros.", 14)); return; }
        actionsBox.addView(buttonRow(
                button("+R$ 10", GREEN, v -> doAction(Game.A_BID, 10)),
                button("+R$ 50", GREEN, v -> doAction(Game.A_BID, 50)),
                button("+R$ 100", GREEN, v -> doAction(Game.A_BID, 100)),
                button("Sair", GREY, v -> doAction(Game.A_PASS))));
    }

    private void confirmBankrupt() {
        new AlertDialog.Builder(this)
                .setTitle("Declarar falência?")
                .setMessage("Você sai da partida e seus bens vão para quem você deve. Tem certeza?")
                .setPositiveButton("Falir", (d, w) -> doAction(Game.A_BANKRUPT))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void confirmExit() {
        String msg = mode == MODE_HOST
                ? "Você é o anfitrião: se sair, a partida acaba para todo mundo."
                : mode == MODE_CLIENT ? "Você vai sair da partida (conta como falência)." : "A partida será perdida.";
        new AlertDialog.Builder(this)
                .setTitle("Sair da partida?")
                .setMessage(msg)
                .setPositiveButton("Sair", (d, w) -> {
                    if (mode == MODE_CLIENT && hostLink != null && display != null && display.phase != Game.PH_OVER) {
                        hostLink.send(Link.ACTION, Msg.action(Game.A_BANKRUPT));
                        leaving = true;
                        final Link l = hostLink;
                        ui.postDelayed(l::close, 400);
                        hostLink = null;
                        mode = MODE_NONE;
                        showMenu();
                        return;
                    }
                    shutdownNetwork();
                    showMenu();
                })
                .setNegativeButton("Continuar jogando", null)
                .show();
    }

    // ================================================================= janelas: casas, bens, jogadores

    private String squareInfo(Game g, int pos) {
        StringBuilder sb = new StringBuilder();
        int t = Board.TYPE[pos];
        if (t == Board.PROP) {
            sb.append("Cor: ").append(Board.GROUP_NAME[Board.GROUP[pos]]).append(Game.isMT(pos) ? " · cidade de MT 🌾" : "").append('\n');
            sb.append("Preço: R$ ").append(g.buyPrice(pos)).append('\n');
            int[] r = Board.RENT[pos];
            sb.append("Aluguel: R$ ").append(r[0]).append(" (R$ ").append(r[0] * 2).append(" com a cor completa)\n");
            for (int i = 1; i <= 4; i++) sb.append("  ").append(i).append(i == 1 ? " casa: R$ " : " casas: R$ ").append(r[i]).append('\n');
            sb.append("  Hotel: R$ ").append(r[5]).append('\n');
            sb.append("Construir custa R$ ").append(Board.HOUSE_COST[pos]).append('\n');
        } else if (t == Board.TRANSPORT) {
            sb.append("Transporte — R$ 200\nAluguel: R$ 25 / 50 / 100 / 200 conforme quantos transportes o dono tem.\n");
        } else if (t == Board.UTIL) {
            sb.append("Companhia — R$ 150\nAluguel: 4× os dados (10× se o dono tiver as duas).\n");
        } else if (t == Board.TAX) {
            sb.append("Pague R$ ").append(Board.PRICE[pos]).append(" ao banco (Advogado paga metade).\n");
        } else if (t == Board.DESTINO || t == Board.SURPRESA) {
            sb.append("Tire uma carta.\n");
        } else if (t == Board.GO) {
            sb.append("Receba R$ 200 e uma carta de poder ao passar.\n");
        } else if (t == Board.GOTOJAIL) {
            sb.append("Vá direto para a prisão.\n");
        } else if (t == Board.JAIL) {
            sb.append("Só visitando — ou preso.\n");
        } else {
            sb.append("Caiu aqui? Gire a roleta! 🎡\n");
        }
        if (Board.buyable(pos)) {
            int o = g.owner[pos];
            sb.append('\n').append("Dono: ").append(o < 0 ? "ninguém (à venda)" : g.players[o].name).append('\n');
            if (o >= 0) {
                if (t == Board.PROP) {
                    int h = g.houses[pos];
                    sb.append("Construções: ").append(h == 5 ? "hotel" : h + (h == 1 ? " casa" : " casas")).append('\n');
                }
                if (g.mortgaged[pos]) sb.append("HIPOTECADA (não cobra aluguel)\n");
                else if (g.isClosed(pos)) sb.append("🚧 FECHADA por sabotagem\n");
                else if (t != Board.UTIL) sb.append("Aluguel agora: R$ ").append(g.rentOf(pos)).append('\n');
                if (g.earned[pos] > 0) sb.append("Já rendeu: R$ ").append(g.earned[pos]).append('\n');
            }
        }
        return sb.toString();
    }

    private void showSquare(final int pos) {
        Game g = display;
        if (g == null) return;
        LinearLayout box = column();
        box.addView(label(squareInfo(g, pos), 15));
        final AlertDialog[] dlg = new AlertDialog[1];
        int o = g.owner[pos];
        int me = mode == MODE_LOCAL ? o : myIndex;
        if (Board.buyable(pos) && o >= 0 && o == me && g.phase != Game.PH_OVER && !animating) {
            if (g.validate(me, Game.A_BUILD, pos) == null) {
                String what = g.houses[pos] == 4 ? "hotel" : "casa";
                box.addView(button("🏗️ Construir " + what + " (R$ " + g.houseCost(me, pos) + ")", GREEN,
                        v -> { dlg[0].dismiss(); doActionAs(me, Game.A_BUILD, pos); }));
            } else if (Board.TYPE[pos] == Board.PROP && g.houses[pos] < 5) {
                String why = g.validate(me, Game.A_BUILD, pos);
                TextView t = label("Construir: " + why, 13);
                t.setTextColor(0xFF757575);
                box.addView(t);
            }
            if (g.validate(me, Game.A_SELL, pos) == null)
                box.addView(button("Vender construção (+R$ " + Board.HOUSE_COST[pos] / 2 + ")", GOLD,
                        v -> { dlg[0].dismiss(); doActionAs(me, Game.A_SELL, pos); }));
            if (g.validate(me, Game.A_MORTGAGE, pos) == null)
                box.addView(button("Hipotecar (+R$ " + Board.mortgageValue(pos) + ")", BROWN,
                        v -> { dlg[0].dismiss(); doActionAs(me, Game.A_MORTGAGE, pos); }));
            if (g.validate(me, Game.A_UNMORTGAGE, pos) == null)
                box.addView(button("Recuperar hipoteca (R$ " + Board.unmortgageCost(pos) + ")", BLUE,
                        v -> { dlg[0].dismiss(); doActionAs(me, Game.A_UNMORTGAGE, pos); }));
        }
        dlg[0] = dialog(Board.NAME[pos], box);
    }

    private void showMyProperties(int p) {
        Game g = display;
        if (g == null || p < 0 || p >= g.players.length) return;
        final List<Integer> mine = new ArrayList<>();
        for (int i = 0; i < Board.SIZE; i++) if (g.owner[i] == p) mine.add(i);
        if (mine.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("🏠 " + g.players[p].name)
                    .setMessage("Ainda não tem propriedades.").setPositiveButton("OK", null).show();
            return;
        }
        String[] items = new String[mine.size()];
        for (int i = 0; i < mine.size(); i++) {
            int pos = mine.get(i);
            String extra = "";
            if (Board.TYPE[pos] == Board.PROP) {
                extra = " [" + Board.GROUP_NAME[Board.GROUP[pos]] + "]";
                int h = g.houses[pos];
                if (h == 5) extra += " 🏨";
                else if (h > 0) extra += " " + h + "🏠";
            }
            if (g.mortgaged[pos]) extra += " (hipotecada)";
            items[i] = Board.NAME[pos] + extra;
        }
        new AlertDialog.Builder(this)
                .setTitle("🏠 Bens de " + g.players[p].name)
                .setItems(items, (d, w) -> showSquare(mine.get(w)))
                .setPositiveButton("Fechar", null)
                .show();
    }

    private void showPlayerInfo(int p) {
        Game g = display;
        if (g == null) return;
        Player pl = g.players[p];
        StringBuilder sb = new StringBuilder();
        sb.append(Rules.CHAR_ICONS[pl.character]).append(" ").append(Rules.CHAR_NAMES[pl.character]).append(": ")
                .append(Rules.CHAR_DESC[pl.character]).append("\n\n");
        sb.append("Dinheiro: R$ ").append(pl.money).append('\n');
        sb.append("Patrimônio: R$ ").append(g.netWorth(p)).append('\n');
        if (pl.loanOwed > 0) sb.append("Deve ao banco: R$ ").append(pl.loanOwed).append(" (até rodada ").append(pl.loanDueRound).append(")\n");
        sb.append("Cartas de poder: ").append(pl.powers.size()).append("  ·  Missão: ").append(pl.missionDone ? "cumprida ✅" : "secreta 🤫").append("\n\n");
        int count = 0;
        for (int i = 0; i < Board.SIZE; i++) {
            if (g.owner[i] != p) continue;
            count++;
            sb.append("• ").append(Board.NAME[i]);
            if (Board.TYPE[i] == Board.PROP && g.houses[i] > 0) sb.append(g.houses[i] == 5 ? " (hotel)" : " (" + g.houses[i] + " casas)");
            if (g.mortgaged[i]) sb.append(" (hipotecada)");
            sb.append('\n');
        }
        if (count == 0) sb.append("Sem propriedades.");
        new AlertDialog.Builder(this).setTitle(pl.name).setMessage(sb.toString()).setPositiveButton("OK", null).show();
    }

    private void showLog() {
        Game g = display;
        if (g == null) return;
        StringBuilder sb = new StringBuilder();
        for (int i = g.log.size() - 1; i >= 0; i--) sb.append("• ").append(g.log.get(i)).append('\n');
        dialog("📜 Histórico", label(sb.toString(), 13));
    }

    // ================================================================= poderes, banco, missão, emoji

    private void showPowers() {
        final Game g = display;
        final int me = viewer();
        if (g == null || me < 0 || me >= g.players.length) return;
        Player p = g.players[me];
        LinearLayout box = column();
        box.addView(label(Rules.GUIDE[7][1], 13));
        final AlertDialog[] dlg = new AlertDialog[1];
        if (p.powers.isEmpty()) box.addView(bold("\nVocê não tem cartas de poder agora.", 15));
        for (int i = 0; i < p.powers.size(); i++) {
            final int hand = i;
            final int card = p.powers.get(i);
            box.addView(card(Rules.POWER_ICONS[card] + "  " + Rules.POWER_NAMES[card] + "\n" + Rules.POWER_DESC[card], 0xFFE0F2F1));
            boolean usable;
            if (card == Rules.P_SABOTAGEM) usable = !sabotageTargets(g, me, hand).isEmpty();
            else usable = g.validate(me, Game.A_POWER, hand, -1) == null;
            if (animating) usable = false;
            if (usable) {
                box.addView(button("Usar " + Rules.POWER_NAMES[card], PURPLE, v -> {
                    dlg[0].dismiss();
                    if (card == Rules.P_SABOTAGEM) chooseSabotage(g, me, hand);
                    else doAction(Game.A_POWER, hand, -1);
                }));
            } else {
                String why = card == Rules.P_SABOTAGEM ? "Use na sua vez, contra a propriedade de um rival." : g.validate(me, Game.A_POWER, hand, -1);
                TextView t = label("Agora não: " + why, 12);
                t.setTextColor(0xFF757575);
                box.addView(t);
            }
        }
        dlg[0] = dialog("🃏 Cartas de poder", box);
    }

    private List<Integer> sabotageTargets(Game g, int me, int hand) {
        List<Integer> r = new ArrayList<>();
        for (int i = 0; i < Board.SIZE; i++) if (g.validate(me, Game.A_POWER, hand, i) == null) r.add(i);
        return r;
    }

    private void chooseSabotage(Game g, int me, int hand) {
        final List<Integer> t = sabotageTargets(g, me, hand);
        String[] items = new String[t.size()];
        for (int i = 0; i < t.size(); i++) items[i] = Board.NAME[t.get(i)] + " (" + g.players[g.owner[t.get(i)]].name + ")";
        new AlertDialog.Builder(this).setTitle("💣 Sabotar qual propriedade?")
                .setItems(items, (d, w) -> doAction(Game.A_POWER, hand, t.get(w)))
                .setNegativeButton("Cancelar", null).show();
    }

    private void showBank() {
        Game g = display;
        int me = viewer();
        if (g == null || me < 0 || me >= g.players.length) return;
        Player p = g.players[me];
        LinearLayout box = column();
        box.addView(label(Rules.GUIDE[6][1], 13));
        box.addView(bold(p.loanOwed > 0 ? "\nVocê deve R$ " + p.loanOwed + " (vence na rodada " + p.loanDueRound + ")"
                : "\nVocê não tem empréstimo.", 15));
        final AlertDialog[] dlg = new AlertDialog[1];
        ArrayList<Button> bs = new ArrayList<>();
        for (int amt : new int[]{100, 200, 300, 500}) {
            if (g.validate(me, Game.A_LOAN, amt) == null && !animating) {
                final int a = amt;
                bs.add(button("R$ " + amt, BLUE, v -> { dlg[0].dismiss(); doAction(Game.A_LOAN, a); }));
            }
        }
        if (!bs.isEmpty()) {
            box.addView(label("Pegar emprestado (devolve +20%):", 14));
            box.addView(buttonRow(bs.toArray(new Button[0])));
        } else {
            box.addView(label("Empréstimo disponível só na sua vez e até o limite de R$ 1.000.", 13));
        }
        if (g.validate(me, Game.A_REPAY) == null && !animating) {
            box.addView(button("Pagar o que der (R$ " + Math.min(p.money, p.loanOwed) + ")", GREEN,
                    v -> { dlg[0].dismiss(); doAction(Game.A_REPAY); }));
        }
        dlg[0] = dialog("🏦 Banco", box);
    }

    private void showMission() {
        Game g = display;
        int me = viewer();
        if (g == null || me < 0 || me >= g.players.length) return;
        Player p = g.players[me];
        String s = (mode == MODE_LOCAL ? "Missão de " + p.name + " (não deixe os outros verem! 🤫)\n\n" : "")
                + "🎯 " + Rules.MISSIONS[p.mission] + "\n\n"
                + (p.missionDone ? "✅ Cumprida!" : "Recompensa: R$ " + Rules.MISSION_REWARD + " + uma carta de poder.")
                + "\n\n" + Rules.GUIDE[9][1];
        new AlertDialog.Builder(this).setTitle("🎯 Missão secreta").setMessage(s).setPositiveButton("OK", null).show();
    }

    private void showEmojis() {
        LinearLayout box = column();
        final AlertDialog[] dlg = new AlertDialog[1];
        LinearLayout r1 = row(), r2 = row();
        for (int i = 0; i < Rules.EMOJIS.length; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setText(Rules.EMOJIS[i]);
            t.setTextSize(30);
            t.setGravity(Gravity.CENTER);
            t.setOnClickListener(v -> { dlg[0].dismiss(); doAction(Game.A_EMOJI, idx); });
            (i < 4 ? r1 : r2).addView(t, new LinearLayout.LayoutParams(0, dp(60), 1f));
        }
        box.addView(r1);
        box.addView(r2);
        dlg[0] = dialog("😀 Mandar emoji", box);
    }

    // ================================================================= trocas

    private void startTrade() {
        final Game g = display;
        final int me = viewer();
        if (g == null || me < 0) return;
        if (me != g.current || (g.phase != Game.PH_ROLL && g.phase != Game.PH_AFTER) || animating) {
            tipNow("🤝 Trocas", "Você pode propor trocas na sua vez (antes de rolar ou depois de andar).\n\n" + Rules.GUIDE[5][1]);
            return;
        }
        if (g.tradeFrom >= 0) { toast("Já existe uma proposta aberta."); return; }
        tip("trade", 5);
        final List<Integer> others = new ArrayList<>();
        for (int i = 0; i < g.players.length; i++) if (i != me && !g.players[i].bankrupt) others.add(i);
        String[] names = new String[others.size()];
        for (int i = 0; i < others.size(); i++) names[i] = g.players[others.get(i)].name;
        new AlertDialog.Builder(this).setTitle("🤝 Trocar com quem?")
                .setItems(names, (d, w) -> tradeEditor(g, me, others.get(w)))
                .setNegativeButton("Cancelar", null).show();
    }

    private void tradeEditor(final Game g, final int me, final int to) {
        LinearLayout box = column();
        final List<CheckBox> giveBoxes = new ArrayList<>(), getBoxes = new ArrayList<>();
        final List<Integer> givePos = new ArrayList<>(), getPos = new ArrayList<>();
        box.addView(bold("Você oferece:", 15));
        for (int i = 0; i < Board.SIZE; i++) {
            if (!g.tradable(i, me)) continue;
            CheckBox cb = new CheckBox(this);
            cb.setText(Board.NAME[i] + (g.mortgaged[i] ? " (hipotecada)" : ""));
            cb.setTextColor(INK);
            box.addView(cb);
            giveBoxes.add(cb);
            givePos.add(i);
        }
        if (givePos.isEmpty()) box.addView(label("(nenhuma propriedade disponível)", 13));
        final EditText giveMoney = input("Dinheiro que você dá (R$)", "");
        giveMoney.setInputType(InputType.TYPE_CLASS_NUMBER);
        box.addView(giveMoney);
        box.addView(bold("\nVocê pede de " + g.players[to].name + ":", 15));
        for (int i = 0; i < Board.SIZE; i++) {
            if (!g.tradable(i, to)) continue;
            CheckBox cb = new CheckBox(this);
            cb.setText(Board.NAME[i] + (g.mortgaged[i] ? " (hipotecada)" : ""));
            cb.setTextColor(INK);
            box.addView(cb);
            getBoxes.add(cb);
            getPos.add(i);
        }
        if (getPos.isEmpty()) box.addView(label("(nenhuma propriedade disponível)", 13));
        final EditText getMoney = input("Dinheiro que você pede (R$)", "");
        getMoney.setInputType(InputType.TYPE_CLASS_NUMBER);
        box.addView(getMoney);
        TextView note = label("Cidades com casas não aparecem: venda as construções antes de trocar.", 12);
        note.setTextColor(0xFF757575);
        box.addView(note);

        ScrollView sv = new ScrollView(this);
        LinearLayout wrap = column();
        wrap.setPadding(dp(18), dp(6), dp(18), dp(6));
        wrap.addView(box);
        sv.addView(wrap);
        new AlertDialog.Builder(this).setTitle("🤝 Proposta para " + g.players[to].name).setView(sv)
                .setPositiveButton("Propor", (d, w) -> {
                    long give = 0, get = 0;
                    for (int i = 0; i < giveBoxes.size(); i++) if (giveBoxes.get(i).isChecked()) give |= Game.bit(givePos.get(i));
                    for (int i = 0; i < getBoxes.size(); i++) if (getBoxes.get(i).isChecked()) get |= Game.bit(getPos.get(i));
                    int gm = parseMoney(giveMoney), tm = parseMoney(getMoney);
                    doAction(Game.A_TRADE, to, (int) give, (int) (give >>> 32), (int) get, (int) (get >>> 32), gm, tm);
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private int parseMoney(EditText e) {
        try { return Math.max(0, Integer.parseInt(e.getText().toString().trim())); } catch (Exception ex) { return 0; }
    }

    private String tradeList(long mask) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Board.SIZE; i++) if ((mask & Game.bit(i)) != 0) sb.append("  • ").append(Board.NAME[i]).append('\n');
        return sb.toString();
    }

    private void showTradeOffer(final Game g) {
        if (g.tradeFrom < 0) return;
        tip("trade", 5);
        final int to = g.tradeTo;
        Player a = g.players[g.tradeFrom];
        StringBuilder sb = new StringBuilder();
        if (mode == MODE_LOCAL) sb.append("Passe o celular para ").append(g.players[to].name).append(".\n\n");
        sb.append(a.name).append(" oferece:\n").append(tradeList(g.tradeGive));
        if (g.tradeGiveMoney > 0) sb.append("  • R$ ").append(g.tradeGiveMoney).append('\n');
        if (g.tradeGive == 0 && g.tradeGiveMoney == 0) sb.append("  (nada)\n");
        sb.append("\nEm troca de:\n").append(tradeList(g.tradeGet));
        if (g.tradeGetMoney > 0) sb.append("  • R$ ").append(g.tradeGetMoney).append('\n');
        if (g.tradeGet == 0 && g.tradeGetMoney == 0) sb.append("  (nada)\n");
        new AlertDialog.Builder(this).setTitle("🤝 " + g.players[to].name + ", aceita a troca?").setMessage(sb.toString())
                .setCancelable(false)
                .setPositiveButton("✅ Aceitar", (d, w) -> doActionAs(to, Game.A_TRADE_ACCEPT))
                .setNegativeButton("❌ Recusar", (d, w) -> doActionAs(to, Game.A_TRADE_REJECT))
                .setNeutralButton("Depois", null)
                .show();
    }

    // ================================================================= fim de jogo

    private void showEndScreen() {
        Game g = display;
        if (g == null || isFinishing()) return;
        LinearLayout box = column();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < g.players.length; i++) order.add(i);
        order.sort((x, y) -> {
            if (x == g.winner) return -1;
            if (y == g.winner) return 1;
            return Integer.compare(g.netWorth(y), g.netWorth(x));
        });
        String[] medals = {"🥇", "🥈", "🥉", "4º", "5º", "6º"};
        for (int k = 0; k < order.size(); k++) {
            int i = order.get(k);
            Player p = g.players[i];
            LinearLayout r = row();
            r.addView(bold(medals[Math.min(k, 5)], 22));
            r.addView(new PawnView(this, p.pawn, p.color), new LinearLayout.LayoutParams(dp(40), dp(40)));
            TextView t = bold(p.name + (p.bankrupt ? "  💀" : "  R$ " + g.netWorth(i)), 16);
            t.setTextColor(Pawns.darker(p.color, 0.8f));
            r.addView(t);
            box.addView(r);
        }
        box.addView(bold("\n📊 Estatísticas", 17));
        box.addView(label(stat(g, "💸 Pagou mais aluguel", p -> p.rentPaid), 14));
        box.addView(label(stat(g, "🤑 Recebeu mais aluguel", p -> p.rentRecv), 14));
        box.addView(label(stat(g, "🛒 Mais compras", p -> p.bought), 14));
        box.addView(label(stat(g, "🔒 Mais vezes preso", p -> p.jailCount), 14));
        int best = -1;
        for (int i = 0; i < Board.SIZE; i++) if (g.earned[i] > 0 && (best < 0 || g.earned[i] > g.earned[best])) best = i;
        if (best >= 0) box.addView(label("🏙️ Propriedade mais lucrativa: " + Board.NAME[best] + " (R$ " + g.earned[best] + ")", 14));
        int missions = 0;
        for (Player p : g.players) if (p.missionDone) missions++;
        box.addView(label("🎯 Missões cumpridas: " + missions + " de " + g.players.length, 14));
        box.addView(label("🗓️ Rodadas jogadas: " + Math.min(g.round, g.maxRounds > 0 ? g.maxRounds : g.round), 14));
        dialog("🏆 Fim de jogo", box);
    }

    private interface Stat { int of(Player p); }

    private String stat(Game g, String title, Stat s) {
        int best = -1;
        for (int i = 0; i < g.players.length; i++) if (best < 0 || s.of(g.players[i]) > s.of(g.players[best])) best = i;
        if (best < 0 || s.of(g.players[best]) == 0) return title + ": ninguém";
        return title + ": " + g.players[best].name + " (" + s.of(g.players[best]) + (title.contains("aluguel") ? " em R$" : "") + ")";
    }
}
