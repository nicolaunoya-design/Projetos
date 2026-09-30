# Ponte WhatsApp ↔ Minecraft (Baileys + UltraCore)

Liga o seu bot de WhatsApp (Node.js + Baileys) ao servidor de Minecraft com o UltraCore, nos dois sentidos:

| Sentido | O que acontece |
|---|---|
| **Servidor → WhatsApp** | Bans, mutes, avisos, denúncias (`/report`), staffchat, "servidor ligou", entradas/saídas... chegam num grupo do WhatsApp. |
| **WhatsApp → Servidor** | Comandos no WhatsApp são executados no servidor: `!online`, `!anunciar`, `!cmd <qualquer comando>`. |

É um arquivo só (`minecraft-bridge.cjs`), **sem dependências**, que você pluga no bot que já tem.

> ⚠️ Bots de WhatsApp não oficiais (Baileys) vão contra os termos do WhatsApp e o número pode ser banido. Use um número separado do seu pessoal.

## Comandos no WhatsApp

| Comando | Quem pode | O que faz |
|---|---|---|
| `!online` | todos | Mostra quem está online |
| `!ajuda` | todos | Lista os comandos |
| `!anunciar <texto>` | admins | Anúncio no servidor (`/broadcast`) |
| `!cmd <comando>` | admins | Roda qualquer comando no console (ex: `!cmd kick Fulano`, `!cmd warn Fulano spam`, `!cmd painel lista`) |
| `!grupoid` | admins | Mostra o ID do chat (para configurar o grupo de avisos) |

Mensagens enviadas pelo próprio número do bot contam como admin (`"aceitarDeMim": true`).

## Instalação

### 1. No servidor de Minecraft

1. No `server.properties`, ligue o RCON e reinicie o servidor:
   ```properties
   enable-rcon=true
   rcon.port=25575
   rcon.password=UMA-SENHA-FORTE
   ```
   **Nunca** deixe a porta do RCON aberta para a internet sem senha forte: quem tiver essa senha controla o servidor. Se o bot e o servidor estão na mesma rede, libere a porta só para o IP da tvbox.
2. No jogo, `/painel` → **Bot do WhatsApp (Webhook)** (clique direito):
   - **Endereço do bot:** `http://IP-DA-TVBOX:3000/ultracore`
   - **Senha (token):** invente uma senha (a mesma vai no bot)
   - Escolha quais avisos enviar e **ligue o módulo** (clique esquerdo no painel).

### 2. No bot (tvbox)

1. Copie `minecraft-bridge.cjs` para a pasta do seu bot.
2. Copie `minecraft-bridge.config.example.json` como `minecraft-bridge.config.json` e preencha:
   - `http.token`: a mesma senha do painel;
   - `rcon.host` / `rcon.senha`: IP do servidor de Minecraft e senha do RCON;
   - `admins`: seu número com DDI e DDD, só números (ex: `5511999999999`);
   - `grupoAvisos`: deixe vazio por enquanto.
3. No código do bot, **crie a ponte uma vez** e **conecte a cada socket**:

   ```js
   // CommonJS (require)
   const { criarPonte } = require('./minecraft-bridge.cjs')
   // ou, se o seu bot usa "import":
   // import ponteMc from './minecraft-bridge.cjs'; const { criarPonte } = ponteMc

   const ponte = criarPonte(require('./minecraft-bridge.config.json'))  // fora da função que conecta

   async function conectarWhatsApp () {
     const sock = makeWASocket({ /* ... suas opções ... */ })
     // ... seu código de sempre ...
     ponte.conectar(sock)   // ← adicione esta linha
   }
   ```

   Se o seu bot já trata as mensagens num `messages.upsert` próprio e você prefere controlar tudo por lá, use `"ouvirMensagens": false` no config e chame `await ponte.processarMensagem(msg)` dentro do seu handler (retorna `true` se era um comando da ponte).
4. Reinicie o bot. No grupo onde quer receber os avisos, mande `!grupoid`, copie o ID (termina em `@g.us`) para `grupoAvisos` e reinicie o bot de novo.

Pronto: teste com `!online` e, no jogo, `/staffchat oi` (se a categoria "Chat da staff" estiver ligada) ou `/warn`.

### Problemas comuns

| Sintoma | Causa provável |
|---|---|
| `!online` responde "Não consegui falar com o servidor" | RCON desligado, IP/porta errados, firewall, ou senha errada ("Senha do RCON incorreta") |
| Nenhum aviso chega no grupo | `grupoAvisos` vazio; módulo desligado no `/painel`; endereço do bot errado; token diferente (o console do servidor mostra "O bot recusou o aviso (HTTP 401)") |
| "Só administradores podem usar" para você | O WhatsApp pode identificar você por um código `@lid` em vez do número. Olhe o log do bot: ele mostra o ID de quem foi negado; copie esse ID para `admins` |

## Testes

- `node teste/teste-local.cjs`: testa a ponte com um RCON e um WhatsApp simulados.
- No GitHub Actions, a ponte também é testada contra um **servidor Paper real** com o UltraCore (1.21.x e a versão mais nova): o aviso de "servidor ligado" chega no grupo, `!online`, `!anunciar` e `!cmd` funcionam, e um `/staffchat` enviado pelo WhatsApp volta como aviso. Só o WhatsApp em si é simulado.

---

## Parte 2: usar a tvbox para o Claude editar os arquivos

Se o **servidor de Minecraft roda na própria tvbox** (ou você quer que eu mexa nos arquivos do bot), dá para instalar o **Claude Code** nela. Aí você abre uma sessão na pasta do servidor/bot e eu edito os arquivos direto, inclusive controlando pelo app do Claude no celular.

1. Veja a arquitetura da tvbox: `uname -m`
   - `aarch64` (ARM 64 bits): **funciona**.
   - `armv7l` / `armhf` (ARM 32 bits): o Claude Code **não roda**. Muitas tvboxes vêm com sistema 32 bits mesmo tendo processador 64 bits; às vezes dá para instalar uma imagem Armbian 64 bits.
2. Pouca memória RAM (1–2 GB) pode deixar lento. Confira os requisitos atuais na documentação oficial do Claude Code.
3. Instale e faça login seguindo a documentação oficial: <https://code.claude.com/docs>
4. Entre na pasta do servidor ou do bot e rode:
   ```bash
   cd ~/meu-servidor
   claude remote-control
   ```
   A sessão aparece no app do Claude (celular/computador) e você conversa comigo de lá, com acesso aos arquivos dessa pasta.

Se o servidor de Minecraft está num host (e não na tvbox), essa parte só serve para os arquivos do bot. Para o servidor, veja se o host permite instalar programas (VPS) ou use SFTP.
