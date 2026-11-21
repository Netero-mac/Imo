package com.netero.magnata.net;

import java.io.IOException;
import java.net.ConnectException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.NoRouteToHostException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Conexão pela rede Wi-Fi (TCP). O anfitrião anuncia a sala por broadcast UDP
 * com o nome, a porta e todos os seus endereços; o cliente tenta cada um.
 */
public final class Wifi {
    private Wifi() {}

    public static final int PORT = 47321;
    public static final int PORT_TRIES = 8;
    public static final int DISCOVERY_PORT = 47320;
    private static final String TAG = "MAGNATA2|";

    public interface RoomCallback {
        void onRoom(String name, String ip, int port, String[] ips);
    }

    /** Endereços IPv4 locais deste celular (Wi-Fi ou roteador/hotspot). */
    public static List<String> localIps() {
        List<String> r = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                String n = ni.getName() == null ? "" : ni.getName();
                if (n.startsWith("rmnet") || n.startsWith("ccmni") || n.startsWith("tun") || n.startsWith("dummy")) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) r.add(a.getHostAddress());
                }
            }
        } catch (Exception ignored) { }
        return r;
    }

    private static List<InetAddress> broadcastAddresses() {
        List<InetAddress> r = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    if (ia.getBroadcast() != null) r.add(ia.getBroadcast());
                }
            }
            r.add(InetAddress.getByName("255.255.255.255"));
        } catch (Exception ignored) { }
        return r;
    }

    /** Servidor do anfitrião: aceita jogadores e anuncia a sala. */
    public static class Server {
        private volatile ServerSocket ss;
        private volatile boolean running;
        public volatile int port = -1;

        public void start(final String roomName, final Bt.ServerCallback cb) {
            running = true;
            // abre a porta já aqui (para saber qual porta anunciar)
            IOException last = null;
            for (int i = 0; i < PORT_TRIES && ss == null; i++) {
                try {
                    ServerSocket s = new ServerSocket();
                    s.setReuseAddress(true);
                    s.bind(new InetSocketAddress(PORT + i));
                    ss = s;
                    port = PORT + i;
                } catch (IOException e) {
                    last = e;
                }
            }
            if (ss == null) {
                running = false;
                cb.onServerError("Não foi possível abrir a sala no Wi-Fi (" + (last != null ? last.getMessage() : "porta ocupada")
                        + "). Feche e abra o jogo de novo.");
                return;
            }
            final ServerSocket server = ss;
            Thread accept = new Thread(() -> {
                try {
                    while (running) {
                        Socket c = server.accept();
                        if (!running) { try { c.close(); } catch (IOException ignored) { } break; }
                        c.setTcpNoDelay(true);
                        c.setKeepAlive(true);
                        cb.onClient(new Link(c, c.getInputStream(), c.getOutputStream()));
                    }
                } catch (IOException e) {
                    if (running) cb.onServerError("Erro no Wi-Fi: " + e.getMessage());
                }
            }, "wifi-accept");
            accept.setDaemon(true);
            accept.start();

            Thread beacon = new Thread(() -> {
                String safe = roomName.replace('|', ' ');
                try (DatagramSocket ds = new DatagramSocket()) {
                    ds.setBroadcast(true);
                    while (running) {
                        String payload = TAG + safe + "|" + port + "|" + String.join(",", localIps());
                        byte[] msg = payload.getBytes(StandardCharsets.UTF_8);
                        for (InetAddress b : broadcastAddresses()) {
                            try { ds.send(new DatagramPacket(msg, msg.length, b, DISCOVERY_PORT)); }
                            catch (IOException ignored) { }
                        }
                        try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
                    }
                } catch (IOException ignored) { }
            }, "wifi-beacon");
            beacon.setDaemon(true);
            beacon.start();
        }

        public void stop() {
            running = false;
            ServerSocket s = ss;
            ss = null;
            if (s != null) {
                try { s.close(); } catch (IOException ignored) { }
            }
        }
    }

    /** Procura salas anunciadas na rede até stop(). */
    public static class Finder {
        private volatile boolean running;
        private volatile DatagramSocket ds;

        public void start(final RoomCallback cb) {
            running = true;
            Thread t = new Thread(() -> {
                try {
                    DatagramSocket s = new DatagramSocket(null);
                    s.setReuseAddress(true);
                    s.setBroadcast(true);
                    s.bind(new InetSocketAddress(DISCOVERY_PORT));
                    s.setSoTimeout(1000);
                    ds = s;
                    byte[] buf = new byte[1024];
                    while (running) {
                        DatagramPacket p = new DatagramPacket(buf, buf.length);
                        try {
                            s.receive(p);
                        } catch (SocketTimeoutException e) {
                            continue;
                        }
                        String m = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                        if (!m.startsWith(TAG) || p.getAddress() == null) continue;
                        String[] parts = m.substring(TAG.length()).split("\\|", -1);
                        String name = parts.length > 0 ? parts[0] : "?";
                        int port = PORT;
                        try { if (parts.length > 1) port = Integer.parseInt(parts[1].trim()); } catch (NumberFormatException ignored) { }
                        String[] ips = parts.length > 2 && !parts[2].isEmpty() ? parts[2].split(",") : new String[0];
                        cb.onRoom(name, p.getAddress().getHostAddress(), port, ips);
                    }
                } catch (IOException ignored) {
                } finally {
                    DatagramSocket s = ds;
                    if (s != null) s.close();
                }
            }, "wifi-finder");
            t.setDaemon(true);
            t.start();
        }

        public void stop() {
            running = false;
            DatagramSocket s = ds;
            if (s != null) s.close();
        }
    }

    /** Mesma sub-rede /24 (ex.: 192.168.0.x). */
    public static boolean sameSubnet(String a, String b) {
        int ia = a.lastIndexOf('.'), ib = b.lastIndexOf('.');
        return ia > 0 && ib > 0 && a.substring(0, ia).equals(b.substring(0, ib));
    }

    /**
     * Monta a lista de endereços a tentar: primeiro o endereço de onde veio o anúncio da sala,
     * depois os outros endereços do anfitrião que estão na mesma rede. Nunca tenta endereços
     * deste próprio celular (evita "conectar em si mesmo").
     */
    public static List<String> candidates(String source, String[] alts) {
        List<String> mine = localIps();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (source != null && !mine.contains(source)) out.add(source);
        if (alts != null) {
            for (String a : alts) {
                a = a.trim();
                if (a.isEmpty() || mine.contains(a)) continue;
                if (source == null || sameSubnet(a, source)) out.add(a);
            }
        }
        if (out.isEmpty() && source != null) out.add(source);
        return new ArrayList<>(out);
    }

    /**
     * Tenta conectar em cada endereço (e porta) da lista, duas rodadas.
     * O diagnóstico final se baseia no primeiro endereço (o principal).
     */
    public static void connect(final List<String> ips, final int[] ports, final Bt.ConnectCallback cb) {
        Thread t = new Thread(() -> {
            List<String> unique = new ArrayList<>(new LinkedHashSet<>(ips));
            String primaryResult = null;
            StringBuilder detail = new StringBuilder();
            for (int round = 0; round < 2; round++) {
                for (int idx = 0; idx < unique.size(); idx++) {
                    String ip = unique.get(idx).trim();
                    for (int port : ports) {
                        Socket s = new Socket();
                        String res;
                        try {
                            s.connect(new InetSocketAddress(ip, port), round == 0 ? 4000 : 8000);
                            s.setTcpNoDelay(true);
                            s.setKeepAlive(true);
                            cb.onConnected(new Link(s, s.getInputStream(), s.getOutputStream()));
                            return;
                        } catch (SocketTimeoutException e) {
                            res = "sem resposta";
                        } catch (ConnectException e) {
                            res = "recusou";
                        } catch (NoRouteToHostException e) {
                            res = "sem rota";
                        } catch (Exception e) {
                            res = e.getClass().getSimpleName();
                        }
                        try { s.close(); } catch (IOException ignored) { }
                        if (round == 1) {
                            if (idx == 0 && primaryResult == null) primaryResult = res;
                            detail.append(ip).append(':').append(port).append(" → ").append(res).append('\n');
                        }
                    }
                }
                try { Thread.sleep(600); } catch (InterruptedException ignored) { }
            }
            String why;
            if ("recusou".equals(primaryResult)) {
                why = "O celular do anfitrião recebeu o pedido mas recusou. Peça para ele cancelar e criar a sala de novo, "
                        + "com o jogo aberto na tela da sala. Se continuar, desliguem VPN / \"Wi-Fi Seguro\" nos dois celulares.";
            } else if ("sem resposta".equals(primaryResult) || "sem rota".equals(primaryResult)) {
                why = "A rede não deixou os celulares conversarem. O roteador provavelmente bloqueia conexão entre aparelhos "
                        + "(comum em Wi-Fi de visitantes, faculdade e alguns roteadores), ou há VPN / \"Wi-Fi Seguro\" ligado.\n\n"
                        + "Solução: um celular liga o roteador do celular (hotspot) e o outro se conecta nele — não gasta internet. "
                        + "Ou usem o Bluetooth.";
            } else {
                why = "Não foi possível conectar.";
            }
            cb.onConnectFailed(why + "\n\nDetalhes:\n" + detail.toString().trim());
        }, "wifi-connect");
        t.setDaemon(true);
        t.start();
    }
}
