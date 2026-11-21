package com.netero.magnata.engine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import static com.netero.magnata.engine.Rules.*;

/**
 * Regras do jogo. Quem tem o Random (anfitrião ou celular no modo local) é a única
 * fonte da verdade; os outros recebem o estado serializado e só desenham.
 */
public class Game {
    public static final int PH_ROLL = 0, PH_BUY = 1, PH_AFTER = 2, PH_OVER = 3,
            PH_TRAVEL = 4, PH_AUCTION = 5, PH_ROULETTE = 6;

    public static final int A_ROLL = 1, A_BUY = 2, A_DECLINE = 3, A_END = 4, A_BAIL = 5,
            A_CARD = 6, A_BUILD = 7, A_SELL = 8, A_MORTGAGE = 9, A_UNMORTGAGE = 10, A_BANKRUPT = 11,
            A_TRAVEL = 12, A_BID = 13, A_PASS = 14, A_POWER = 15,
            A_TRADE = 16, A_TRADE_ACCEPT = 17, A_TRADE_REJECT = 18, A_TRADE_CANCEL = 19,
            A_LOAN = 20, A_REPAY = 21, A_SPIN = 22, A_EMOJI = 23;

    public static final int START_MONEY = 1500, GO_SALARY = 200, BAIL = 50, MAX_POWERS = 3, MAX_LOAN = 1200;

    /** Tipos de movimento para a animação. */
    public static final int MV_WALK = 0, MV_JUMP = 1;

    public Player[] players;
    public int current;
    public int phase;
    public int d1 = 1, d2 = 1, d3;
    public int doubles;
    public boolean rollAgain;
    public int[] owner = new int[Board.SIZE];
    public int[] houses = new int[Board.SIZE];
    public boolean[] mortgaged = new boolean[Board.SIZE];
    public int[] closedUntil = new int[Board.SIZE];
    public int[] earned = new int[Board.SIZE];
    public int pendingBuy = -1;
    public int winner = -1;
    public int creditor = -1;
    public int round = 1;
    public int maxRounds;
    public int version;

    // vez atual
    public int pendingSteps;
    public boolean turbo, shield;

    // evento global
    public int eventId = -1, eventArg = -1, eventLeft, eventSeq;

    // leilão
    public int auctionPos = -1, highBid, highBidder = -1, auctionSeq;
    public boolean[] auctionPassed = new boolean[0];

    // roleta
    public int rouletteResult = -1, rouletteSeq;

    // troca
    public int tradeFrom = -1, tradeTo = -1, tradeGiveMoney, tradeGetMoney, tradeSeq;
    public long tradeGive, tradeGet;

    // emoji
    public int emojiWho = -1, emojiIdx, emojiSeq;

    // animação: o que aconteceu na última ação
    public int rollSeq;
    public ArrayList<int[]> moves = new ArrayList<>();   // {jogador, de, para, tipo}
    public ArrayList<String> popups = new ArrayList<>(); // "TIPO\u001fTítulo\u001fTexto"

    public ArrayList<String> log = new ArrayList<>();

    private transient Random rnd;
    private transient int landDepth;

    private Game() {}

    public Game(Player.Setup[] setups, int maxRounds, Random rnd) {
        this.rnd = rnd;
        this.maxRounds = maxRounds;
        players = new Player[setups.length];
        // missões sem repetir
        ArrayList<Integer> pool = new ArrayList<>();
        for (int i = 0; i < MISSIONS.length; i++) pool.add(i);
        for (int i = 0; i < setups.length; i++) {
            Player p = new Player(setups[i], START_MONEY);
            p.mission = pool.remove(rnd.nextInt(pool.size()));
            p.powers.add(rnd.nextInt(POWER_NAMES.length));
            players[i] = p;
        }
        for (int i = 0; i < Board.SIZE; i++) owner[i] = -1;
        auctionPassed = new boolean[setups.length];
        current = 0;
        phase = PH_ROLL;
        log("A partida começou! Cada jogador tem R$ " + START_MONEY + " e uma carta de poder.");
        if (maxRounds > 0) log("Modo com limite de " + maxRounds + " rodadas.");
        log("Rodada 1. Vez de " + players[0].name + ".");
    }

    // ================================================================= consultas

    public Player cur() { return players[current]; }

    public static boolean isMT(int pos) {
        return Board.TYPE[pos] == Board.PROP && (Board.GROUP[pos] <= 3 || pos == 21);
    }

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

