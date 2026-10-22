/*
 * Teste de ponta a ponta usado no GitHub Actions: servidor Paper real + UltraCore + RCON.
 * Só o WhatsApp é simulado. Variáveis: RCON_PORTA, RCON_SENHA, HTTP_PORTA, TOKEN.
 */
'use strict'
const { EventEmitter } = require('node:events')
const { criarPonte } = require('../minecraft-bridge.cjs')

const GRUPO = '120363000000000000@g.us'
const ADMIN = '5511999999999@s.whatsapp.net'
const enviados = []
const sock = { ev: new EventEmitter(), sendMessage: async (jid, c) => { enviados.push({ jid, texto: c.text }); console.log('  [whatsapp]', jid, '->', c.text) } }
const espera = (ms) => new Promise((r) => setTimeout(r, ms))
const ultimo = () => enviados[enviados.length - 1]?.texto || ''
let falhas = 0
function checar (ok, nome) { console.log((ok ? '✔ ' : '✘ ') + nome); if (!ok) falhas++ }

async function aguardar (cond, ms, passo = 1000) {
  const fim = Date.now() + ms
  while (Date.now() < fim) { if (await cond()) return true; await espera(passo) }
  return false
}

async function comandoWhats (texto) {
  const antes = enviados.length
  sock.ev.emit('messages.upsert', { type: 'notify', messages: [{ key: { remoteJid: GRUPO, fromMe: false, participant: ADMIN }, message: { conversation: texto } }] })
  await aguardar(() => enviados.length > antes, 15000, 200)
  return ultimo()
}

async function main () {
  const ponte = criarPonte({
    http: { host: '127.0.0.1', porta: Number(process.env.HTTP_PORTA), caminho: '/ultracore', token: process.env.TOKEN },
    rcon: { host: '127.0.0.1', porta: Number(process.env.RCON_PORTA), senha: process.env.RCON_SENHA },
    grupoAvisos: GRUPO,
    admins: ['5511999999999']
  })
  ponte.conectar(sock)

  console.log('Esperando o servidor ligar (RCON)...')
  const rconOk = await aguardar(async () => { try { await ponte.rcon.comando('list'); return true } catch { return false } }, 6 * 60 * 1000, 3000)
  checar(rconOk, 'RCON conectou no servidor real')
  if (!rconOk) process.exit(1)

  // Servidor -> WhatsApp: o UltraCore avisa que ligou
  const ligou = await aguardar(() => enviados.some((e) => e.jid === GRUPO && e.texto.includes('Servidor online')), 30000)
  checar(ligou, 'aviso "servidor ligado" do UltraCore chegou no grupo')

  // WhatsApp -> Servidor
  checar(/players online/i.test(await comandoWhats('!online')), '!online retorna a lista de jogadores')
  checar(/Anúncio enviado/.test(await comandoWhats('!anunciar Teste da ponte')), '!anunciar executa /broadcast')
  checar(/Módulos:/.test(await comandoWhats('!cmd painel lista')), '!cmd painel lista mostra os módulos do UltraCore')

  // Ida e volta: comando pelo WhatsApp gera um aviso do servidor de volta no grupo
  const antes = enviados.length
  await comandoWhats('!cmd staffchat oi da staff pelo zap')
  const voltou = await aguardar(() => enviados.slice(antes).some((e) => e.texto.includes('oi da staff pelo zap') && e.texto.startsWith('🛡️')), 15000)
  checar(voltou, 'staffchat enviado pelo WhatsApp volta como aviso no grupo')

  await ponte.rcon.comando('stop').catch(() => {})
  ponte.fechar()
  console.log(falhas ? `\n${falhas} TESTE(S) FALHARAM` : '\nTODOS OS TESTES PASSARAM')
  process.exit(falhas ? 1 : 0)
}
main().catch((err) => { console.error('FALHOU:', err); process.exit(1) })
