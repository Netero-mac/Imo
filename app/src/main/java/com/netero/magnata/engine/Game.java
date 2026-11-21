package com.netero.magnata.engine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

/**
 * Regras do jogo. Quem tem o Random (o anfitrião, ou o celular no modo local)
 * é a única fonte da verdade; os outros só recebem o estado serializado.
 */
public class Game {
    public static final int PH_ROLL = 0, PH_BUY = 1, PH_AFTER = 2, PH_OVER = 3;

    public static final int A_ROLL = 1, A_BUY = 2, A_DECLINE = 3, A_END = 4, A_BAIL = 5,
            A_CARD = 6, A_BUILD = 7, A_SELL = 8, A_MORTGAGE = 9, A_UNMORTGAGE = 10, A_BANKRUPT = 11;

    public static final int START_MONEY = 1500, GO_SALARY = 200, BAIL = 50;

    public static final int[] PLAYER_COLORS = {
            0xFFE53935, 0xFF1E88E5, 0xFF43A047, 0xFFFDD835, 0xFF8E24AA, 0xFFFF7043
    };

    public Player[] players;
    public int current;
    public int phase;
    public int d1 = 1, d2 = 1;
    public int doubles;
    public boolean rollAgain;
    public int[] owner = new int[Board.SIZE];
    public int[] houses = new int[Board.SIZE];
    public boolean[] mortgaged = new boolean[Board.SIZE];
    public int pendingBuy = -1;
    public int winner = -1;
    public int creditor = -1;
    public String lastCard = "";
    public int cardSeq;
    public int version;
    public ArrayList<String> log = new ArrayList<>();

    private transient Random rnd;
    private transient int landDepth;

    private Game() {}

    public Game(String[] names, Random rnd) {
        this.rnd = rnd;
        players = new Player[names.length];
        for (int i = 0; i < names.length; i++) {
            players[i] = new Player(names[i], START_MONEY, PLAYER_COLORS[i % PLAYER_COLORS.length]);
        }
        for (int i = 0; i < Board.SIZE; i++) owner[i] = -1;
        current = 0;
        phase = PH_ROLL;
        log("A partida começou! Cada jogador tem R$ " + START_MONEY + ".");
        log("Vez de " + players[0].name + ".");
    }

    // ------------------------------------------------------------------ consultas

    public Player cur() { return players[current]; }

    public boolean ownsGroup(int p, int group) {
        for (int i = 0; i < Board.SIZE; i++) {
            if (Board.TYPE[i] == Board.PROP && Board.GROUP[i] == group && owner[i] != p) return false;
        }
        return true;
    }

    private boolean groupHasMortgage(int group) {
        for (int i = 0; i < Board.SIZE; i++) {
            if (Board.TYPE[i] == Board.PROP && Board.GROUP[i] == group && mortgaged[i]) return true;
        }
        return false;
    }

    private int groupHouses(int group) {
        int n = 0;
        for (int i = 0; i < Board.SIZE; i++) {
            if (Board.TYPE[i] == Board.PROP && Board.GROUP[i] == group) n += houses[i];
        }
        return n;
    }

    private int countOwned(int p, int type) {
        int n = 0;
        for (int i = 0; i < Board.SIZE; i++) if (Board.TYPE[i] == type && owner[i] == p) n++;
        return n;
    }

    public int aliveCount() {
        int n = 0;
        for (Player p : players) if (!p.bankrupt) n++;
        return n;
    }

    /** Aluguel atual da casa (para quem cair nela). */
    public int rentOf(int pos) {
        int o = owner[pos];
        if (o < 0 || mortgaged[pos]) return 0;
        switch (Board.TYPE[pos]) {
            case Board.PROP:
                if (houses[pos] > 0) return Board.RENT[pos][houses[pos]];
                return ownsGroup(o, Board.GROUP[pos]) ? Board.RENT[pos][0] * 2 : Board.RENT[pos][0];
            case Board.TRANSPORT:
                return 25 << (countOwned(o, Board.TRANSPORT) - 1);
            case Board.UTIL:
                return (d1 + d2) * (countOwned(o, Board.UTIL) == 2 ? 10 : 4);
            default:
                return 0;
        }
    }

