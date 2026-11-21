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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.netero.magnata.engine.Board;
import com.netero.magnata.engine.Game;
import com.netero.magnata.engine.Player;
import com.netero.magnata.net.Bt;
import com.netero.magnata.net.Link;
import com.netero.magnata.net.Msg;
import com.netero.magnata.ui.BoardView;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@SuppressLint("MissingPermission")
public class MainActivity extends Activity {

    private static final int MODE_NONE = 0, MODE_LOCAL = 1, MODE_HOST = 2, MODE_CLIENT = 3;
    private static final int REQ_PERM = 1, REQ_ENABLE = 2, REQ_DISCOVERABLE = 3;
    private static final int MAX_PLAYERS = 6;

    private static final int GREEN = 0xFF1B5E20;
    private static final int GREEN_LIGHT = 0xFF2E7D32;
    private static final int GOLD = 0xFFC9A227;
    private static final int RED = 0xFFC62828;
    private static final int GREY = 0xFF9E9E9E;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private BluetoothAdapter bt;

    private int mode = MODE_NONE;
    private Game game;
    private int myIndex = 0;
    private boolean leaving;

    // anfitrião
    private Bt.Server server;
    private final List<Link> clients = new ArrayList<>();
    private boolean gameStarted;

    // cliente
    private Link hostLink;
    private String[] lobbyNames = new String[0];

    // telas
    private String screen = "";
    private LinearLayout listBox;   // lobby / lista de aparelhos
    private TextView statusText;
    private final List<BluetoothDevice> found = new ArrayList<>();

    // tela de jogo
    private BoardView board;
    private TextView gameStatus;
    private LinearLayout actionsBox, playersBox;
    private TextView logView;
    private int lastCardSeq = -1;
    private int lastCurrent = -1;

    private Runnable afterPermission;

