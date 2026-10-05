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
  - **Restaurar padrão** 🆕 (balde de água): volta todas as opções daquele módulo para o valor original. Clique duas vezes para confirmar. Não liga nem desliga o módulo e não mexe em dados salvos (loja, kits, warps...).
  - **Comandos** 🆕 (bloco de comando): liga/desliga **cada comando** do módulo separadamente. Ex: em **Utilidades**, desligar só o `/craft` e deixar o resto. Comando desligado responde "Este comando está desativado".
  - **Abrir editor** 🆕 (bigorna): aparece em módulos com editor visual, como a **Loja**.
- Botões: **Recarregar config**, **Ativar todos**, **Desativar todos**.

Pelo console: `/painel lista`, `/painel ativar <módulo>`, `/painel desativar <módulo>`, `/painel padrao <módulo>`, `/painel comando <comando> <ativar|desativar>`, `/painel reload`.

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
| Chat | Formato, anti-spam, anti-CAPS, filtro | `/staffchat`, `/clearchat`, `/broadcast` |
| Mensagens Privadas | Conversas privadas + socialspy | `/msg`, `/r`, `/socialspy` |
| **Apelidos** 🆕 | Apelido no chat e na TAB, com cores para VIP | `/nick` |
| **Tags** 🆕 | Tags como [VIP], [Admin] no chat e na TAB; o jogador escolhe a sua | `/tag` |
| **Diversão (só OP)** 🆕 | Raio, explosão, foguete, fogos, congelar, susto... Sem nome = você; `todos` = todo mundo | `/trovao`, `/explodir`, `/foguete`, `/fogos`, `/queimar`, `/congelar`, `/assustar`, `/arremessar`, `/falsoop`, `/fazerfalar`, `/chuvadeitens`, `/girar` |
| Entrada e Saída | Mensagens de entrada/saída, boas-vindas, título, **entrada silenciosa** 🆕 | `/silentjoin` |
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
- **Editor visual (admins)** 🆕: `/loja editar`, o botão **Editar loja** (bigorna) dentro da `/loja`, ou `/painel` → Loja → **Abrir editor**.
  - **Adicionar:** com o editor aberto, clique em um item do **seu inventário**. Abre a tela do item com:
    - **Preço de compra** e **preço de venda**: botões vermelhos/verdes de -100, -10, -1, +1, +10, +100, ou clique no lingote/esmeralda e digite o valor exato no chat (ex: `12.5`). `0` = desativado.
    - **Tamanho do pacote**: -16, -4, -1, +1, +4, +16, ou digite.
    - **SALVAR**, **Remover da loja** (clique duas vezes) ou **Voltar sem salvar**.
  - **Editar:** clique no item na lista do editor.
- **Pelo chat (admins):** segure um item e use `/loja add <preço de compra> <preço de venda>` (a quantidade na mão vira o tamanho do pacote; preço `0` desativa compra ou venda). `/loja remover` tira o item da mão da loja. Ou edite o `shop.yml`.

### Tags (chat e TAB)

- `/tag` abre um menu com as tags. As liberadas aparecem como etiqueta; clique para usar. Também dá para escolher **Tag automática** (usa a melhor que você tiver) ou **Sem tag**.
- `/tag <id>`, `/tag off`, `/tag auto`, `/tag lista` fazem o mesmo pelo chat.
- **Quem tem cada tag:** a permissão `ultracore.tag.<id>` (ex: dê `ultracore.tag.vip` para o grupo VIP no LuckPerms) **ou** um admin dá direto, sem LuckPerms:
  - `/tag dar <jogador> <id>` e `/tag tirar <jogador> <id>` (funciona com o jogador offline).
- **Criar tags:** `/tag criar <id> <texto>` (ex: `/tag criar lenda &5[Lenda]`), `/tag apagar <id>`. Ou no `/painel` → **Tags** → **Tags** (formato `id:texto`). Aceita cores `&` e hex `&#ff8800`.
- A ordem da lista é a prioridade da tag automática (a primeira que o jogador tiver).
- No `/painel` dá para ligar/desligar a tag no chat e na TAB separadamente. No formato do chat a tag é o `{prefix}` (ou `{tag}`); em scoreboard/TAB/anúncios use `{tag}`.
- Tags que já vêm prontas: `dono`, `admin`, `mod`, `vip`, `construtor`, `youtuber`. OP tem todas.

