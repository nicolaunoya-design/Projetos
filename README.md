# UltraCore — plugin tudo-em-um para servidores de Minecraft

Plugin para **Paper 1.21 até 26.x** (e forks como Purpur) que junta num só lugar os recursos mais usados em servidores: teleportes, economia, loja, kits, chat, moderação, ferramentas da staff, recompensas e muito mais.

Cada recurso é um **módulo** que pode ser **ligado, desligado e configurado dentro do jogo** pelo comando `/painel`, sem mexer em arquivos nem reiniciar o servidor. Todas as mensagens podem ser editadas no `messages.yml`.

## ⬇️ Download

Baixe o `.jar` mais recente na página de **[Releases](../../releases/latest)** → seção *Assets* → `UltraCore-x.y.z.jar`.

## Versões suportadas

Testado automaticamente ligando um **servidor Paper real** de cada versão com o plugin (workflow *Compatibilidade Paper*):

**1.21, 1.21.1, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1.1, 26.1.2, 26.2 e 26.3**

- Paper 1.21.x usa **Java 21**; Paper 26.x usa **Java 25**. O mesmo `.jar` serve para todas.
- A cada atualização do código, o GitHub descobre sozinho as versões novas do Paper e testa de novo, **sem Vault e com Vault**.
- Se algum módulo falhar numa versão futura, ele é desligado sozinho e o resto do plugin continua funcionando.
- Os testes ligam o servidor e usam comandos pelo console. Recursos que dependem de um jogador dentro do jogo (menus, chat, scoreboard na tela) não são testados automaticamente.

## Plugins necessários

**Nenhum.** O UltraCore funciona sozinho: é só colocar o `.jar` na pasta `plugins/`.

| Você precisa ter | Versão |
|---|---|
| Servidor **Paper** (ou fork, como Purpur) | 1.21 até 26.x |
| **Java** | 21 para Paper 1.21.x · 25 para Paper 26.x |

### Plugins opcionais (recomendados)

