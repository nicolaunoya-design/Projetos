/*
 * Teste de ponta a ponta com DOIS jogadores-robô (mineflayer) num servidor Paper real com o UltraCore:
 *   Robo = OP, Alvo = jogador comum.
 * Confere cooldowns, segredo dos comandos de diversão, menções, menus (abrindo e clicando),
 * painel, editor da loja e vanish ao relogar. Falha se aparecer mensagem "crua" (ex: fun.target).
 * Variáveis: RCON_PORTA, RCON_SENHA, MC_VERSAO
 */
'use strict'
const path = require('node:path')
const { Rcon } = require(path.join(__dirname, '../../integracoes/whatsapp-bot/minecraft-bridge.cjs'))
const mineflayer = require('mineflayer')

const espera = (ms) => new Promise((r) => setTimeout(r, ms))
async function aguardar (cond, ms, passo = 250) {
  const fim = Date.now() + ms
  while (Date.now() < fim) { if (await cond()) return true; await espera(passo) }
  return false
}
let falhas = 0
let total = 0
// "::error::" vira anotação no GitHub Actions (dá para ler o que falhou sem baixar o log)
const anotar = (tipo, t) => console.log(`::${tipo}::${String(t).replace(/\r?\n/g, ' ')}`)
function checar (ok, nome) {
  total++
  console.log((ok ? '✔ ' : '✘ ') + nome)
  if (!ok) { falhas++; anotar('error', 'Falhou: ' + nome + ' | últimas mensagens: Robo=' + JSON.stringify(msgs.Robo.slice(-4)) + ' Alvo=' + JSON.stringify(msgs.Alvo.slice(-4))) }
}

const semCor = (s) => String(s).replace(/§./g, '')
const texto = (v) => v == null ? '' : semCor(typeof v === 'string' ? v : JSON.stringify(v))
/** Nome de um item de menu (o nome customizado, em qualquer formato que o mineflayer devolver). */
const nomeItem = (it) => it ? [it.customName, it.components, it.nbt].map(texto).join(' ') : ''

// ---------- robôs ----------

const msgs = { Robo: [], Alvo: [] }
const barra = { Robo: [], Alvo: [] }
const bots = {}

function criar (nome) {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: nome, version: process.env.MC_VERSAO, auth: 'offline' })
  bot.on('messagestr', (m, pos, obj) => {
    // no chat de jogador, o texto com o formato do servidor (tags, @menções) vem no "unsigned"
    const t = pos === 'chat' && obj && obj.unsigned ? obj.unsigned.toString() : m
    if (pos === 'game_info') { barra[nome].push(t); return }
    msgs[nome].push(t)
    console.log(`  [${nome}] ${t}`)
  })
  // a barra de ação (action bar) chega num pacote próprio que o mineflayer não trata
  bot._client.on('action_bar', (p) => barra[nome].push(texto(p.text ?? p)))
  bot._client.on('set_action_bar_text', (p) => barra[nome].push(texto(p.text ?? p)))
  bot.on('error', (e) => console.log(`  [${nome}] erro:`, e.message))
  bot.on('kicked', (r) => { console.log(`  [${nome}] expulso:`, JSON.stringify(r)); anotar('warning', `${nome} foi expulso: ${JSON.stringify(r)}`) })
  bots[nome] = bot
  return new Promise((resolve) => { bot.once('spawn', () => resolve(bot)); setTimeout(() => resolve(null), 60000) })
}

/** Espera uma mensagem nova (depois da posição "desde") que combine com o regex. */
async function esperarMsg (nome, re, desde, ms = 10000) {
  return aguardar(() => msgs[nome].slice(desde).some((m) => re.test(m)), ms)
}
const marca = (nome) => msgs[nome].length

async function comando (nome, cmd, re, ms = 10000) {
  const desde = marca(nome)
  bots[nome].chat(cmd)
  return re ? esperarMsg(nome, re, desde, ms) : true
}

/** Executa "acao" e espera abrir um menu. Devolve a janela (ou null). */
function janela (bot, acao, ms = 10000) {
  return new Promise((resolve) => {
    const h = (w) => { clearTimeout(t); resolve(w) }
    const t = setTimeout(() => { bot.removeListener('windowOpen', h); resolve(null) }, ms)
    bot.once('windowOpen', h)
    acao()
  })
}
const tituloDe = (w) => w ? texto(w.title) : ''
const slotCom = (w, txt) => w ? w.slots.findIndex((it, i) => i < w.inventoryStart && it && nomeItem(it).includes(txt)) : -1
const temItem = (w, txt) => slotCom(w, txt) >= 0

