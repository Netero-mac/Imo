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

    /**
     * Tenta conectar em cada endereço (e porta) da lista, duas rodadas.
     * ports: portas a tentar em cada endereço.
     */
    public static void connect(final List<String> ips, final int[] ports, final Bt.ConnectCallback cb) {
        Thread t = new Thread(() -> {
            LinkedHashSet<String> unique = new LinkedHashSet<>(ips);
            boolean timeout = false, refused = false, unreachable = false;
            String lastMsg = "";
            for (int round = 0; round < 2; round++) {
                for (String ip : unique) {
                    for (int port : ports) {
                        Socket s = new Socket();
                        try {
                            s.connect(new InetSocketAddress(ip.trim(), port), round == 0 ? 4000 : 7000);
                            s.setTcpNoDelay(true);
                            s.setKeepAlive(true);
                            cb.onConnected(new Link(s, s.getInputStream(), s.getOutputStream()));
                            return;
                        } catch (SocketTimeoutException e) {
                            timeout = true;
                            lastMsg = e.getMessage();
                        } catch (ConnectException e) {
                            refused = true;
                            lastMsg = e.getMessage();
                        } catch (NoRouteToHostException e) {
                            unreachable = true;
                            lastMsg = e.getMessage();
                        } catch (Exception e) {
                            lastMsg = e.getClass().getSimpleName() + ": " + e.getMessage();
                        }
                        try { s.close(); } catch (IOException ignored) { }
                    }
                }
                try { Thread.sleep(600); } catch (InterruptedException ignored) { }
            }
            String why;
            if (refused) {
                why = "O celular do anfitrião recusou a conexão. Peça para ele cancelar e criar a sala de novo (e manter o jogo aberto na tela da sala).";
            } else if (timeout || unreachable) {
                why = "A rede não deixou os celulares conversarem. Isso acontece quando o roteador bloqueia a conexão entre aparelhos "
                        + "(comum em Wi-Fi de visitantes, faculdade ou alguns roteadores) ou quando há VPN ligada. "
                        + "Solução: um celular liga o roteador (hotspot) e o outro se conecta nele, ou usem o Bluetooth.";
            } else {
                why = "Não foi possível conectar (" + lastMsg + ").";
            }
            cb.onConnectFailed(why);
        }, "wifi-connect");
        t.setDaemon(true);
        t.start();
    }
}