| Plugin | Para quê | Obrigatório? |
|---|---|---|
| [LuckPerms](https://luckperms.net/) | Criar grupos (VIP, Mod, Admin) e dar as permissões `ultracore.vip`, `ultracore.staff`, `ultracore.admin` e as tags do chat. Sem ele, só dá pra separar "jogador" e "OP". | Não, mas **muito recomendado** |
| [Vault](https://www.spigotmc.org/resources/vault.34315/) | Deixar outros plugins (lojas, leilão, ranks pagos...) usarem o dinheiro do UltraCore. Se o Vault estiver instalado, o UltraCore se registra nele **sozinho**. | Só se algum outro plugin pedir |
| [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi) | Usar placeholders `%...%` de outros plugins no scoreboard, na tab, no chat e nos anúncios. Detectado automaticamente. | Não |

**Atenção:**
- **Outra economia + Vault:** se você tiver outro plugin de economia (ex: EssentialsX Economy), só um deve cuidar do dinheiro. Desligue o módulo **Economia** no `/painel` ou a economia do outro plugin.
- **EssentialsX:** dá pra usar junto, mas os dois têm comandos iguais (`/home`, `/spawn`, `/tpa`, `/money`...). Use só um dos dois, ou desligue no `/painel` os módulos do UltraCore que já existem no Essentials.

## Como instalar

1. Baixe o `.jar` na página de **[Releases](../../releases/latest)**.
2. Coloque na pasta `plugins/` do servidor.
3. Inicie o servidor, entre como OP e digite **`/painel`**.

## O painel (`/painel`)

- **Clique esquerdo** em um módulo → liga/desliga na hora.
- **Clique direito** → abre as configurações do módulo:
  - Sim/Não: clique para alternar.
  - Números: esquerdo aumenta, direito diminui, Shift altera 10x.
  - Textos: clique e digite o novo valor no chat (`cancelar` para desistir, `vazio` para deixar em branco).
  - Listas (linhas do scoreboard, anúncios...): esquerdo adiciona linha, direito remove a última, Shift+direito limpa.
- Botões: **Recarregar config**, **Ativar todos**, **Desativar todos**.

Pelo console: `/painel lista`, `/painel ativar <módulo>`, `/painel desativar <módulo>`, `/painel reload`.

## Arquivos de configuração

Ficam em `plugins/UltraCore/`:

| Arquivo | O que tem |
|---|---|
| `config.yml` | Prefixo das mensagens e todas as opções dos módulos (o mesmo que o `/painel` edita) |
| `messages.yml` | **Todas as mensagens** que os jogadores veem. Edite e use `/painel reload` |
| `shop.yml` | Itens e preços da loja |
| `kits.yml` | Kits (crie pelo jogo com `/createkit`) |
| `warps.yml`, `spawn.yml` | Warps e spawn |
| `bans.yml` | Banimentos e bans de IP |
| `players/` | Dados de cada jogador (saldo, homes, histórico...) |

Textos aceitam cores `&a`, `&l`... e hex `&#FF8800`.

## Módulos e comandos

| Módulo | O que faz | Comandos |
|---|---|---|
| Spawn | Spawn do servidor, spawn na 1ª entrada e ao renascer | `/spawn`, `/setspawn` |
| Homes | Casas por jogador, com limite configurável | `/sethome`, `/home`, `/delhome`, `/homes` |
| Warps | Pontos públicos com menu visual | `/warp`, `/warps`, `/setwarp`, `/delwarp` |
| TPA | Pedidos de teleporte | `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, `/tptoggle` |
| Back | Voltar ao local anterior ou da morte | `/back` |
| Teleporte Aleatório | Local aleatório e seguro, com cooldown | `/rtp` |
| Economia | Dinheiro, pagamentos, ranking, prêmio por abate, integração com Vault | `/money`, `/pay`, `/baltop`, `/eco` |
| **Loja** 🆕 | Loja em menu para comprar e vender itens | `/loja`, `/vender`, `/vender tudo` |
| Kits | Kits com cooldown ou uso único, kit inicial | `/kit`, `/createkit`, `/delkit` |
| **Recompensas** 🆕 | Recompensa diária com bônus por dias seguidos, tempo de jogo | `/daily`, `/tempo` |
| Chat | Formato, tags por permissão, anti-spam, anti-CAPS, filtro | `/staffchat`, `/clearchat`, `/broadcast` |
| Mensagens Privadas | Conversas privadas + socialspy | `/msg`, `/r`, `/socialspy` |
| **Apelidos** 🆕 | Apelido no chat e na TAB, com cores para VIP | `/nick` |
| Entrada e Saída | Mensagens de entrada/saída, boas-vindas, título | — |
| Anúncios Automáticos | Mensagens periódicas no chat | — |
| Scoreboard | Placar lateral com placeholders | — |
| Tablist | Cabeçalho e rodapé da TAB | — |
| MOTD | Descrição na lista de servidores (desligado por padrão) | — |
| Moderação | Ban, tempban, ban por IP, kick, mute com tempo, **avisos com punição automática**, **histórico**, **denúncias** | `/ban`, `/tempban`, `/unban`, `/banip`, `/unbanip`, `/kick`, `/mute`, `/unmute`, `/warn`, `/limparavisos`, `/historico`, `/report` |
| Ferramentas da Staff | Voo, cura, gamemode, deus, vanish, invsee... | `/fly`, `/heal`, `/feed`, `/gm`, `/god`, `/vanish`, `/invsee`, `/ecsee`, `/clearinv`, `/speed`, `/tphere` |
| Utilidades | Bancada, enderchest, chapéu, ping, lixeira, regras | `/craft`, `/ec`, `/hat`, `/ping`, `/lixo`, `/regras` |
| Proteção do Mundo | Creeper/TNT sem quebrar blocos, fogo, enderman, phantoms, keep inventory, clima, queda, plantações | — |
| AFK | Detecta ausentes, tag [AFK], expulsão opcional | `/afk` |
| Dormir | % de jogadores necessários para pular a noite | — |
| Colheita Fácil | Botão direito colhe e replanta | — |
| Mortes | Coordenadas da morte, cabeça do jogador em PvP | — |
| Limpeza de Itens | Remove itens do chão com aviso | `/limparitens` |
| **Discord** 🆕 | Envia punições, denúncias, staffchat e mais para um canal do Discord (desligado por padrão) | — |
| **Bot do WhatsApp** 🆕 | Envia os mesmos avisos para o seu bot do WhatsApp, além do **feed do jogo** (chat, conquistas e mortes); com a ponte, o bot também executa comandos no servidor (desligado por padrão) | — |

A maioria dos comandos também aceita apelidos em português: `/casa`, `/pagar`, `/voar`, `/curar`, `/avisar`, `/denunciar`, `/diario`, `/apelido`...

Tempos nos comandos aceitam `30s`, `10m`, `2h`, `7d`, `1w` (ex: `/tempban Fulano 7d hack`, `/mute Fulano 30m spam`).

### Loja

- `/loja` abre o menu. **Esquerdo** compra, **Shift+Esquerdo** compra 10x, **Direito** vende, **Shift+Direito** vende todos.
- `/vender` vende o item da mão; `/vender tudo` vende tudo o que a loja aceita.
- Só são aceitos itens "puros" (sem nome ou encantamento), para ninguém vender item especial por engano.
- **Admins:** segure um item e use `/loja add <preço de compra> <preço de venda>` (a quantidade na mão vira o tamanho do pacote; preço `0` desativa compra ou venda). `/loja remover` tira o item da mão da loja. Ou edite o `shop.yml`.

### Avisos e punição automática

Cada `/warn` soma um aviso. Com as opções padrão (ajustáveis no `/painel` → Moderação):
- **3 avisos** → mute automático de 60 minutos;
- **5 avisos** → ban automático de 24 horas.

`/historico <jogador>` mostra todas as punições (bans, mutes, kicks, avisos) com data, duração, quem aplicou e o motivo. `/limparavisos <jogador>` zera os avisos.

### Discord

1. No Discord: configurações do canal → **Integrações** → **Webhooks** → **Novo webhook** → **Copiar URL**.
2. No jogo: `/painel` → **Discord** (clique direito) → **URL do webhook** → cole a URL no chat.
3. Ligue o módulo e escolha o que enviar (punições, denúncias, staffchat, entrada/saída, chat).

A URL do webhook funciona como uma senha: não mostre para ninguém.

### Bot do WhatsApp (Baileys)

Tem uma ponte pronta para bots de WhatsApp feitos em Node.js com Baileys, em [`integracoes/whatsapp-bot`](integracoes/whatsapp-bot):
- **Servidor → WhatsApp:** punições, denúncias, staffchat, "servidor ligou" etc. chegam num grupo; o feed do jogo (chat, conquistas, mortes) pode ir para outro grupo.
- **WhatsApp → Servidor:** `!online`, `!anunciar <texto>` e `!cmd <comando>` (só admins) rodam no servidor pelo RCON.

O passo a passo está no [README da ponte](integracoes/whatsapp-bot/README.md).

### Placeholders (scoreboard, tab, anúncios, entrada/saída)

`{player}` `{displayname}` `{money}` `{online}` `{max}` `{kills}` `{deaths}` `{ping}` `{world}` `{x}` `{y}` `{z}` — e, com o **PlaceholderAPI**, qualquer `%placeholder%` dele.

## Permissões

| Permissão | Quem tem por padrão | Inclui |
|---|---|---|
| `ultracore.player` | todos | spawn, home, warp, tpa, back, rtp, money, pay, baltop, kit, msg, ping, afk, regras, lixo, loja, vender, daily, tempo, report |
| `ultracore.vip` | ninguém (dê pelo LuckPerms) | craft, enderchest, hat, cores no chat, fly, nick com cores, tag [VIP] |
| `ultracore.staff` | OP | kick, mute, ban, warn, histórico, ver denúncias, staffchat, vanish, invsee, gm, heal, feed, tphere... |
| `ultracore.admin` | OP | tudo + `/painel`, setspawn, setwarp, eco, createkit, banip, editar a loja, god, speed... |

Outras úteis: `ultracore.homes.unlimited`, `ultracore.bypass.delay`, `ultracore.bypass.cooldown`, `ultracore.kit.<nome>`, `ultracore.warp.<nome>`, `ultracore.tag.<dono|admin|mod|vip>`, `ultracore.nick.others`, `ultracore.report.see`.

## Para desenvolvedores

```
src/main/java/com/ultracore/
├── UltraCore.java          # classe principal, registra os módulos
├── module/                 # sistema de módulos (Module, Setting, ModuleManager)
├── gui/                    # menus do /painel
├── core/                   # teleporte com delay, entrada pelo chat, /painel
├── data/                   # dados dos jogadores (plugins/UltraCore/players/)
├── hooks/                  # integração opcional com o Vault
├── util/                   # cores, mensagens (Lang), itens, tempo, arquivos
└── modules/                # um arquivo por módulo
src/main/resources/
├── plugin.yml              # comandos e permissões
└── messages.yml            # mensagens padrão
integracoes/whatsapp-bot/   # ponte para bots de WhatsApp (Node.js + Baileys)
```

Compilar: `mvn package` (Java 21 + Maven). O `.jar` sai em `target/`.

Para criar um módulo novo: crie uma classe em `modules/` que estende `Module`, declare as configurações (`bool`, `integer`, `text`, `list`...) e os comandos no construtor, registre em `UltraCore.onEnable()`, adicione os comandos no `plugin.yml` e as mensagens no `messages.yml`. Ele aparece sozinho no painel.

Para lançar uma versão nova, aumente `<version>` no `pom.xml`: o GitHub compila e publica a Release automaticamente.