    /** Patrimônio total (dinheiro + propriedades + casas). */
    public int netWorth(int p) {
        int v = players[p].money;
        for (int i = 0; i < Board.SIZE; i++) {
            if (owner[i] == p) {
                v += mortgaged[i] ? Board.mortgageValue(i) : Board.PRICE[i];
                v += houses[i] * Board.HOUSE_COST[i];
            }
        }
        return v;
    }

    // ------------------------------------------------------------------ ações

    /** Retorna null se a ação é permitida, ou o motivo em português. */
    public String validate(int p, int a, int arg) {
        if (phase == PH_OVER) return "A partida acabou.";
        if (p < 0 || p >= players.length) return "Jogador inválido.";
        Player pl = players[p];
        if (pl.bankrupt) return "Você está fora da partida.";
        boolean turn = p == current;
        boolean posOk = arg >= 0 && arg < Board.SIZE;
        switch (a) {
            case A_ROLL:
                if (!turn) return "Não é sua vez.";
                if (phase != PH_ROLL) return "Você não pode rolar os dados agora.";
                if (pl.money < 0) return "Pague suas dívidas primeiro.";
                return null;
            case A_BUY:
                if (!turn || phase != PH_BUY) return "Nada para comprar agora.";
                if (pl.money < Board.PRICE[pendingBuy]) return "Dinheiro insuficiente.";
                return null;
            case A_DECLINE:
                if (!turn || phase != PH_BUY) return "Nada para recusar agora.";
                return null;
            case A_END:
                if (!turn) return "Não é sua vez.";
                if (phase != PH_AFTER) return "Você ainda não pode encerrar a vez.";
                if (pl.money < 0) return "Você está devendo. Venda casas, hipoteque ou declare falência.";
                return null;
            case A_BAIL:
                if (!turn || phase != PH_ROLL || !pl.inJail) return "Você não está na prisão.";
                if (pl.money < BAIL) return "Dinheiro insuficiente para a fiança.";
                return null;
            case A_CARD:
                if (!turn || phase != PH_ROLL || !pl.inJail) return "Você não está na prisão.";
                if (pl.jailCards <= 0) return "Você não tem a carta.";
                return null;
            case A_BUILD:
                if (!posOk || Board.TYPE[arg] != Board.PROP) return "Só dá para construir em cidades.";
                if (owner[arg] != p) return "Essa cidade não é sua.";
                if (!ownsGroup(p, Board.GROUP[arg])) return "Você precisa ter todas as cidades da cor.";
                if (groupHasMortgage(Board.GROUP[arg])) return "Há cidade hipotecada nessa cor.";
                if (houses[arg] >= 5) return "Já tem hotel.";
                if (pl.money < Board.HOUSE_COST[arg]) return "Dinheiro insuficiente.";
                return null;
            case A_SELL:
                if (!posOk || owner[arg] != p) return "Essa propriedade não é sua.";
                if (houses[arg] <= 0) return "Não há construções.";
                return null;
            case A_MORTGAGE:
                if (!posOk || owner[arg] != p) return "Essa propriedade não é sua.";
                if (mortgaged[arg]) return "Já está hipotecada.";
                if (Board.TYPE[arg] == Board.PROP && groupHouses(Board.GROUP[arg]) > 0)
                    return "Venda as casas dessa cor primeiro.";
                return null;
            case A_UNMORTGAGE:
                if (!posOk || owner[arg] != p) return "Essa propriedade não é sua.";
                if (!mortgaged[arg]) return "Não está hipotecada.";
                if (pl.money < Board.unmortgageCost(arg)) return "Dinheiro insuficiente.";
                return null;
            case A_BANKRUPT:
                return null;
            default:
                return "Ação desconhecida.";
        }
    }

