# Magnata 🎲🏠

Jogo de tabuleiro de negócios para Android, no estilo dos clássicos de compra e venda de imóveis,
com cidades de Mato Grosso e do Brasil. Jogue com até 6 amigos via **Wi-Fi**, **Bluetooth** ou passando o celular.

## Como baixar o APK
Link direto (sempre a versão mais nova):
**https://github.com/Netero-mac/Imo/releases/latest/download/Magnata.apk**

Ou abra a aba **Releases** do repositório e baixe o `Magnata.apk`.

Todos os jogadores precisam instalar o mesmo APK.

## Como jogar com amigos

### Pelo Wi-Fi (recomendado)
1. Todos os celulares conectados na **mesma rede Wi-Fi** — ou um celular liga o roteador (hotspot) e os outros se conectam nele (não gasta internet).
2. Um jogador toca em **Criar sala > Wi-Fi**.
3. Os outros tocam em **Entrar numa sala > Wi-Fi** e escolhem a sala que aparece.
   Se não aparecer, digitem o endereço mostrado no celular do anfitrião (ex.: `192.168.0.10`).
4. O anfitrião toca em **Começar partida**.

Algumas redes públicas (faculdade, shopping) bloqueiam a conexão entre aparelhos; nesse caso use o roteador de um dos celulares.

### Pelo Bluetooth
1. Um jogador toca em **Criar sala > Bluetooth** e aceita ficar visível.
2. Os outros tocam em **Entrar numa sala > Bluetooth** e escolhem o celular do anfitrião.
3. O anfitrião toca em **Começar partida**.

Se o celular do anfitrião não aparecer na lista, pareie os aparelhos antes nas configurações de Bluetooth.

## Visual
- Tabuleiro em **2.5D** (isométrico): arraste com o dedo, pinça para zoom, toque duplo para aproximar.
- Dados 3D rolando, peão andando casa por casa, casinhas e hotéis em relevo, bandeirinha do dono.
- Cada jogador escolhe **cor**, **peão** (Peão, Carro, Cartola, Foguete, Coroa, Estrela, Barco, Diamante) e **personagem**.
- Sons, vibração na sua vez e emojis para provocar os amigos.

## Guia rápido das mecânicas
| Mecânica | Resumo |
|---|---|
| 🎲 Básico | Role, ande, compre propriedades sem dono, pague aluguel nas dos outros. Passar pela Partida dá R$ 200. |
| 🏠 Construções | Com a cor completa o aluguel dobra e dá para construir até 4 casas e um hotel. |
| 🔒 Prisão | Tire dupla, pague a fiança ou use a carta. Na 3ª tentativa a fiança é cobrada. |
| 🧑 Personagens | Fazendeiro (+50% aluguel em MT), Empreiteiro (−20% nas construções), Advogado (fiança R$ 20, meio imposto), Viajante (anda ±1 casa), Investidor (rende 5% ao passar pela Partida). |
| 🔨 Leilão | Se ninguém comprar, a propriedade vai a leilão com lances de +10/+50/+100. 10 s sem lance encerra. |
| 🤝 Trocas | Na sua vez, ofereça propriedades e dinheiro em troca das de outro jogador. |
| 🏦 Empréstimo | Até R$ 1.000 do banco, devolvendo +20% em até 5 rodadas. |
| 🃏 Cartas de poder | Escudo, Sabotagem, Teletransporte, Desconto, Turbo e Cobrança. Ganha ao passar pela Partida, na roleta e nas missões (máx. 3). |
| 🌎 Eventos globais | Seca no agro, Feriadão, Boom imobiliário, Crise, Festa na cidade, Auxílio, Greve e Inflação mudam o jogo por algumas rodadas. |
| 🎯 Missão secreta | Um objetivo escondido para cada um: vale R$ 300 + uma carta. |
| 🎡 Roleta | Cair no Descanso gira uma roleta de prêmios (e castigos). |
| ⏱️ Modo rápido | Partida de 15 ou 25 rodadas; vence o maior patrimônio. |

O próprio jogo tem um **Guia** completo e mostra uma dica na primeira vez que cada mecânica aparece.

## Estrutura
- `engine/` — regras do jogo (Java puro, sem Android)
- `net/` — conexão Wi-Fi (TCP + busca automática por UDP) e Bluetooth (o celular do anfitrião é quem decide; os outros enviam ações)
- `engine/Rules.java` — textos e tabelas de personagens, poderes, eventos, missões e roleta
- `ui/BoardView.java` — tabuleiro 2.5D, animações, dados e roleta
- `ui/Pawns.java` — desenho dos peões
- `ui/Sound.java` — efeitos sonoros (`res/raw`)
- `MainActivity.java` — telas
