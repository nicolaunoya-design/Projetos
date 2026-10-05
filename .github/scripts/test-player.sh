#!/usr/bin/env bash
# Servidor Paper real + UltraCore + dois jogadores-robô (mineflayer): cooldowns, diversão secreta,
# menus (abrindo e clicando), painel, editor da loja e vanish ao relogar.
# Uso: test-player.sh <versão-do-minecraft> <jar-do-plugin>
set -uo pipefail
VERSION="$1"
PLUGIN_JAR="$(realpath "$2")"
ROOT="$(pwd)"
UA="UltraCore-CI (github.com/nicolaunoya-design/Projetos)"
DIR="player-$VERSION"
mkdir -p "$DIR/plugins/UltraCore"
cp "$PLUGIN_JAR" "$DIR/plugins/"
(cd "$ROOT/.github/scripts" && npm install --no-save --no-audit --no-fund mineflayer@4 > /dev/null 2>&1) || { echo "::error::npm install mineflayer falhou"; exit 1; }
cd "$DIR"
URL=$(curl -fsSL -A "$UA" "https://fill.papermc.io/v3/projects/paper/versions/$VERSION/builds/latest" | jq -r '.downloads["server:default"].url')
curl -fsSL -A "$UA" -o paper.jar "$URL"
echo "eula=true" > eula.txt
cat > server.properties <<PROPS
online-mode=false
level-type=minecraft\:flat
generate-structures=false
spawn-protection=0
spawn-monsters=false
enable-rcon=true
rcon.port=25575
rcon.password=senha-ci
PROPS
# teleportes sem espera para o teste ser rápido (os cooldowns ficam no padrão: 30s no /spawn e no /tpa)
cat > plugins/UltraCore/config.yml <<CFG
modules:
  spawn:
    delay: 0
  homes:
    delay: 0
  warps:
    delay: 0
  tpa:
    delay: 0
  afk:
    auto-afk-minutes: 0
CFG
RCON_PORTA=25575 RCON_SENHA=senha-ci MC_VERSAO="$VERSION" \
  node "$ROOT/.github/scripts/teste-jogadores.cjs" > teste.log 2>&1 &
TESTE=$!
sleep 1
java -Xms1G -Xmx2G -jar paper.jar --nogui < /dev/null > server.log 2>&1 &
SERVER=$!
wait $TESTE
RESULT=$?
for _ in $(seq 1 90); do kill -0 $SERVER 2>/dev/null || break; sleep 1; done
kill -9 $SERVER 2>/dev/null
echo "=============== TESTE COM JOGADORES ($VERSION) ==============="; cat teste.log
# erros do servidor viram anotações (no máximo 5)
grep -n "esconder-debug" server.log | head -5 | while read -r L; do echo "::warning::${L:0:400}"; done
grep -nE "Exception|ERROR\]|at com\.ultracore\." server.log | grep -v "No key layers" | head -5 | while read -r L; do echo "::warning::servidor: ${L:0:400}"; done
echo "=============== LOG DO SERVIDOR ($VERSION) ==============="; tail -150 server.log
if grep -nE "at com\.ultracore\.|Erro no comando|Erro ao ativar o módulo" server.log; then echo "::error::erros do UltraCore no log"; RESULT=1; fi
if [ $RESULT -eq 0 ]; then echo "| Jogadores-robô $VERSION (menus, cooldowns, diversão) | ✅ OK |" >> "$GITHUB_STEP_SUMMARY"; else echo "| Jogadores-robô $VERSION | ❌ Falhou |" >> "$GITHUB_STEP_SUMMARY"; fi
exit $RESULT
