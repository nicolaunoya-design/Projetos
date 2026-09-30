#!/usr/bin/env bash
# Servidor Paper real + UltraCore + jogador-robô (mineflayer): chat, conquistas e mortes.
# Uso: test-feed.sh <versão-do-minecraft> <jar-do-plugin>
set -uo pipefail
VERSION="$1"
PLUGIN_JAR="$(realpath "$2")"
ROOT="$(pwd)"
UA="UltraCore-CI (github.com/nicolaunoya-design/Projetos)"
DIR="feed-$VERSION"
mkdir -p "$DIR/plugins/UltraCore"
cp "$PLUGIN_JAR" "$DIR/plugins/"
(cd "$ROOT/integracoes/whatsapp-bot" && npm install --no-save --no-audit --no-fund mineflayer@4 > /dev/null 2>&1) || { echo "::error::npm install mineflayer falhou"; exit 1; }
cd "$DIR"
URL=$(curl -fsSL -A "$UA" "https://fill.papermc.io/v3/projects/paper/versions/$VERSION/builds/latest" | jq -r '.downloads["server:default"].url')
curl -fsSL -A "$UA" -o paper.jar "$URL"
echo "eula=true" > eula.txt
cat > server.properties <<PROPS
online-mode=false
level-type=minecraft\:flat
generate-structures=false
spawn-protection=0
enable-rcon=true
rcon.port=25575
rcon.password=senha-ci
PROPS
cat > plugins/UltraCore/config.yml <<CFG
modules:
  webhook:
    enabled: true
    url: http://127.0.0.1:3998/ultracore
    token: segredo-ci
    chat: true
CFG
RCON_PORTA=25575 RCON_SENHA=senha-ci HTTP_PORTA=3998 TOKEN=segredo-ci MC_VERSAO="$VERSION" \
  node "$ROOT/integracoes/whatsapp-bot/teste/teste-feed-jogo.cjs" > teste.log 2>&1 &
TESTE=$!
sleep 1
java -Xms1G -Xmx2G -jar paper.jar --nogui < /dev/null > server.log 2>&1 &
SERVER=$!
wait $TESTE
RESULT=$?
for _ in $(seq 1 90); do kill -0 $SERVER 2>/dev/null || break; sleep 1; done
kill -9 $SERVER 2>/dev/null
echo "=============== TESTE DO FEED ($VERSION) ==============="; cat teste.log
echo "=============== LOG DO SERVIDOR ($VERSION) ==============="; tail -60 server.log
if grep -nE "at com\.ultracore\." server.log; then echo "::error::erros do UltraCore no log"; RESULT=1; fi
if [ $RESULT -eq 0 ]; then echo "| Feed do jogo $VERSION | ✅ OK |" >> "$GITHUB_STEP_SUMMARY"; else echo "| Feed do jogo $VERSION | ❌ Falhou |" >> "$GITHUB_STEP_SUMMARY"; fi
exit $RESULT
