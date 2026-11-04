/*
 * Teste da ponte sem WhatsApp e sem Minecraft de verdade:
 * sobe um servidor RCON falso e usa um "socket" do Baileys falso.
 * Uso: node teste/teste-local.cjs
 */
'use strict'
const net = require('node:net')
const { EventEmitter } = require('node:events')
const assert = require('node:assert')
const { criarPonte } = require('../minecraft-bridge.cjs')

const SENHA = 'senha-rcon'
const recebidos = []

// ---------- RCON falso (mesmo protocolo do Minecraft) ----------
function pacote (id, tipo, corpo) {
  const t = Buffer.from(corpo, 'utf8')
  const b = Buffer.alloc(14 + t.length)
  b.writeInt32LE(10 + t.length, 0); b.writeInt32LE(id, 4); b.writeInt32LE(tipo, 8); t.copy(b, 12)
  return b
}
const rconFalso = net.createServer((s) => {
  let buf = Buffer.alloc(0); let logado = false
  s.on('data', (d) => {
    buf = Buffer.concat([buf, d])
    while (buf.length >= 4) {
      const len = buf.readInt32LE(0); if (buf.length < len + 4) break
      const id = buf.readInt32LE(4); const tipo = buf.readInt32LE(8)
      const corpo = buf.subarray(12, 4 + len - 2).toString('utf8'); buf = buf.subarray(4 + len)
      if (tipo === 3) { logado = corpo === SENHA; s.write(pacote(logado ? id : -1, 2, '')); continue }
      if (!logado) continue
      recebidos.push(corpo)
      let resp = ''
      if (corpo === 'list') resp = '§6There are §c2§6 of a max of §c20§6 players online: Steve, Alex'
      else if (corpo === 'grande') resp = 'x'.repeat(4096) // mandado em 2 pacotes
      if (corpo === 'grande') { s.write(pacote(id, 0, resp)); s.write(pacote(id, 0, 'FIM')) } else s.write(pacote(id, 0, resp))
    }
  })
})

// ---------- Socket do Baileys falso ----------
function sockFalso () {
  const ev = new EventEmitter(); const enviados = []
  return { ev, enviados, sendMessage: async (jid, conteudo) => { enviados.push({ jid, texto: conteudo.text }) } }
}
function msg (texto, de, chat = '120363000000000000@g.us') {
  return { key: { remoteJid: chat, fromMe: false, participant: de }, message: { conversation: texto } }
}
const espera = (ms) => new Promise((r) => setTimeout(r, ms))

