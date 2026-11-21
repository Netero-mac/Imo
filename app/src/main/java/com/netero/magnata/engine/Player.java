package com.netero.magnata.engine;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Player {
    public String name;
    public int money;
    public int pos;
    public boolean inJail;
    public int jailTurns;
    public int jailCards;
    public boolean bankrupt;
    public int color;

    public Player() {}

    public Player(String name, int money, int color) {
        this.name = name;
        this.money = money;
        this.color = color;
    }

    void write(DataOutputStream o) throws IOException {
        o.writeUTF(name);
        o.writeInt(money);
        o.writeInt(pos);
        o.writeBoolean(inJail);
        o.writeInt(jailTurns);
        o.writeInt(jailCards);
        o.writeBoolean(bankrupt);
        o.writeInt(color);
    }

    static Player read(DataInputStream in) throws IOException {
        Player p = new Player();
        p.name = in.readUTF();
        p.money = in.readInt();
        p.pos = in.readInt();
        p.inJail = in.readBoolean();
        p.jailTurns = in.readInt();
        p.jailCards = in.readInt();
        p.bankrupt = in.readBoolean();
        p.color = in.readInt();
        return p;
    }
}