    public String apply(int p, int a, int arg) {
        String err = validate(p, a, arg);
        if (err != null) return err;
        Player pl = players[p];
        switch (a) {
            case A_ROLL: doRoll(); break;
            case A_BUY: {
                int pos = pendingBuy;
                pl.money -= Board.PRICE[pos];
                owner[pos] = p;
                pendingBuy = -1;
                phase = PH_AFTER;
                log(pl.name + " comprou " + Board.NAME[pos] + " por R$ " + Board.PRICE[pos] + ".");
                afterLanding();
                break;
            }
            case A_DECLINE:
                log(pl.name + " não comprou " + Board.NAME[pendingBuy] + ".");
                pendingBuy = -1;
                phase = PH_AFTER;
                afterLanding();
                break;
            case A_END: nextTurn(); break;
            case A_BAIL:
                pl.money -= BAIL;
                pl.inJail = false;
                pl.jailTurns = 0;
                log(pl.name + " pagou R$ " + BAIL + " de fiança e saiu da prisão.");
                break;
            case A_CARD:
                pl.jailCards--;
                pl.inJail = false;
                pl.jailTurns = 0;
                log(pl.name + " usou a carta e saiu da prisão.");
                break;
            case A_BUILD:
                pl.money -= Board.HOUSE_COST[arg];
                houses[arg]++;
                log(pl.name + (houses[arg] == 5 ? " construiu um hotel em " : " construiu uma casa em ")
                        + Board.NAME[arg] + ".");
                break;
            case A_SELL:
                houses[arg]--;
                pl.money += Board.HOUSE_COST[arg] / 2;
                log(pl.name + " vendeu uma construção em " + Board.NAME[arg] + ".");
                break;
            case A_MORTGAGE:
                mortgaged[arg] = true;
                pl.money += Board.mortgageValue(arg);
                log(pl.name + " hipotecou " + Board.NAME[arg] + " (+R$ " + Board.mortgageValue(arg) + ").");
                break;
            case A_UNMORTGAGE:
                mortgaged[arg] = false;
                pl.money -= Board.unmortgageCost(arg);
                log(pl.name + " recuperou " + Board.NAME[arg] + ".");
                break;
            case A_BANKRUPT:
                bankrupt(p, pl.money < 0 && p == current ? creditor : -1);
                break;
        }
        version++;
        return null;
    }

    // ------------------------------------------------------------------ lógica interna

    private void doRoll() {
        Player pl = cur();
        d1 = rnd.nextInt(6) + 1;
        d2 = rnd.nextInt(6) + 1;
        boolean dbl = d1 == d2;
        int sum = d1 + d2;
        log(pl.name + " tirou " + d1 + " + " + d2 + (dbl ? " (dupla!)" : "") + ".");
        rollAgain = false;
        phase = PH_AFTER;
        if (pl.inJail) {
            if (dbl) {
                pl.inJail = false;
                pl.jailTurns = 0;
                log(pl.name + " saiu da prisão com a dupla.");
                move(sum);
            } else {
                pl.jailTurns++;
                if (pl.jailTurns >= 3) {
                    log(pl.name + " pagou a fiança obrigatória de R$ " + BAIL + ".");
                    pl.inJail = false;
                    pl.jailTurns = 0;
                    charge(current, -1, BAIL);
                    move(sum);
                } else {
                    log(pl.name + " continua na prisão (" + pl.jailTurns + "/3).");
                }
            }
        } else {
            if (dbl) {
                doubles++;
                if (doubles >= 3) {
                    log("Três duplas seguidas! " + pl.name + " vai para a prisão.");
                    sendToJail(current);
                    return;
                }
                rollAgain = true;
            }
            move(sum);
        }
        afterLanding();
    }

    private void afterLanding() {
        if (phase == PH_BUY || phase == PH_OVER) return;
        Player pl = cur();
        phase = (rollAgain && !pl.inJail && !pl.bankrupt) ? PH_ROLL : PH_AFTER;
        if (phase == PH_ROLL) log(pl.name + " joga de novo.");
    }

