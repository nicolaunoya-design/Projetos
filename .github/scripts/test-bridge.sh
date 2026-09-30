#!/usr/bin/env bash
# Servidor Paper real + UltraCore + ponte do WhatsApp (WhatsApp simulado).
# Uso: test-bridge.sh <versão-do-minecraft> <jar-do-plugin>
set -uo pipefail
VERSION="$1"
PLUGIN_JAR="$(realpath "$2")"
ROOT="$(pwd)"
UA="UltraCore-CI (github.com/nicolaunoya-design/Projetos)"
DIR="bridge-$VERSION"
mkdir -p "$DIR/plugins/UltraCore"
cp "$PLUGIN_JAR" "$DIR/plugins/"
cd "$DIR"

URL=$(curl -fsSL -A "$UA" "https://fill.papermc.io/v3/projects/paper/versions/$VERSION/builds/latest" | jq -r '.downloads["server:default"].url')
curl -fsSL -A "$UA" -o paper.jar "$URL"
echo "eula=true" > eula.txt
cat > server.properties <<PROPS
online-mode=false
level-type=minecraft\:flat
generate-structures=false
enable-rcon=true
rcon.port=25575
rcon.password=senha-ci
PROPS
# Liga o módulo "Bot do WhatsApp" apontando para a ponte
cat > plugins/UltraCore/config.yml <<CFG
modules:
  webhook:
    enabled: true
    url: http://127.0.0.1:3999/ultracore
    token: segredo-ci
    staffchat: true
CFG

RCON_PORTA=25575 RCON_SENHA=senha-ci HTTP_PORTA=3999 TOKEN=segredo-ci \
  node "$ROOT/integracoes/whatsapp-bot/teste/teste-servidor-real.cjs" > ponte.log 2>&1 &
PONTE=$!
sleep 1
java -Xms1G -Xmx2G -jar paper.jar --nogui < /dev/null > server.log 2>&1 &
SERVER=$!

wait $PONTE
RESULT=$?
for _ in $(seq 1 90); do kill -0 $SERVER 2>/dev/null || break; sleep 1; done
kill -9 $SERVER 2>/dev/null

echo "=============== LOG DA PONTE ($VERSION) ==============="; cat ponte.log
echo "=============== LOG DO SERVIDOR ($VERSION) ==============="; cat server.log
if grep -nE "at com\.ultracore\." server.log; then echo "::error::erros do UltraCore no log"; RESULT=1; fi
if [ $RESULT -eq 0 ]; then echo "| Ponte WhatsApp $VERSION | ✅ OK |" >> "$GITHUB_STEP_SUMMARY"; else echo "| Ponte WhatsApp $VERSION | ❌ Falhou |" >> "$GITHUB_STEP_SUMMARY"; fi
exit $RESULT