    public int groupHouses(int group) {
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

    public boolean isClosed(int pos) { return round < closedUntil[pos]; }

    public int buyPrice(int pos) {
        int pr = Board.PRICE[pos];
        if (eventId == E_INFLACAO) pr = pr * 5 / 4;
        return pr;
    }

    public int houseCost(int p, int pos) {
        int c = Board.HOUSE_COST[pos];
        if (p >= 0 && players[p].character == EMPREITEIRO) c = c * 4 / 5;
        if (eventId == E_BOOM) c = c * 7 / 10;
        return c;
    }

    public int bail(int p) { return players[p].character == ADVOGADO ? 20 : BAIL; }

    /** Aluguel atual da casa (para quem cair nela). */
    public int rentOf(int pos) {
        int o = owner[pos];
        if (o < 0 || mortgaged[pos] || isClosed(pos)) return 0;
        int r;
        switch (Board.TYPE[pos]) {
            case Board.PROP:
                if (houses[pos] > 0) r = Board.RENT[pos][houses[pos]];
                else r = ownsGroup(o, Board.GROUP[pos]) ? Board.RENT[pos][0] * 2 : Board.RENT[pos][0];
                if (isMT(pos)) {
                    if (eventId == E_SECA) r /= 2;
                    if (players[o].character == FAZENDEIRO) r = r * 3 / 2;
                }
                if (eventId == E_FESTA && eventArg == pos) r *= 2;
                return r;
            case Board.TRANSPORT:
                r = 25 << (countOwned(o, Board.TRANSPORT) - 1);
                if (eventId == E_FERIADAO) r *= 2;
                return r;
            case Board.UTIL:
                if (eventId == E_GREVE) return 0;
                return (d1 + d2 + d3) * (countOwned(o, Board.UTIL) == 2 ? 10 : 4);
            default:
                return 0;
        }
    }

    /** Patrimônio total (dinheiro + propriedades + construções − empréstimo). */
    public int netWorth(int p) {
        Player pl = players[p];
        if (pl.bankrupt) return 0;
        int v = pl.money - pl.loanOwed;
        for (int i = 0; i < Board.SIZE; i++) {
            if (owner[i] == p) {
                v += mortgaged[i] ? Board.mortgageValue(i) : Board.PRICE[i];
                v += houses[i] * Board.HOUSE_COST[i];
            }
        }
        return v;
    }

    /** Uma propriedade pode entrar numa troca? */
    public boolean tradable(int pos, int p) {
        if (!Board.buyable(pos) || owner[pos] != p) return false;
        return Board.TYPE[pos] != Board.PROP || groupHouses(Board.GROUP[pos]) == 0;
    }

    public static long bit(int pos) { return 1L << pos; }

    // ================================================================= validação

    private static int x(int[] a, int i) { return a != null && a.length > i ? a[i] : 0; }

    /** Retorna null se a ação é permitida, ou o motivo em português. */
    public String validate(int p, int a, int... args) {
        if (p < 0 || p >= players.length) return "Jogador inválido.";
        if (a == A_EMOJI) {
            int e = x(args, 0);
            return e >= 0 && e < EMOJIS.length ? null : "Emoji inválido.";
        }
        if (phase == PH_OVER) return "A partida acabou.";
        Player pl = players[p];
        if (pl.bankrupt) return "Você está fora da partida.";
        boolean turn = p == current;
        int arg = x(args, 0);
        boolean posOk = arg >= 0 && arg < Board.SIZE;
        switch (a) {
            case A_ROLL:
                if (!turn) return "Não é sua vez.";
                if (phase != PH_ROLL) return "Você não pode rolar os dados agora.";
                if (pl.money < 0) return "Pague suas dívidas primeiro.";
                return null;
            case A_BUY:
                if (!turn || phase != PH_BUY) return "Nada para comprar agora.";
                if (pl.money < buyPrice(pendingBuy)) return "Dinheiro insuficiente.";
                return null;
            case A_DECLINE:
                if (!turn || phase != PH_BUY) return "Nada para recusar agora.";
                return null;
            case A_END:
                if (!turn) return "Não é sua vez.";
                if (phase != PH_AFTER) return "Você ainda não pode encerrar a vez.";
                if (pl.money < 0) return "Você está devendo. Venda, hipoteque, peça empréstimo ou declare falência.";
                return null;
            case A_BAIL:
                if (!turn || phase != PH_ROLL || !pl.inJail) return "Você não está na prisão.";
                if (pl.money < bail(p)) return "Dinheiro insuficiente para a fiança.";
                return null;
            case A_CARD:
                if (!turn || phase != PH_ROLL || !pl.inJail) return "Você não está na prisão.";
                if (pl.jailCards <= 0) return "Você não tem a carta.";
                return null;
            case A_BUILD:
                if (phase == PH_AUCTION) return "Espere o leilão acabar.";
                if (!posOk || Board.TYPE[arg] != Board.PROP) return "Só dá para construir em cidades.";
                if (owner[arg] != p) return "Essa cidade não é sua.";
                if (!ownsGroup(p, Board.GROUP[arg])) return "Você precisa ter todas as cidades da cor.";
                if (groupHasMortgage(Board.GROUP[arg])) return "Há cidade hipotecada nessa cor.";
                if (houses[arg] >= 5) return "Já tem hotel.";
                if (pl.money < houseCost(p, arg)) return "Dinheiro insuficiente.";
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
            case A_TRAVEL:
                if (!turn || phase != PH_TRAVEL) return "Agora não.";
                if (arg < -1 || arg > 1) return "Escolha inválida.";
                return null;
            case A_BID:
                if (phase != PH_AUCTION) return "Não há leilão.";
                if (auctionPassed[p]) return "Você saiu do leilão.";
                if (p == highBidder) return "Você já tem o maior lance.";
                if (arg != 10 && arg != 50 && arg != 100) return "Lance inválido.";
                if (pl.money < highBid + arg) return "Dinheiro insuficiente para esse lance.";
                return null;
            case A_PASS:
                if (phase != PH_AUCTION) return "Não há leilão.";
                if (auctionPassed[p]) return "Você já saiu do leilão.";
                if (p == highBidder) return "Você tem o maior lance.";
                return null;
            case A_POWER:
                return validatePower(p, arg, x(args, 1));
            case A_TRADE:
                return validateTrade(p, args);
            case A_TRADE_ACCEPT:
                if (tradeFrom < 0 || tradeTo != p) return "Não há proposta para você.";
                return checkTradeAssets();
            case A_TRADE_REJECT:
                if (tradeFrom < 0 || tradeTo != p) return "Não há proposta para você.";
                return null;
            case A_TRADE_CANCEL:
                if (tradeFrom < 0 || tradeFrom != p) return "Você não tem proposta aberta.";
                return null;
            case A_LOAN:
                if (!turn) return "Peça empréstimo na sua vez.";
                if (phase == PH_AUCTION) return "Espere o leilão acabar.";
                if (arg < 100 || arg > 500 || arg % 100 != 0) return "Valor inválido.";
                if (pl.loanOwed + arg * 6 / 5 > MAX_LOAN) return "Limite de empréstimo atingido.";
                return null;
            case A_REPAY:
                if (pl.loanOwed <= 0) return "Você não tem empréstimo.";
                if (pl.money <= 0) return "Sem dinheiro para pagar.";
                if (phase == PH_AUCTION) return "Espere o leilão acabar.";
                return null;
            case A_SPIN:
                if (!turn || phase != PH_ROULETTE) return "Agora não.";
                return null;
            default:
                return "Ação desconhecida.";
        }
    }

    private String validatePower(int p, int hand, int target) {
        Player pl = players[p];
        if (p != current) return "Use cartas de poder na sua vez.";
        if (hand < 0 || hand >= pl.powers.size()) return "Carta inválida.";
        if (phase == PH_AUCTION) return "Espere o leilão acabar.";
        switch (pl.powers.get(hand)) {
            case P_ESCUDO:
                if (phase != PH_ROLL && phase != PH_TRAVEL) return "Use o Escudo antes de andar.";
                if (shield) return "O Escudo já está ativo.";
                return null;
            case P_SABOTAGEM:
                if (target < 0 || target >= Board.SIZE || !Board.buyable(target)) return "Escolha uma propriedade.";
                if (owner[target] < 0 || owner[target] == p) return "Escolha a propriedade de um rival.";
                if (isClosed(target)) return "Essa já está fechada.";
                return null;
            case P_TELEPORTE:
                if (phase != PH_ROLL || pl.inJail) return "Use antes de rolar (e fora da prisão).";
                if (pl.money < 0) return "Pague suas dívidas primeiro.";
                return null;
            case P_DESCONTO:
                if (phase != PH_BUY) return "Use na hora de comprar uma propriedade.";
                if (pl.money < buyPrice(pendingBuy) / 2) return "Dinheiro insuficiente.";
                return null;
            case P_TURBO:
                if (phase != PH_ROLL || pl.inJail) return "Use antes de rolar (e fora da prisão).";
                if (turbo) return "O Turbo já está ativo.";
                return null;
            case P_COBRANCA:
                return null;
            default:
                return "Carta inválida.";
        }
    }

    private String validateTrade(int p, int[] a) {
        if (p != current) return "Proponha trocas na sua vez.";
        if (phase != PH_ROLL && phase != PH_AFTER) return "Agora não dá para trocar.";
        if (tradeFrom >= 0) return "Já existe uma proposta aberta.";
        int to = x(a, 0);
        if (to < 0 || to >= players.length || to == p || players[to].bankrupt) return "Escolha outro jogador.";
        long give = (x(a, 1) & 0xFFFFFFFFL) | ((long) x(a, 2) << 32);
        long get = (x(a, 3) & 0xFFFFFFFFL) | ((long) x(a, 4) << 32);
        int gm = x(a, 5), tm = x(a, 6);
        if (gm < 0 || tm < 0) return "Valor inválido.";
        if (give == 0 && get == 0 && gm == 0 && tm == 0) return "A troca está vazia.";
        for (int i = 0; i < Board.SIZE; i++) {
            if ((give & bit(i)) != 0 && !tradable(i, p)) return Board.NAME[i] + " não pode ser trocada.";
            if ((get & bit(i)) != 0 && !tradable(i, to)) return Board.NAME[i] + " não pode ser trocada.";
        }
        if (gm > players[p].money) return "Você não tem esse dinheiro.";
        if (tm > players[to].money) return players[to].name + " não tem esse dinheiro.";
        return null;
    }

    private String checkTradeAssets() {
        for (int i = 0; i < Board.SIZE; i++) {
            if ((tradeGive & bit(i)) != 0 && !tradable(i, tradeFrom)) return "A troca não é mais válida.";
            if ((tradeGet & bit(i)) != 0 && !tradable(i, tradeTo)) return "A troca não é mais válida.";
        }
        if (players[tradeFrom].money < tradeGiveMoney || players[tradeTo].money < tradeGetMoney)
            return "Falta dinheiro para essa troca.";
        return null;
    }

    // ================================================================= aplicar ações

    public String apply(int p, int a, int... args) {
        String err = validate(p, a, args);
        if (err != null) return err;
        moves.clear();
        popups.clear();
        Player pl = players[p];
        int arg = x(args, 0);
        switch (a) {
            case A_ROLL: doRoll(); break;
            case A_BUY: buy(buyPrice(pendingBuy)); break;
            case A_DECLINE:
                log(pl.name + " não quis " + Board.NAME[pendingBuy] + ".");
                startAuction(pendingBuy);
                break;
            case A_END: nextTurn(); break;
            case A_BAIL: {
                int b = bail(p);
                pl.money -= b;
                pl.inJail = false;
                pl.jailTurns = 0;
                log(pl.name + " pagou R$ " + b + " de fiança e saiu da prisão.");
                break;
            }
            case A_CARD:
                pl.jailCards--;
                pl.inJail = false;
                pl.jailTurns = 0;
                log(pl.name + " usou a carta e saiu da prisão.");
                break;
            case A_BUILD: {
                int c = houseCost(p, arg);
                pl.money -= c;
                houses[arg]++;
                log(pl.name + (houses[arg] == 5 ? " construiu um hotel em " : " construiu uma casa em ")
                        + Board.NAME[arg] + " (R$ " + c + ").");
                break;
            }
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
            case A_TRAVEL: {
                int steps = pendingSteps + arg;
                pendingSteps = 0;
                phase = PH_AFTER;
                if (arg != 0) log(pl.name + " (Viajante) andou " + (arg > 0 ? "1 casa a mais." : "1 casa a menos."));
                move(steps);
                afterLanding();
                break;
            }
            case A_BID:
                highBid += arg;
                highBidder = p;
                auctionSeq++;
                log(pl.name + " deu lance de R$ " + highBid + ".");
                checkAuction();
                break;
            case A_PASS:
                auctionPassed[p] = true;
                auctionSeq++;
                log(pl.name + " saiu do leilão.");
                checkAuction();
                break;
            case A_POWER: usePower(p, arg, x(args, 1)); break;
            case A_TRADE:
                tradeFrom = p;
                tradeTo = arg;
                tradeGive = (x(args, 1) & 0xFFFFFFFFL) | ((long) x(args, 2) << 32);
                tradeGet = (x(args, 3) & 0xFFFFFFFFL) | ((long) x(args, 4) << 32);
                tradeGiveMoney = x(args, 5);
                tradeGetMoney = x(args, 6);
                tradeSeq++;
                log(pl.name + " propôs uma troca a " + players[arg].name + ".");
                break;
            case A_TRADE_ACCEPT: doTrade(); break;
            case A_TRADE_REJECT:
                log(pl.name + " recusou a troca.");
                clearTrade();
                break;
            case A_TRADE_CANCEL:
                log(pl.name + " cancelou a proposta de troca.");
                clearTrade();
                break;
            case A_LOAN:
                pl.money += arg;
                pl.loanOwed += arg * 6 / 5;
                if (pl.loanDueRound == 0) pl.loanDueRound = round + 5;
                log(pl.name + " pegou R$ " + arg + " emprestado (deve R$ " + pl.loanOwed + " até a rodada " + pl.loanDueRound + ").");
                break;
            case A_REPAY: {
                int pay = Math.min(pl.money, pl.loanOwed);
                pl.money -= pay;
                pl.loanOwed -= pay;
                if (pl.loanOwed == 0) pl.loanDueRound = 0;
                log(pl.name + " pagou R$ " + pay + " ao banco" + (pl.loanOwed == 0 ? " e quitou o empréstimo." : "."));
                break;
            }
            case A_SPIN: spinRoulette(); break;
            case A_EMOJI:
                emojiWho = p;
                emojiIdx = arg;
                emojiSeq++;
                break;
        }
        if (a != A_EMOJI) checkMissions();
        version++;
        return null;
    }

    /** O anfitrião chama quando o tempo do leilão acaba. */
    public boolean closeAuctionTimeout() {
        if (phase != PH_AUCTION) return false;
        moves.clear();
        popups.clear();
        closeAuction();
        checkMissions();
        version++;
        return true;
    }

    // ================================================================= movimento

    private void doRoll() {
        Player pl = cur();
        d1 = rnd.nextInt(6) + 1;
        d2 = rnd.nextInt(6) + 1;
        d3 = turbo ? rnd.nextInt(6) + 1 : 0;
        turbo = false;
        rollSeq++;
        boolean dbl = d1 == d2;
        int sum = d1 + d2 + d3;
        log(pl.name + " tirou " + d1 + " + " + d2 + (d3 > 0 ? " + " + d3 + " (Turbo)" : "") + (dbl ? " (dupla!)" : "") + ".");
        rollAgain = false;
        phase = PH_AFTER;
        if (pl.inJail) {
            if (dbl) {
                pl.inJail = false;
                pl.jailTurns = 0;
                log(pl.name + " saiu da prisão com a dupla.");
                startMove(sum);
            } else {
                pl.jailTurns++;
                if (pl.jailTurns >= 3) {
                    int b = bail(current);
                    log(pl.name + " pagou a fiança obrigatória de R$ " + b + ".");
                    pl.inJail = false;
                    pl.jailTurns = 0;
                    charge(current, -1, b);
                    startMove(sum);
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
            startMove(sum);
        }
        afterLanding();
    }

    private void startMove(int steps) {
        if (cur().character == VIAJANTE) {
            pendingSteps = steps;
            phase = PH_TRAVEL;
            return;
        }
        move(steps);
    }

    private boolean waiting() {
        return phase == PH_BUY || phase == PH_OVER || phase == PH_AUCTION || phase == PH_ROULETTE || phase == PH_TRAVEL;
    }

    private void afterLanding() {
        if (waiting()) return;
        Player pl = cur();
        phase = (rollAgain && !pl.inJail && !pl.bankrupt) ? PH_ROLL : PH_AFTER;
        if (phase == PH_ROLL) log(pl.name + " joga de novo.");
    }

    private void nextTurn() {
        if (phase == PH_OVER) return;
        if (tradeFrom >= 0) { log("A proposta de troca foi cancelada."); clearTrade(); }
        int n = players.length;
        int old = current;
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
        pendingSteps = 0;
        creditor = -1;
        turbo = false;
        shield = false;
        if (c <= old) {
            newRound();
            if (phase == PH_OVER) return;
        }
        Player pl = cur();
        log("Vez de " + pl.name + (pl.inJail ? " (na prisão)." : "."));
    }

    private void newRound() {
        round++;
        if (maxRounds > 0 && round > maxRounds) {
            endByRounds();
            return;
        }
        log("— Rodada " + round + (maxRounds > 0 ? " de " + maxRounds : "") + " —");
        // eventos globais
        if (eventId >= 0) {
            eventLeft--;
            if (eventLeft <= 0) {
                log("O evento \"" + EVENT_NAMES[eventId] + "\" acabou.");
                eventId = -1;
                eventArg = -1;
            }
        } else if (round >= 2) {
            startEvent(rnd.nextInt(EVENT_NAMES.length));
        }
        // empréstimos vencidos
        for (int i = 0; i < players.length; i++) {
            Player p = players[i];
            if (!p.bankrupt && p.loanOwed > 0 && round >= p.loanDueRound) {
                int owed = p.loanOwed;
                p.loanOwed = 0;
                p.loanDueRound = 0;
                log("O banco cobrou o empréstimo de " + p.name + ": R$ " + owed + ".");
                popup("INFO", "🏦 Banco", "O empréstimo de " + p.name + " venceu: R$ " + owed + " cobrados.");
                charge(i, -1, owed);
                if (phase == PH_OVER) return;
            }
        }
    }

    private void startEvent(int e) {
        eventId = e;
        eventLeft = (e == E_CRISE || e == E_AUXILIO) ? 1 : 2;
        eventArg = -1;
        eventSeq++;
        String desc = EVENT_DESC[e];
        if (e == E_FESTA) {
            ArrayList<Integer> cities = new ArrayList<>();
            for (int i = 0; i < Board.SIZE; i++) if (Board.TYPE[i] == Board.PROP) cities.add(i);
            eventArg = cities.get(rnd.nextInt(cities.size()));
            desc = "Festa em " + Board.NAME[eventArg] + ": o aluguel lá fica em dobro.";
        }
        log("🌎 Evento: " + EVENT_NAMES[e] + " — " + desc);
        popup("EVENT", EVENT_ICONS[e] + " " + EVENT_NAMES[e], desc);
        if (e == E_CRISE) {
            for (int i = 0; i < players.length; i++) {
                Player p = players[i];
                if (p.bankrupt || p.money <= 0) continue;
                charge(i, -1, Math.min(150, p.money / 10));
            }
        } else if (e == E_AUXILIO) {
            for (Player p : players) if (!p.bankrupt) p.money += 100;
        }
    }

    public String eventText() {
        if (eventId < 0) return "";
        if (eventId == E_FESTA && eventArg >= 0) return "Festa em " + Board.NAME[eventArg] + ": aluguel em dobro.";
        return EVENT_DESC[eventId];
    }

    private void endByRounds() {
        int best = -1;
        for (int i = 0; i < players.length; i++) {
            if (players[i].bankrupt) continue;
            if (best < 0 || netWorth(i) > netWorth(best)) best = i;
        }
        winner = best;
        phase = PH_OVER;
        pendingBuy = -1;
        log("Fim das rodadas!");
        if (best >= 0) {
            log("🏆 " + players[best].name + " venceu com patrimônio de R$ " + netWorth(best) + "!");
            popup("WIN", "🏆 Fim de jogo", players[best].name + " venceu com o maior patrimônio!");
        }
    }

    private void passGo() {
        Player pl = cur();
        pl.money += GO_SALARY;
        log(pl.name + " passou pela Partida e recebeu R$ " + GO_SALARY + ".");
        if (pl.character == INVESTIDOR && pl.money > 0) {
            int bonus = Math.min(150, pl.money * 5 / 100);
            pl.money += bonus;
            log(pl.name + " (Investidor) rendeu R$ " + bonus + ".");
        }
        int c = addPower(current);
        if (c >= 0) log(pl.name + " ganhou a carta de poder " + POWER_NAMES[c] + ".");
    }

    private void move(int steps) {
        Player pl = cur();
        int from = pl.pos;
        int np = pl.pos + steps;
        if (np >= Board.SIZE) passGo();
        pl.pos = ((np % Board.SIZE) + Board.SIZE) % Board.SIZE;
        moves.add(new int[]{current, from, pl.pos, MV_WALK});
        land();
    }

    private void moveTo(int target, boolean collectGo) {
        Player pl = cur();
        int from = pl.pos;
        if (collectGo && target < pl.pos) passGo();
        pl.pos = target;
        moves.add(new int[]{current, from, target, MV_JUMP});
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
                        if (pl.money >= buyPrice(pos)) {
                            pendingBuy = pos;
                            phase = PH_BUY;
                            log(pl.name + " caiu em " + Board.NAME[pos] + " (à venda por R$ " + buyPrice(pos) + ").");
                        } else {
                            log(pl.name + " caiu em " + Board.NAME[pos] + ", mas não tem dinheiro para comprar.");
                            startAuction(pos);
                        }
                    } else if (o == current) {
                        log(pl.name + " caiu na própria propriedade " + Board.NAME[pos] + ".");
                    } else if (mortgaged[pos]) {
                        log(pl.name + " caiu em " + Board.NAME[pos] + " (hipotecada, sem aluguel).");
                    } else if (isClosed(pos)) {
                        log(pl.name + " caiu em " + Board.NAME[pos] + " (fechada por sabotagem, sem aluguel).");
                    } else {
                        int r = rentOf(pos);
                        if (r <= 0) {
                            log(pl.name + " caiu em " + Board.NAME[pos] + " e não paga nada agora.");
                        } else if (shield) {
                            shield = false;
                            log("🛡️ O Escudo de " + pl.name + " bloqueou o aluguel de R$ " + r + "!");
                            popup("INFO", "🛡️ Escudo", pl.name + " não pagou R$ " + r + " de aluguel.");
                        } else {
                            log(pl.name + " pagou R$ " + r + " de aluguel a " + players[o].name + " (" + Board.NAME[pos] + ").");
                            pl.rentPaid += r;
                            players[o].rentRecv += r;
                            earned[pos] += r;
                            charge(current, o, r);
                        }
                    }
                    break;
                }
                case Board.TAX: {
                    int t = Board.PRICE[pos];
                    if (pl.character == ADVOGADO) t /= 2;
                    log(pl.name + " pagou R$ " + t + " de " + Board.NAME[pos] + ".");
                    charge(current, -1, t);
                    break;
                }
                case Board.DESTINO: drawDestino(); break;
                case Board.SURPRESA: drawSurpresa(); break;
                case Board.GOTOJAIL:
                    log(pl.name + " foi mandado para a prisão!");
                    sendToJail(current);
                    break;
                case Board.PARKING:
                    log(pl.name + " chegou ao Descanso: hora da roleta!");
                    phase = PH_ROULETTE;
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
        if (pl.pos != Board.JAIL_POS) moves.add(new int[]{p, pl.pos, Board.JAIL_POS, MV_JUMP});
        pl.pos = Board.JAIL_POS;
        pl.inJail = true;
        pl.jailTurns = 0;
        pl.jailCount++;
        if (p == current) {
            rollAgain = false;
            doubles = 0;
            if (phase != PH_OVER) phase = PH_AFTER;
        }
    }

    private void buy(int price) {
        Player pl = cur();
        int pos = pendingBuy;
        pl.money -= price;
        owner[pos] = current;
        pl.bought++;
        pendingBuy = -1;
        phase = PH_AFTER;
        log(pl.name + " comprou " + Board.NAME[pos] + " por R$ " + price + ".");
        afterLanding();
    }

    // ================================================================= leilão

    private void startAuction(int pos) {
        phase = PH_AUCTION;
        auctionPos = pos;
        pendingBuy = -1;
        highBid = 0;
        highBidder = -1;
        auctionPassed = new boolean[players.length];
        for (int i = 0; i < players.length; i++) auctionPassed[i] = players[i].bankrupt;
        auctionSeq++;
        log("🔨 Leilão de " + Board.NAME[pos] + "!");
    }

    private void checkAuction() {
        if (phase != PH_AUCTION) return;
        int active = 0;
        for (int i = 0; i < players.length; i++) {
            if (!players[i].bankrupt && !auctionPassed[i] && i != highBidder) active++;
        }
        if (active == 0) closeAuction();
    }

    private void closeAuction() {
        int pos = auctionPos;
        if (highBidder >= 0 && !players[highBidder].bankrupt && players[highBidder].money >= highBid) {
            Player w = players[highBidder];
            w.money -= highBid;
            owner[pos] = highBidder;
            w.bought++;
            log("🔨 " + w.name + " arrematou " + Board.NAME[pos] + " por R$ " + highBid + ".");
            popup("INFO", "🔨 Leilão encerrado", w.name + " levou " + Board.NAME[pos] + " por R$ " + highBid + ".");
        } else {
            log("🔨 Ninguém arrematou " + Board.NAME[pos] + ".");
        }
        auctionPos = -1;
        highBidder = -1;
        highBid = 0;
        auctionSeq++;
        phase = PH_AFTER;
        afterLanding();
    }

    // ================================================================= roleta, poderes, trocas, missões

    private void spinRoulette() {
        Player pl = cur();
        int r = rnd.nextInt(ROULETTE.length);
        rouletteResult = r;
        rouletteSeq++;
        phase = PH_AFTER;
        String txt;
        switch (r) {
            case 0: pl.money += 100; txt = "Ganhou R$ 100!"; break;
            case 1: pl.money += 300; txt = "Ganhou R$ 300!"; break;
            case 2: txt = "Perdeu R$ 150."; charge(current, -1, 150); break;
            case 3: {
                int c = addPower(current);
                txt = c >= 0 ? "Ganhou a carta " + POWER_NAMES[c] + "!" : "Ganharia uma carta, mas a mão está cheia.";
                break;
            }
            case 4: txt = "Foi para a prisão!"; sendToJail(current); break;
            case 5: txt = "Foi para a Partida e recebeu R$ 200!"; moveTo(0, true); break;
            case 6: txt = "Nada aconteceu."; break;
            default: {
                txt = "Recebeu R$ 50 de cada jogador!";
                for (int q = 0; q < players.length; q++) {
                    if (q != current && !players[q].bankrupt) charge(q, current, 50);
                }
                break;
            }
        }
        log("🎡 Roleta de " + pl.name + ": " + ROULETTE[r] + ". " + txt);
        popup("ROULETTE", "🎡 " + ROULETTE[r], pl.name + ": " + txt);
        afterLanding();
    }

    private int addPower(int p) {
        Player pl = players[p];
        if (pl.powers.size() >= MAX_POWERS) return -1;
        int c = rnd.nextInt(POWER_NAMES.length);
        pl.powers.add(c);
        return c;
    }

    private void usePower(int p, int hand, int target) {
        Player pl = players[p];
        int card = pl.powers.remove(hand);
        String msg;
        switch (card) {
            case P_ESCUDO:
                shield = true;
                msg = pl.name + " ativou o Escudo: o próximo aluguel desta vez é grátis.";
                break;
            case P_SABOTAGEM:
                closedUntil[target] = round + 2;
                msg = pl.name + " sabotou " + Board.NAME[target] + " de " + players[owner[target]].name
                        + ": fechada até o fim da próxima rodada.";
                break;
            case P_TELEPORTE: {
                int t = 5;
                for (int s : new int[]{5, 15, 25, 35}) if (s > pl.pos) { t = s; break; }
                if (pl.pos >= 35) t = 5;
                rollAgain = false;
                doubles = 0;
                phase = PH_AFTER;
                msg = pl.name + " se teletransportou para " + Board.NAME[t] + ".";
                log("🌀 " + msg);
                popup("POWER", "🌀 Teletransporte", msg);
                moveTo(t, true);
                afterLanding();
                return;
            }
            case P_DESCONTO:
                msg = pl.name + " usou Desconto.";
                log("🏷️ " + msg);
                buy(buyPrice(pendingBuy) / 2);
                popup("POWER", "🏷️ Desconto", pl.name + " comprou pela metade do preço!");
                return;
            case P_TURBO:
                turbo = true;
                msg = pl.name + " ativou o Turbo: vai rolar 3 dados.";
                break;
            default: {
                msg = pl.name + " usou Cobrança: cada jogador paga R$ 30.";
                log(POWER_ICONS[card] + " " + msg);
                popup("POWER", POWER_ICONS[card] + " " + POWER_NAMES[card], msg);
                for (int q = 0; q < players.length; q++) {
                    if (q != p && !players[q].bankrupt) charge(q, p, 30);
                }
                return;
            }
        }
        log(POWER_ICONS[card] + " " + msg);
        popup("POWER", POWER_ICONS[card] + " " + POWER_NAMES[card], msg);
    }

    private void doTrade() {
        Player a = players[tradeFrom], b = players[tradeTo];
        for (int i = 0; i < Board.SIZE; i++) {
            if ((tradeGive & bit(i)) != 0) owner[i] = tradeTo;
            if ((tradeGet & bit(i)) != 0) owner[i] = tradeFrom;
        }
        a.money += tradeGetMoney - tradeGiveMoney;
        b.money += tradeGiveMoney - tradeGetMoney;
        log("🤝 " + b.name + " aceitou a troca com " + a.name + ".");
        popup("INFO", "🤝 Troca feita", a.name + " e " + b.name + " fecharam negócio.");
        clearTrade();
    }

    private void clearTrade() {
        tradeFrom = -1;
        tradeTo = -1;
        tradeGive = tradeGet = 0;
        tradeGiveMoney = tradeGetMoney = 0;
        tradeSeq++;
    }

    public boolean missionMet(int p) {
        Player pl = players[p];
        switch (pl.mission) {
            case 0: return countOwned(p, Board.TRANSPORT) >= 2;
            case 1:
                for (int g = 0; g < 8; g++) if (ownsGroup(p, g)) return true;
                return false;
            case 2: {
                int n = 0;
                for (int i = 0; i < Board.SIZE; i++) if (owner[i] == p) n += houses[i];
                return n >= 3;
            }
            case 3: {
                int n = 0;
                for (int i = 0; i < Board.SIZE; i++) if (owner[i] == p && isMT(i)) n++;
                return n >= 3;
            }
            case 4: return pl.money >= 2000;
            case 5: {
                int n = 0;
                for (int i = 0; i < Board.SIZE; i++) if (owner[i] == p) n++;
                return n >= 6;
            }
            case 6:
                for (int i = 0; i < Board.SIZE; i++) if (owner[i] == p && houses[i] == 5) return true;
                return false;
            default: return pl.rentRecv >= 500;
        }
    }

    private void checkMissions() {
        if (phase == PH_OVER) return;
        for (int i = 0; i < players.length; i++) {
            Player p = players[i];
            if (p.bankrupt || p.missionDone || !missionMet(i)) continue;
            p.missionDone = true;
            p.money += MISSION_REWARD;
            int c = addPower(i);
            log("🎯 " + p.name + " cumpriu a missão secreta (" + MISSIONS[p.mission] + ") e ganhou R$ " + MISSION_REWARD
                    + (c >= 0 ? " + carta " + POWER_NAMES[c] : "") + "!");
            popup("MISSION", "🎯 Missão cumprida!", p.name + ": " + MISSIONS[p.mission] + " (+R$ " + MISSION_REWARD + ")");
        }
    }

    // ================================================================= dinheiro e falência

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
                log(pl.name + " está devendo R$ " + (-pl.money) + ". Venda, hipoteque, peça empréstimo ou declare falência.");
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
        if (pl.money < 0) bankrupt(p, cred);
        else log(pl.name + " vendeu/hipotecou bens para pagar a dívida.");
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
        pl.loanOwed = 0;
        pl.powers.clear();
        pl.inJail = false;
        pl.bankrupt = true;
        if (tradeFrom == p || tradeTo == p) clearTrade();
        log("💀 " + pl.name + " faliu e está fora da partida" + (cred >= 0 ? " (bens para " + players[cred].name + ")." : "."));
        popup("INFO", "💀 Falência", pl.name + " está fora da partida.");
        if (aliveCount() <= 1) {
            for (int i = 0; i < players.length; i++) if (!players[i].bankrupt) winner = i;
            phase = PH_OVER;
            pendingBuy = -1;
            auctionPos = -1;
            if (winner >= 0) {
                log("🏆 " + players[winner].name + " venceu a partida!");
                popup("WIN", "🏆 Fim de jogo", players[winner].name + " venceu a partida!");
            }
            return;
        }
        if (phase == PH_AUCTION) {
            if (p == current) {
                auctionPos = -1;
                highBidder = -1;
                highBid = 0;
                phase = PH_AFTER;
            } else {
                if (highBidder == p) { highBidder = -1; highBid = 0; }
                auctionPassed[p] = true;
                checkAuction();
            }
        }
        if (p == current) {
            phase = PH_AFTER;
            nextTurn();
        }
    }

