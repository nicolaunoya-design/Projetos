/*
 * Exemplo mínimo de bot Baileys já com a ponte do Minecraft.
 * Serve de referência para encaixar a ponte no SEU bot (veja o README desta pasta).
 *
 *   npm install baileys pino qrcode-terminal
 *   cp minecraft-bridge.config.example.json minecraft-bridge.config.json   (e edite)
 *   node exemplo-bot.cjs
 */
'use strict'
const { criarPonte } = require('./minecraft-bridge.cjs')
const config = require('./minecraft-bridge.config.json')

// Cria a ponte UMA vez (ela sobe o servidor HTTP que recebe os avisos do Minecraft).
const ponte = criarPonte(config)

async function iniciar () {
  // O pacote novo se chama "baileys"; versões antigas usam "@whiskeysockets/baileys".
  let baileys
  try { baileys = await import('baileys') } catch { baileys = await import('@whiskeysockets/baileys') }
  const makeWASocket = baileys.default?.default || baileys.default || baileys.makeWASocket
  const { useMultiFileAuthState, DisconnectReason } = baileys
  const qrcode = require('qrcode-terminal')
  const pino = require('pino')

  const { state, saveCreds } = await useMultiFileAuthState('auth_info')
  const sock = makeWASocket({ auth: state, logger: pino({ level: 'warn' }) })
  sock.ev.on('creds.update', saveCreds)
  sock.ev.on('connection.update', ({ connection, lastDisconnect, qr }) => {
    if (qr) qrcode.generate(qr, { small: true })
    if (connection === 'open') console.log('WhatsApp conectado!')
    if (connection === 'close') {
      const deslogado = lastDisconnect?.error?.output?.statusCode === DisconnectReason.loggedOut
      if (!deslogado) setTimeout(iniciar, 3000)
      else console.log('Sessão encerrada. Apague a pasta auth_info e escaneie o QR de novo.')
    }
  })

  // Liga a ponte a este socket (repita sempre que o Baileys reconectar).
  ponte.conectar(sock)
}

iniciar()
