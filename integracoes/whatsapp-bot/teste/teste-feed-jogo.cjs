/*
 * Teste de ponta a ponta do "feed do jogo" (GitHub Actions):
 * servidor Paper real + UltraCore + um jogador-robô (mineflayer).
 * Confere se chat, conquista e morte saem do plugin para o webhook.
 * Variáveis: RCON_PORTA, RCON_SENHA, HTTP_PORTA, TOKEN, MC_VERSAO
 */
'use strict'
const http = require('node:http')
const { Rcon } = require('../minecraft-bridge.cjs')
const mineflayer = require('mineflayer')

const recebidos = []
const pendentes = []   // comandos que o plugin vai buscar (papel do Frog)
const resultados = []  // saídas devolvidas pelo plugin
const espera = (ms) => new Promise((r) => setTimeout(r, ms))
async function aguardar (cond, ms, passo = 500) {
  const fim = Date.now() + ms
  while (Date.now() < fim) { if (await cond()) return true; await espera(passo) }
  return false
}
let falhas = 0
function checar (ok, nome) { console.log((ok ? '✔ ' : '✘ ') + nome); if (!ok) falhas++ }

const receptor = http.createServer((req, res) => {
  let b = ''
  req.on('data', (c) => { b += c })
  req.on('end', () => {
    if (req.headers.authorization !== 'Bearer ' + process.env.TOKEN) { res.writeHead(401).end(); return }
    if (req.url === '/ultracore/comandos') {
      const lote = pendentes.splice(0)
      res.writeHead(200, { 'Content-Type': 'application/json' }).end(JSON.stringify({ ok: true, comandos: lote }))
      return
    }
    const d = JSON.parse(b)
    if (req.url === '/ultracore/resultado') { resultados.push(d); console.log('  [resultado]', d.id, d.ok, '->', d.saida); res.writeHead(200).end('{}'); return }
    recebidos.push(d); console.log('  [webhook]', d.event, '->', d.message)
    res.writeHead(204).end()
  })
})

async function main () {
  await new Promise((r) => receptor.listen(Number(process.env.HTTP_PORTA), '127.0.0.1', r))
  const rcon = new Rcon({ host: '127.0.0.1', porta: Number(process.env.RCON_PORTA), senha: process.env.RCON_SENHA })
  const ligado = await aguardar(async () => { try { await rcon.comando('list'); return true } catch { return false } }, 6 * 60 * 1000, 3000)
  checar(ligado, 'servidor ligou (RCON)')
  if (!ligado) process.exit(1)

  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Robo', version: process.env.MC_VERSAO, auth: 'offline' })
  bot.on('error', (e) => console.log('  [bot] erro:', e.message))
  bot.on('kicked', (r) => console.log('  [bot] expulso:', JSON.stringify(r)))
  const entrou = await new Promise((resolve) => { bot.once('spawn', () => resolve(true)); setTimeout(() => resolve(false), 60000) })
  checar(entrou, 'jogador-robô entrou no servidor')
  if (!entrou) process.exit(1)
  await espera(2000)

  const tem = (ev, fn) => recebidos.some((r) => r.event === ev && fn(r.message))

  bot.chat('ola do robo pelo zap')
  checar(await aguardar(() => tem('chat', (m) => m.includes('Robo') && m.includes('ola do robo pelo zap')), 15000),
    'mensagem do chat do jogo chegou no webhook')

  await rcon.comando('advancement grant Robo only minecraft:story/mine_stone')
  checar(await aguardar(() => tem('advancements', (m) => m.includes('Robo')), 15000), 'conquista chegou no webhook')
  const conquista = recebidos.find((r) => r.event === 'advancements')
  checar(conquista && /Stone Age/i.test(conquista.message), 'conquista veio com o nome traduzido ("Stone Age"), não com código interno')

  await rcon.comando('advancement grant Robo only minecraft:recipes/misc/charcoal')
  await espera(3000)
  checar(!recebidos.some((r) => r.event === 'advancements' && /charcoal|recipes/i.test(r.message)), 'receitas (conquistas ocultas) não são enviadas')

  // comandos remotos: o plugin busca no "Frog", executa e devolve a saída
  pendentes.push({ id: 'c1', comando: 'list' }, { id: 'c2', comando: '/broadcast oi do zap' }, { id: 'c3', comando: 'comandoquenaoexiste' })
  checar(await aguardar(() => resultados.length >= 3, 20000), 'plugin buscou e devolveu os 3 comandos')
  const r = (id) => resultados.find((x) => x.id === id) || {}
  checar(r('c1').ok === true && /Robo/.test(r('c1').saida), 'saída do /list voltou com o jogador online')
  checar(r('c2').ok === true, '/broadcast pelo bot executou')
  checar(r('c3').ok === false, 'comando inexistente volta como falha')
  await espera(3000)
  checar(resultados.length === 3, 'cada comando executou uma vez só')

  await rcon.comando('kill Robo')
  checar(await aguardar(() => tem('deaths', (m) => m.includes('Robo')), 15000), 'morte chegou no webhook')

  bot.quit(); rcon.comando('stop').catch(() => {})
  await espera(1000); receptor.close()
  console.log(falhas ? `\n${falhas} TESTE(S) FALHARAM` : '\nTODOS OS TESTES PASSARAM')
  process.exit(falhas ? 1 : 0)
}
main().catch((e) => { console.error('FALHOU:', e); process.exit(1) })
