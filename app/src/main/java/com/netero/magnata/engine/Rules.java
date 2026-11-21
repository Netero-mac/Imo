package com.netero.magnata.engine;

/** Textos e tabelas das mecânicas extras (personagens, poderes, eventos, missões, roleta). */
public final class Rules {
    private Rules() {}

    // ---------------------------------------------------------------- cores e peões
    public static final int[] COLORS = {
            0xFFE53935, 0xFF1E88E5, 0xFF43A047, 0xFFFDD835,
            0xFF8E24AA, 0xFFFF7043, 0xFFEC407A, 0xFF00ACC1
    };
    public static final String[] COLOR_NAMES = {
            "Vermelho", "Azul", "Verde", "Amarelo", "Roxo", "Laranja", "Rosa", "Ciano"
    };
    public static final String[] PAWN_NAMES = {
            "Peão", "Carro", "Cartola", "Foguete", "Coroa", "Estrela", "Barco", "Diamante"
    };

    // ---------------------------------------------------------------- personagens
    public static final int FAZENDEIRO = 0, EMPREITEIRO = 1, ADVOGADO = 2, VIAJANTE = 3, INVESTIDOR = 4;
    public static final String[] CHAR_NAMES = { "Fazendeiro", "Empreiteiro", "Advogado", "Viajante", "Investidor" };
    public static final String[] CHAR_ICONS = { "🌾", "🏗️", "⚖️", "🧭", "📈" };
    public static final String[] CHAR_DESC = {
            "Recebe 50% a mais de aluguel nas cidades de Mato Grosso.",
            "Paga 20% a menos para construir casas e hotéis.",
            "Fiança de só R$ 20 e paga metade dos impostos.",
            "Depois de rolar os dados, pode andar 1 casa a mais ou a menos.",
            "Ao passar pela Partida, ganha 5% do dinheiro que tem (até R$ 150)."
    };

    // ---------------------------------------------------------------- cartas de poder
    public static final int P_ESCUDO = 0, P_SABOTAGEM = 1, P_TELEPORTE = 2, P_DESCONTO = 3, P_TURBO = 4, P_COBRANCA = 5;
    public static final String[] POWER_NAMES = { "Escudo", "Sabotagem", "Teletransporte", "Desconto", "Turbo", "Cobrança" };
    public static final String[] POWER_ICONS = { "🛡️", "💣", "🌀", "🏷️", "🚀", "💰" };
    public static final String[] POWER_DESC = {
            "Use antes de andar: o próximo aluguel desta vez é de graça.",
            "Feche uma propriedade de um rival: ela não cobra aluguel por 1 rodada.",
            "Em vez de rolar, vá direto para o próximo transporte.",
            "Na hora de comprar: pague metade do preço.",
            "Use antes de rolar: você rola 3 dados nesta vez.",
            "Cada jogador paga R$ 30 para você."
    };

    // ---------------------------------------------------------------- eventos globais
    public static final int E_SECA = 0, E_FERIADAO = 1, E_BOOM = 2, E_CRISE = 3, E_FESTA = 4, E_AUXILIO = 5,
            E_GREVE = 6, E_INFLACAO = 7;
    public static final String[] EVENT_NAMES = {
            "Seca no agro", "Feriadão", "Boom imobiliário", "Crise econômica",
            "Festa na cidade", "Auxílio do governo", "Greve das companhias", "Inflação"
    };
    public static final String[] EVENT_ICONS = { "🌵", "🏖️", "🏗️", "📉", "🎉", "🎁", "✊", "💸" };
    public static final String[] EVENT_DESC = {
            "Aluguel das cidades de Mato Grosso cai pela metade.",
            "Todo mundo viaja: transportes cobram aluguel em dobro.",
            "Construir casas e hotéis fica 30% mais barato.",
            "Cada jogador paga 10% do seu dinheiro ao banco (até R$ 150).",
            "Uma cidade sorteada cobra aluguel em dobro.",
            "Cada jogador recebe R$ 100 do banco.",
            "As companhias de energia e água não cobram nada.",
            "Comprar propriedades fica 25% mais caro."
    };

