# Magnata 🎲🏠

Jogo de tabuleiro de negócios para Android, no estilo dos clássicos de compra e venda de imóveis,
com cidades de Mato Grosso e do Brasil. Jogue com até 6 amigos via **Bluetooth** ou passando o celular.

## Como baixar o APK
1. Abra a aba **Actions** do repositório.
2. Entre na execução mais recente de **Gerar APK**.
3. Baixe o arquivo **Magnata-apk** (vem em .zip), extraia e instale o `app-debug.apk` no celular.

Todos os jogadores precisam instalar o mesmo APK.

## Como jogar com amigos
1. Um jogador toca em **Criar sala (Bluetooth)** e aceita ficar visível.
2. Os outros tocam em **Entrar numa sala (Bluetooth)** e escolhem o celular do anfitrião.
3. Quando todos aparecerem na sala, o anfitrião toca em **Começar partida**.

Se o celular do anfitrião não aparecer na lista, pareie os aparelhos antes nas configurações de Bluetooth.

## Regras
- Começa com R$ 1.500; passar pela Partida dá R$ 200.
- Compre propriedades sem dono; quem cair nelas paga aluguel.
- Com todas as cidades de uma cor: aluguel em dobro e pode construir casas (até 4) e hotel.
- Dupla = joga de novo; três duplas = prisão. Na prisão: dupla, fiança de R$ 50 ou carta.
- Cartas de **Destino** e **Surpresa**. Hipoteca e venda de casas para pagar dívidas.
- Vence o último que não falir.

## Estrutura
- `engine/` — regras do jogo (Java puro, sem Android)
- `net/` — conexão Bluetooth (o celular do anfitrião é quem decide; os outros enviam ações)
- `ui/BoardView.java` — desenho do tabuleiro
- `MainActivity.java` — telas