    // ================================================================= cartas

    private void showCard(String deck, String text) {
        log(cur().name + " tirou " + deck + ": " + text);
        popup("CARD", deck.equals("DESTINO") ? "❓ Destino" : "⭐ Surpresa", text);
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
                int from = pl.pos;
                pl.pos = (pl.pos + Board.SIZE - 3) % Board.SIZE;
                moves.add(new int[]{current, from, pl.pos, MV_JUMP});
                land();
                break;
            }
            case 5: showCard("DESTINO", "Vá direto para a prisão. Não passe pela Partida."); sendToJail(current); break;
            case 6: showCard("DESTINO", "Saída livre da prisão. Guarde esta carta."); pl.jailCards++; break;
            case 7: showCard("DESTINO", "Multa por excesso de velocidade: pague R$ 15."); charge(current, -1, 15); break;
            case 8: showCard("DESTINO", "Você ganhou na loteria! Receba R$ 150."); pl.money += 150; break;
            case 9:
                showCard("DESTINO", "Reforma geral: pague R$ 25 por casa e R$ 100 por hotel.");
                charge(current, -1, repairs(25, 100));
                break;
            case 10:
                showCard("DESTINO", "Você foi eleito síndico: pague R$ 50 a cada jogador.");
                for (int q = 0; q < players.length; q++) {
                    if (q != current && !players[q].bankrupt) charge(current, q, 50);
                }
                break;
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
            case 6:
                showCard("SURPRESA", "É seu aniversário! Receba R$ 10 de cada jogador.");
                for (int q = 0; q < players.length; q++) {
                    if (q != current && !players[q].bankrupt) charge(q, current, 10);
                }
                break;
            case 7: showCard("SURPRESA", "Restituição do imposto: receba R$ 20."); pl.money += 20; break;
            case 8: showCard("SURPRESA", "Conta do hospital: pague R$ 100."); charge(current, -1, 100); break;
            case 9: showCard("SURPRESA", "Você recebeu uma herança: R$ 100."); pl.money += 100; break;
            case 10:
                showCard("SURPRESA", "Conserto das ruas: pague R$ 40 por casa e R$ 115 por hotel.");
                charge(current, -1, repairs(40, 115));
                break;
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

