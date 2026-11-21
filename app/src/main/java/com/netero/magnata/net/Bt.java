package com.netero.magnata.net;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.util.UUID;

/** Conexões Bluetooth clássicas (RFCOMM). */
@SuppressLint("MissingPermission")
public final class Bt {
    private Bt() {}

    public static final UUID APP_UUID = UUID.fromString("7a3c5e10-4b2d-4f6a-9c1e-6d2b8a9f0c11");
    public static final String SERVICE = "MagnataJogo";

    public interface ServerCallback {
        void onClient(Link link);
        void onServerError(String msg);
    }

    public interface ConnectCallback {
        void onConnected(Link link);
        void onConnectFailed(String msg);
    }

    /** Servidor do anfitrião: aceita jogadores até ser parado. */
    public static class Server {
        private volatile BluetoothServerSocket ss;
        private volatile boolean running;

        public void start(final BluetoothAdapter adapter, final ServerCallback cb) {
            running = true;
            Thread t = new Thread(() -> {
                try {
                    ss = adapter.listenUsingInsecureRfcommWithServiceRecord(SERVICE, APP_UUID);
                    while (running) {
                        BluetoothSocket s = ss.accept();
                        if (s == null) continue;
                        if (!running) { try { s.close(); } catch (IOException ignored) { } break; }
                        cb.onClient(new Link(s, s.getInputStream(), s.getOutputStream()));
                    }
                } catch (IOException | SecurityException e) {
                    if (running) cb.onServerError("Erro no Bluetooth: " + e.getMessage());
                }
            }, "bt-accept");
            t.setDaemon(true);
            t.start();
        }

        public void stop() {
            running = false;
            BluetoothServerSocket s = ss;
            if (s != null) {
                try { s.close(); } catch (IOException ignored) { }
            }
        }
    }

    /** Cliente: conecta no celular do anfitrião. */
    public static void connect(final BluetoothAdapter adapter, final BluetoothDevice device, final ConnectCallback cb) {
        Thread t = new Thread(() -> {
            try { adapter.cancelDiscovery(); } catch (SecurityException ignored) { }
            BluetoothSocket s = null;
            String err = "";
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    s = attempt == 0
                            ? device.createInsecureRfcommSocketToServiceRecord(APP_UUID)
                            : device.createRfcommSocketToServiceRecord(APP_UUID);
                    s.connect();
                    cb.onConnected(new Link(s, s.getInputStream(), s.getOutputStream()));
                    return;
                } catch (IOException | SecurityException e) {
                    err = e.getMessage();
                    if (s != null) { try { s.close(); } catch (IOException ignored) { } }
                    s = null;
                }
            }
            cb.onConnectFailed("Não foi possível conectar. Confira se o anfitrião está com a sala aberta. (" + err + ")");
        }, "bt-connect");
        t.setDaemon(true);
        t.start();
    }
}