    // ---------------------------------------------------------------- missões secretas
    public static final String[] MISSIONS = {
            "Tenha 2 transportes.",
            "Complete uma cor inteira.",
            "Tenha 3 construções no total.",
            "Tenha 3 cidades de Mato Grosso.",
            "Junte R$ 2.000 em dinheiro.",
            "Tenha 6 propriedades.",
            "Construa um hotel.",
            "Receba R$ 500 de aluguel no total."
    };
    public static final int MISSION_REWARD = 300;

    // ---------------------------------------------------------------- roleta do Descanso
    public static final String[] ROULETTE = {
            "+R$ 100", "+R$ 300", "-R$ 150", "Carta de poder", "Prisão!", "Vá à Partida", "Nada", "R$ 50 de cada"
    };
    public static final int[] ROULETTE_COLORS = {
            0xFF66BB6A, 0xFFFFCA28, 0xFFEF5350, 0xFF42A5F5, 0xFF616161, 0xFFAB47BC, 0xFFBDBDBD, 0xFFFF7043
    };

    // ---------------------------------------------------------------- modo de jogo
    public static final int[] ROUND_OPTIONS = { 0, 15, 25 };
    public static final String[] ROUND_NAMES = { "Clássico (até sobrar um)", "Rápido (15 rodadas)", "Médio (25 rodadas)" };

    public static final String[] EMOJIS = { "😂", "😡", "💸", "😎", "😱", "🙏", "👏", "🤡" };

    // ---------------------------------------------------------------- guia
    public static final String[][] GUIDE = {
            {"🎲 O básico", "Na sua vez, role os dados e ande. Caiu numa propriedade sem dono? Compre. "
                    + "Caiu na de outro jogador? Pague aluguel. Passar pela Partida dá R$ 200. Vence quem não falir."},
            {"🏠 Construções", "Com todas as cidades de uma cor, o aluguel dobra e você pode construir até 4 casas e depois um hotel. "
                    + "Toque na cidade no tabuleiro para construir, vender ou hipotecar."},
            {"🔒 Prisão", "Tire dupla, pague a fiança ou use a carta de saída livre. Na 3ª tentativa a fiança é cobrada. "
                    + "Três duplas seguidas mandam você para a prisão."},
            {"🧑 Personagens", "Cada jogador escolhe um personagem com uma habilidade: Fazendeiro, Empreiteiro, Advogado, "
                    + "Viajante ou Investidor. Escolha o que combina com seu jeito de jogar."},
            {"🔨 Leilão", "Se quem caiu não comprar a propriedade, ela vai a leilão: todos dão lances (+10, +50, +100). "
                    + "Quando ninguém cobre o lance por alguns segundos, quem deu o maior leva."},
            {"🤝 Trocas", "Na sua vez, toque em Trocar para oferecer propriedades e dinheiro a outro jogador em troca das dele. "
                    + "Ele aceita ou recusa. Cidades com casas não podem ser trocadas."},
            {"🏦 Empréstimo", "Precisa de dinheiro? Pegue até R$ 1.000 no banco. Você devolve 20% a mais e tem 5 rodadas; "
                    + "se não pagar antes, o banco cobra sozinho."},
            {"🃏 Cartas de poder", "Você ganha uma ao passar pela Partida, na roleta e ao cumprir missões (máximo 3 na mão). "
                    + "Use na hora certa: Escudo, Sabotagem, Teletransporte, Desconto, Turbo e Cobrança."},
            {"🌎 Eventos globais", "De tempos em tempos acontece um evento que muda o jogo para todos por algumas rodadas, "
                    + "como Seca no agro, Feriadão ou Inflação. Fique de olho no aviso no topo da tela."},
            {"🎯 Missão secreta", "Cada jogador recebe um objetivo escondido. Cumpriu? Ganha R$ 300 e uma carta de poder. "
                    + "Veja a sua no botão Missão."},
            {"🎡 Roleta", "Caiu no Descanso? Gire a roleta: pode ganhar dinheiro, uma carta de poder… ou ir para a prisão!"},
            {"⏱️ Modo rápido", "No modo Rápido ou Médio a partida tem limite de rodadas. No fim, vence quem tiver o maior patrimônio "
                    + "(dinheiro + propriedades + construções − dívidas)."}
    };
}
