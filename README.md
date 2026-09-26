# UltraCore — plugin tudo-em-um para servidores de Minecraft

Plugin para **Paper 1.21 até 26.x** (e forks como Purpur) que junta num só lugar os recursos mais usados em servidores.
Cada recurso é um **módulo** que pode ser **ativado, desativado e configurado dentro do jogo** pelo comando `/painel` — sem precisar mexer em arquivos nem reiniciar.

## Versões suportadas

Testado automaticamente ligando um **servidor Paper real** de cada versão com o plugin (workflow *Compatibilidade Paper*):

**1.21, 1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1.1, 26.1.2, 26.2 e 26.3** ✅

- Paper 1.21.x usa **Java 21**; Paper 26.x usa **Java 25**. O mesmo `.jar` serve para todas.
- A cada envio de código, o GitHub descobre sozinho as versões novas do Paper e testa de novo.
- Se algum módulo falhar numa versão futura, ele é desligado sozinho e o resto do plugin continua funcionando.

## Como instalar

1. Baixe o `UltraCore-1.0.0.jar`:
   - Na página **Releases** do GitHub (lado direito do repositório) → versão mais recente → *Assets* → `UltraCore-x.y.z.jar`; **ou**
   - Na aba **Actions** → último build com ✅ → seção *Artifacts* → `UltraCore-plugin`; **ou**
   - Compile você mesmo: `mvn package` (precisa de Java 21 e Maven). O jar sai em `target/`.
2. Coloque o `.jar` na pasta `plugins/` do servidor Paper 1.21+.
3. Inicie o servidor. Entre como OP e digite **`/painel`**.

## O painel (`/painel`)

- **Clique esquerdo** em um módulo → ativa/desativa na hora.
- **Clique direito** → abre as configurações do módulo:
  - Opções Sim/Não: clique para alternar.
  - Números: esquerdo aumenta, direito diminui, Shift altera 10x.
  - Textos: clique e digite o novo valor no chat (`cancelar` para desistir, `vazio` para deixar em branco).
  - Listas (linhas do scoreboard, anúncios...): esquerdo adiciona linha, direito remove a última, Shift+direito limpa.
- Botões: **Recarregar config**, **Ativar todos**, **Desativar todos**.

Tudo fica salvo em `plugins/UltraCore/config.yml`. Pelo console: `/painel lista`, `/painel ativar <módulo>`, `/painel desativar <módulo>`, `/painel reload`.

Textos aceitam cores `&a`, `&l`... e hex `&#FF8800`.

## Módulos

| Módulo | O que faz | Comandos |
|---|---|---|
| Spawn | Spawn do servidor, spawn na 1ª entrada e ao renascer | `/spawn`, `/setspawn` |
| Homes | Casas por jogador, com limite configurável | `/sethome`, `/home`, `/delhome`, `/homes` |
| Warps | Pontos públicos com menu visual | `/warp`, `/warps`, `/setwarp`, `/delwarp` |
| TPA | Pedidos de teleporte | `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, `/tptoggle` |
| Back | Voltar ao local anterior/da morte | `/back` |
| Teleporte Aleatório | Local aleatório e seguro, com cooldown | `/rtp` |
| Economia | Dinheiro, pagamentos, ranking, prêmio por abate | `/money`, `/pay`, `/baltop`, `/eco` |
| Kits | Kits com cooldown ou uso único; kit inicial | `/kit`, `/createkit`, `/delkit` |
| Chat | Formato, tags por permissão, anti-spam, anti-CAPS, filtro | `/staffchat`, `/clearchat`, `/broadcast` |
| Mensagens Privadas | Conversas privadas + socialspy | `/msg`, `/r`, `/socialspy` |
| Entrada e Saída | Mensagens de entrada/saída, boas-vindas, título | — |
| Anúncios Automáticos | Mensagens periódicas no chat | — |
| Scoreboard | Placar lateral com placeholders | — |
| Tablist | Cabeçalho e rodapé da TAB | — |
| MOTD | Descrição na lista de servidores (desligado por padrão) | — |
| Moderação | Ban, tempban, kick, mute temporário | `/ban`, `/tempban`, `/unban`, `/kick`, `/mute`, `/unmute` |
| Ferramentas da Staff | Voo, cura, gamemode, deus, vanish, invsee... | `/fly`, `/heal`, `/feed`, `/gm`, `/god`, `/vanish`, `/invsee`, `/ecsee`, `/clearinv`, `/speed`, `/tphere` |
| Utilidades | Bancada, enderchest, chapéu, ping, lixeira, regras | `/craft`, `/ec`, `/hat`, `/ping`, `/lixo`, `/regras` |
| Proteção do Mundo | Creeper/TNT sem quebrar blocos, fogo, enderman, phantoms, keep inventory, clima, queda, plantações | — |
| AFK | Detecta ausentes, tag [AFK], expulsão opcional | `/afk` |
| Dormir | % de jogadores necessários para pular a noite | — |
| Colheita Fácil | Botão direito colhe e replanta | — |
| Mortes | Coordenadas da morte, cabeça do jogador em PvP | — |
| Limpeza de Itens | Remove itens do chão com aviso | `/limparitens` |

Tempos nos comandos aceitam `30s`, `10m`, `2h`, `7d`, `1w` (ex: `/tempban Fulano 7d hack`, `/mute Fulano 30m spam`).

### Placeholders (scoreboard, tab, anúncios, mensagens)

`{player}` `{money}` `{online}` `{max}` `{kills}` `{deaths}` `{ping}` `{world}` `{x}` `{y}` `{z}` — e, se o **PlaceholderAPI** estiver instalado, qualquer `%placeholder%` dele.

## Permissões

| Permissão | Quem tem por padrão | Inclui |
|---|---|---|
| `ultracore.player` | todos | spawn, home, warp, tpa, back, rtp, money, pay, baltop, kit, msg, ping, afk, regras, lixo |
| `ultracore.vip` | ninguém (dê pelo LuckPerms) | craft, enderchest, hat, cores no chat, fly, tag [VIP] |
| `ultracore.staff` | OP | kick, mute, ban, staffchat, vanish, invsee, gm, heal, feed, tphere... |
| `ultracore.admin` | OP | tudo + `/painel`, setspawn, setwarp, eco, createkit, god, speed... |

Outras úteis: `ultracore.homes.unlimited`, `ultracore.bypass.delay`, `ultracore.bypass.cooldown`, `ultracore.kit.<nome>`, `ultracore.warp.<nome>`, `ultracore.tag.<dono|admin|mod|vip>`.

Recomendado usar junto: **LuckPerms** (grupos/permissões) e, se quiser, **PlaceholderAPI**.

## Estrutura do código

```
src/main/java/com/ultracore/
├── UltraCore.java          # classe principal, registra os módulos
├── module/                 # sistema de módulos (Module, Setting, ModuleManager)
├── gui/                    # menus do /painel
├── core/                   # teleporte com delay, entrada pelo chat, /painel
├── data/                   # dados dos jogadores (plugins/UltraCore/players/)
├── util/                   # cores, itens, tempo, arquivos
└── modules/                # um arquivo por módulo
```

Para criar um módulo novo: crie uma classe em `modules/` que estende `Module`, declare as configurações (`bool`, `integer`, `text`, `list`...) e os comandos no construtor, registre em `UltraCore.onEnable()` e adicione os comandos no `plugin.yml`. Ele aparece sozinho no painel.
