#!/usr/bin/env bash
# Liga um servidor Paper real com o UltraCore, executa comandos no console e procura erros.
# Uso: test-server.sh <versão-do-minecraft> <caminho-do-jar-do-plugin> [vault]
set -uo pipefail

VERSION="$1"
PLUGIN_JAR="$2"
WITH_VAULT="${3:-}"
UA="UltraCore-CI (github.com/nicolaunoya-design/Projetos)"
DIR="server-$VERSION${WITH_VAULT:+-vault}"
LABEL="$VERSION$([ -n "$WITH_VAULT" ] && echo ' + Vault' || echo ' (sem Vault)')"

mkdir -p "$DIR/plugins"
cp "$PLUGIN_JAR" "$DIR/plugins/"
cd "$DIR"

if [ -n "$WITH_VAULT" ]; then
  curl -fsSL -A "$UA" -o plugins/Vault.jar https://github.com/MilkBowl/Vault/releases/download/1.7.3/Vault.jar
fi

URL=$(curl -fsSL -A "$UA" "https://fill.papermc.io/v3/projects/paper/versions/$VERSION/builds/latest" \
  | jq -r '.downloads["server:default"].url')
if [ -z "$URL" ] || [ "$URL" = "null" ]; then
  echo "::error::Não encontrei build do Paper para $VERSION"
  exit 1
fi
echo "Baixando $URL"
curl -fsSL -A "$UA" -o paper.jar "$URL"

echo "eula=true" > eula.txt
cat > server.properties <<EOF
online-mode=false
level-type=minecraft\:flat
generate-structures=false
spawn-protection=0
EOF

mkfifo console
java -Xms1G -Xmx2G -jar paper.jar --nogui < console > server.log 2>&1 &
PID=$!
exec 3>console

# Espera o servidor terminar de iniciar (até 6 min)
for _ in $(seq 1 360); do
  if grep -q "Done (" server.log; then break; fi
  if ! kill -0 $PID 2>/dev/null; then break; fi
  sleep 1
done

if grep -q "Done (" server.log; then
  for cmd in "painel lista" "painel desativar scoreboard" "painel ativar scoreboard" \
             "painel desativar sleep" "painel ativar sleep" "painel reload" \
             "money Ninguem" "eco give Ninguem 10" "broadcast teste de compatibilidade" "limparitens" \
             "painel desativar economy" "painel ativar economy" \
             "warn Ninguem teste" "historico Ninguem" "painel desativar shop" "painel ativar shop" \
             "painel ativar discord" "painel desativar discord" "painel desativar nick" "painel ativar nick" \
             "tag lista" "tag criar lenda &5[Lenda]" "tag dar Ninguem lenda" "tag apagar lenda" \
             "painel desativar tags" "painel ativar tags" "painel padrao chat" "painel padrao tags" \
             "painel comando broadcast desativar" "broadcast nao deveria aparecer" "painel comando broadcast ativar" "silentjoin" \
             "esconder Ninguem" "escondernick Ninguem" "luz Ninguem" "painel desativar menus" "painel ativar menus" "painel comando top desativar" "painel comando top ativar" \
             "msgtoggle" "menu" "painel nether desativar" "painel nether ativar" "trovao" "trovao Ninguem" "explodir todos" "foguete todos" "painel desativar fun" "painel ativar fun" \
             "painel lista"; do
    echo "$cmd" >&3
    sleep 2
  done
  if [ -n "$WITH_VAULT" ]; then
    echo "vault-info" >&3
    sleep 3
  fi
  sleep 3
  echo "stop" >&3
fi

# Espera desligar (até 2 min)
for _ in $(seq 1 120); do
  kill -0 $PID 2>/dev/null || break
  sleep 1
done
kill -9 $PID 2>/dev/null
exec 3>&-

echo "================ LOG DO SERVIDOR ($LABEL) ================"
cat server.log
echo "============================================================"

FAIL=0
if ! grep -q "Done (" server.log; then
  echo "::error::[$VERSION] servidor não terminou de iniciar"
  FAIL=1
fi
if ! grep -q "UltraCore ativado" server.log; then
  echo "::error::[$VERSION] UltraCore não foi ativado"
  FAIL=1
fi
# Qualquer stack trace que passe pelo nosso pacote, ou erro citando o plugin
if grep -nE "at com\.ultracore\.|Error occurred while enabling UltraCore|Could not load 'plugins/UltraCore|Erro ao ativar o módulo|Erro ao desativar o módulo|Erro no comando" server.log; then
  echo "::error::[$VERSION] erros do UltraCore encontrados no log (veja acima)"
  FAIL=1
fi
if [ ! -f plugins/UltraCore/messages.yml ] || ! grep -q "moderation:" plugins/UltraCore/messages.yml; then
  echo "::error::[$VERSION] messages.yml não foi criado"
  FAIL=1
fi
if [ ! -f plugins/UltraCore/shop.yml ]; then
  echo "::error::[$VERSION] shop.yml não foi criado"
  FAIL=1
fi
for esperado in "Tag lenda criada" "Tag lenda apagada" "restauradas para o padrão" \
                "Comando /broadcast desligado" "Este comando está desativado" "Comando /broadcast ligado" \
                "Ninguém online" "Nether desligado" "Nether liberado"; do
  if ! grep -q "$esperado" server.log; then
    echo "::error::Não encontrei \"$esperado\" no log (tags / restaurar padrão / comandos)"
    FAIL=1
  fi
done
if ! grep -q "Módulos:" server.log; then
  echo "::error::[$VERSION] o comando /painel lista não respondeu"
  FAIL=1
fi

if [ -n "$WITH_VAULT" ]; then
  if ! grep -q "Economia do UltraCore registrada" server.log || ! grep -q "Economy: UltraCore" server.log; then
    echo "::error::[$LABEL] a economia do UltraCore não foi registrada no Vault"
    FAIL=1
  fi
else
  if grep -q "Economia do UltraCore registrada" server.log; then
    echo "::error::[$LABEL] tentou registrar no Vault sem o Vault instalado"
    FAIL=1
  fi
fi

if [ $FAIL -eq 0 ]; then
  echo "[$LABEL] OK - UltraCore funcionando"
  echo "| $LABEL | ✅ OK |" >> "$GITHUB_STEP_SUMMARY"
else
  echo "| $LABEL | ❌ Falhou |" >> "$GITHUB_STEP_SUMMARY"
fi
exit $FAIL