async function main () {
  await new Promise((r) => rconFalso.listen(0, '127.0.0.1', r))
  const portaRcon = rconFalso.address().port
  const GRUPO = '120363111111111111@g.us'
  const ADMIN = '5511999999999@s.whatsapp.net'
  const ponte = criarPonte({
    http: { host: '127.0.0.1', porta: 0, caminho: '/ultracore', token: 'segredo' },
    rcon: { host: '127.0.0.1', porta: portaRcon, senha: SENHA },
    grupoAvisos: GRUPO,
    admins: ['5511999999999', '99887766@lid'],
    log: () => {}
  })
  await new Promise((r) => ponte.servidor.once('listening', r))
  const portaHttp = ponte.servidor.address().port
  const sock = sockFalso(); ponte.conectar(sock)
  const ultimo = () => sock.enviados[sock.enviados.length - 1]?.texto || ''
  const post = (token, corpo) => fetch(`http://127.0.0.1:${portaHttp}/ultracore`, {
    method: 'POST', headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' }, body: JSON.stringify(corpo)
  })

  // 1) Servidor -> WhatsApp
  let r = await post('segredo', { event: 'punishments', message: 'Fulano foi banido', timestamp: 1 })
  assert.strictEqual(r.status, 204); await espera(50)
  assert.deepStrictEqual(sock.enviados[0], { jid: GRUPO, texto: '🔨 Fulano foi banido' })
  console.log('✔ aviso do servidor chega no grupo')

  r = await post('errado', { event: 'reports', message: 'não deveria chegar' })
  assert.strictEqual(r.status, 401); await espera(50)
  assert.strictEqual(sock.enviados.length, 1)
  console.log('✔ aviso com token errado é recusado')

  // 1b) feed do jogo: vai pro grupoChat, agrupado, sem emoji repetido
  const ponteFeed = criarPonte({ http: { host: '127.0.0.1', porta: 0, caminho: '/ultracore', token: 't' }, rcon: {},
    grupoAvisos: GRUPO, grupoChat: 'chat@g.us', intervaloFeedMs: 150, log: () => {} })
  await new Promise((r) => ponteFeed.servidor.once('listening', r))
  const sockFeed = sockFalso(); ponteFeed.conectar(sockFeed)
  const postFeed = (corpo) => fetch(`http://127.0.0.1:${ponteFeed.servidor.address().port}/ultracore`, {
    method: 'POST', headers: { Authorization: 'Bearer t' }, body: JSON.stringify(corpo) })
  await postFeed({ event: 'chat', message: '💬 Steve: oi' })
  await postFeed({ event: 'deaths', message: '💀 Alex was slain by Zombie' })
  await postFeed({ event: 'punishments', message: 'Bob foi banido' })
  await postFeed({ event: 'staffchat', message: '[STAFF] Ana: reunião às 20h' })
  await espera(300)
  assert.deepStrictEqual(sockFeed.enviados, [
    { jid: GRUPO, texto: '🔨 Bob foi banido' },
    { jid: GRUPO, texto: '🛡️ [STAFF] Ana: reunião às 20h' },
    { jid: 'chat@g.us', texto: '💬 Steve: oi\n💀 Alex was slain by Zombie' }
  ])
  ponteFeed.fechar()
  console.log('✔ feed do jogo vai pro grupoChat agrupado; punições pro grupoAvisos')

  // 2) WhatsApp -> Servidor
  sock.ev.emit('messages.upsert', { type: 'notify', messages: [msg('!online', 'qualquer@s.whatsapp.net')] }); await espera(400)
  assert.match(ultimo(), /There are 2 of a max of 20 players online: Steve, Alex/)
  assert.ok(!ultimo().includes('§'), 'cores removidas')
  console.log('✔ !online (público) consulta o servidor pelo RCON')

  sock.ev.emit('messages.upsert', { type: 'notify', messages: [msg('!cmd op Hacker', '5500000000000@s.whatsapp.net')] }); await espera(200)
  assert.match(ultimo(), /Só administradores/); assert.ok(!recebidos.includes('op Hacker'))
  console.log('✔ !cmd de quem não é admin é bloqueado')

  sock.ev.emit('messages.upsert', { type: 'notify', messages: [msg('!anunciar Evento às 20h', ADMIN)] }); await espera(400)
  assert.ok(recebidos.includes('broadcast Evento às 20h')); assert.match(ultimo(), /Anúncio enviado/)
  console.log('✔ !anunciar do admin (número) vira /broadcast')

  sock.ev.emit('messages.upsert', { type: 'notify', messages: [msg('!cmd /grande', '99887766@lid')] }); await espera(600)
  assert.ok(recebidos.includes('grande'), 'comando chegou sem a barra')
  assert.ok(ultimo().startsWith('🖥️ xxx'), 'admin @lid recebeu a resposta')
  const grande = await ponte.rcon.comando('grande')
  assert.strictEqual(grande, 'x'.repeat(4096) + 'FIM')
  console.log('✔ admin no formato @lid funciona e respostas grandes são juntadas')

  const antes = sock.enviados.length
  const qtdRcon = recebidos.length
  sock.ev.emit('messages.upsert', { type: 'notify', messages: [msg('oi pessoal', ADMIN), msg('!naoexiste', ADMIN)] }); await espera(300)
  assert.strictEqual(sock.enviados.length, antes)
  assert.strictEqual(recebidos.length, qtdRcon)
  console.log('✔ mensagens normais e comandos desconhecidos são ignorados')

  // 3) RCON com senha errada
  const ponteRuim = criarPonte({ http: { host: '127.0.0.1', porta: 0, token: 'x' }, rcon: { host: '127.0.0.1', porta: portaRcon, senha: 'errada' }, admins: [], log: () => {} })
  await assert.rejects(ponteRuim.rcon.comando('list'), /Senha do RCON incorreta/)
  ponteRuim.fechar()
  console.log('✔ senha errada do RCON dá erro claro')

  ponte.fechar(); rconFalso.close()
  console.log('\nTODOS OS TESTES PASSARAM')
}
main().catch((err) => { console.error('FALHOU:', err); process.exit(1) })