A TAB junta tudo: `[AFK]` + tag + apelido. Os nomes na TAB não são reordenados pela tag.

### Diversão (só OP)

Todos aceitam `[jogador]` (sem nome = em você mesmo) ou `todos`. Permissão `ultracore.diversao`, que **só OP tem** (não vem junto com `ultracore.admin`).

| Comando | O que faz |
|---|---|
| `/trovao [jogador]` (`/raio`) | Cai um raio. No `/painel` dá para deixar o raio sem dano. |
| `/explodir [jogador] [força 1-10]` | Explosão. Por padrão **não quebra blocos** (configurável). |
| `/foguete [jogador]` | Lança para o alto. Sem dano de queda. |
| `/fogos [jogador]` | Fogos de artifício coloridos em volta. |
| `/queimar [jogador] [segundos]` | Põe fogo. |
| `/congelar [jogador]` | Congela no lugar (de novo para descongelar). |
| `/assustar [jogador]` | Som e tela assustadora do guardião, só para o alvo. |
| `/arremessar [jogador]` | Joga para longe numa direção aleatória. Sem dano de queda. |
| `/falsoop [jogador]` | Mostra a mensagem falsa "Made X a server operator". |
| `/fazerfalar <jogador> <mensagem>` | Faz a pessoa falar no chat (comandos não são permitidos). |
| `/chuvadeitens [jogador]` | Chuva de diamantes, esmeraldas... só enfeite, ninguém pega. |
| `/girar [jogador]` | Faz a pessoa girar. |

Cada um pode ser desligado no `/painel` → Diversão → **Comandos**.

### Entrada silenciosa (`/silentjoin`)

- Quem tem `ultracore.silentjoin` (staff/admin) digita `/silentjoin` (ou `/sj`) para ligar/desligar. Fica salvo.
- Ligado: **ninguém vê** a mensagem de entrada/saída, nem a de novato, e o aviso de entrada/saída **não vai** para o Discord/WhatsApp.
- A staff com `ultracore.silentjoin.see` recebe um aviso discreto (`[Silencioso] Fulano entrou`); dá para desligar isso no `/painel` → Entrada e Saída.
- Para sumir também da TAB e do mundo, use o `/vanish`. 🆕 O **vanish continua ligado** quando você sai e entra de novo (entra invisível e sem mensagem de entrada/saída, nem no Discord/WhatsApp). `/vanish` de novo para aparecer.

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
- **Hospedagem gratuita (sem RCON nem porta aberta):** ligue **Comandos remotos** no módulo *Bot do WhatsApp*. O plugin busca os comandos no bot a cada poucos segundos (`GET {url}/comandos`), executa no console e devolve a saída (`POST {url}/resultado`). Toda conexão parte do servidor, então funciona em qualquer host que permita acessar a internet.

O passo a passo está no [README da ponte](integracoes/whatsapp-bot/README.md).

### Placeholders (scoreboard, tab, anúncios, entrada/saída)

`{player}` `{displayname}` `{tag}` `{money}` `{online}` `{max}` `{kills}` `{deaths}` `{ping}` `{world}` `{x}` `{y}` `{z}` — e, com o **PlaceholderAPI**, qualquer `%placeholder%` dele.

## Permissões

| Permissão | Quem tem por padrão | Inclui |
|---|---|---|
| `ultracore.player` | todos | spawn, home, warp, tpa, back, rtp, money, pay, baltop, kit, msg, ping, afk, regras, lixo, loja, vender, daily, tempo, report |
| `ultracore.vip` | ninguém (dê pelo LuckPerms) | craft, enderchest, hat, cores no chat, fly, nick com cores, tag [VIP] |
| `ultracore.staff` | OP | kick, mute, ban, warn, histórico, ver denúncias, staffchat, vanish, invsee, gm, heal, feed, tphere... |
| `ultracore.admin` | OP | tudo + `/painel`, setspawn, setwarp, eco, createkit, banip, editar a loja, gerenciar tags, god, speed... |

Outras úteis: `ultracore.homes.unlimited`, `ultracore.bypass.delay`, `ultracore.bypass.cooldown`, `ultracore.kit.<nome>`, `ultracore.warp.<nome>`, `ultracore.tag.<id>` (cada tag), `ultracore.tags.admin` (/tag dar, tirar, criar, apagar), `ultracore.nick.others`, `ultracore.report.see`, `ultracore.silentjoin` e `ultracore.silentjoin.see` (staff).

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
