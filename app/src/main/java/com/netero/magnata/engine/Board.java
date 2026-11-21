package com.netero.magnata.engine;

/** Dados fixos do tabuleiro (40 casas). */
public final class Board {
    private Board() {}

    public static final int SIZE = 40;

    public static final int GO = 0, PROP = 1, TRANSPORT = 2, UTIL = 3, TAX = 4,
            DESTINO = 5, SURPRESA = 6, JAIL = 7, PARKING = 8, GOTOJAIL = 9;

    public static final int JAIL_POS = 10;

    public static final int[] TYPE = new int[SIZE];
    public static final String[] NAME = new String[SIZE];
    public static final String[] SHORT = new String[SIZE];
    public static final int[] PRICE = new int[SIZE];
    public static final int[] GROUP = new int[SIZE];
    public static final int[] HOUSE_COST = new int[SIZE];
    public static final int[][] RENT = new int[SIZE][];

    /** Cores dos 8 grupos (ARGB). */
    public static final int[] GROUP_COLOR = {
            0xFF8D5524, // marrom
            0xFF7FD3F5, // azul-claro
            0xFFD9418C, // rosa
            0xFFF39C12, // laranja
            0xFFE53935, // vermelho
            0xFFF4D03F, // amarelo
            0xFF27AE60, // verde
            0xFF1F3A93  // azul-escuro
    };
    public static final String[] GROUP_NAME = {
            "Marrom", "Azul-claro", "Rosa", "Laranja", "Vermelho", "Amarelo", "Verde", "Azul-escuro"
    };

    private static void prop(int pos, String name, String shortName, int group, int price, int house, int... rent) {
        TYPE[pos] = PROP; NAME[pos] = name; SHORT[pos] = shortName; GROUP[pos] = group;
        PRICE[pos] = price; HOUSE_COST[pos] = house; RENT[pos] = rent;
    }

    private static void other(int pos, int type, String name, String shortName, int price) {
        TYPE[pos] = type; NAME[pos] = name; SHORT[pos] = shortName; GROUP[pos] = -1; PRICE[pos] = price;
    }

    static {
        other(0, GO, "Partida", "PARTIDA", 0);
        prop(1, "Pedra Preta", "Pedra Preta", 0, 60, 50, 2, 10, 30, 90, 160, 250);
        other(2, SURPRESA, "Surpresa", "Surpresa", 0);
        prop(3, "Jaciara", "Jaciara", 0, 60, 50, 4, 20, 60, 180, 320, 450);
        other(4, TAX, "Imposto de Renda", "Imposto", 200);
        other(5, TRANSPORT, "Rodoviária", "Rodoviária", 200);
        prop(6, "Juscimeira", "Juscimeira", 1, 100, 50, 6, 30, 90, 270, 400, 550);
        other(7, DESTINO, "Destino", "Destino", 0);
        prop(8, "Guiratinga", "Guiratinga", 1, 100, 50, 6, 30, 90, 270, 400, 550);
        prop(9, "Poxoréu", "Poxoréu", 1, 120, 50, 8, 40, 100, 300, 450, 600);
        other(10, JAIL, "Prisão / Visita", "PRISÃO", 0);
        prop(11, "Primavera do Leste", "Primavera", 2, 140, 100, 10, 50, 150, 450, 625, 750);
        other(12, UTIL, "Cia. de Energia", "Energia", 150);
        prop(13, "Cáceres", "Cáceres", 2, 140, 100, 10, 50, 150, 450, 625, 750);
        prop(14, "Tangará da Serra", "Tangará", 2, 160, 100, 12, 60, 180, 500, 700, 900);
        other(15, TRANSPORT, "Ferroviária", "Ferroviária", 200);
        prop(16, "Sinop", "Sinop", 3, 180, 100, 14, 70, 200, 550, 750, 950);
        other(17, SURPRESA, "Surpresa", "Surpresa", 0);
        prop(18, "Sorriso", "Sorriso", 3, 180, 100, 14, 70, 200, 550, 750, 950);
        prop(19, "Rondonópolis", "Rondonópolis", 3, 200, 100, 16, 80, 220, 600, 800, 1000);
        other(20, PARKING, "Descanso", "DESCANSO", 0);
        prop(21, "Cuiabá", "Cuiabá", 4, 220, 150, 18, 90, 250, 700, 875, 1050);
        other(22, DESTINO, "Destino", "Destino", 0);
        prop(23, "Campo Grande", "Campo Grande", 4, 220, 150, 18, 90, 250, 700, 875, 1050);
        prop(24, "Goiânia", "Goiânia", 4, 240, 150, 20, 100, 300, 750, 925, 1100);
        other(25, TRANSPORT, "Aeroporto", "Aeroporto", 200);
        prop(26, "Brasília", "Brasília", 5, 260, 150, 22, 110, 330, 800, 975, 1150);
        prop(27, "Curitiba", "Curitiba", 5, 260, 150, 22, 110, 330, 800, 975, 1150);
        other(28, UTIL, "Cia. de Água", "Água", 150);
        prop(29, "Belo Horizonte", "Belo Horizonte", 5, 280, 150, 24, 120, 360, 850, 1025, 1200);
        other(30, GOTOJAIL, "Vá para a Prisão", "VÁ PARA PRISÃO", 0);
        prop(31, "Porto Alegre", "Porto Alegre", 6, 300, 200, 26, 130, 390, 900, 1100, 1275);
        prop(32, "Salvador", "Salvador", 6, 300, 200, 26, 130, 390, 900, 1100, 1275);
        other(33, SURPRESA, "Surpresa", "Surpresa", 0);
        prop(34, "Recife", "Recife", 6, 320, 200, 28, 150, 450, 1000, 1200, 1400);
        other(35, TRANSPORT, "Porto", "Porto", 200);
        other(36, DESTINO, "Destino", "Destino", 0);
        prop(37, "Rio de Janeiro", "Rio de Janeiro", 7, 350, 200, 35, 175, 500, 1100, 1300, 1500);
        other(38, TAX, "Taxa de Luxo", "Taxa Luxo", 100);
        prop(39, "São Paulo", "São Paulo", 7, 400, 200, 50, 200, 600, 1400, 1700, 2000);
    }

    public static boolean buyable(int pos) {
        int t = TYPE[pos];
        return t == PROP || t == TRANSPORT || t == UTIL;
    }

    public static int mortgageValue(int pos) { return PRICE[pos] / 2; }

    public static int unmortgageCost(int pos) { return PRICE[pos] / 2 + PRICE[pos] / 20; }
}