    private void popup(String kind, String title, String text) {
        popups.add(kind + "\u001f" + title + "\u001f" + text);
    }

    private void log(String s) {
        log.add(s);
        while (log.size() > 50) log.remove(0);
    }

    // ================================================================= serialização

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
            o.writeInt(d3);
            o.writeInt(doubles);
            o.writeBoolean(rollAgain);
            for (int i = 0; i < Board.SIZE; i++) {
                o.writeInt(owner[i]);
                o.writeInt(houses[i]);
                o.writeBoolean(mortgaged[i]);
                o.writeInt(closedUntil[i]);
                o.writeInt(earned[i]);
            }
            o.writeInt(pendingBuy);
            o.writeInt(winner);
            o.writeInt(creditor);
            o.writeInt(round);
            o.writeInt(maxRounds);
            o.writeInt(version);
            o.writeInt(pendingSteps);
            o.writeBoolean(turbo);
            o.writeBoolean(shield);
            o.writeInt(eventId);
            o.writeInt(eventArg);
            o.writeInt(eventLeft);
            o.writeInt(eventSeq);
            o.writeInt(auctionPos);
            o.writeInt(highBid);
            o.writeInt(highBidder);
            o.writeInt(auctionSeq);
            o.writeInt(auctionPassed.length);
            for (boolean b : auctionPassed) o.writeBoolean(b);
            o.writeInt(rouletteResult);
            o.writeInt(rouletteSeq);
            o.writeInt(tradeFrom);
            o.writeInt(tradeTo);
            o.writeInt(tradeGiveMoney);
            o.writeInt(tradeGetMoney);
            o.writeInt(tradeSeq);
            o.writeLong(tradeGive);
            o.writeLong(tradeGet);
            o.writeInt(emojiWho);
            o.writeInt(emojiIdx);
            o.writeInt(emojiSeq);
            o.writeInt(rollSeq);
            o.writeInt(moves.size());
            for (int[] m : moves) for (int k = 0; k < 4; k++) o.writeInt(m[k]);
            o.writeInt(popups.size());
            for (String s : popups) o.writeUTF(s);
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
        g.d3 = in.readInt();
        g.doubles = in.readInt();
        g.rollAgain = in.readBoolean();
        for (int i = 0; i < Board.SIZE; i++) {
            g.owner[i] = in.readInt();
            g.houses[i] = in.readInt();
            g.mortgaged[i] = in.readBoolean();
            g.closedUntil[i] = in.readInt();
            g.earned[i] = in.readInt();
        }
        g.pendingBuy = in.readInt();
        g.winner = in.readInt();
        g.creditor = in.readInt();
        g.round = in.readInt();
        g.maxRounds = in.readInt();
        g.version = in.readInt();
        g.pendingSteps = in.readInt();
        g.turbo = in.readBoolean();
        g.shield = in.readBoolean();
        g.eventId = in.readInt();
        g.eventArg = in.readInt();
        g.eventLeft = in.readInt();
        g.eventSeq = in.readInt();
        g.auctionPos = in.readInt();
        g.highBid = in.readInt();
        g.highBidder = in.readInt();
        g.auctionSeq = in.readInt();
        int ap = in.readInt();
        g.auctionPassed = new boolean[ap];
        for (int i = 0; i < ap; i++) g.auctionPassed[i] = in.readBoolean();
        g.rouletteResult = in.readInt();
        g.rouletteSeq = in.readInt();
        g.tradeFrom = in.readInt();
        g.tradeTo = in.readInt();
        g.tradeGiveMoney = in.readInt();
        g.tradeGetMoney = in.readInt();
        g.tradeSeq = in.readInt();
        g.tradeGive = in.readLong();
        g.tradeGet = in.readLong();
        g.emojiWho = in.readInt();
        g.emojiIdx = in.readInt();
        g.emojiSeq = in.readInt();
        g.rollSeq = in.readInt();
        int mv = in.readInt();
        for (int i = 0; i < mv; i++) g.moves.add(new int[]{in.readInt(), in.readInt(), in.readInt(), in.readInt()});
        int pp = in.readInt();
        for (int i = 0; i < pp; i++) g.popups.add(in.readUTF());
        int ls = in.readInt();
        for (int i = 0; i < ls; i++) g.log.add(in.readUTF());
        return g;
    }
}