async function clicar (bot, slot, botao = 0, modo = 0) {
  try { await bot.clickWindow(slot, botao, modo) } catch (e) { console.log('  clique:', e.message) }
}

async function main () {
  const rcon = new Rcon({ host: '127.0.0.1', porta: Number(process.env.RCON_PORTA), senha: process.env.RCON_SENHA })
  const ligado = await aguardar(async () => { try { await rcon.comando('list'); return true } catch { return false } }, 6 * 60 * 1000, 3000)
  checar(ligado, 'servidor ligou (RCON)')
  if (!ligado) process.exit(1)

  // ---------- entrada ----------
  checar(!!(await criar('Robo')), 'Robo (OP) entrou')
  await rcon.comando('op Robo')
  await espera(1500)
  checar(await comando('Robo', '/setspawn', /Spawn definido/), 'OP definiu o spawn')
  checar(!!(await criar('Alvo')), 'Alvo (jogador comum) entrou')
  await espera(3000)

  // ---------- cooldowns ----------
  checar(await comando('Alvo', '/spawn', /Teleportado/), '/spawn teleporta')
  checar(await comando('Alvo', '/spawn', /Aguarde .*\/spawn/), '/spawn de novo cai no cooldown')
  checar(await comando('Alvo', '/tpa Robo', /Pedido enviado/), '/tpa envia o pedido')
  checar(await comando('Alvo', '/tpa Robo', /Aguarde .*\/tpa/), '/tpa de novo cai no cooldown')
  await comando('Robo', '/tpdeny')

  // ---------- menções ----------
  let desde = marca('Robo')
  bots.Alvo.chat('oi Robo tudo certo')
  checar(await esperarMsg('Robo', /@Robo/, desde), 'menção aparece como @Robo no chat')
  checar(await aguardar(() => barra.Robo.some((m) => /mencionou/.test(m)), 5000), 'quem foi mencionado recebe aviso na barra')

  // ---------- diversão secreta ----------
  desde = marca('Alvo')
  checar(await comando('Robo', '/trovao Alvo', /Raio .*Alvo/), 'OP recebe a confirmação do /trovao')
  await comando('Robo', '/arremessar Alvo', /Arremesso .*Alvo/)
  await comando('Robo', '/assustar Alvo', /Susto .*Alvo/)
  await espera(3000)
  const vistas = msgs.Alvo.slice(desde)
  checar(!vistas.some((m) => /raio|trov|robo|arremess|susto|fun\./i.test(m)), 'o alvo não recebe nenhuma mensagem dos comandos de diversão')

  // morte por raio não aparece no chat (resistência ao fogo para o fogo do raio anterior não matar antes)
  await rcon.comando('effect give Alvo minecraft:fire_resistance 120 0 true')
  await rcon.comando('effect give Alvo minecraft:instant_health 1 10')
  await espera(2500)
  const morreu = new Promise((resolve) => { bots.Alvo.once('death', () => resolve(true)); setTimeout(() => resolve(false), 15000) })
  await rcon.comando('damage Alvo 18')
  await espera(1500)
  const desdeRobo = marca('Robo')
  const desdeAlvo = marca('Alvo')
  for (let i = 0; i < 3; i++) { bots.Robo.chat('/trovao Alvo'); await espera(1200) }
  checar(await morreu, 'Alvo morreu com o raio')
  await espera(2500)
  const morte = /struck by lightning|was killed|burned|Alvo (died|was)|Alvo morreu/i
  checar(!msgs.Robo.slice(desdeRobo).some((m) => morte.test(m)) && !msgs.Alvo.slice(desdeAlvo).some((m) => morte.test(m)),
    'mensagem de morte por brincadeira não aparece no chat')
  await espera(3000)

  // ---------- menus do jogador ----------
  let w = await janela(bots.Alvo, () => bots.Alvo.chat('/menu'))
  checar(/Menu do Servidor/.test(tituloDe(w)), '/menu abre a central do servidor')
  checar(['Spawn', 'Homes', 'Warps', 'Loja', 'Kits', 'Rankings', 'Tags'].every((t) => temItem(w, t)), '/menu mostra os botões (spawn, homes, warps, loja, kits, rankings, tags)')
  checar(!temItem(w, 'Painel do UltraCore'), 'jogador comum não vê o botão do painel')
  w = await janela(bots.Alvo, () => clicar(bots.Alvo, slotCom(w, 'Homes')))
  checar(/Homes/.test(tituloDe(w)), 'botão Homes abre o menu de homes')

  w && await clicar(bots.Alvo, slotCom(w, 'Criar home'))
  await espera(1500)
  w = await janela(bots.Alvo, () => bots.Alvo.chat('base'))
  checar(temItem(w, 'base'), 'criar home pelo menu (digitando o nome) funciona e aparece na lista')
  desde = marca('Alvo')
  if (w) await clicar(bots.Alvo, slotCom(w, 'base'))
  checar(await esperarMsg('Alvo', /Teleportado/, desde), 'clicar na home teleporta')

  w = await janela(bots.Alvo, () => bots.Alvo.chat('/kit'))
  checar(/Kits/.test(tituloDe(w)) && temItem(w, 'inicial'), '/kit abre o menu de kits com o kit inicial')
  w = await janela(bots.Alvo, () => bots.Alvo.chat('/tpa'))
  checar(temItem(w, 'Robo'), '/tpa sem nome abre o menu com os jogadores online')
  w = await janela(bots.Alvo, () => bots.Alvo.chat('/warps'))
  checar(/Warps/.test(tituloDe(w)), '/warps abre o menu de warps')
  w = await janela(bots.Alvo, () => bots.Alvo.chat('/top'))
  checar(/Rankings/.test(tituloDe(w)) && temItem(w, '#1'), '/top abre os rankings com o #1')
  w = await janela(bots.Alvo, () => bots.Alvo.chat('/tag'))
  checar(!!w, '/tag abre o menu de tags')

  w = await janela(bots.Alvo, () => bots.Alvo.chat('/preferencias'))
  checar(temItem(w, 'Pedidos de teleporte'), '/preferencias abre com a opção de TPA')
  desde = marca('Alvo')
  if (w) await clicar(bots.Alvo, slotCom(w, 'Pedidos de teleporte'))
  checar(await esperarMsg('Alvo', /não receberá mais pedidos/, desde), 'desligar pedidos de TPA pelo menu')
  if (bots.Alvo.currentWindow) bots.Alvo.closeWindow(bots.Alvo.currentWindow)
  checar(await comando('Robo', '/tpa Alvo', /não está aceitando pedidos/), 'quem desligou não recebe /tpa')
  await comando('Alvo', '/tptoggle', /Agora você recebe/)

  // ---------- painel (OP) ----------
  w = await janela(bots.Robo, () => bots.Robo.chat('/painel'))
  checar(/Painel/.test(tituloDe(w)) && temItem(w, 'Teleporte') && temItem(w, 'Economia'), '/painel abre com as categorias')
  w = await janela(bots.Robo, () => clicar(bots.Robo, slotCom(w, 'Teleporte')))
  checar(/Teleporte/.test(tituloDe(w)) && temItem(w, 'Spawn'), 'categoria Teleporte abre com o módulo Spawn')
  w = await janela(bots.Robo, () => clicar(bots.Robo, slotCom(w, 'Spawn'), 1))
  checar(temItem(w, 'Cooldown'), 'configurações do Spawn mostram o Cooldown')
  w = await janela(bots.Robo, () => clicar(bots.Robo, slotCom(w, 'Comandos')))
  checar(/Comandos/.test(tituloDe(w)) && temItem(w, '/spawn'), 'menu de comandos do módulo abre')
  if (bots.Robo.currentWindow) bots.Robo.closeWindow(bots.Robo.currentWindow)

  // ---------- dimensões (Nether / The End) ----------
  w = await janela(bots.Robo, () => bots.Robo.chat('/painel'))
  checar(temItem(w, 'Dimens'), '/painel tem o botão Dimensões')
  w = await janela(bots.Robo, () => clicar(bots.Robo, slotCom(w, 'Dimens')))
  checar(temItem(w, 'Nether') && temItem(w, 'The End'), 'menu Dimensões mostra Nether e The End')
  desde = marca('Robo')
  if (w) await clicar(bots.Robo, slotCom(w, 'The End'))
  checar(await esperarMsg('Robo', /The End desligado/, desde), 'desligar o The End pelo menu')
  if (bots.Robo.currentWindow) bots.Robo.closeWindow(bots.Robo.currentWindow)
  checar(await comando('Robo', '/painel end ativar', /The End liberado/), 'religar o The End pelo comando')

  // ---------- editor da loja (OP) ----------
  await rcon.comando('give Robo minecraft:amethyst_shard 4')
  await espera(1000)
  w = await janela(bots.Robo, () => bots.Robo.chat('/loja editar'))
  checar(/Editor da Loja/.test(tituloDe(w)), '/loja editar abre o editor')
  const cristal = w ? w.slots.findIndex((it, i) => i >= w.inventoryStart && it && it.name === 'amethyst_shard') : -1
  w = await janela(bots.Robo, () => clicar(bots.Robo, cristal))
  checar(/Loja/.test(tituloDe(w)) && temItem(w, 'SALVAR'), 'clicar num item do inventário abre a tela do item')
  if (w) {
    await clicar(bots.Robo, 15); await espera(400)  // compra +10
    await clicar(bots.Robo, 15); await espera(400)  // compra +10
    await clicar(bots.Robo, 23); await espera(400)  // venda +1
    desde = marca('Robo')
    await clicar(bots.Robo, 49)                     // salvar
    checar(await esperarMsg('Robo', /Amethyst shard.*adicionado/, desde), 'item salvo na loja pelo editor')
  }
  if (bots.Robo.currentWindow) bots.Robo.closeWindow(bots.Robo.currentWindow)
  await espera(800)
  w = await janela(bots.Robo, () => bots.Robo.chat('/loja'))
  checar(temItem(w, 'Amethyst shard'), 'item novo aparece na /loja')
  if (bots.Robo.currentWindow) bots.Robo.closeWindow(bots.Robo.currentWindow)

  // ---------- /esconder (some da TAB, continua no mundo) ----------
  checar(await comando('Robo', '/esconder', /Escondido da TAB/), '/esconder liga')
  checar(await aguardar(() => bots.Alvo.players.Robo && bots.Alvo.players.Robo.listed === false, 8000), 'quem usou /esconder some da TAB dos outros')
  checar(await comando('Robo', '/esconder', /Visível na TAB/), '/esconder de novo desliga')
  checar(await aguardar(() => bots.Alvo.players.Robo && bots.Alvo.players.Robo.listed !== false, 8000), 'volta a aparecer na TAB')

  // ---------- vanish continua ao relogar ----------
  checar(await comando('Robo', '/vanish', /invisível/), '/vanish liga')
  await espera(1000)
  desde = marca('Alvo')
  bots.Robo.quit()
  await espera(3000)
  checar(!!(await criar('Robo')), 'Robo voltou ao servidor')
  checar(await esperarMsg('Robo', /entrou invisível/, 0, 8000), 'quem saiu invisível volta invisível')
  await espera(2000)
  checar(!msgs.Alvo.slice(desde).some((m) => /Robo/.test(m)), 'ninguém vê a saída/entrada de quem está invisível')
  checar(!bots.Alvo.players.Robo, 'invisível não aparece na TAB dos outros')
  await comando('Robo', '/vanish', /visível novamente/)

  // ---------- mensagens "cruas" ----------
  const cruas = [...msgs.Robo, ...msgs.Alvo, ...barra.Robo, ...barra.Alvo].filter((m) => /^(\[UltraCore\]\s*)?[a-z]+(\.[a-z0-9-]+)+$/.test(m.trim()))
  checar(cruas.length === 0, 'nenhuma mensagem apareceu como código (ex: fun.target)' + (cruas.length ? ': ' + cruas.join(' | ') : ''))

  bots.Robo.quit(); bots.Alvo.quit()
  await espera(1000)
  rcon.comando('stop').catch(() => {})
  console.log(falhas ? `\n${falhas} TESTE(S) FALHARAM` : '\nTODOS OS TESTES PASSARAM')
  anotar(falhas ? 'error' : 'notice', `Jogadores-robô: ${total - falhas} de ${total} verificações passaram`)
  process.exit(falhas ? 1 : 0)
}
main().catch((e) => { console.error('FALHOU:', e); anotar('error', 'O teste quebrou: ' + (e && e.stack || e)); process.exit(1) })
