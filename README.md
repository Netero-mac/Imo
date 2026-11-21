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

## Regras
- Começa com R$ 1.500; passar pela Partida dá R$ 200.
- Compre propriedades sem dono; quem cair nelas paga aluguel.
- Com todas as cidades de uma cor: aluguel em dobro e pode construir casas (até 4) e hotel.
- Dupla = joga de novo; três duplas = prisão. Na prisão: dupla, fiança de R$ 50 ou carta.
- Cartas de **Destino** e **Surpresa**. Hipoteca e venda de casas para pagar dívidas.
- Vence o último que não falir.

## Estrutura
- `engine/` — regras do jogo (Java puro, sem Android)
- `net/` — conexão Wi-Fi (TCP + busca automática por UDP) e Bluetooth (o celular do anfitrião é quem decide; os outros enviam ações)
- `ui/BoardView.java` — desenho do tabuleiro
- `MainActivity.java` — telas
