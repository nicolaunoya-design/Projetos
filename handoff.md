# Handoff: UltraCore (plugin Paper 1.21 até 26.x)

## Objetivo final do projeto
O UltraCore é um plugin "tudo-em-um" para servidores **Paper de 1.21 até 26.x**. Cada recurso é um módulo que se liga, desliga e configura no jogo pelo `/painel`. O dono do servidor (usuário que fala português do Brasil) pede recursos, e a sessão implementa, testa no CI e publica uma release.

Pedido mais recente, já implementado (v3.0.0):
- Empregos estilo **Jobs Reborn**, com todos os empregos.
- Loja estilo **EconomyShopGUI**, com todos os itens vanilla com preço de compra e venda. Os preços precisam ser equilibrados, sem dar para lucrar comprando e revendendo.
- **Preço de venda embaixo do nome do item**.
- **Outras formas de ganhar dinheiro**.
- Melhorias gerais.

## Regras do projeto (sempre seguir)
**Branch e PR**
- Repositório `nicolaunoya-design/Projetos`.
- Trabalhar e dar push **só** na branch `claude/minecraft-plugin-multifunction-eqr5fa`.
- O PR #1 já existe e o usuário faz o merge sozinho. **Não criar PR.**

**Commits**
- Mensagens em português.
- Terminar todo commit com estas duas linhas:
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_017g7AyYotwGp4So7NqXCoYf
  ```
- Não colocar nome/ID de modelo em código, commits ou arquivos.

**Fluxo de cada versão**
1. Aumentar a `<version>` no `pom.xml`.
2. Atualizar `README.md`, `plugin.yml` e `messages.yml`.
3. Adicionar testes com robôs.
4. Fazer push e acompanhar o CI "Testes e Release". A release sai sozinha se tudo passar.
5. Responder ao usuário em português, com o link da release.

**Segurança**
- Nunca enviar o e-mail do usuário a lugar nenhum.
- Senha do RCON e URL/token de webhook são segredos.
- Não recomendar plugins que dão acesso remoto a arquivos.
- O WhatsApp usa Baileys, que não é oficial: o número pode ser banido.

## Estado atual
**Código**
- Tudo commitado e enviado. Último commit: `c9ab22d`, com o `pom.xml` em **3.0.0**.
- O CI do commit `c9ab22d` (run 39003265917) **passou em tudo**:
  - build;
  - compilação contra a paper-api mais nova;
  - servidores Paper 1.21 → 26.3, com e sem Vault;
  - robôs: **151/151** verificações;
  - pontes do WhatsApp.
- A loja calcula de 1183 itens com preço (1.21) até 1427 (26.3).
- O "preço embaixo do nome" funciona em todas as versões, inclusive na 26.3.

**Release v3.0.0: publicada**
- https://github.com/nicolaunoya-design/Projetos/releases/tag/v3.0.0
- Download: https://github.com/nicolaunoya-design/Projetos/releases/download/v3.0.0/UltraCore-3.0.0.jar
- A cobrança do GitHub bloqueou o Actions enquanto o repositório era privado. O dono deixou o repositório **público** (Actions grátis) e o run 39003265917 foi rodado de novo: tudo passou e a release saiu sozinha.

## O que foi feito na 3.0.0 (arquivos)
### Novos
**Base compartilhada (`core/`)**
- `src/main/java/com/ultracore/core/PlacedBlocks.java`
  - Guarda no PDC de cada chunk os blocos colocados por jogadores e os itens de aldeão.
  - A marca acompanha pistão e areia que cai.
  - A remoção é adiada 1 tick, para todos os ouvintes de MONITOR ainda conseguirem ler a marca.
  - Liga e desliga por contagem de quem usa (`acquire`/`release`).
- `src/main/java/com/ultracore/core/Work.java` e `WorkTracker.java`
  - Detectam as ações dos jogadores (quebrar, colocar, matar, pescar, craftar, fornalha, encantar, poção, reproduzir, domar, tosquiar, colher, explorar, forjar) e já barram abusos.
  - Avisam quem estiver inscrito (Empregos e Missões).

**Loja (`shop/`)**
- `src/main/java/com/ultracore/shop/PriceTable.java`
  - Lê `loja/precos.txt` (preços base + equivalências) e calcula o resto pelas receitas Bukkit do namespace minecraft, com ponto fixo pelo mínimo.
  - Combustível da fornalha custa 0.1 por item.
  - Também calcula se cada item pode ser comprado e vendido.
- `src/main/java/com/ultracore/shop/ShopSections.java`: monta as seções pela regra de `loja/secoes.txt`.
- `src/main/java/com/ultracore/shop/DynamicPrices.java`
  - Preço de venda dinâmico: a pressão cai com meia-vida.
  - Fórmula: `quote = D·ln((D+p+S)/(D+p))`.
- `src/main/java/com/ultracore/shop/PriceLore.java`
  - Coloca um handler Netty antes de `packet_handler` e reescreve os pacotes de inventário só no cliente.
  - Pacotes reescritos: `ClientboundContainerSetContent`, `...SetSlot`, `SetCursorItem`, `SetPlayerInventory`.
  - Funciona com pacote em formato record (MC novo) ou com campos (MC antigo).
  - Não mostra no criativo, e tira as linhas que voltam do pacote criativo (marca: `insertion="ultracore:preco"`).
  - Em menu de plugin, só o inventário de baixo ganha o preço.
  - Se algo falhar, desliga sozinho com aviso no console.

**Módulos novos**
- `src/main/java/com/ultracore/modules/JobsModule.java`
  - 13 empregos, definidos em `empregos.yml`.
  - XP por nível: `50 + 25L + 2L²`. Bônus de 1% por nível.
  - Limite de dinheiro por hora, barra de ação, boss bar, ranking e editor no painel.
- `QuestsModule.java`: missões diárias (`/missoes`), 21 modelos.
- `MarketModule.java`: `/mercado`, com itens guardados em base64 de `serializeAsBytes` em `mercado.yml`.
- `BountyModule.java`: `/procurado`, guardado em `procurados.yml`.
- `QuizModule.java`: `/quiz`.

**Recursos**
- `src/main/resources/empregos.yml`
- `loja/precos.txt`, `loja/secoes.txt`, `loja/busca.txt`

**Repositório**
- `handoff.md` (este arquivo).

### Modificados
- `ShopModule.java`: reescrito por completo.
  - Seções, tela de quantidade e SellBox (`/vender`).
  - `/vender mao|tudo` e `/preco`.
  - Editor do admin com preço por unidade.
  - Converte o `shop.yml` antigo, guardando cópia em `shop-antigo.yml`.
  - Proteção contra itens de aldeão: PDC `ultracore:aldeao` nas trocas; a marca passa para craft, fornalha, ferraria, crafter, bloco colocado e quebrado, explosão e queda; o cortador de pedras é bloqueado.
- `EconomyModule.java`: o símbolo da moeda fica em memória, porque o preço no item roda fora da thread principal.
- `RewardsModule.java`: dinheiro por tempo online, sem contar AFK.
- `MenusModule.java`: botões Mercado, Empregos, Missões e Procurados.
- `UltraCore.java`
  - Registra os módulos novos e expõe `placedBlocks()` e `work()`.
  - Novos placeholders `{empregos}` e `{missoes}`.
- `PlayerData.java`: guarda `jobs.*`, `jobs-changed` e `quests.*`.
- `Msg.java`: helpers do Adventure (`itemName` traduzido, `tellItem`, `withItem`, `loreLine`, `actionBar`).
- `MainMenu.java`: novos ids na categoria Economia.
- `pom.xml`: versão 3.0.0 e `io.netty:netty-transport 4.1.97.Final` com escopo `provided`.
- `plugin.yml`
  - Comandos novos: preco, empregos, missoes, mercado, procurado, quiz.
  - `loja` perdeu o alias `mercado`.
  - Permissões novas, e o grupo VIP ganhou `loja.vip` e `empregos.max.4`.
- `messages.yml`: seções shop, jobs, quests, market, bounty, quiz e rewards.playtime-money.
- `README.md`: "Novidades da 3.0" e seções Loja, Empregos, Missões, Mercado, Procurados e Quiz.
- CI e testes:
  - `.github/scripts/teste-jogadores.cjs`: testes novos antes da âncora `/luz`.
  - `test-server.sh`: comandos de console novos, anotações `::notice::` com a contagem de itens da loja, e falha se houver erro de plugin.yml.
  - `test-player.sh`: tempo online de 1 dinheiro a cada 2 minutos.

## Soluções que falharam (não repetir)
**Ferramentas e rede**
- **Baixar artifact/log do CI daqui:** bloqueado. O redirect vai para `*.blob.core.windows.net`, que é recusado. Use só **anotações** (`gh api repos/.../check-runs/<job>/annotations`). Por isso os scripts de CI emitem `::error::`/`::notice::`.
- **Hosts bloqueados aqui:** repo.papermc.io, fill.papermc.io, Mojang e jitpack. Não dá para compilar o Paper nem subir um servidor local.
  - Verificação local possível: `javac` só de sintaxe, filtrando "cannot find symbol" e afins.
  - O Maven Central limitou por excesso de pedidos (HTTP 429) ao baixar o netty.
  - Para conferir preços offline: `npm minecraft-data` + o simulador em `scratchpad/mcdata/sim.cjs` (se perdido, refazer: ele segue a mesma lógica do `PriceTable`).
- **`sleep` no primeiro plano é bloqueado:** espere com `run_in_background` + laço `until`, ou com Monitor.
- **Push com run em andamento:** a concurrency cancela o run anterior. Junte as correções antes de dar push.

**Código e testes**
- **`str.replace` sem limite no plugin.yml:** ao inserir permissões antes de `  ultracore.empregos.max.4:`, também alterou os filhos do grupo VIP (o texto procurado era parte de `      ultracore.empregos.max.4: true`). Isso quebrou o YAML (ClassCastException e "Permission node invalid"). Use trechos únicos e confira com `yaml.safe_load`.
- **`CraftItemStack.asCraftMirror(nms)`:** não existe no Paper 26.3. Agora o código procura o método pela assinatura e, se não achar, usa `asBukkitCopy`.
- **`Attribute.GENERIC_MAX_HEALTH`:** não usar, quebra nas APIs novas. Busque pelo registro: `max_health` e depois `generic.max_health`.
- **Teste de lore com `[minecraft:lore]`:** todo item 1.21 já tem lore vazia por padrão, então "tem lore" sempre passa. Use `minecraft:cobblestone[minecraft:lore=[]]`. O `data get entity ... Inventory` pelo RCON também não serviu.
- **Robôs:**
  - Quebrar pedra segurando trigo não conta, porque precisa da ferramenta certa (anti-abuso). Equipe a picareta.
  - Interagir com mob a mais de ~3 blocos falha.
  - Depois de um teleporte, espere o bloco aparecer no cliente (helper `cavar`).
- **Mensagens já instaladas:** quem atualiza guarda as mensagens antigas no `messages.yml`. Se os placeholders de uma chave mudarem, crie uma **chave nova** (por isso existem `shop.item-buy`, `shop.item-sell` e `shop.admin-saved`).

## Próximo passo exato
Nada pendente. A v3.0.0 está publicada. Esperar o próximo pedido do usuário e seguir o "Fluxo de cada versão".
- O repositório agora é público: nunca commitar senhas, tokens, URLs de webhook ou números reais.
- Esta sessão não pode criar releases pela API (HTTP 403) nem baixar artifacts; a release só sai pelo CI.
