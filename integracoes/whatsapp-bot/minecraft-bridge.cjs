/*
 * Ponte entre um bot de WhatsApp (Baileys) e um servidor de Minecraft com o UltraCore.
 *
 *  - Servidor -> WhatsApp: recebe os avisos do módulo "Bot do WhatsApp (Webhook)" do UltraCore
 *    por HTTP e manda no grupo configurado.
 *  - WhatsApp -> Servidor: comandos como !online, !anunciar e !cmd são executados no servidor
 *    pelo RCON (vem no próprio Minecraft, ative no server.properties).
 *
 * Sem dependências: usa só módulos do Node (18+).
 *
 * Uso no seu bot:
 *   const { criarPonte } = require('./minecraft-bridge.cjs')
 *   const ponte = criarPonte(require('./minecraft-bridge.config.json'))   // uma vez só
 *   ...
 *   const sock = makeWASocket({ ... })
 *   ponte.conectar(sock)   // toda vez que o Baileys criar/recriar a conexão
 */
'use strict'

const http = require('node:http')
const net = require('node:net')

// ---------------------------------------------------------------------------
// RCON (protocolo do Minecraft)
// ---------------------------------------------------------------------------

const TIPO_COMANDO = 2
const TIPO_LOGIN = 3

class Rcon {
  constructor ({ host = '127.0.0.1', porta = 25575, senha = '', timeout = 8000 } = {}) {
    this.host = host
    this.porta = porta
    this.senha = senha
    this.timeout = timeout
    this.socket = null
    this.buffer = Buffer.alloc(0)
    this.seq = 10
    this.espera = new Map() // id -> { partes, resolver, rejeitar, timer }
    this.fila = Promise.resolve()
  }

  _pacote (id, tipo, corpo) {
    const texto = Buffer.from(corpo, 'utf8')
    const buf = Buffer.alloc(14 + texto.length)
    buf.writeInt32LE(10 + texto.length, 0)
    buf.writeInt32LE(id, 4)
    buf.writeInt32LE(tipo, 8)
    texto.copy(buf, 12)
    return buf // os dois últimos bytes já são zero
  }

  _aoReceber (dados) {
    this.buffer = Buffer.concat([this.buffer, dados])
    while (this.buffer.length >= 4) {
      const tamanho = this.buffer.readInt32LE(0)
      if (this.buffer.length < tamanho + 4) break
      const id = this.buffer.readInt32LE(4)
      const corpo = this.buffer.subarray(12, 4 + tamanho - 2).toString('utf8')
      this.buffer = this.buffer.subarray(4 + tamanho)
      this._aoPacote(id, corpo)
    }
  }

  _aoPacote (id, corpo) {
    if (id === -1) { // senha errada
      for (const [, e] of this.espera) { clearTimeout(e.timer); e.rejeitar(new Error('Senha do RCON incorreta')) }
      this.espera.clear()
      this.fechar()
      return
    }
    const e = this.espera.get(id)
    if (!e) return
    e.partes.push(corpo)
    // respostas grandes vêm em vários pacotes: espera um pouco antes de concluir
    clearTimeout(e.timer)
    e.timer = setTimeout(() => this._concluir(id), 150)
  }

  _concluir (id) {
    const e = this.espera.get(id)
    if (!e) return
    this.espera.delete(id)
    e.resolver(e.partes.join(''))
  }

  _pedido (tipo, corpo) {
    return new Promise((resolve, reject) => {
      const id = ++this.seq
      const timer = setTimeout(() => {
        this.espera.delete(id)
        reject(new Error('O servidor não respondeu pelo RCON (timeout)'))
      }, this.timeout)
      this.espera.set(id, { partes: [], resolver: resolve, rejeitar: reject, timer })
      this.socket.write(this._pacote(id, tipo, corpo))
    })
  }

  async _conectar () {
    if (this.socket) return
    await new Promise((resolve, reject) => {
      const s = net.createConnection({ host: this.host, port: this.porta })
      const falhar = (err) => { s.destroy(); reject(err) }
      s.setTimeout(this.timeout, () => falhar(new Error('Tempo esgotado conectando ao RCON')))
      s.once('error', falhar)
      s.once('connect', () => {
        s.setTimeout(0)
        s.removeListener('error', falhar)
        s.on('error', () => this.fechar())
        s.on('close', () => this.fechar())
        s.on('data', (d) => this._aoReceber(d))
        this.socket = s
        resolve()
      })
    })
    try {
      await this._pedido(TIPO_LOGIN, this.senha)
    } catch (err) {
      this.fechar()
      throw err
    }
  }

