package com.netero.magnata.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.netero.magnata.engine.Player;

/** Ajudantes para montar/ler o conteúdo das mensagens. */
public final class Msg {
    private Msg() {}

    public static final int PROTOCOL = 2;

    public static byte[] setup(Player.Setup s) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream o = new DataOutputStream(b);
            o.writeInt(PROTOCOL);
            o.writeUTF(s.name);
            o.writeInt(s.color);
            o.writeInt(s.pawn);
            o.writeInt(s.character);
            o.flush();
            return b.toByteArray();
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    /** null se a versão do jogo for diferente. */
    public static Player.Setup readSetup(byte[] d) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(d));
            if (in.readInt() != PROTOCOL) return null;
            return new Player.Setup(in.readUTF(), in.readInt(), in.readInt(), in.readInt());
        } catch (IOException e) { return null; }
    }

    public static byte[] lobby(List<Player.Setup> list, int maxRounds) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream o = new DataOutputStream(b);
            o.writeInt(maxRounds);
            o.writeInt(list.size());
            for (Player.Setup s : list) {
                o.writeUTF(s.name);
                o.writeInt(s.color);
                o.writeInt(s.pawn);
                o.writeInt(s.character);
            }
            o.flush();
            return b.toByteArray();
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    /** Retorna {maxRounds} em out[0] e a lista. */
    public static List<Player.Setup> readLobby(byte[] d, int[] out) {
        List<Player.Setup> r = new ArrayList<>();
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(d));
            out[0] = in.readInt();
            int n = in.readInt();
            for (int i = 0; i < n && i < 16; i++) r.add(new Player.Setup(in.readUTF(), in.readInt(), in.readInt(), in.readInt()));
        } catch (IOException ignored) { }
        return r;
    }

    public static byte[] action(int a, int... x) {
        int[] v = new int[x.length + 2];
        v[0] = a;
        v[1] = x.length;
        System.arraycopy(x, 0, v, 2, x.length);
        return ints(v);
    }

    /** {ação, args...} */
    public static int[] readAction(byte[] d) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(d));
            int a = in.readInt();
            int n = in.readInt();
            if (n < 0 || n > 16) n = 0;
            int[] r = new int[n + 1];
            r[0] = a;
            for (int i = 0; i < n; i++) r[i + 1] = in.readInt();
            return r;
        } catch (IOException e) { return new int[]{0}; }
    }

    public static byte[] str(String s) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream o = new DataOutputStream(b);
            o.writeUTF(s == null ? "" : s);
            o.flush();
            return b.toByteArray();
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    public static String readStr(byte[] d) {
        try {
            return new DataInputStream(new ByteArrayInputStream(d)).readUTF();
        } catch (IOException e) { return ""; }
    }

    public static byte[] ints(int... v) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream o = new DataOutputStream(b);
            for (int x : v) o.writeInt(x);
            o.flush();
            return b.toByteArray();
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    public static int[] readInts(byte[] d, int n) {
        int[] r = new int[n];
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(d));
            for (int i = 0; i < n; i++) r[i] = in.readInt();
        } catch (IOException ignored) { }
        return r;
    }

    public static byte[] names(List<String> names) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream o = new DataOutputStream(b);
            o.writeInt(names.size());
            for (String s : names) o.writeUTF(s);
            o.flush();
            return b.toByteArray();
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    public static String[] readNames(byte[] d) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(d));
            int n = in.readInt();
            if (n < 0 || n > 16) return new String[0];
            String[] r = new String[n];
            for (int i = 0; i < n; i++) r[i] = in.readUTF();
            return r;
        } catch (IOException e) { return new String[0]; }
    }
}