    // ================================================================= ciclo de vida

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("magnata", MODE_PRIVATE);
        BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bt = bm != null ? bm.getAdapter() : null;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(GREEN);

        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothDevice.ACTION_FOUND);
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(receiver, f);

        showMenu();
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(receiver); } catch (Exception ignored) { }
        shutdownNetwork();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (screen.equals("menu")) { super.onBackPressed(); return; }
        if (screen.equals("game")) { confirmExit(); return; }
        shutdownNetwork();
        showMenu();
    }

    // ================================================================= helpers de UI

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private ScrollView page(LinearLayout content) {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BoardView.BG);
        sv.setFillViewport(true);
        content.setPadding(dp(16), dp(16), dp(16), dp(24));
        sv.addView(content);
        return sv;
    }

    private TextView title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(30);
        t.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        t.setTextColor(GREEN);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(8), 0, dp(4));
        return t;
    }

    private TextView label(String s, float size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(BoardView.INK);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private Button button(String s, int color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(12));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    private EditText input(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(true);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(14)});
        e.setTextColor(BoardView.INK);
        return e;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private String myName() {
        String n = prefs.getString("name", "").trim();
        return n.isEmpty() ? "Jogador" : n;
    }

    // ================================================================= menu

    private void showMenu() {
        screen = "menu";
        mode = MODE_NONE;
        game = null;
        LinearLayout c = column();
        c.addView(title("MAGNATA"));
        TextView sub = label("Compre cidades, cobre aluguel e seja o último de pé!", 15);
        sub.setGravity(Gravity.CENTER);
        c.addView(sub);

        c.addView(label("Seu nome:", 15));
        final EditText name = input("Seu nome", prefs.getString("name", ""));
        c.addView(name);
        Runnable save = () -> prefs.edit().putString("name", name.getText().toString().trim()).apply();

        c.addView(button("📶  Criar sala (Bluetooth)", GREEN, v -> { save.run(); startHost(); }));
        c.addView(button("🔗  Entrar numa sala (Bluetooth)", GREEN_LIGHT, v -> { save.run(); startJoin(); }));
        c.addView(button("📱  Jogar no mesmo celular", GOLD, v -> { save.run(); showLocalSetup(); }));
        c.addView(button("❓  Como jogar", GREY, v -> showHelp()));
        setContentView(page(c));
    }

    private void showHelp() {
        String s = "• Todos começam com R$ 1.500. Passar pela Partida dá R$ 200.\n\n"
                + "• Na sua vez, role os dados. Caiu numa propriedade sem dono? Pode comprar.\n\n"
                + "• Caiu na propriedade de outro jogador? Paga aluguel.\n\n"
                + "• Com todas as cidades de uma cor, o aluguel dobra e você pode construir casas (até 4) e depois um hotel. "
                + "Toque numa casa do tabuleiro para construir, vender ou hipotecar.\n\n"
                + "• Dupla nos dados = joga de novo. Três duplas seguidas = prisão.\n\n"
                + "• Na prisão: tire dupla, pague R$ 50 ou use a carta. Na 3ª tentativa a fiança é cobrada.\n\n"
                + "• Ficou devendo? Venda casas ou hipoteque. Se não der, é falência.\n\n"
                + "• Vence quem sobrar!\n\n"
                + "Bluetooth: um celular cria a sala; os amigos tocam em \"Entrar numa sala\" e escolhem o celular do anfitrião. "
                + "Se o celular não aparecer, pareie os aparelhos antes nas configurações de Bluetooth.";
        new AlertDialog.Builder(this).setTitle("Como jogar").setMessage(s).setPositiveButton("Entendi", null).show();
    }

    // ================================================================= modo local

    private void showLocalSetup() {
        screen = "local";
        LinearLayout c = column();
        c.addView(title("Mesmo celular"));
        c.addView(label("Escreva o nome de 2 a 6 jogadores (deixe vazio quem não vai jogar). O celular passa de mão em mão.", 15));
        final EditText[] fields = new EditText[MAX_PLAYERS];
        for (int i = 0; i < MAX_PLAYERS; i++) {
            String def = i == 0 ? myName() : (i == 1 ? "Jogador 2" : "");
            fields[i] = input("Jogador " + (i + 1), def);
            c.addView(fields[i]);
        }
        c.addView(button("▶  Começar", GREEN, v -> {
            List<String> names = new ArrayList<>();
            for (EditText f : fields) {
                String s = f.getText().toString().trim();
                if (!s.isEmpty()) names.add(s);
            }
            if (names.size() < 2) { toast("Precisa de pelo menos 2 jogadores."); return; }
            mode = MODE_LOCAL;
            myIndex = -1;
            game = new Game(names.toArray(new String[0]), new SecureRandom());
            showGame();
        }));
        c.addView(button("Voltar", GREY, v -> showMenu()));
        setContentView(page(c));
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
            mode = MODE_HOST;
            myIndex = 0;
            gameStarted = false;
            leaving = false;
            clients.clear();
            server = new Bt.Server();
            server.start(bt, hostServerCallback);
            requestDiscoverable();
            showHostLobby();
        });
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
            ui.post(() -> { if (mode == MODE_HOST && !gameStarted) toast(msg); });
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
            String n = Msg.readStr(data).trim();
            link.name = n.isEmpty() ? "Amigo" : n;
            if (gameStarted) { link.close(); return; }
            broadcastLobby();
            if (screen.equals("hostLobby")) refreshHostLobby();
        } else if (type == Link.ACTION) {
            if (game == null || link.tag < 0) return;
            int[] v = Msg.readInts(data, 2);
            String err = game.apply(link.tag, v[0], v[1]);
            if (err != null) link.send(Link.ERROR, Msg.str(err));
            else hostChanged();
        }
    }

    private void hostClientGone(Link link) {
        if (!clients.remove(link)) return;
        if (mode != MODE_HOST) return;
        if (!gameStarted) {
            broadcastLobby();
            if (screen.equals("hostLobby")) refreshHostLobby();
        } else if (game != null && link.tag >= 0 && game.phase != Game.PH_OVER
                && !game.players[link.tag].bankrupt) {
            toast(link.name + " desconectou e saiu da partida.");
            game.apply(link.tag, Game.A_BANKRUPT, 0);
            hostChanged();
        }
    }

    private List<String> lobbyList() {
        List<String> names = new ArrayList<>();
        names.add(myName());
        for (Link l : clients) if (!l.name.isEmpty()) names.add(l.name);
        return names;
    }

    private void broadcastLobby() {
        byte[] d = Msg.names(lobbyList());
        for (Link l : clients) if (!l.name.isEmpty()) l.send(Link.LOBBY, d);
    }

    private void showHostLobby() {
        screen = "hostLobby";
        LinearLayout c = column();
        c.addView(title("Sala aberta"));
        c.addView(label("Peça para os amigos abrirem o Magnata, tocarem em \"Entrar numa sala\" e escolherem este celular"
                + (bt != null && bt.getName() != null ? " (\"" + bt.getName() + "\")" : "") + ".", 15));
        c.addView(label("Jogadores na sala:", 17));
        listBox = column();
        c.addView(listBox);
        c.addView(button("👁  Ficar visível de novo", GREEN_LIGHT, v -> requestDiscoverable()));
        c.addView(button("▶  Começar partida", GREEN, v -> hostStartGame()));
        c.addView(button("Cancelar", GREY, v -> { shutdownNetwork(); showMenu(); }));
        setContentView(page(c));
        refreshHostLobby();
    }

    private void refreshHostLobby() {
        if (listBox == null) return;
        listBox.removeAllViews();
        List<String> names = lobbyList();
        for (int i = 0; i < names.size(); i++) {
            TextView t = label((i == 0 ? "👑 " : "• ") + names.get(i) + (i == 0 ? " (você)" : ""), 17);
            t.setTextColor(Game.PLAYER_COLORS[i % Game.PLAYER_COLORS.length]);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            listBox.addView(t);
        }
        if (names.size() < 2) listBox.addView(label("Aguardando amigos se conectarem...", 14));
    }

    private void hostStartGame() {
        List<Link> ready = new ArrayList<>();
        for (Link l : new ArrayList<>(clients)) {
            if (l.name.isEmpty()) { l.close(); continue; }
            ready.add(l);
        }
        if (ready.isEmpty()) { toast("Espere pelo menos 1 amigo entrar na sala."); return; }
        List<String> names = new ArrayList<>();
        names.add(myName());
        for (int i = 0; i < ready.size(); i++) {
            Link l = ready.get(i);
            l.tag = i + 1;
            names.add(l.name);
            l.send(Link.WELCOME, Msg.ints(l.tag));
        }
        gameStarted = true;
        if (server != null) server.stop();
        game = new Game(names.toArray(new String[0]), new SecureRandom());
        showGame();
        hostChanged();
    }

    private void hostChanged() {
        if (game == null) return;
        byte[] s = game.toBytes();
        for (Link l : clients) if (l.tag >= 0) l.send(Link.STATE, s);
        refreshGame();
    }

    // ================================================================= cliente

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
        c.addView(title("Entrar numa sala"));
        c.addView(label("Toque no celular de quem criou a sala:", 15));
        statusText = label("", 14);
        c.addView(statusText);
        listBox = column();
        c.addView(listBox);
        c.addView(button("🔍  Procurar aparelhos", GREEN_LIGHT, v -> startDiscovery()));
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
        for (final BluetoothDevice d : found) {
            listBox.addView(button("📱  " + deviceName(d), GREEN, v -> connectTo(d)));
        }
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
                if (n == null) return; // ignora aparelhos sem nome (fones, relógios etc. costumam ter)
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
            @Override public void onConnected(final Link link) {
                ui.post(() -> {
                    if (!screen.equals("join")) { link.close(); return; }
                    mode = MODE_CLIENT;
                    leaving = false;
                    hostLink = link;
                    myIndex = -1;
                    lobbyNames = new String[0];
                    link.start(clientListener);
                    link.send(Link.HELLO, Msg.str(myName()));
                    showWaiting();
                });
            }
            @Override public void onConnectFailed(final String msg) {
                ui.post(() -> {
                    if (statusText != null) statusText.setText(msg);
                });
            }
        });
    }

    private final Link.Listener clientListener = new Link.Listener() {
        @Override public void onMessage(Link link, final byte type, final byte[] data) {
            ui.post(() -> clientHandle(type, data));
        }
        @Override public void onClosed(Link link) {
            ui.post(() -> {
                if (mode != MODE_CLIENT || leaving) return;
                boolean over = game != null && game.phase == Game.PH_OVER;
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
            case Link.LOBBY:
                lobbyNames = Msg.readNames(data);
                if (screen.equals("waiting")) refreshWaiting();
                break;
            case Link.STATE:
                try {
                    game = Game.fromBytes(data);
                } catch (Exception e) {
                    return;
                }
                if (!screen.equals("game")) showGame();
                else refreshGame();
                break;
            case Link.ERROR:
                toast(Msg.readStr(data));
                break;
            default:
                break;
        }
    }

    private void showWaiting() {
        screen = "waiting";
        LinearLayout c = column();
        c.addView(title("Na sala!"));
        c.addView(label("Conectado. Aguarde o anfitrião começar a partida.", 15));
        listBox = column();
        c.addView(listBox);
        c.addView(button("Sair da sala", GREY, v -> { shutdownNetwork(); showMenu(); }));
        setContentView(page(c));
        refreshWaiting();
    }

    private void refreshWaiting() {
        if (listBox == null) return;
        listBox.removeAllViews();
        for (int i = 0; i < lobbyNames.length; i++) {
            TextView t = label((i == 0 ? "👑 " : "• ") + lobbyNames[i], 17);
            t.setTextColor(Game.PLAYER_COLORS[i % Game.PLAYER_COLORS.length]);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            listBox.addView(t);
        }
    }

    // ================================================================= rede: encerrar

    private void shutdownNetwork() {
        leaving = true;
        stopDiscovery();
        if (server != null) { server.stop(); server = null; }
        for (Link l : new ArrayList<>(clients)) l.close();
        clients.clear();
        if (hostLink != null) { hostLink.close(); hostLink = null; }
        gameStarted = false;
    }

    // ================================================================= tela do jogo

    private void showGame() {
        screen = "game";
        lastCardSeq = -1;
        lastCurrent = -1;
        LinearLayout c = column();
        c.setPadding(0, 0, 0, dp(24));
        board = new BoardView(this);
        board.setOnSquareTap(this::showSquare);
        c.addView(board, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout inner = column();
        inner.setPadding(dp(12), dp(4), dp(12), 0);
        gameStatus = label("", 15);
        gameStatus.setTypeface(Typeface.DEFAULT_BOLD);
        inner.addView(gameStatus);
        actionsBox = column();
        inner.addView(actionsBox);
        TextView pl = label("Jogadores", 17);
        pl.setTypeface(Typeface.DEFAULT_BOLD);
        pl.setPadding(0, dp(14), 0, 0);
        inner.addView(pl);
        playersBox = column();
        inner.addView(playersBox);
        TextView lt = label("Acontecimentos", 17);
        lt.setTypeface(Typeface.DEFAULT_BOLD);
        lt.setPadding(0, dp(14), 0, 0);
        inner.addView(lt);
        logView = label("", 13);
        inner.addView(logView);
        c.addView(inner);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BoardView.BG);
        sv.addView(c);
        setContentView(sv);
        refreshGame();
    }

    /** Quem está agindo neste celular. */
    private int actor() {
        if (game == null) return -1;
        return mode == MODE_LOCAL ? game.current : myIndex;
    }

    private void doAction(int a, int arg) {
        if (game == null) return;
        if (mode == MODE_CLIENT) {
            if (hostLink != null) hostLink.send(Link.ACTION, Msg.ints(a, arg));
            return;
        }
        int p = actor();
        if (mode == MODE_LOCAL && (a == Game.A_BUILD || a == Game.A_SELL || a == Game.A_MORTGAGE || a == Game.A_UNMORTGAGE)) {
            p = game.owner[arg];
        }
        String err = game.apply(p, a, arg);
        if (err != null) { toast(err); return; }
        if (mode == MODE_HOST) hostChanged();
        else refreshGame();
    }

    private void refreshGame() {
        if (game == null || !screen.equals("game")) return;
        int me = actor();
        board.setGame(game, me);

        // carta sorteada
        if (lastCardSeq == -1) {
            lastCardSeq = game.cardSeq;
        } else if (game.cardSeq != lastCardSeq) {
            lastCardSeq = game.cardSeq;
            if (!game.lastCard.isEmpty()) {
                new AlertDialog.Builder(this).setMessage(game.lastCard).setPositiveButton("OK", null).show();
            }
        }
        // aviso de vez
        if (game.current != lastCurrent) {
            lastCurrent = game.current;
            if (mode != MODE_LOCAL && game.current == myIndex && game.phase != Game.PH_OVER) toast("🎲 Sua vez!");
            if (mode == MODE_LOCAL && game.phase != Game.PH_OVER) toast("Vez de " + game.cur().name);
        }

        Player cur = game.cur();
        actionsBox.removeAllViews();

        if (game.phase == Game.PH_OVER) {
            String wn = game.winner >= 0 ? game.players[game.winner].name : "?";
            gameStatus.setText("🏆 " + wn + " venceu a partida!");
            if (mode == MODE_LOCAL) {
                actionsBox.addView(button("Nova partida", GREEN, v -> showLocalSetup()));
            }
            actionsBox.addView(button("Voltar ao menu", GREY, v -> { shutdownNetwork(); showMenu(); }));
        } else {
            boolean myTurn = me == game.current;
            Player mp = me >= 0 && me < game.players.length ? game.players[me] : null;
            if (mp != null && mp.bankrupt && mode != MODE_LOCAL) {
                gameStatus.setText("Você faliu. Pode continuar assistindo.");
            } else if (!myTurn) {
                gameStatus.setText("Aguardando " + cur.name + " jogar...");
            } else {
                String hint;
                switch (game.phase) {
                    case Game.PH_ROLL: hint = cur.inJail ? "Você está na prisão." : "Role os dados!"; break;
                    case Game.PH_BUY: hint = "Quer comprar " + Board.NAME[game.pendingBuy] + "?"; break;
                    default: hint = cur.money < 0 ? "Você está devendo R$ " + (-cur.money) + "!" : "Toque em Encerrar vez."; break;
                }
                gameStatus.setText((mode == MODE_LOCAL ? cur.name + ": " : "") + hint + "   Saldo: R$ " + cur.money);

                if (game.validate(me, Game.A_ROLL, 0) == null) {
                    actionsBox.addView(button("🎲  Rolar dados", GREEN, v -> doAction(Game.A_ROLL, 0)));
                }
                if (game.phase == Game.PH_ROLL && cur.inJail) {
                    if (game.validate(me, Game.A_BAIL, 0) == null)
                        actionsBox.addView(button("Pagar fiança (R$ " + Game.BAIL + ")", GOLD, v -> doAction(Game.A_BAIL, 0)));
                    if (game.validate(me, Game.A_CARD, 0) == null)
                        actionsBox.addView(button("Usar carta de saída livre", GOLD, v -> doAction(Game.A_CARD, 0)));
                }
                if (game.phase == Game.PH_BUY) {
                    int pos = game.pendingBuy;
                    actionsBox.addView(button("✅  Comprar " + Board.NAME[pos] + " (R$ " + Board.PRICE[pos] + ")", GREEN,
                            v -> doAction(Game.A_BUY, 0)));
                    actionsBox.addView(button("Não comprar", GREY, v -> doAction(Game.A_DECLINE, 0)));
                }
                if (game.phase == Game.PH_AFTER) {
                    actionsBox.addView(button("⏭  Encerrar vez", cur.money < 0 ? GREY : GREEN_LIGHT,
                            v -> doAction(Game.A_END, 0)));
                }
                if (cur.money < 0) {
                    actionsBox.addView(button("Declarar falência", RED, v -> confirmBankrupt()));
                }
            }
            if (mp != null && !mp.bankrupt) {
                actionsBox.addView(button("🏠  Minhas propriedades", 0xFF5D4037, v -> showMyProperties(me)));
            }
            actionsBox.addView(button("Sair da partida", GREY, v -> confirmExit()));
        }

        // jogadores
        playersBox.removeAllViews();
        for (int i = 0; i < game.players.length; i++) {
            final int idx = i;
            Player p = game.players[i];
            StringBuilder sb = new StringBuilder();
            sb.append(i == game.current && game.phase != Game.PH_OVER ? "▶ " : "● ");
            sb.append(p.name);
            if (mode != MODE_LOCAL && i == myIndex) sb.append(" (você)");
            if (p.bankrupt) sb.append("  — faliu");
            else {
                sb.append("   R$ ").append(p.money);
                if (p.inJail) sb.append("  🔒preso");
                if (p.jailCards > 0) sb.append("  🎫").append(p.jailCards);
            }
            TextView t = label(sb.toString(), 16);
            t.setTextColor(p.bankrupt ? GREY : p.color);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setShadowLayer(1, 0, 0, 0x55000000);
            t.setOnClickListener(v -> showPlayerInfo(idx));
            playersBox.addView(t);
        }

        // registro
        StringBuilder lg = new StringBuilder();
        int shown = 0;
        for (int i = game.log.size() - 1; i >= 0 && shown < 10; i--, shown++) {
            lg.append("• ").append(game.log.get(i)).append('\n');
        }
        logView.setText(lg.toString());
    }

    private void confirmBankrupt() {
        new AlertDialog.Builder(this)
                .setTitle("Declarar falência?")
                .setMessage("Você sai da partida e seus bens vão para quem você deve. Tem certeza?")
                .setPositiveButton("Falir", (d, w) -> doAction(Game.A_BANKRUPT, 0))
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
                    if (mode == MODE_CLIENT && hostLink != null && game != null && game.phase != Game.PH_OVER) {
                        hostLink.send(Link.ACTION, Msg.ints(Game.A_BANKRUPT, 0));
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

    // ================================================================= detalhes de casas e jogadores

    private String squareInfo(int pos) {
        StringBuilder sb = new StringBuilder();
        int t = Board.TYPE[pos];
        if (t == Board.PROP) {
            sb.append("Cor: ").append(Board.GROUP_NAME[Board.GROUP[pos]]).append('\n');
            sb.append("Preço: R$ ").append(Board.PRICE[pos]).append('\n');
            int[] r = Board.RENT[pos];
            sb.append("Aluguel: R$ ").append(r[0]).append(" (R$ ").append(r[0] * 2).append(" com a cor completa)\n");
            for (int i = 1; i <= 4; i++) sb.append("  ").append(i).append(i == 1 ? " casa: R$ " : " casas: R$ ").append(r[i]).append('\n');
            sb.append("  Hotel: R$ ").append(r[5]).append('\n');
            sb.append("Casa/hotel custa R$ ").append(Board.HOUSE_COST[pos]).append('\n');
        } else if (t == Board.TRANSPORT) {
            sb.append("Transporte — preço R$ 200\nAluguel: R$ 25 / 50 / 100 / 200 conforme quantos transportes o dono tem.\n");
        } else if (t == Board.UTIL) {
            sb.append("Companhia — preço R$ 150\nAluguel: 4× os dados (10× se o dono tiver as duas).\n");
        } else if (t == Board.TAX) {
            sb.append("Pague R$ ").append(Board.PRICE[pos]).append(" ao banco.\n");
        } else if (t == Board.DESTINO || t == Board.SURPRESA) {
            sb.append("Tire uma carta.\n");
        } else if (t == Board.GO) {
            sb.append("Receba R$ 200 ao passar.\n");
        } else if (t == Board.GOTOJAIL) {
            sb.append("Vá direto para a prisão.\n");
        } else if (t == Board.JAIL) {
            sb.append("Só visitando — ou preso.\n");
        } else {
            sb.append("Nada acontece aqui. Descanse!\n");
        }
        if (game != null && Board.buyable(pos)) {
            int o = game.owner[pos];
            sb.append('\n').append("Dono: ").append(o < 0 ? "ninguém (à venda)" : game.players[o].name).append('\n');
            if (o >= 0) {
                if (t == Board.PROP) {
                    int h = game.houses[pos];
                    sb.append("Construções: ").append(h == 5 ? "hotel" : h + (h == 1 ? " casa" : " casas")).append('\n');
                }
                if (game.mortgaged[pos]) sb.append("HIPOTECADA (não cobra aluguel)\n");
                else if (t != Board.UTIL) sb.append("Aluguel agora: R$ ").append(game.rentOf(pos)).append('\n');
            }
        }
        return sb.toString();
    }

    private void showSquare(final int pos) {
        if (game == null) return;
        LinearLayout box = column();
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(label(squareInfo(pos), 15));

        final AlertDialog[] dlg = new AlertDialog[1];
        int o = game.owner[pos];
        int me = mode == MODE_LOCAL ? o : myIndex;
        if (Board.buyable(pos) && o >= 0 && o == me && game.phase != Game.PH_OVER) {
            if (game.validate(me, Game.A_BUILD, pos) == null) {
                String what = game.houses[pos] == 4 ? "hotel" : "casa";
                box.addView(button("Construir " + what + " (R$ " + Board.HOUSE_COST[pos] + ")", GREEN,
                        v -> { dlg[0].dismiss(); doAction(Game.A_BUILD, pos); }));
            }
            if (game.validate(me, Game.A_SELL, pos) == null) {
                box.addView(button("Vender construção (+R$ " + Board.HOUSE_COST[pos] / 2 + ")", GOLD,
                        v -> { dlg[0].dismiss(); doAction(Game.A_SELL, pos); }));
            }
            if (game.validate(me, Game.A_MORTGAGE, pos) == null) {
                box.addView(button("Hipotecar (+R$ " + Board.mortgageValue(pos) + ")", 0xFF5D4037,
                        v -> { dlg[0].dismiss(); doAction(Game.A_MORTGAGE, pos); }));
            }
            if (game.validate(me, Game.A_UNMORTGAGE, pos) == null) {
                box.addView(button("Recuperar hipoteca (R$ " + Board.unmortgageCost(pos) + ")", GREEN_LIGHT,
                        v -> { dlg[0].dismiss(); doAction(Game.A_UNMORTGAGE, pos); }));
            }
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        dlg[0] = new AlertDialog.Builder(this)
                .setTitle(Board.NAME[pos])
                .setView(sv)
                .setPositiveButton("Fechar", null)
                .show();
    }

    private void showMyProperties(int p) {
        if (game == null || p < 0) return;
        final List<Integer> mine = new ArrayList<>();
        for (int i = 0; i < Board.SIZE; i++) if (game.owner[i] == p) mine.add(i);
        if (mine.isEmpty()) {
            new AlertDialog.Builder(this).setTitle(game.players[p].name)
                    .setMessage("Ainda não tem propriedades.").setPositiveButton("OK", null).show();
            return;
        }
        String[] items = new String[mine.size()];
        for (int i = 0; i < mine.size(); i++) {
            int pos = mine.get(i);
            String extra = "";
            if (Board.TYPE[pos] == Board.PROP) {
                extra = " [" + Board.GROUP_NAME[Board.GROUP[pos]] + "]";
                int h = game.houses[pos];
                if (h == 5) extra += " 🏨";
                else if (h > 0) extra += " " + h + "🏠";
            }
            if (game.mortgaged[pos]) extra += " (hipotecada)";
            items[i] = Board.NAME[pos] + extra;
        }
        new AlertDialog.Builder(this)
                .setTitle("Propriedades de " + game.players[p].name)
                .setItems(items, (d, w) -> showSquare(mine.get(w)))
                .setPositiveButton("Fechar", null)
                .show();
    }

    private void showPlayerInfo(int p) {
        if (game == null) return;
        Player pl = game.players[p];
        StringBuilder sb = new StringBuilder();
        sb.append("Dinheiro: R$ ").append(pl.money).append('\n');
        sb.append("Patrimônio: R$ ").append(game.netWorth(p)).append("\n\n");
        int count = 0;
        for (int i = 0; i < Board.SIZE; i++) {
            if (game.owner[i] != p) continue;
            count++;
            sb.append("• ").append(Board.NAME[i]);
            if (Board.TYPE[i] == Board.PROP && game.houses[i] > 0)
                sb.append(game.houses[i] == 5 ? " (hotel)" : " (" + game.houses[i] + " casas)");
            if (game.mortgaged[i]) sb.append(" (hipotecada)");
            sb.append('\n');
        }
        if (count == 0) sb.append("Sem propriedades.");
        new AlertDialog.Builder(this).setTitle(pl.name).setMessage(sb.toString()).setPositiveButton("OK", null).show();
    }
}