    private void nextTurn() {
        if (phase == PH_OVER) return;
        int n = players.length;
        int c = current;
        for (int i = 0; i < n; i++) {
            c = (c + 1) % n;
            if (!players[c].bankrupt) break;
        }
        current = c;
        phase = PH_ROLL;
        doubles = 0;
        rollAgain = false;
        pendingBuy = -1;
        creditor = -1;
        Player pl = cur();
        log("Vez de " + pl.name + (pl.inJail ? " (na prisão)." : "."));
    }

    private void move(int steps) {
        Player pl = cur();
        int np = pl.pos + steps;
        if (np >= Board.SIZE) {
            pl.money += GO_SALARY;
            log(pl.name + " passou pela Partida e recebeu R$ " + GO_SALARY + ".");
        }
        pl.pos = np % Board.SIZE;
        land();
    }

    private void moveTo(int target, boolean collectGo) {
        Player pl = cur();
        if (collectGo && target < pl.pos) {
            pl.money += GO_SALARY;
            log(pl.name + " passou pela Partida e recebeu R$ " + GO_SALARY + ".");
        }
        pl.pos = target;
        land();
    }

    private void land() {
        if (landDepth > 3) return;
        landDepth++;
        try {
            Player pl = cur();
            int pos = pl.pos;
            switch (Board.TYPE[pos]) {
                case Board.PROP:
                case Board.TRANSPORT:
                case Board.UTIL: {
                    int o = owner[pos];
                    if (o < 0) {
                        if (pl.money >= Board.PRICE[pos]) {
                            pendingBuy = pos;
                            phase = PH_BUY;
                            log(pl.name + " caiu em " + Board.NAME[pos] + " (à venda por R$ " + Board.PRICE[pos] + ").");
                        } else {
                            log(pl.name + " caiu em " + Board.NAME[pos] + ", mas não tem dinheiro para comprar.");
                        }
                    } else if (o == current) {
                        log(pl.name + " caiu na própria propriedade " + Board.NAME[pos] + ".");
                    } else if (mortgaged[pos]) {
                        log(pl.name + " caiu em " + Board.NAME[pos] + " (hipotecada, sem aluguel).");
                    } else {
                        int r = rentOf(pos);
                        log(pl.name + " pagou R$ " + r + " de aluguel a " + players[o].name + " (" + Board.NAME[pos] + ").");
                        charge(current, o, r);
                    }
                    break;
                }
                case Board.TAX:
                    log(pl.name + " pagou R$ " + Board.PRICE[pos] + " de " + Board.NAME[pos] + ".");
                    charge(current, -1, Board.PRICE[pos]);
                    break;
                case Board.DESTINO:
                    drawDestino();
                    break;
                case Board.SURPRESA:
                    drawSurpresa();
                    break;
                case Board.GOTOJAIL:
                    log(pl.name + " foi mandado para a prisão!");
                    sendToJail(current);
                    break;
                case Board.PARKING:
                    log(pl.name + " está descansando.");
                    break;
                default:
                    break;
            }
        } finally {
            landDepth--;
        }
    }

    private void sendToJail(int p) {
        Player pl = players[p];
        pl.pos = Board.JAIL_POS;
        pl.inJail = true;
        pl.jailTurns = 0;
        if (p == current) {
            rollAgain = false;
            doubles = 0;
            phase = PH_AFTER;
        }
    }

    /** Cobra 'amount' de p e entrega a 'to' (-1 = banco). */
    private void charge(int p, int to, int amount) {
        if (amount <= 0) return;
        Player pl = players[p];
        if (pl.bankrupt) return;
        pl.money -= amount;
        if (to >= 0) players[to].money += amount;
        if (pl.money < 0) {
            if (p == current) {
                creditor = to;
                log(pl.name + " está devendo R$ " + (-pl.money) + ". Venda casas, hipoteque ou declare falência.");
            } else {
                autoLiquidate(p, to);
            }
        }
    }

