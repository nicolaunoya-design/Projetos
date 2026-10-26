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

  // ---------- /esconder (some da TAB, continua no mundo) ----------
  checar(await comando('Robo', '/esconder', /Escondido da TAB/), '/esconder liga')
  checar(await aguardar(() => bots.Alvo.players.Robo && !bots.Alvo.players.Robo.listed, 8000), 'quem usou /esconder some da TAB dos outros')
  checar(!!bots.Alvo.players.Robo && !!bots.Alvo.players.Robo.entity, 'quem usou /esconder continua visível no mundo')
  desde = marca('Alvo')
  checar(await comando('Robo', 'mensagem do oculto', /não foi enviada/), 'oculto não fala no chat sem querer')
  await espera(1500)
  checar(!msgs.Alvo.slice(desde).some((m) => /mensagem do oculto/.test(m)), 'ninguém vê a mensagem de quem está oculto')
  checar(await comando('Robo', '/esconder', /Visível na TAB/), '/esconder de novo desliga')
  checar(await aguardar(() => bots.Alvo.players.Robo && !!bots.Alvo.players.Robo.listed, 8000), 'volta a aparecer na TAB')

  // ---------- /ultimamorte (local salvo da morte) ----------
  await comando('Robo', '/kill Alvo')
  await espera(2500)
  checar(await comando('Robo', '/ultimamorte Alvo', /Última morte de Alvo: X -?\d+ Y -?\d+ Z -?\d+/), '/ultimamorte mostra onde o jogador morreu')
  checar(await comando('Alvo', '/ultimamorte', /Última morte de Alvo/), 'jogador vê a própria última morte')
  checar(await comando('Alvo', '/ultimamorte Robo', /permiss/i), 'jogador comum não vê a morte dos outros')

  // ---------- /troca (só com quem está perto) ----------
  const conta = (bot, nome) => bot.inventory.items().filter((i) => i.name === nome).reduce((n, i) => n + i.count, 0)
  const abreJanela = (bot, ms = 10000) => new Promise((resolve) => {
    const t = setTimeout(() => resolve(null), ms)
    bot.once('windowOpen', (w) => { clearTimeout(t); resolve(w) })
  })
  for (const n of ['Robo', 'Alvo']) await rcon.comando(`gamemode survival ${n}`)
  await rcon.comando('clear Alvo')
  await rcon.comando('clear Robo')
  await rcon.comando('give Alvo minecraft:nautilus_shell 3')
  await rcon.comando('give Robo minecraft:heart_of_the_sea 1')
  await rcon.comando('execute as Robo at @s run tp @s ~60 ~ ~')
  await espera(1500)
  checar(await comando('Alvo', '/troca Robo', /longe demais/), '/troca não funciona com quem está longe')
  await rcon.comando('tp Robo Alvo')
  await espera(1500)
  desde = marca('Robo')
  checar(await comando('Alvo', '/troca Robo', /Pedido de troca enviado/), '/troca envia o pedido para quem está perto')
  checar(await esperarMsg('Robo', /quer trocar/, desde), 'quem recebe o pedido é avisado')
  let jAlvo = abreJanela(bots.Alvo)
  let jRobo = abreJanela(bots.Robo)
  bots.Robo.chat('/troca aceitar')
  const [wa, wr] = await Promise.all([jAlvo, jRobo])
  checar(/Troca/.test(tituloDe(wa)) && /Troca/.test(tituloDe(wr)), '/troca aceitar abre o menu da troca para os dois')
  if (wa && wr) {
    const conchas = wa.slots.findIndex((it, i) => i >= wa.inventoryStart && it && it.name === 'nautilus_shell')
    await clicar(bots.Alvo, conchas, 0, 1) // shift-clique: vai para o lado do Alvo
    await espera(800)
    const coracao = wr.slots.findIndex((it, i) => i >= wr.inventoryStart && it && it.name === 'heart_of_the_sea')
    await clicar(bots.Robo, coracao, 0, 0)
    await clicar(bots.Robo, 5, 0, 0) // coloca no lado do Robo
    await espera(800)
    await clicar(bots.Robo, 0, 0, 0) // tenta pegar do lado do Alvo (tem que ser bloqueado)
    await espera(800)
    desde = marca('Alvo')
    await clicar(bots.Alvo, 47, 0, 0)
    await clicar(bots.Robo, 51, 0, 0)
    checar(await esperarMsg('Alvo', /Troca concluída/, desde, 10000), 'os dois confirmam e a troca é concluída')
    await espera(1500)
    checar(conta(bots.Robo, 'nautilus_shell') === 3 && conta(bots.Alvo, 'nautilus_shell') === 0,
      `os itens do Alvo foram para o Robo, sem duplicar (Robo ${conta(bots.Robo, 'nautilus_shell')}, Alvo ${conta(bots.Alvo, 'nautilus_shell')})`)
    checar(conta(bots.Alvo, 'heart_of_the_sea') === 1 && conta(bots.Robo, 'heart_of_the_sea') === 0, 'o item do Robo foi para o Alvo')
  }
  // fechar o menu cancela e devolve
  await espera(5500) // cooldown do pedido
  jAlvo = abreJanela(bots.Alvo)
  jRobo = abreJanela(bots.Robo)
  await comando('Alvo', '/troca Robo')
  await espera(800)
  bots.Robo.chat('/troca Alvo') // pedir de volta também aceita
  const [wa2] = await Promise.all([jAlvo, jRobo])
  if (wa2) {
    const c = wa2.slots.findIndex((it, i) => i >= wa2.inventoryStart && it && it.name === 'heart_of_the_sea')
    await clicar(bots.Alvo, c, 0, 1)
    await espera(800)
    desde = marca('Alvo')
    bots.Alvo.closeWindow(wa2)
    checar(await esperarMsg('Alvo', /Troca cancelada/, desde), 'fechar o menu cancela a troca')
    await espera(1500)
    checar(conta(bots.Alvo, 'heart_of_the_sea') === 1, 'ao cancelar, o item volta para o dono')
  } else {
    checar(false, '/troca <nome> de volta aceita o pedido')
  }
  await espera(1000)
  if (bots.Robo.currentWindow) bots.Robo.closeWindow(bots.Robo.currentWindow)

  // ---------- registro de mortes (/painel → Mortes ou /mortes) ----------
  await rcon.comando('clear Alvo')
  await rcon.comando('give Alvo minecraft:heart_of_the_sea 1')
  await rcon.comando('execute as Alvo at @s run tp @s ~ ~ ~40')
  await espera(1000)
  await rcon.comando('kill Alvo')
  await espera(3000)
  let wm = await janela(bots.Robo, () => bots.Robo.chat('/mortes Alvo'))
  checar(/Mortes/.test(tituloDe(wm)), '/mortes abre o registro de mortes')
  checar(!!wm && !!wm.slots[10] && nomeItem(wm.slots[10]).includes('Alvo'), 'a morte do Alvo aparece no registro')
  const wi = wm ? await janela(bots.Robo, () => clicar(bots.Robo, 10, 1, 0)) : null
  checar(/Itens de/.test(tituloDe(wi)) && !!wi && wi.slots.slice(0, 45).some((it) => it && it.name === 'heart_of_the_sea'),
    'clique direito mostra os itens perdidos na morte')
  if (wi) {
    await rcon.comando('clear Alvo')
    await espera(500)
    desde = marca('Alvo')
    await clicar(bots.Robo, 49, 0, 0)
    await espera(500)
    await clicar(bots.Robo, 49, 0, 0)
    checar(await esperarMsg('Alvo', /devolveu os itens/, desde), 'admin devolve os itens da morte')
    await espera(1000)
    checar(conta(bots.Alvo, 'heart_of_the_sea') === 1, 'os itens voltam para o jogador')
    await clicar(bots.Robo, 49, 0, 0)
    await espera(1000)
    checar(conta(bots.Alvo, 'heart_of_the_sea') === 1, 'não dá para devolver duas vezes')
  }
  if (bots.Robo.currentWindow) bots.Robo.closeWindow(bots.Robo.currentWindow)
  await espera(500)
  const wp = await janela(bots.Robo, () => bots.Robo.chat('/painel'))
  checar(temItem(wp, 'Mortes'), 'o /painel tem o botão Mortes')
  if (bots.Robo.currentWindow) bots.Robo.closeWindow(bots.Robo.currentWindow)

  // ---------- limpeza dos itens do chão: liga/desliga ----------
  checar(await comando('Robo', '/limparitens desligar', /automática desligada/), '/limparitens desligar')
  checar(await comando('Robo', '/limparitens status', /desligada/), '/limparitens status mostra desligada')
  checar(await comando('Robo', '/limparitens ligar', /automática ligada/), '/limparitens ligar')

  // ---------- PvP liga/desliga ----------
  for (const n of ['Robo', 'Alvo']) await rcon.comando(`gamemode survival ${n}`)
  await rcon.comando('tp Robo Alvo')
  await rcon.comando('effect give Alvo minecraft:instant_health 1 5')
  await espera(2000)
  const bater = async () => {
    const alvo = bots.Robo.players.Alvo && bots.Robo.players.Alvo.entity
    if (!alvo) return false
    await bots.Robo.lookAt(alvo.position.offset(0, 1.6, 0), true)
    bots.Robo.attack(alvo)
    return true
  }
  desde = marca('Alvo')
  checar(await comando('Robo', '/pvp desligar', /PvP DESLIGADO/), '/pvp desligar')
  checar(await esperarMsg('Alvo', /PvP DESLIGADO/, desde), 'todos são avisados que o PvP desligou')
  const vidaAntes = bots.Alvo.health
  checar(await bater(), 'Robo encontra o Alvo para bater')
  await espera(1200)
  await bater()
  await espera(1500)
  checar(bots.Alvo.health >= vidaAntes, `com o PvP desligado o Alvo não perde vida (${vidaAntes} -> ${bots.Alvo.health})`)
  checar(await comando('Robo', '/pvp ligar', /PvP LIGADO/), '/pvp ligar')
  await espera(1200)
  await bater()
  checar(await aguardar(() => bots.Alvo.health < vidaAntes, 5000), `com o PvP ligado o Alvo perde vida (${vidaAntes} -> ${bots.Alvo.health})`)
  checar(await comando('Robo', '/pvp', /PvP do servidor: LIGADO/), '/pvp mostra o estado')
  await rcon.comando('effect give Alvo minecraft:instant_health 1 5')
  const wpv = await janela(bots.Robo, () => bots.Robo.chat('/painel'))
  checar(temItem(wpv, 'PvP'), 'o /painel tem o botão PvP')
  if (bots.Robo.currentWindow) bots.Robo.closeWindow(bots.Robo.currentWindow)

  // ---------- brincadeiras novas (2.9) ----------
  for (const n of ['Robo', 'Alvo']) await rcon.comando(`effect give ${n} minecraft:resistance 90 4 true`)
  await rcon.comando('tp Alvo Robo')
  await espera(1000)
  desde = marca('Alvo')
  const novas = ['bigorna', 'chuvadegalinhas', 'bebado', 'cegar', 'levitar', 'terremoto', 'tornado', 'gigante', 'anao',
    'explosaofalsa', 'amor', 'chuvadetnt', 'meteoro', 'inverter', 'pipoca', 'abduzir']
  const falharam = []
  for (const c of novas) {
    if (!(await comando('Robo', `/${c} Alvo`, / em Alvo/, 6000))) falharam.push(c)
    await espera(400)
  }
  checar(falharam.length === 0, `as ${novas.length} brincadeiras novas funcionam${falharam.length ? ' (falharam: ' + falharam.join(', ') + ')' : ''}`)
  checar(await comando('Robo', '/tamanho Alvo normal', /Tamanho normal em Alvo/), '/tamanho volta ao normal')
  await espera(6000)
  checar(!msgs.Alvo.slice(desde).some((m) => /robo|fun\.|bigorna|tornado|gigante|meteoro|diversão/i.test(m)), 'as brincadeiras novas também são secretas para o alvo')
  checar(bots.Alvo.health > 0, 'o alvo sobreviveu (com resistência) e o servidor não deu erro')
  for (const n of ['Robo', 'Alvo']) await rcon.comando(`effect clear ${n}`)
  await rcon.comando('tp Alvo Robo')
  const wd = await janela(bots.Robo, () => bots.Robo.chat('/diversao'))
  checar(/Diversão/.test(tituloDe(wd)) && temItem(wd, 'TODOS'), '/diversao abre o menu de escolher o alvo')
  if (wd) {
    const cab = wd.slots.findIndex((it, i) => i < wd.inventoryStart && it && it.name === 'player_head' && nomeItem(it).includes('Alvo'))
    const we = cab >= 0 ? await janela(bots.Robo, () => clicar(bots.Robo, cab, 0, 0)) : null
    checar(temItem(we, 'Bigorna') && temItem(we, 'Abduzir'), 'clicar no jogador mostra as brincadeiras')
  }
  if (bots.Robo.currentWindow) bots.Robo.closeWindow(bots.Robo.currentWindow)
  await espera(1000)

  // ---------- criador de tag ----------
  checar(await comando('Alvo', '/tag criador', /não pode criar tags/), 'jogador sem permissão não abre o criador de tag')
  let wc = await janela(bots.Robo, () => bots.Robo.chat('/tag criador'))
  checar(/Criador de Tag/.test(tituloDe(wc)), '/tag criador abre o criador')
  const digitarTexto = async (txt) => {
    const d0 = marca('Robo')
    await clicar(bots.Robo, 10, 0, 0)
    await esperarMsg('Robo', /texto da tag/, d0, 5000)
    return janela(bots.Robo, () => bots.Robo.chat(txt))
  }
  if (wc) {
    wc = await digitarTexto('Lenda')
    checar(!!wc && !!wc.slots[4] && nomeItem(wc.slots[4]).includes('Lenda'), 'o texto digitado aparece na prévia')
    await clicar(bots.Robo, 19, 0, 0) // cor vermelha
    await clicar(bots.Robo, 20, 1, 0) // degradê até dourado
    await clicar(bots.Robo, 12, 0, 0) // negrito
    await espera(500)
    const d1 = marca('Robo')
    await clicar(bots.Robo, 50, 0, 0)
    checar(await esperarMsg('Robo', /Tag lenda\d* criada/, d1), 'admin cria uma tag do servidor com degradê')
  }
  await espera(1000)
  if (bots.Robo.currentWindow) bots.Robo.closeWindow(bots.Robo.currentWindow)
  wc = await janela(bots.Robo, () => bots.Robo.chat('/tag criador'))
  if (wc) {
    wc = await digitarTexto('Rei')
    const d0 = marca('Robo')
    await clicar(bots.Robo, 48, 0, 0)
    checar(await esperarMsg('Robo', /tag personalizada está pronta/, d0), 'jogador salva a própria tag personalizada')
  }
  await espera(1000)
  desde = marca('Alvo')
  bots.Robo.chat('testando a tag nova')
  checar(await esperarMsg('Alvo', /Rei.*testando a tag nova/, desde), 'a tag personalizada aparece no chat')
  await comando('Robo', '/tag auto')
  if (bots.Robo.currentWindow) bots.Robo.closeWindow(bots.Robo.currentWindow)

  // ---------- loja nova (todos os itens vanilla) ----------
  const { Vec3 } = require('vec3')
  /** Espera o bloco aparecer no cliente (chunk carregado depois do teleporte) e quebra. */
  const cavar = async (bot, x, y, z, nome = 'stone') => {
    await aguardar(() => { const b = bot.blockAt(new Vec3(x, y, z)); return !!b && b.name === nome }, 8000)
    const b = bot.blockAt(new Vec3(x, y, z))
    if (!b || b.name !== nome) { console.log(`  cavar: ${nome} não apareceu em ${x} ${y} ${z}`); return false }
    try { await bot.dig(b, true); return true } catch (e) { console.log('  dig:', e.message); return false }
  }
  const loreDe = (it) => it ? [it.components, it.nbt, it.customLore].map(texto).join(' ') : ''
  const itemInv = (bot, nome) => bot.inventory.items().find((i) => i.name === nome)
  const saldo = async (nome) => {
    const r = semCor(await rcon.comando(`money ${nome}`))
    const m = /([\d.]+,\d{2})/.exec(r)
    return m ? Number(m[1].replace(/\./g, '').replace(',', '.')) : NaN
  }
  for (const n of ['Robo', 'Alvo']) await rcon.comando(`gamemode survival ${n}`)
  await rcon.comando('clear Alvo')
  await rcon.comando('eco set Alvo 5000')
  await rcon.comando('eco set Robo 5000')
  let wl = await janela(bots.Alvo, () => bots.Alvo.chat('/loja'))
  checar(/Loja do Servidor/.test(tituloDe(wl)) && temItem(wl, 'Minérios') && temItem(wl, 'Madeiras') && temItem(wl, 'Blocos'), '/loja abre com as seções (Blocos, Madeiras, Minérios...)')
  wl = wl ? await janela(bots.Alvo, () => clicar(bots.Alvo, slotCom(wl, 'Minérios'))) : null
  const slotDiamante = wl ? wl.slots.findIndex((it, i) => i < wl.inventoryStart && it && it.name === 'diamond') : -1
  checar(slotDiamante >= 0 && /Comprar/.test(loreDe(wl.slots[slotDiamante])) && /Vender/.test(loreDe(wl.slots[slotDiamante])), 'a seção Minérios tem o diamante com preço de compra e venda')
  const wq = slotDiamante >= 0 ? await janela(bots.Alvo, () => clicar(bots.Alvo, slotDiamante, 0, 0)) : null
  checar(/Comprar/.test(tituloDe(wq)), 'clicar no item abre a tela de quantidade')
  if (wq) {
    desde = marca('Alvo')
    await clicar(bots.Alvo, 49, 0, 0)
    checar(await esperarMsg('Alvo', /Você comprou 1x .*Diamond|Você comprou 1x/i, desde), 'comprar 1 diamante pela tela de quantidade')
    await espera(800)
    checar(conta(bots.Alvo, 'diamond') === 1, 'o diamante comprado chegou no inventário')
  }
  if (bots.Alvo.currentWindow) bots.Alvo.closeWindow(bots.Alvo.currentWindow)
  await espera(500)
  // preço embaixo do nome: só na tela do jogador, o item de verdade não muda
  await rcon.comando('give Alvo minecraft:cobblestone 32')
  checar(await aguardar(() => /Vende por/.test(loreDe(itemInv(bots.Alvo, 'cobblestone'))), 8000), 'o preço de venda aparece embaixo do nome do item')
  // no servidor os 32 pedregulhos continuam com a lore vazia (o preço é só na tela do jogador)
  const semLore = semCor(await rcon.comando('execute if items entity Alvo container.* minecraft:cobblestone[minecraft:lore=[]]'))
  checar(/count: 32/.test(semLore), `no servidor o item continua igual (o preço é só visual) [${semLore.slice(0, 100)}]`)
  await rcon.comando('gamemode creative Alvo')
  checar(await aguardar(() => !/Vende por/.test(loreDe(itemInv(bots.Alvo, 'cobblestone'))), 8000), 'no criativo o preço some (não estraga o item)')
  await rcon.comando('gamemode survival Alvo')
  checar(await aguardar(() => /Vende por/.test(loreDe(itemInv(bots.Alvo, 'cobblestone'))), 8000), 'saindo do criativo o preço volta')
  // /vender mao, ferramenta não vende, item de aldeão não vende
  const pedra = itemInv(bots.Alvo, 'cobblestone')
  if (pedra) await bots.Alvo.equip(pedra, 'hand')
  checar(await comando('Alvo', '/vender mao', /Você vendeu 32x/), '/vender mao vende o item da mão')
  checar(conta(bots.Alvo, 'cobblestone') === 0, 'os itens vendidos saíram do inventário')
  await rcon.comando('give Alvo minecraft:iron_pickaxe')
  await espera(800)
  const pic = itemInv(bots.Alvo, 'iron_pickaxe')
  if (pic) await bots.Alvo.equip(pic, 'hand')
  checar(await comando('Alvo', '/vender mao', /não compra/), 'ferramenta não é vendida (evita abuso)')
  await rcon.comando('give Alvo minecraft:cobblestone[custom_data={PublicBukkitValues:{"ultracore:aldeao":1b}}] 4')
  await espera(800)
  const deAldeao = bots.Alvo.inventory.items().find((i) => i.name === 'cobblestone')
  if (deAldeao) await bots.Alvo.equip(deAldeao, 'hand')
  checar(await comando('Alvo', '/vender mao', /aldeão/), 'item comprado de aldeão não é vendido na loja')
  await rcon.comando('clear Alvo')
  // /vender abre a tela: coloca os itens e fecha
  await rcon.comando('give Alvo minecraft:oak_log 16')
  await espera(800)
  const wv = await janela(bots.Alvo, () => bots.Alvo.chat('/vender'))
  checar(/Vender/.test(tituloDe(wv)), '/vender abre a tela de vender')
  if (wv) {
    const tora = wv.slots.findIndex((it, i) => i >= wv.inventoryStart && it && it.name === 'oak_log')
    await clicar(bots.Alvo, tora, 0, 1)
    await espera(800)
    desde = marca('Alvo')
    bots.Alvo.closeWindow(wv)
    checar(await esperarMsg('Alvo', /Você vendeu 16 itens/, desde), 'fechar a tela vende o que foi colocado')
  }
  checar(await comando('Alvo', '/preco diamond', /compra .*venda/), '/preco mostra o preço de compra e venda')
  const wb = await janela(bots.Alvo, () => bots.Alvo.chat('/loja buscar diamante'))
  checar(!!wb && wb.slots.some((it, i) => i < wb.inventoryStart && it && it.name === 'diamond_pickaxe'), 'a busca em português (diamante) acha os itens de diamante')
  if (bots.Alvo.currentWindow) bots.Alvo.closeWindow(bots.Alvo.currentWindow)
  // editor do admin
  await rcon.comando('give Robo minecraft:amethyst_shard 4')
  await espera(800)
  let we = await janela(bots.Robo, () => bots.Robo.chat('/loja editar'))
  checar(/Editor da Loja/.test(tituloDe(we)), '/loja editar abre o editor')
  const cristal = we ? we.slots.findIndex((it, i) => i >= we.inventoryStart && it && it.name === 'amethyst_shard') : -1
  we = cristal >= 0 ? await janela(bots.Robo, () => clicar(bots.Robo, cristal)) : null
  checar(/Preço/.test(tituloDe(we)) && temItem(we, 'SALVAR'), 'clicar num item do inventário abre a tela do preço')
  if (we) {
    await clicar(bots.Robo, 16); await espera(400) // compra +10
    desde = marca('Robo')
    await clicar(bots.Robo, 38) // salvar
    checar(await esperarMsg('Robo', /Preço de .* salvo/, desde), 'admin muda o preço de um item pelo editor')
  }
  if (bots.Robo.currentWindow) bots.Robo.closeWindow(bots.Robo.currentWindow)
  await espera(500)
  checar(await comando('Robo', '/preco amethyst_shard', /compra/), 'o preço novo vale na hora')

  // ---------- empregos ----------
  await rcon.comando('clear Alvo')
  await rcon.comando('tp Alvo 200 -60 200')
  await rcon.comando('fill 196 -60 196 206 -56 206 air')
  await espera(1500)
  let wj = await janela(bots.Alvo, () => bots.Alvo.chat('/empregos'))
  checar(/Empregos/.test(tituloDe(wj)) && temItem(wj, 'Minerador') && temItem(wj, 'Pescador') && temItem(wj, 'Cozinheiro'), '/empregos abre com os 13 empregos')
  if (wj) {
    desde = marca('Alvo')
    await clicar(bots.Alvo, slotCom(wj, 'Minerador'), 0, 0)
    checar(await esperarMsg('Alvo', /Você agora é Minerador/, desde), 'entrar no emprego Minerador pelo menu')
  }
  if (bots.Alvo.currentWindow) bots.Alvo.closeWindow(bots.Alvo.currentWindow)
  checar(await comando('Alvo', '/empregos entrar fazendeiro', /Você agora é Fazendeiro/), '/empregos entrar fazendeiro')
  checar(await comando('Alvo', '/empregos entrar cacador', /Você agora é Caçador/), '/empregos entrar cacador')
  checar(await comando('Alvo', '/empregos entrar pescador', /máximo de empregos/), 'não dá para passar do máximo de empregos (3)')
  await rcon.comando('give Alvo minecraft:iron_pickaxe')
  await rcon.comando('setblock 201 -60 200 minecraft:stone')
  await espera(1000)
  const picareta = itemInv(bots.Alvo, 'iron_pickaxe')
  if (picareta) await bots.Alvo.equip(picareta, 'hand')
  let antes = barra.Alvo.length
  await cavar(bots.Alvo, 201, -60, 200)
  checar(await aguardar(() => barra.Alvo.slice(antes).some((m) => /Minerador.*\+/.test(m)), 6000), 'quebrar pedra natural paga o Minerador (barra de ação)')
  // anti-abuso: bloco colocado pelo jogador não paga
  await rcon.comando('give Alvo minecraft:stone 2')
  await espera(800)
  const pedraMao = itemInv(bots.Alvo, 'stone')
  try {
    if (pedraMao) await bots.Alvo.equip(pedraMao, 'hand')
    await aguardar(() => !!bots.Alvo.blockAt(new Vec3(202, -61, 200)), 5000)
    await bots.Alvo.placeBlock(bots.Alvo.blockAt(new Vec3(202, -61, 200)), new Vec3(0, 1, 0))
  } catch (e) { console.log('  colocar:', e.message) }
  await espera(800)
  const colocada = bots.Alvo.blockAt(new Vec3(202, -60, 200))
  checar(!!colocada && colocada.name === 'stone', 'Alvo colocou uma pedra')
  if (picareta) await bots.Alvo.equip(itemInv(bots.Alvo, 'iron_pickaxe'), 'hand')
  await espera(1500)
  antes = barra.Alvo.length
  try { if (colocada) await bots.Alvo.dig(colocada, true) } catch (e) { console.log('  dig:', e.message) }
  await espera(3000)
  checar(!barra.Alvo.slice(antes).some((m) => /Minerador.*\+/.test(m)), 'quebrar a pedra que o próprio jogador colocou NÃO paga')
  // fazendeiro: reproduzir vacas; caçador: mob de comando não paga, filhote nascido de cruzamento paga
  await rcon.comando('give Alvo minecraft:wheat 4')
  for (let i = 0; i < 2; i++) await rcon.comando('summon minecraft:cow 201.5 -60 201.5 {Tags:["adulto"]}')
  await espera(1500)
  const trigo = itemInv(bots.Alvo, 'wheat')
  if (trigo) await bots.Alvo.equip(trigo, 'hand')
  antes = barra.Alvo.length
  for (const ent of Object.values(bots.Alvo.entities).filter((x) => x.name === 'cow')) {
    try { await bots.Alvo.lookAt(ent.position.offset(0, 1, 0), true); await bots.Alvo.activateEntity(ent) } catch (e) { console.log('  vaca:', e.message) }
    await espera(400)
  }
  checar(await aguardar(() => barra.Alvo.slice(antes).some((m) => /Fazendeiro.*\+/.test(m)), 15000), 'reproduzir vacas paga o Fazendeiro')
  antes = barra.Alvo.length
  await rcon.comando('damage @e[type=minecraft:cow,tag=adulto,limit=1,sort=nearest] 100 minecraft:player_attack by Alvo')
  await espera(2500)
  checar(!barra.Alvo.slice(antes).some((m) => /Caçador.*\+/.test(m)), 'matar mob criado por comando NÃO paga o Caçador')
  antes = barra.Alvo.length
  await rcon.comando('damage @e[type=minecraft:cow,tag=!adulto,limit=1,sort=nearest] 100 minecraft:player_attack by Alvo')
  checar(await aguardar(() => barra.Alvo.slice(antes).some((m) => /Caçador.*\+/.test(m)), 6000), 'matar o filhote nascido do cruzamento paga o Caçador')
  checar(await comando('Alvo', '/empregos stats', /Minerador.*nível/), '/empregos stats mostra o nível')
  await rcon.comando('kill @e[type=minecraft:cow]')

  // ---------- missões diárias ----------
  const wm2 = await janela(bots.Alvo, () => bots.Alvo.chat('/missoes'))
  checar(/Missões/.test(tituloDe(wm2)) && !!wm2 && wm2.slots.some((it, i) => i < wm2.inventoryStart && it && /Progresso/.test(loreDe(it))), '/missoes abre com as missões do dia')
  if (bots.Alvo.currentWindow) bots.Alvo.closeWindow(bots.Alvo.currentWindow)
  checar(await comando('Robo', '/missoes trocar Alvo pedra 2', /Primeira missão de Alvo/), 'admin troca a missão de um jogador')
  // pedra só conta com a ferramenta certa (na mão não dá nada)
  const picaretaMissao = itemInv(bots.Alvo, 'iron_pickaxe')
  if (picaretaMissao) await bots.Alvo.equip(picaretaMissao, 'hand')
  desde = marca('Alvo')
  for (const z of [199, 201]) {
    await rcon.comando(`setblock 199 -60 ${z} minecraft:stone`)
    await cavar(bots.Alvo, 199, -60, z)
    await espera(600)
  }
  checar(await esperarMsg('Alvo', /Missão completa/, desde, 8000), 'completar a missão paga na hora')

  // ---------- mercado dos jogadores ----------
  await rcon.comando('clear Alvo')
  await rcon.comando('give Alvo minecraft:diamond 2')
  await espera(800)
  const dia = itemInv(bots.Alvo, 'diamond')
  if (dia) await bots.Alvo.equip(dia, 'hand')
  checar(await comando('Alvo', '/mercado vender 100', /Anunciado/), '/mercado vender anuncia o item da mão')
  checar(conta(bots.Alvo, 'diamond') === 0, 'o item anunciado sai do inventário')
  const saldoAlvo = await saldo('Alvo')
  const wmk = await janela(bots.Robo, () => bots.Robo.chat('/mercado'))
  const anuncio = wmk ? wmk.slots.findIndex((it, i) => i < wmk.inventoryStart && it && it.name === 'diamond') : -1
  checar(/Mercado/.test(tituloDe(wmk)) && anuncio >= 0, 'o anúncio aparece no /mercado dos outros')
  const wcf = anuncio >= 0 ? await janela(bots.Robo, () => clicar(bots.Robo, anuncio, 0, 0)) : null
  checar(/Confirmar/.test(tituloDe(wcf)), 'clicar no anúncio pede confirmação')
  if (wcf) {
    desde = marca('Alvo')
    await clicar(bots.Robo, 11, 0, 0)
    checar(await esperarMsg('Alvo', /comprou seu/, desde), 'o vendedor é avisado da venda')
    await espera(1000)
    checar(conta(bots.Robo, 'diamond') === 2, 'o comprador recebe o item')
    const depois = await saldo('Alvo')
    checar(Math.abs(depois - saldoAlvo - 95) < 2, `o vendedor recebe o valor menos a taxa de 5% (${saldoAlvo} -> ${depois})`)
  }
  if (bots.Robo.currentWindow) bots.Robo.closeWindow(bots.Robo.currentWindow)

  // ---------- lojas da comunidade (/lojinha) ----------
  await rcon.comando('eco set Alvo 5000')
  await rcon.comando('eco set Robo 5000')
  await rcon.comando('clear Alvo')
  await rcon.comando('clear Robo')
  await rcon.comando('give Alvo minecraft:cobblestone 10')
  await espera(800)
  const naJanela = (w, nome, embaixo) => w ? w.slots.findIndex((it, i) => (embaixo ? i >= w.inventoryStart : i < w.inventoryStart) && it && it.name === nome) : -1
  const fechar = (bot) => { if (bot.currentWindow) bot.closeWindow(bot.currentWindow) }
  let wlj = await janela(bots.Alvo, () => bots.Alvo.chat('/lojinha criar'))
  checar(/Criar minha loja/.test(tituloDe(wlj)), '/lojinha criar mostra a taxa e pede confirmação')
  wlj = wlj ? await janela(bots.Alvo, () => clicar(bots.Alvo, 11)) : null
  checar(/Gerenciar/.test(tituloDe(wlj)), 'criar a loja abre o menu de gerenciar')
  checar(Math.abs(await saldo('Alvo') - 2500) < 2, 'criar a loja cobra a taxa de $2.500')
  const pedraInv = naJanela(wlj, 'cobblestone', true)
  let wof = pedraInv >= 0 ? await janela(bots.Alvo, () => clicar(bots.Alvo, pedraInv)) : null
  checar(/Editar item/.test(tituloDe(wof)), 'clicar num item do inventário põe à venda e abre o editor')
  const noServidor = async (quem, item) => {
    const r = semCor(await rcon.comando(`execute if items entity ${quem} container.* minecraft:${item}`))
    const m = /count: (\d+)/.exec(r)
    return m ? Number(m[1]) : 0
  }
  checar(await aguardar(() => conta(bots.Alvo, 'cobblestone') === 0, 5000) && await noServidor('Alvo', 'cobblestone') === 0, 'o item sai do inventário e vai para o estoque da loja')
  desde = marca('Alvo')
  if (wof) await clicar(bots.Alvo, 31) // preço em dinheiro (pelo chat)
  await espera(1500)
  wof = await janela(bots.Alvo, () => bots.Alvo.chat('50'))
  checar(await esperarMsg('Alvo', /Preço salvo/, desde) && /Editar item/.test(tituloDe(wof)), 'o dono define o preço digitando no chat')
  fechar(bots.Alvo)

  let wvis = await janela(bots.Robo, () => bots.Robo.chat('/lojinha Alvo'))
  let oferta = naJanela(wvis, 'cobblestone', false)
  checar(/Loja do Alvo/.test(tituloDe(wvis)) && oferta >= 0, '/lojinha <jogador> abre a loja com o item à venda')
  let wcomp = oferta >= 0 ? await janela(bots.Robo, () => clicar(bots.Robo, oferta)) : null
  checar(/Comprar/.test(tituloDe(wcomp)), 'clicar no item abre a tela de compra')
  if (wcomp) {
    const saldoRobo = await saldo('Robo')
    const saldoDono = await saldo('Alvo')
    await clicar(bots.Robo, 23) // +1: duas compras
    await espera(600)
    desde = marca('Alvo')
    await clicar(bots.Robo, 49)
    checar(await esperarMsg('Alvo', /comprou .*na sua loja/, desde), 'o dono é avisado da venda')
    await espera(1000)
    checar(conta(bots.Robo, 'cobblestone') === 2, 'o comprador recebe os itens (2 compras)')
    checar(Math.abs(saldoRobo - await saldo('Robo') - 100) < 2, 'o comprador paga $50 por compra')
    checar(Math.abs(await saldo('Alvo') - saldoDono - 95) < 2, 'o dono recebe o dinheiro menos a taxa de 5%')
  }
  fechar(bots.Robo)

  // pagamento em item: 2 diamantes por compra
  let wger = await janela(bots.Alvo, () => bots.Alvo.chat('/lojinha minha'))
  const ofertaDono = naJanela(wger, 'cobblestone', false)
  let wed = ofertaDono >= 0 ? await janela(bots.Alvo, () => clicar(bots.Alvo, ofertaDono)) : null
  checar(/Editar item/.test(tituloDe(wed)), 'clicar na oferta no gerenciar abre o editor')
  if (wed) {
    await clicar(bots.Alvo, 24) // cobrar em item (começa no diamante)
    await espera(700)
    await clicar(bots.Alvo, 33) // +1 item de pagamento
    await espera(700)
  }
  fechar(bots.Alvo)
  await rcon.comando('give Robo minecraft:diamond 3')
  await espera(800)
  wvis = await janela(bots.Robo, () => bots.Robo.chat('/lojinha Alvo'))
  oferta = naJanela(wvis, 'cobblestone', false)
  wcomp = oferta >= 0 ? await janela(bots.Robo, () => clicar(bots.Robo, oferta)) : null
  if (wcomp) {
    desde = marca('Alvo')
    await clicar(bots.Robo, 49)
    checar(await esperarMsg('Alvo', /pagou/, desde), 'compra paga com item avisa o dono')
    await espera(1000)
  }
  const diaServ = await noServidor('Robo', 'diamond')
  const pedraServ = await noServidor('Robo', 'cobblestone')
  checar(diaServ === 1 && pedraServ === 3, `no servidor o comprador pagou 2 diamantes e recebeu o item (diamantes: ${diaServ}, pedregulho: ${pedraServ})`)
  checar(await aguardar(() => conta(bots.Robo, 'diamond') === 1 && conta(bots.Robo, 'cobblestone') === 3, 5000),
    `o inventário do comprador mostra isso (diamantes: ${conta(bots.Robo, 'diamond')}, pedregulho: ${conta(bots.Robo, 'cobblestone')})`)
  fechar(bots.Robo)
  const wcofre = await janela(bots.Alvo, () => bots.Alvo.chat('/lojinha cofre'))
  const diaCofre = naJanela(wcofre, 'diamond', false)
  checar(/Cofre/.test(tituloDe(wcofre)) && diaCofre >= 0, 'o pagamento em item fica no cofre da loja')
  if (diaCofre >= 0) await clicar(bots.Alvo, diaCofre)
  await espera(1000)
  checar(conta(bots.Alvo, 'diamond') === 2, 'o dono pega os diamantes do cofre')
  fechar(bots.Alvo)
  const wlista = await janela(bots.Robo, () => bots.Robo.chat('/lojinha'))
  checar(/Lojas da Comunidade/.test(tituloDe(wlista)) && temItem(wlista, 'Loja do Alvo'), '/lojinha lista as lojas abertas')
  fechar(bots.Robo)

  // ---------- procurados ----------
  desde = marca('Alvo')
  checar(await comando('Robo', '/procurado Alvo 100', /Você colocou/), '/procurado coloca dinheiro na cabeça de alguém')
  checar(await esperarMsg('Alvo', /Sua cabeça agora vale/, desde), 'o procurado é avisado')
  await rcon.comando('effect clear Alvo')
  desde = marca('Robo')
  await rcon.comando('damage Alvo 1000 minecraft:player_attack by Robo')
  checar(await esperarMsg('Robo', /ganhou o prêmio/, desde), 'quem mata o procurado leva o prêmio')
  await espera(2500)

  // ---------- botões novos no /menu ----------
  const wmenu = await janela(bots.Alvo, () => bots.Alvo.chat('/menu'))
  checar(['Mercado', 'Lojas da Comunidade', 'Empregos', 'Missões', 'Procurados', 'Rankings'].every((t) => temItem(wmenu, t)), '/menu tem os botões novos (mercado, lojas da comunidade, empregos, missões, procurados)')
  if (bots.Alvo.currentWindow) bots.Alvo.closeWindow(bots.Alvo.currentWindow)

  // ---------- quiz do chat ----------
  const palavras = ['diamante', 'esmeralda', 'picareta', 'creeper', 'esqueleto', 'aldeao', 'obsidiana', 'netherita', 'redstone', 'fornalha', 'bancada',
    'machado', 'espada', 'armadura', 'encantamento', 'abobora', 'melancia', 'galinha', 'ovelha', 'lobo', 'enderman', 'dragao', 'portal', 'minerio',
    'carvao', 'lanterna', 'tocha', 'escudo', 'flecha', 'pocao', 'trigo', 'cenoura', 'batata', 'cogumelo', 'cacto', 'bambu', 'esponja', 'farol',
    'bigorna', 'estante', 'elitro', 'tridente', 'ametista', 'cobre', 'quartzo', 'ouro', 'ferro', 'pedregulho', 'areia', 'cascalho', 'neve', 'gelo', 'lava']
  const ordenar = (w) => w.split('').sort().join('')
  desde = marca('Alvo')
  await rcon.comando('quiz agora')
  let resposta = null
  await aguardar(() => {
    for (const m of msgs.Alvo.slice(desde)) {
      let r
      if ((r = /Quanto é (\d+) ([+×-]) (\d+)\?/.exec(m))) resposta = String(r[2] === '+' ? +r[1] + +r[3] : r[2] === '-' ? r[1] - r[3] : r[1] * r[3])
      else if ((r = /Desembaralhe a palavra: (\S+)/.exec(m))) resposta = palavras.find((w) => ordenar(w) === ordenar(r[1].toLowerCase()))
      else if ((r = /Quem digitar primeiro: (\S+)/.exec(m))) resposta = r[1]
    }
    return !!resposta
  }, 8000)
  checar(!!resposta, 'o quiz aparece no chat')
  if (resposta) {
    desde = marca('Robo')
    bots.Alvo.chat(resposta)
    checar(await esperarMsg('Robo', /Alvo acertou/, desde), 'quem acerta o quiz ganha e todos são avisados')
  }

  // ---------- /luz (visão noturna sem fim) ----------
  const efeitoNv = () => {
    const r = bots.Alvo.registry
    const ef = (r.effectsByName && (r.effectsByName.NightVision || r.effectsByName.night_vision)) ||
      Object.values(r.effects || {}).find((x) => /night.?vision/i.test(String(x.name)))
    return ef ? (bots.Alvo.entity.effects || {})[ef.id] : undefined
  }
  checar(await comando('Alvo', '/luz', /Luz ligada/), '/luz liga (jogador comum)')
  checar(await aguardar(() => !!efeitoNv(), 8000), '/luz dá visão noturna')
  checar(await comando('Alvo', '/luz', /Luz desligada/), '/luz de novo desliga')
  checar(await aguardar(() => !efeitoNv(), 8000), '/luz tira a visão noturna')

  // ---------- /escondernick (some o nome em cima da cabeça) ----------
  const timeDoRobo = () => bots.Alvo.teamMap && bots.Alvo.teamMap.Robo
  checar(await comando('Robo', '/escondernick', /Nome escondido/), '/escondernick liga')
  checar(await aguardar(() => timeDoRobo() && String(timeDoRobo().nameTagVisibility) === 'never', 8000), 'os outros não veem o nome em cima da cabeça')
  checar(await comando('Robo', '/escondernick', /visível de novo/), '/escondernick de novo desliga')
  checar(await aguardar(() => !timeDoRobo() || String(timeDoRobo().nameTagVisibility) !== 'never', 8000), 'o nome volta a aparecer')

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
  desde = marca('Alvo')
  checar(await comando('Robo', 'segredo do fantasma', /não foi enviada/), 'invisível não fala no chat sem querer')
  await espera(1500)
  checar(!msgs.Alvo.slice(desde).some((m) => /segredo do fantasma/.test(m)), 'ninguém vê a mensagem de quem está invisível')
  await comando('Robo', '/vanish', /visível novamente/)

  // ---------- dinheiro por tempo online (no teste: 1 a cada 2 minutos) ----------
  checar(await esperarMsg('Alvo', /por jogar 2 minutos/, 0, 30000), 'quem joga ganha dinheiro por tempo online')

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
