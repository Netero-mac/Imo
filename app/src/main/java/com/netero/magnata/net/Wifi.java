package com.netero.magnata.net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Conexão pela rede Wi-Fi (TCP). O anfitrião também anuncia a sala por
 * broadcast UDP, para os outros celulares acharem sem digitar IP.
 */
public final class Wifi {
    private Wifi() {}

    public static final int PORT = 47321;
    public static final int DISCOVERY_PORT = 47322;
    private static final String TAG = "MAGNATA1|";

    public interface RoomCallback {
        void onRoom(String name, String ip);
    }

    /** Endereços IPv4 locais deste celular (Wi-Fi ou roteador/hotspot). */
    public static List<String> localIps() {
        List<String> r = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
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

        public void start(final String roomName, final Bt.ServerCallback cb) {
            running = true;
            Thread accept = new Thread(() -> {
                try {
                    ServerSocket s = new ServerSocket();
                    s.setReuseAddress(true);
                    s.bind(new InetSocketAddress(PORT));
                    ss = s;
                    while (running) {
                        Socket c = s.accept();
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
                byte[] msg = (TAG + roomName).getBytes(StandardCharsets.UTF_8);
                try (DatagramSocket ds = new DatagramSocket()) {
                    ds.setBroadcast(true);
                    while (running) {
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

        /** Para de aceitar novos jogadores (e de anunciar a sala). */
        public void stop() {
            running = false;
            ServerSocket s = ss;
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
                    byte[] buf = new byte[512];
                    while (running) {
                        DatagramPacket p = new DatagramPacket(buf, buf.length);
                        try {
                            s.receive(p);
                        } catch (SocketTimeoutException e) {
                            continue;
                        }
                        String m = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                        if (m.startsWith(TAG) && p.getAddress() != null) {
                            cb.onRoom(m.substring(TAG.length()), p.getAddress().getHostAddress());
                        }
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

    public static void connect(final String ip, final Bt.ConnectCallback cb) {
        Thread t = new Thread(() -> {
            Socket s = new Socket();
            try {
                s.connect(new InetSocketAddress(ip, PORT), 6000);
                s.setTcpNoDelay(true);
                s.setKeepAlive(true);
                cb.onConnected(new Link(s, s.getInputStream(), s.getOutputStream()));
            } catch (Exception e) {
                try { s.close(); } catch (IOException ignored) { }
                cb.onConnectFailed("Não foi possível conectar em " + ip
                        + ". Confira se estão na mesma rede Wi-Fi e se a sala está aberta.");
            }
        }, "wifi-connect");
        t.setDaemon(true);
        t.start();
    }
}
