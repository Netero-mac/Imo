package com.netero.magnata.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

/** Ajudantes para montar/ler o conteúdo das mensagens. */
public final class Msg {
    private Msg() {}

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
