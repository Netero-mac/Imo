package com.netero.magnata.net;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Uma conexão (socket Bluetooth) com mensagens em quadros:
 * [int tamanho][byte tipo][dados].
 */
public class Link {
    // Cliente -> Anfitrião
    public static final byte HELLO = 1;   // dados: perfil do jogador + versão
    public static final byte ACTION = 2;  // dados: int ação, int n, ints...
    // Anfitrião -> Cliente
    public static final byte WELCOME = 10; // dados: int índice do jogador
    public static final byte LOBBY = 11;   // dados: perfis + modo de jogo
    public static final byte STATE = 12;   // dados: bytes do Game
    public static final byte ERROR = 13;   // dados: UTF mensagem

    public interface Listener {
        void onMessage(Link link, byte type, byte[] data);
        void onClosed(Link link);
    }

    public int tag = -1;
    public String name = "";
    public com.netero.magnata.engine.Player.Setup setup;

    private final Closeable socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private volatile boolean closed;
    private Listener listener;

    public Link(Closeable socket, InputStream in, OutputStream out) {
        this.socket = socket;
        this.in = new DataInputStream(in);
        this.out = new DataOutputStream(out);
    }

    public void start(Listener l) {
        this.listener = l;
        Thread t = new Thread(this::readLoop, "link-reader");
        t.setDaemon(true);
        t.start();
    }

    private void readLoop() {
        try {
            while (!closed) {
                int len = in.readInt();
                if (len < 1 || len > 1_000_000) throw new IOException("quadro inválido");
                byte type = in.readByte();
                byte[] data = new byte[len - 1];
                in.readFully(data);
                if (listener != null) listener.onMessage(this, type, data);
            }
        } catch (IOException e) {
            // conexão caiu
        } finally {
            close();
        }
    }

    public void send(final byte type, final byte[] data) {
        if (closed) return;
        try {
            writer.execute(() -> {
                try {
                    synchronized (out) {
                        out.writeInt(data.length + 1);
                        out.writeByte(type);
                        out.write(data);
                        out.flush();
                    }
                } catch (IOException e) {
                    close();
                }
            });
        } catch (Exception ignored) {
            // executor encerrado
        }
    }

    public boolean isClosed() { return closed; }

    public void close() {
        if (closed) return;
        closed = true;
        try { socket.close(); } catch (IOException ignored) { }
        writer.shutdown();
        if (listener != null) listener.onClosed(this);
    }
}
