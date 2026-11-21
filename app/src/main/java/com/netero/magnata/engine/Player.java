package com.netero.magnata.engine;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class Player {
    public String name;
    public int money;
    public int pos;
    public boolean inJail;
    public int jailTurns;
    public int jailCards;
    public boolean bankrupt;
    public int color;
    public int pawn;
    public int character;
    public ArrayList<Integer> powers = new ArrayList<>();
    public int mission;
    public boolean missionDone;
    public int loanOwed;
    public int loanDueRound;
    // estatísticas
    public int rentPaid, rentRecv, jailCount, bought;

    public Player() {}

    public Player(Setup s, int money) {
        this.name = s.name;
        this.color = s.color;
        this.pawn = s.pawn;
        this.character = s.character;
        this.money = money;
    }

    /** Escolhas do jogador antes da partida. */
    public static class Setup {
        public String name;
        public int color, pawn, character;

        public Setup(String name, int color, int pawn, int character) {
            this.name = name; this.color = color; this.pawn = pawn; this.character = character;
        }
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
        o.writeInt(pawn);
        o.writeInt(character);
        o.writeInt(powers.size());
        for (int c : powers) o.writeInt(c);
        o.writeInt(mission);
        o.writeBoolean(missionDone);
        o.writeInt(loanOwed);
        o.writeInt(loanDueRound);
        o.writeInt(rentPaid);
        o.writeInt(rentRecv);
        o.writeInt(jailCount);
        o.writeInt(bought);
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
        p.pawn = in.readInt();
        p.character = in.readInt();
        int n = in.readInt();
        for (int i = 0; i < n; i++) p.powers.add(in.readInt());
        p.mission = in.readInt();
        p.missionDone = in.readBoolean();
        p.loanOwed = in.readInt();
        p.loanDueRound = in.readInt();
        p.rentPaid = in.readInt();
        p.rentRecv = in.readInt();
        p.jailCount = in.readInt();
        p.bought = in.readInt();
        return p;
    }
}