  /** Executa um comando no console do servidor e devolve a resposta (sem cores). */
  comando (cmd) {
    const tarefa = this.fila.then(async () => {
      await this._conectar()
      const resposta = await this._pedido(TIPO_COMANDO, cmd.replace(/^\//, ''))
      return limparCores(resposta).trim()
    })
    this.fila = tarefa.catch(() => {})
    return tarefa
  }

  fechar () {
    if (this.socket) {
      this.socket.removeAllListeners()
      this.socket.destroy()
    }
    this.socket = null
    this.buffer = Buffer.alloc(0)
  }
}

function limparCores (texto) {
  return String(texto).replace(/§[0-9a-fk-orx]/gi, '')
}

// ---------------------------------------------------------------------------
// Ponte
// ---------------------------------------------------------------------------

const EMOJIS = {
  punishments: '🔨',
  reports: '🚨',
  staffchat: '🛡️',
  'join-quit': '🚪',
  chat: '💬',
  advancements: '🏆',
  deaths: '💀',
  'server-start': ''
}

/** Eventos do "feed do jogo": vão para o grupoChat (se configurado) e são agrupados. */
const FEED = new Set(['chat', 'advancements', 'deaths', 'join-quit'])

function textoDaMensagem (m) {
  const msg = m.message || {}
  return msg.conversation ||
    msg.extendedTextMessage?.text ||
    msg.imageMessage?.caption ||
    msg.videoMessage?.caption ||
    ''
}

/** Todos os identificadores possíveis de quem mandou (número normal e o novo formato "@lid"). */
function idsDoRemetente (m) {
  const k = m.key || {}
  return [k.participant, k.participantPn, k.participantAlt, k.remoteJid, k.remoteJidAlt, k.senderPn, m.participant]
    .filter(Boolean)
}

function somenteNumero (jid) {
  return String(jid).split('@')[0].split(':')[0].replace(/\D/g, '')
}

function criarPonte (config = {}) {
  const cfg = {
    http: { host: '0.0.0.0', porta: 3000, caminho: '/ultracore', token: '', ...(config.http || {}) },
    rcon: { host: '127.0.0.1', porta: 25575, senha: '', ...(config.rcon || {}) },
    grupoAvisos: config.grupoAvisos || '',
    grupoChat: config.grupoChat || '',
    intervaloFeedMs: config.intervaloFeedMs ?? 4000,
    admins: config.admins || [],
    comandosPublicos: config.comandosPublicos || ['online', 'ajuda'],
    prefixo: config.prefixo || '!',
    aceitarDeMim: config.aceitarDeMim ?? true,
    ouvirMensagens: config.ouvirMensagens ?? true,
    log: config.log || ((...a) => console.log('[ponte-minecraft]', ...a))
  }
  if (!cfg.http.token || cfg.http.token === 'troque-esta-senha') {
    cfg.log('AVISO: defina um "token" próprio em http.token (e o mesmo no /painel do UltraCore).')
  }

  const rcon = new Rcon(cfg.rcon)
  let sock = null

  function ehAdmin (m) {
    const ids = idsDoRemetente(m)
    if (m.key?.fromMe) return true // mensagens do próprio número do bot
    return cfg.admins.some((a) => {
      const alvo = String(a)
      return ids.some((id) => id === alvo || (somenteNumero(alvo) && somenteNumero(id) === somenteNumero(alvo)))
    })
  }

  async function enviar (jid, texto, citar) {
    if (!sock) throw new Error('bot do WhatsApp ainda não conectado')
    return sock.sendMessage(jid, { text: texto }, citar ? { quoted: citar } : undefined)
  }

  // ---------- Servidor -> WhatsApp ----------

  // O feed do jogo (chat, conquistas, mortes) é juntado em uma mensagem a cada
  // poucos segundos, para não mandar dezenas de mensagens seguidas (risco de ban do número).
  const filas = new Map()
  function enfileirar (jid, linha) {
    let f = filas.get(jid)
    if (!f) { f = { linhas: [], timer: null }; filas.set(jid, f) }
    f.linhas.push(linha)
    if (f.linhas.length >= 25) return despejar(jid)
    if (!f.timer) f.timer = setTimeout(() => despejar(jid), cfg.intervaloFeedMs)
  }
  async function despejar (jid) {
    const f = filas.get(jid)
    if (!f) return
    clearTimeout(f.timer)
    filas.delete(jid)
    try {
      await enviar(jid, f.linhas.join('\n'))
    } catch (err) {
      cfg.log('Não consegui mandar o feed no WhatsApp:', err.message)
    }
  }

  const servidor = http.createServer((req, res) => {
    const caminho = (req.url || '').split('?')[0]
    if (req.method !== 'POST' || caminho !== cfg.http.caminho) {
      res.writeHead(404).end()
      return
    }
    if ((req.headers.authorization || '') !== 'Bearer ' + cfg.http.token) {
      cfg.log('Aviso recusado: token inválido vindo de', req.socket.remoteAddress)
      res.writeHead(401).end()
      return
    }
    let corpo = ''
    req.setEncoding('utf8')
    req.on('data', (parte) => {
      corpo += parte
      if (corpo.length > 64 * 1024) req.destroy()
    })
    req.on('end', async () => {
      let dados
      try {
        dados = JSON.parse(corpo)
      } catch {
        res.writeHead(400).end()
        return
      }
      res.writeHead(204).end()
      const feed = FEED.has(dados.event)
      const alvo = (feed && cfg.grupoChat) || cfg.grupoAvisos
      if (!alvo) {
        cfg.log('Aviso recebido, mas "grupoAvisos" não está configurado. Use !grupoid no grupo para descobrir o ID.')
        return
      }
      const msg = String(dados.message || '').trim()
      // não repete o emoji se a mensagem já começa com um (ex: "💬 Steve: oi")
      const texto = /^\p{Extended_Pictographic}/u.test(msg) ? msg : `${EMOJIS[dados.event] ?? '📢'} ${msg}`.trim()
      if (feed) return enfileirar(alvo, texto)
      try {
        await enviar(alvo, texto)
      } catch (err) {
        cfg.log('Não consegui mandar o aviso no WhatsApp:', err.message)
      }
    })
  })
  servidor.listen(cfg.http.porta, cfg.http.host, () => {
    cfg.log(`Recebendo avisos do servidor em http://${cfg.http.host}:${cfg.http.porta}${cfg.http.caminho}`)
  })

  // ---------- WhatsApp -> Servidor ----------

  const ajuda = () => [
    '🎮 *Comandos do servidor*',
    `${cfg.prefixo}online — quem está online`,
    `${cfg.prefixo}anunciar <texto> — anúncio no servidor (admin)`,
    `${cfg.prefixo}cmd <comando> — roda qualquer comando (admin)`,
    `${cfg.prefixo}grupoid — mostra o ID deste chat (admin)`
  ].join('\n')

  /** Trata uma mensagem do Baileys. Retorna true se era um comando da ponte. */
  async function processarMensagem (m) {
    if (!m || !m.message) return false
    if (m.key?.fromMe && !cfg.aceitarDeMim) return false
    const texto = textoDaMensagem(m).trim()
    if (!texto.startsWith(cfg.prefixo)) return false
    const [nome, ...resto] = texto.slice(cfg.prefixo.length).split(/\s+/)
    const comando = (nome || '').toLowerCase()
    const args = resto.join(' ').trim()
    const chat = m.key.remoteJid
    const conhecidos = ['online', 'anunciar', 'cmd', 'grupoid', 'ajuda']
    if (!conhecidos.includes(comando)) return false

    if (!cfg.comandosPublicos.includes(comando) && !ehAdmin(m)) {
      cfg.log(`Comando negado para ${idsDoRemetente(m).join(' / ')} — adicione um desses em "admins" se for você.`)
      await enviar(chat, '⛔ Só administradores podem usar esse comando.', m)
      return true
    }

    try {
      switch (comando) {
        case 'ajuda':
          await enviar(chat, ajuda(), m)
          break
        case 'grupoid':
          await enviar(chat, `🆔 ID deste chat: ${chat}`, m)
          break
        case 'online': {
          const r = await rcon.comando('list')
          await enviar(chat, `👥 ${r || 'Sem resposta do servidor.'}`, m)
          break
        }
        case 'anunciar':
          if (!args) {
            await enviar(chat, `Uso: ${cfg.prefixo}anunciar <texto>`, m)
            break
          }
          await rcon.comando('broadcast ' + args)
          await enviar(chat, '📢 Anúncio enviado no servidor.', m)
          break
        case 'cmd': {
          if (!args) {
            await enviar(chat, `Uso: ${cfg.prefixo}cmd <comando>`, m)
            break
          }
          const r = await rcon.comando(args)
          await enviar(chat, '🖥️ ' + (r ? r.slice(0, 3500) : '(comando executado, sem resposta)'), m)
          break
        }
      }
    } catch (err) {
      cfg.log('Erro no comando', comando, '-', err.message)
      await enviar(chat, '⚠️ Não consegui falar com o servidor: ' + err.message, m).catch(() => {})
    }
    return true
  }

  function conectar (novoSock) {
    sock = novoSock
    if (cfg.ouvirMensagens) {
      novoSock.ev.on('messages.upsert', ({ messages, type }) => {
        if (type !== 'notify') return
        for (const m of messages) processarMensagem(m).catch((err) => cfg.log('Erro:', err.message))
      })
    }
  }

  function fechar () {
    servidor.close()
    rcon.fechar()
  }

  return { conectar, processarMensagem, rcon, servidor, fechar }
}

module.exports = { criarPonte, Rcon, limparCores }
