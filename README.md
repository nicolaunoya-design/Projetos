# Projetos

## 🎮 Roube uma Adelaide

Jogo de navegador: compre Adelaides na passarela, junte dinheiro, roube das bases rivais e defenda a sua.
Funciona no PC (teclado) e no celular (joystick na tela).

- O jogo fica em [`roube-uma-adelaide/index.html`](roube-uma-adelaide/index.html) (um arquivo só, sem build).
- O `index.html` da raiz só redireciona para o jogo.
- `roube-uma-adelaide/peerjs.min.js` é a biblioteca [PeerJS](https://peerjs.com/) (licença MIT, ver `peerjs.LICENSE.txt`), usada pelo multiplayer.

### Publicar na Vercel (pelo GitHub)

1. Entre em <https://vercel.com> com a sua conta do GitHub.
2. Clique em **Add New… → Project** e escolha o repositório **Projetos** (se não aparecer, clique em *Adjust GitHub App Permissions* e libere o repositório).
3. Em **Framework Preset** deixe **Other**. Não precisa mudar *Build Command* nem *Output Directory*.
4. Clique em **Deploy**. Em um minuto aparece o link (algo como `projetos-xxxx.vercel.app`).

A Vercel publica o branch principal (`main`). Toda vez que algo novo entrar no `main`, o site atualiza sozinho.

> Também funciona no **GitHub Pages**: *Settings → Pages → Deploy from a branch → main / (root)*.

### Multiplayer

- **No site (Vercel/GitHub Pages):** cada jogador abre o link, digita o **mesmo código da sala** e clica em **🌐 Multiplayer**.
  A conexão é direta entre os navegadores (WebRTC), usando o servidor gratuito do PeerJS só para os jogadores se encontrarem.
  Até 6 jogadores por sala. Algumas redes muito fechadas (de escola/empresa) podem bloquear a conexão.
- **Dentro do claude.ai:** usa a sala da própria página do Claude (só para quem foi convidado pelo nome).
