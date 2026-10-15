# UltraCore — plugin tudo-em-um para servidores de Minecraft

Plugin para **Paper 1.21 até 26.x** (e forks como Purpur) que junta num só lugar os recursos mais usados em servidores: teleportes, economia, loja, kits, chat, moderação, ferramentas da staff, recompensas e muito mais.

Cada recurso é um **módulo** que pode ser **ligado, desligado e configurado dentro do jogo** pelo comando `/painel`, sem mexer em arquivos nem reiniciar o servidor. Todas as mensagens podem ser editadas no `messages.yml`.

## ✨ Novidades da 2.6 a 2.9

- **15 brincadeiras novas na Diversão** e o menu **`/diversao`** (escolhe o alvo e clica na brincadeira): bigorna, chuva de galinhas, bêbado, cegar, levitar, terremoto, tornado, gigante, anão, tamanho, explosão falsa, amor, chuva de TNT, meteoro, inverter, pipoca e abduzir.
- **Ligar/desligar o PvP**: botão **PvP** na tela inicial do `/painel`, `/pvp ligar` / `/pvp desligar` ou `/painel pvp ativar|desativar`. Também dá para deixar mundos sem PvP e liberar o PvP pessoal (`/pvp eu`). Veja [PvP](#pvp).

- **`/troca`**: troca segura de itens e dinheiro com quem está **perto** de você (ou agache e clique com o botão direito no jogador). Veja [Troca](#troca-troca).
- **Registro de mortes**: botão **Mortes** na tela inicial do `/painel` (ou `/mortes [jogador]`): quem morreu, onde, quando, a causa e os itens que caíram. Dá para ir até o local e **devolver os itens** se algo bugar. Veja [Mortes](#registro-de-mortes).
- **Limpeza dos itens do chão**: `/limparitens desligar` / `ligar` / `status`, opção para **proteger itens novos** (ex: o que caiu de quem acabou de morrer) e itens com nome.
- **Correções**: teleporte montado (cavalo, barco) agora funciona; o teleporte com contagem é cancelado se você levar dano; kits de uso único não podem mais ser pegos de novo se o servidor cair; valores absurdos em `/ban`, `/loja add` e na economia são recusados; aviso quando o preço de venda da loja é maior que o de compra (dinheiro infinito); menus mais leves.

## ✨ Novidades da 2.0

- **`/menu`**: central do servidor para os jogadores, com perfil (dinheiro, abates, tempo de jogo) e botões para spawn, homes, warps, teleporte, loja, kits, recompensa diária, rankings, tags e preferências.
- **Menus novos**: homes (`/homes`: clique para ir, criar e apagar), kits (`/kit`: o que vem em cada kit e quando pode pegar), teleporte (`/tpa`: cabeças dos jogadores online), warps e **rankings** (`/top`: mais ricos, mais abates, mais tempo de jogo).
- **`/preferencias`**: cada jogador escolhe se recebe pedidos de TPA, mensagens privadas e o som de menção (fica salvo). Novo `/msgtoggle`.
- **Cooldowns** no `/spawn` e no `/tpa` (30 segundos por padrão) e opcionais no `/home`, `/warp` e `/back`, ajustáveis no `/painel`.
- **Menções no chat**: escrever o nome de alguém destaca `@Nome` e avisa a pessoa com som.
- **Desligar o Nether e/ou o The End** pelo botão **Dimensões** na tela inicial do `/painel` (ou `/painel nether desativar`, `/painel end desativar`): portais param de funcionar e quem estiver lá volta para o spawn (admins com `ultracore.bypass.dimension` continuam entrando).
- **Painel por categorias** (Teleporte, Economia, Chat, Visual, Staff, Mundo, Diversão, Integrações).
- **Diversão 100% secreta**: além de não avisar o alvo, quem morre por `/trovao`, `/explodir` etc. não gera mensagem de morte no chat nem no Discord/WhatsApp.
- **Modo fantasma** (`/vanish` ou `/fantasma`): além de sumir do mundo e da TAB, não aparece mensagem de morte quando você mata ou morre, não conta como online (lista de servidores e `{online}`), não pega itens, mobs ignoram, conquistas não são anunciadas, não atrapalha o pular a noite, e o chat fica bloqueado para você não se entregar (comece com `!` para falar). Continua ao relogar. Cada parte liga/desliga no `/painel` → Staff → Ferramentas da Staff.
- **Testado de verdade**: a cada versão, servidores Paper reais de todas as versões sobem com o plugin, e dois jogadores-robô entram, usam os comandos e clicam nos menus. A Release só é publicada se tudo passar.

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
3. Inicie o servidor, entre como OP e digite **`/painel`**. Para os jogadores, o comando principal é **`/menu`**.

Para atualizar: troque o `.jar` e **reinicie** o servidor (não use `/reload`). Suas configurações são mantidas.

## O painel (`/painel`)

O painel abre com as **categorias** (Teleporte, Economia, Chat e Social, Visual, Staff, Mundo, Diversão, Integrações). Dentro de cada uma:

- **Clique esquerdo** em um módulo → liga/desliga na hora.
- **Clique direito** → abre as configurações do módulo:
  - Sim/Não: clique para alternar.
  - Números: esquerdo aumenta, direito diminui, Shift altera 10x.
  - Textos: clique e digite o novo valor no chat (`cancelar` para desistir, `vazio` para deixar em branco).
  - Listas (linhas do scoreboard, anúncios...): esquerdo adiciona linha, direito remove a última, Shift+direito limpa.
  - **Restaurar padrão** 🆕 (balde de água): volta todas as opções daquele módulo para o valor original. Clique duas vezes para confirmar. Não liga nem desliga o módulo e não mexe em dados salvos (loja, kits, warps...).
  - **Comandos** 🆕 (bloco de comando): liga/desliga **cada comando** do módulo separadamente. Ex: em **Utilidades**, desligar só o `/craft` e deixar o resto. Comando desligado responde "Este comando está desativado".
  - **Abrir editor** 🆕 (bigorna): aparece em módulos com editor visual, como a **Loja**.
- Botões: **Recarregar config** (na tela principal), **Ativar/Desativar todos desta categoria** (dentro de cada categoria).

Pelo console: `/painel lista`, `/painel ativar <módulo>`, `/painel desativar <módulo>`, `/painel padrao <módulo>`, `/painel comando <comando> <ativar|desativar>`, `/painel reload`.

## Menus dos jogadores

| Comando | O que abre |
|---|---|
| `/menu` (`/servidor`) | Central do servidor: perfil e botões para tudo abaixo (só aparecem os recursos ligados e que o jogador pode usar). Admins veem também o botão do painel. |
| `/homes` | Suas homes: **clique** para teleportar, **Shift + clique direito** duas vezes para apagar, botão **Criar home aqui**. |
| `/kit` | Kits com os itens de cada um, a recarga e se já pode pegar. Clique para pegar. (`/kit lista` mostra no chat.) |
| `/tpa` | Cabeças dos jogadores online: **esquerdo** pede para ir até ele, **direito** pede para ele vir até você. |
| `/warps` | Warps do servidor. |
| `/top` (`/ranking`) | Rankings: mais ricos, mais abates e mais tempo de jogo. |
| `/preferencias` | Liga/desliga: pedidos de TPA, mensagens privadas, som de menção e (staff) entrada silenciosa. |
| `/tag` | Escolher a tag. |

## Cooldowns

| Comando | Padrão | Onde muda |
|---|---|---|
| `/spawn` | 30 s | `/painel` → Teleporte → Spawn → Cooldown |
| `/tpa` e `/tpahere` | 30 s (entre um pedido e outro) | `/painel` → Teleporte → TPA → Cooldown |
| `/home`, `/warp`, `/back` | desligado (0) | `/painel` → Teleporte → módulo → Cooldown |
| `/rtp` | 5 min | `/painel` → Teleporte → Teleporte Aleatório |

O cooldown só começa quando o teleporte acontece (se a pessoa se mexer e cancelar, não perde a vez). Quem tem `ultracore.bypass.cooldown` (admins/OP) não tem cooldown.

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
| TPA | Pedidos de teleporte, com menu e cooldown | `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, `/tptoggle` |
| Back | Voltar ao local anterior ou da morte (continua valendo depois de reiniciar o servidor); ver onde morreu | `/back`, `/ultimamorte [jogador]` |
| Teleporte Aleatório | Local aleatório e seguro, com cooldown | `/rtp` |
| Economia | Dinheiro, pagamentos, ranking, prêmio por abate, integração com Vault | `/money`, `/pay`, `/baltop`, `/eco` |
| **Loja** 🆕 | Loja em menu para comprar e vender itens | `/loja`, `/vender`, `/vender tudo` |
| **Troca** 🆕 | Troca segura de itens e dinheiro com quem está perto | `/troca <jogador>`, `/troca aceitar`, `/troca recusar` |
| Kits | Kits com cooldown ou uso único, kit inicial | `/kit`, `/createkit`, `/delkit` |
| **Recompensas** 🆕 | Recompensa diária com bônus por dias seguidos, tempo de jogo | `/daily`, `/tempo` |
| Chat | Formato, anti-spam, anti-CAPS, filtro, **menções @nome** 🆕 | `/staffchat`, `/clearchat`, `/broadcast` |
| Mensagens Privadas | Conversas privadas + socialspy; cada um pode desligar | `/msg`, `/r`, `/socialspy`, `/msgtoggle` |
| **Menus dos Jogadores** 🆕 | Central do servidor, rankings e preferências | `/menu`, `/top`, `/preferencias` |
| **Apelidos** 🆕 | Apelido no chat e na TAB, com cores para VIP | `/nick` |
| **Tags** 🆕 | Tags como [VIP], [Admin] no chat e na TAB; o jogador escolhe a sua | `/tag` |
| **Diversão (só OP)** 🆕 | Raio, explosão, foguete, fogos, congelar, susto... Sem nome = você; `todos` = todo mundo | `/trovao`, `/explodir`, `/foguete`, `/fogos`, `/queimar`, `/congelar`, `/assustar`, `/arremessar`, `/falsoop`, `/fazerfalar`, `/chuvadeitens`, `/girar`, `/bigorna`, `/chuvadegalinhas`, `/bebado`, `/cegar`, `/levitar`, `/terremoto`, `/tornado`, `/gigante`, `/anao`, `/tamanho`, `/explosaofalsa`, `/amor`, `/chuvadetnt`, `/meteoro`, `/inverter`, `/pipoca`, `/abduzir`, menu `/diversao` |
| Entrada e Saída | Mensagens de entrada/saída, boas-vindas, título, **entrada silenciosa** 🆕 | `/silentjoin` |
| Anúncios Automáticos | Mensagens periódicas no chat | — |
| Scoreboard | Placar lateral com placeholders | — |
| Tablist | Cabeçalho e rodapé da TAB | — |
| MOTD | Descrição na lista de servidores (desligado por padrão) | — |
| Moderação | Ban, tempban, ban por IP, kick, mute com tempo, **avisos com punição automática**, **histórico**, **denúncias** | `/ban`, `/tempban`, `/unban`, `/banip`, `/unbanip`, `/kick`, `/mute`, `/unmute`, `/warn`, `/limparavisos`, `/historico`, `/report` |
| Ferramentas da Staff | Voo, cura, gamemode, deus, vanish, invsee... | `/fly`, `/heal`, `/feed`, `/gm`, `/god`, `/vanish`, `/esconder`, `/escondernick`, `/invsee`, `/ecsee`, `/clearinv`, `/speed`, `/tphere` |
| Utilidades | Bancada, enderchest, chapéu, ping, lixeira, regras, luz (visão noturna que continua ao morrer, beber leite e relogar) | `/craft`, `/ec`, `/hat`, `/ping`, `/lixo`, `/regras`, `/luz` |
| Proteção do Mundo | Creeper/TNT sem quebrar blocos, fogo, enderman, phantoms, keep inventory, clima, queda, plantações, **desligar o Nether e o The End** 🆕 | — |
| AFK | Detecta ausentes, tag [AFK], expulsão opcional | `/afk` |
| Dormir | % de jogadores necessários para pular a noite | — |
| Colheita Fácil | Botão direito colhe e replanta | — |
| Mortes | Coordenadas da morte, cabeça do jogador em PvP | — |
| Limpeza de Itens | Remove itens do chão com aviso (automática pode ser desligada; protege itens novos e com nome) | `/limparitens`, `/limparitens ligar\|desligar\|status` |
| **Discord** 🆕 | Envia punições, denúncias, staffchat e mais para um canal do Discord (desligado por padrão) | — |
| **Bot do WhatsApp** 🆕 | Envia os mesmos avisos para o seu bot do WhatsApp, além do **feed do jogo** (chat, conquistas e mortes); com a ponte, o bot também executa comandos no servidor (desligado por padrão) | — |

A maioria dos comandos também aceita apelidos em português: `/casa`, `/pagar`, `/voar`, `/curar`, `/avisar`, `/denunciar`, `/diario`, `/apelido`...

Tempos nos comandos aceitam `30s`, `10m`, `2h`, `7d`, `1w` (ex: `/tempban Fulano 7d hack`, `/mute Fulano 30m spam`).

### PvP

- **Botão PvP** (espada) na tela inicial do `/painel`: um clique liga ou desliga o PvP do servidor todo. Também: `/pvp ligar`, `/pvp desligar` (permissão `ultracore.pvp.admin`, só OP) ou `/painel pvp ativar|desativar`. Todo mundo é avisado no chat.
- Com o PvP desligado ninguém machuca outro jogador: espada, machado, flecha, tridente, poções de dano/veneno, lobo domesticado, TNT acesa por jogador, fogo e vara de pescar. Mobs continuam normais.
- `/pvp` mostra se está ligado.
- No `/painel` → Mundo → PvP:
  - **Mundos sem PvP**: lista de mundos onde nunca tem PvP (ex: o mundo do spawn).
  - **PvP pessoal** (desligado por padrão): cada jogador usa `/pvp eu` para desligar o próprio PvP (não apanha e não bate). Quem brigou há menos de 15 segundos não pode desligar, para não fugir da luta.

### Registro de mortes

Botão **Mortes** (caveira) na tela inicial do `/painel`, ou `/mortes` (`/mortes <jogador>` mostra só as dele). Só OP (`ultracore.mortes`).

- Cada morte mostra **quem**, **quando**, **onde** (coordenadas e mundo), a **causa** e quantos itens caíram. Fica salvo no `deaths.yml` e continua depois de reiniciar.
- **Clique esquerdo**: vai até o local. **Clique direito**: vê os itens perdidos e pode **devolver tudo** para o jogador (precisa estar online; dois cliques para confirmar; só dá para devolver uma vez). **Shift + clique**: mostra só as mortes daquele jogador. **Buscar jogador**: digita o nome no chat.
- No `/painel` → Mundo → Mortes: ligar/desligar o registro, guardar ou não os itens e quantas mortes guardar (200 por padrão).

### Troca (`/troca`)

1. Chegue perto do outro jogador (até **10 blocos**, ajustável) e use `/troca <nome>`, ou **agache e clique com o botão direito** nele.
2. Ele aceita clicando em **[ACEITAR]** no chat (ou `/troca aceitar`). `/troca <seu nome>` de volta também aceita.
3. Abre um menu: cada um coloca os itens **do seu lado** (esquerda = quem pediu, direita = quem aceitou) e, se quiser, dinheiro no lingote de ouro.
4. Os dois clicam em **confirmar**. A troca acontece depois de **3 segundos**. Qualquer mudança nesse tempo desfaz as confirmações, o que evita golpe de última hora.

Fechar o menu, sair, morrer, se afastar ou clicar em **Cancelar** devolve tudo para os donos. Ninguém consegue mexer no lado do outro. Quem está no criativo não troca (dá para mudar). Cada troca fica registrada no console. No `/painel` → Economia → Troca: distância, tempo do pedido, cooldown, agachar + clique, dinheiro e bloqueio do criativo.

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

Todos aceitam `[jogador]` (sem nome = em você mesmo) ou `todos`. São **secretos**: só quem usou recebe a confirmação; o alvo não é avisado de nada, e se alguém morrer por causa de uma brincadeira, **a mensagem de morte não aparece** no chat nem no Discord/WhatsApp. Permissão `ultracore.diversao`, que **só OP tem** (não vem junto com `ultracore.admin`).

| Comando | O que faz |
|---|---|
| `/trovao [jogador]` (`/raio`) | Cai um raio (não põe fogo no mapa). No `/painel` dá para deixar o raio sem dano. |
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
| `/bigorna [jogador]` | Uma bigorna cai na cabeça (3 corações; não vira bloco no mapa). |
| `/chuvadegalinhas [jogador]` | Chuva de galinhas (somem sozinhas depois de 8s). |
| `/bebado [jogador]` | Tela girando, lento e andando torto por 15s. |
| `/cegar [jogador]` | Cegueira e escuridão por 10s. |
| `/levitar [jogador]` | Flutua por 5s (sem dano de queda). |
| `/terremoto [jogador]` | Treme o chão de todo mundo num raio de 15 blocos. |
| `/tornado [jogador]` | Gira e sobe como um tornado (sem dano de queda). |
| `/gigante [jogador]` / `/anao [jogador]` | Fica gigante ou pequeno. De novo volta ao normal. Fica salvo até voltar. |
| `/tamanho <jogador\|todos> <0.1 a 10\|normal>` | Tamanho exato. |
| `/explosaofalsa [jogador]` | Barulho e fumaça de explosão, sem dano. |
| `/amor [jogador]` | Corações em volta. |
| `/chuvadetnt [jogador]` | 6 TNTs caem do céu. Não quebram o mapa (a não ser que "Explosão quebra blocos" esteja ligado). |
| `/meteoro [jogador]` | Bola de fogo cai do céu. Não quebra o mapa nem põe fogo. |
| `/inverter [jogador]` | Vira a pessoa de costas. |
| `/pipoca [jogador]` | Pula sem parar como pipoca. |
| `/abduzir [jogador]` | Um feixe de luz e a pessoa sobe como se fosse abduzida (sem dano de queda). |
| `/diversao` | **Menu**: escolha o jogador (ou todos) e clique na brincadeira. |

Cada um pode ser desligado no `/painel` → Diversão → **Comandos**.

### Modo oculto (`/esconder`)

Seu **personagem continua aparecendo no mundo** (dá para ver, bater, conversar perto), mas:

- você **some da TAB** de todo mundo, inclusive dos OPs (na sua própria TAB você sempre aparece);
- **não aparece no chat**: sem mensagem de entrada/saída, de morte (matando ou morrendo) e de conquista, nem no Discord/WhatsApp;
- **não conta como online** (lista de servidores e `{online}`);
- o **chat fica bloqueado** para você não se entregar; comece a mensagem com `!` para falar mesmo assim;
- continua assim ao sair e entrar de novo. `/esconder` outra vez para voltar ao normal.

`/esconder <jogador>` faz o mesmo com outra pessoa. Só OP pode usar (`ultracore.tabhide`, `ultracore.tabhide.others`). Quem receber `ultracore.tabhide.see` pelo LuckPerms continua vendo. Para sumir **também do mundo**, use o `/vanish`.

### Nome escondido (`/escondernick`)

Some o **nome que aparece em cima da sua cabeça**: os outros veem o personagem, mas não sabem quem é. Você continua na TAB e no chat normalmente (junte com o `/esconder` para sumir de lá também). Fica salvo ao sair e entrar; `/escondernick` de novo mostra o nome. `/escondernick <jogador>` faz o mesmo com outra pessoa. Só OP pode usar (`ultracore.hidenick`, `ultracore.hidenick.others`).

### Entrada silenciosa (`/silentjoin`)

- Quem tem `ultracore.silentjoin` (staff/admin) digita `/silentjoin` (ou `/sj`) para ligar/desligar. Fica salvo.
- Ligado: **ninguém vê** a mensagem de entrada/saída, nem a de novato, e o aviso de entrada/saída **não vai** para o Discord/WhatsApp.
- O `/vanish` não manda mensagem de entrada/saída no chat (nem falsa): quem quiser esconder que entrou/saiu usa o `/silentjoin`.
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
| `ultracore.player` | todos | spawn, home, warp, tpa, back, rtp, money, pay, baltop, kit, msg, ping, afk, regras, lixo, loja, vender, daily, tempo, report, tag, menu, top, preferencias |
| `ultracore.vip` | ninguém (dê pelo LuckPerms) | craft, enderchest, hat, cores no chat, fly, nick com cores, tag [VIP] |
| `ultracore.staff` | OP | kick, mute, ban, warn, histórico, ver denúncias, staffchat, vanish, invsee, gm, heal, feed, tphere... |
| `ultracore.admin` | OP | tudo + `/painel`, setspawn, setwarp, eco, createkit, banip, editar a loja, gerenciar tags, god, speed... |

Outras úteis: `ultracore.homes.unlimited`, `ultracore.bypass.delay`, `ultracore.bypass.cooldown`, `ultracore.kit.<nome>`, `ultracore.warp.<nome>`, `ultracore.tag.<id>` (cada tag), `ultracore.tags.admin` (/tag dar, tirar, criar, apagar), `ultracore.nick.others`, `ultracore.report.see`, `ultracore.silentjoin` e `ultracore.silentjoin.see` (staff), `ultracore.msg.bypass` (staff: manda mensagem privada mesmo para quem desligou).

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