    /** Para quem fica devendo fora da sua vez: vende/hipoteca sozinho. */
    private void autoLiquidate(int p, int cred) {
        Player pl = players[p];
        while (pl.money < 0) {
            int best = -1;
            for (int i = 0; i < Board.SIZE; i++) {
                if (owner[i] == p && houses[i] > 0 && (best < 0 || houses[i] > houses[best])) best = i;
            }
            if (best >= 0) {
                houses[best]--;
                pl.money += Board.HOUSE_COST[best] / 2;
                continue;
            }
            int m = -1;
            for (int i = 0; i < Board.SIZE; i++) {
                if (owner[i] == p && !mortgaged[i]) { m = i; break; }
            }
            if (m >= 0) {
                mortgaged[m] = true;
                pl.money += Board.mortgageValue(m);
                continue;
            }
            break;
        }
        if (pl.money < 0) {
            bankrupt(p, cred);
        } else {
            log(pl.name + " vendeu/hipotecou bens para pagar a dívida.");
        }
    }

    private void bankrupt(int p, int cred) {
        Player pl = players[p];
        if (pl.bankrupt) return;
        if (cred >= 0 && players[cred].bankrupt) cred = -1;
        for (int i = 0; i < Board.SIZE; i++) {
            if (owner[i] == p) {
                pl.money += houses[i] * Board.HOUSE_COST[i] / 2;
                houses[i] = 0;
                if (cred >= 0) {
                    owner[i] = cred;
                } else {
                    owner[i] = -1;
                    mortgaged[i] = false;
                }
            }
        }
        if (cred >= 0) {
            if (pl.money > 0) players[cred].money += pl.money;
            players[cred].jailCards += pl.jailCards;
        }
        pl.money = 0;
        pl.jailCards = 0;
        pl.inJail = false;
        pl.bankrupt = true;
        log(pl.name + " faliu e está fora da partida" + (cred >= 0 ? " (bens para " + players[cred].name + ")." : "."));
        if (aliveCount() <= 1) {
            for (int i = 0; i < players.length; i++) if (!players[i].bankrupt) winner = i;
            phase = PH_OVER;
            pendingBuy = -1;
            if (winner >= 0) log("🏆 " + players[winner].name + " venceu a partida!");
            return;
        }
        if (p == current) {
            phase = PH_AFTER;
            nextTurn();
        }
    }

    private void showCard(String deck, String text) {
        lastCard = deck + "\n\n" + text;
        cardSeq++;
        log(cur().name + " tirou " + deck + ": " + text);
    }

    private void drawDestino() {
        int c = rnd.nextInt(12);
        Player pl = cur();
        switch (c) {
            case 0: showCard("DESTINO", "Avance até a Partida e receba R$ 200."); moveTo(0, true); break;
            case 1: showCard("DESTINO", "Avance até Rondonópolis."); moveTo(19, true); break;
            case 2: showCard("DESTINO", "Avance até São Paulo."); moveTo(39, true); break;
            case 3: showCard("DESTINO", "Pegue um barco: vá até o Porto."); moveTo(35, true); break;
            case 4: {
                showCard("DESTINO", "Volte 3 casas.");
                pl.pos = (pl.pos + Board.SIZE - 3) % Board.SIZE;
                land();
                break;
            }
            case 5: showCard("DESTINO", "Vá direto para a prisão. Não passe pela Partida."); sendToJail(current); break;
            case 6: showCard("DESTINO", "Saída livre da prisão. Guarde esta carta."); pl.jailCards++; break;
            case 7: showCard("DESTINO", "Multa por excesso de velocidade: pague R$ 15."); charge(current, -1, 15); break;
            case 8: showCard("DESTINO", "Você ganhou na loteria! Receba R$ 150."); pl.money += 150; break;
            case 9: {
                showCard("DESTINO", "Reforma geral: pague R$ 25 por casa e R$ 100 por hotel.");
                charge(current, -1, repairs(25, 100));
                break;
            }
            case 10: {
                showCard("DESTINO", "Você foi eleito síndico: pague R$ 50 a cada jogador.");
                for (int q = 0; q < players.length; q++) {
                    if (q != current && !players[q].bankrupt) charge(current, q, 50);
                }
                break;
            }
            default: showCard("DESTINO", "Dividendos das suas ações: receba R$ 50."); pl.money += 50; break;
        }
    }

    private void drawSurpresa() {
        int c = rnd.nextInt(12);
        Player pl = cur();
        switch (c) {
            case 0: showCard("SURPRESA", "Avance até a Partida e receba R$ 200."); moveTo(0, true); break;
            case 1: showCard("SURPRESA", "Erro do banco a seu favor: receba R$ 200."); pl.money += 200; break;
            case 2: showCard("SURPRESA", "Consulta médica: pague R$ 50."); charge(current, -1, 50); break;
            case 3: showCard("SURPRESA", "Venda de ações: receba R$ 50."); pl.money += 50; break;
            case 4: showCard("SURPRESA", "Saída livre da prisão. Guarde esta carta."); pl.jailCards++; break;
            case 5: showCard("SURPRESA", "Vá direto para a prisão. Não passe pela Partida."); sendToJail(current); break;
            case 6: {
                showCard("SURPRESA", "É seu aniversário! Receba R$ 10 de cada jogador.");
                for (int q = 0; q < players.length; q++) {
                    if (q != current && !players[q].bankrupt) charge(q, current, 10);
                }
                break;
            }
            case 7: showCard("SURPRESA", "Restituição do imposto: receba R$ 20."); pl.money += 20; break;
            case 8: showCard("SURPRESA", "Conta do hospital: pague R$ 100."); charge(current, -1, 100); break;
            case 9: showCard("SURPRESA", "Você recebeu uma herança: R$ 100."); pl.money += 100; break;
            case 10: {
                showCard("SURPRESA", "Conserto das ruas: pague R$ 40 por casa e R$ 115 por hotel.");
                charge(current, -1, repairs(40, 115));
                break;
            }
            default: showCard("SURPRESA", "Você venceu um concurso de culinária: receba R$ 10."); pl.money += 10; break;
        }
    }

    private int repairs(int perHouse, int perHotel) {
        int total = 0;
        for (int i = 0; i < Board.SIZE; i++) {
            if (owner[i] == current) total += houses[i] == 5 ? perHotel : houses[i] * perHouse;
        }
        return total;
    }

    private void log(String s) {
        log.add(s);
        while (log.size() > 40) log.remove(0);
    }

    // ------------------------------------------------------------------ serialização

    public byte[] toBytes() {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            DataOutputStream o = new DataOutputStream(bo);
            o.writeInt(players.length);
            for (Player p : players) p.write(o);
            o.writeInt(current);
            o.writeInt(phase);
            o.writeInt(d1);
            o.writeInt(d2);
            o.writeInt(doubles);
            o.writeBoolean(rollAgain);
            for (int i = 0; i < Board.SIZE; i++) {
                o.writeInt(owner[i]);
                o.writeInt(houses[i]);
                o.writeBoolean(mortgaged[i]);
            }
            o.writeInt(pendingBuy);
            o.writeInt(winner);
            o.writeInt(creditor);
            o.writeUTF(lastCard);
            o.writeInt(cardSeq);
            o.writeInt(version);
            o.writeInt(log.size());
            for (String s : log) o.writeUTF(s);
            o.flush();
            return bo.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Game fromBytes(byte[] b) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(b));
        Game g = new Game();
        int n = in.readInt();
        g.players = new Player[n];
        for (int i = 0; i < n; i++) g.players[i] = Player.read(in);
        g.current = in.readInt();
        g.phase = in.readInt();
        g.d1 = in.readInt();
        g.d2 = in.readInt();
        g.doubles = in.readInt();
        g.rollAgain = in.readBoolean();
        for (int i = 0; i < Board.SIZE; i++) {
            g.owner[i] = in.readInt();
            g.houses[i] = in.readInt();
            g.mortgaged[i] = in.readBoolean();
        }
        g.pendingBuy = in.readInt();
        g.winner = in.readInt();
        g.creditor = in.readInt();
        g.lastCard = in.readUTF();
        g.cardSeq = in.readInt();
        g.version = in.readInt();
        int ls = in.readInt();
        for (int i = 0; i < ls; i++) g.log.add(in.readUTF());
        return g;
    }
}
