#!/usr/bin/env bash
# Confere na Mojang que toda textura da lista de cabeças (src/main/resources/cabecas.txt) existe e é uma skin (PNG).
set -uo pipefail
FILE="${1:-src/main/resources/cabecas.txt}"
TMP="$(mktemp -d)"
FAIL=0
N=0
while read -r MOB TEX; do
  [[ -z "${MOB:-}" || "$MOB" == \#* ]] && continue
  N=$((N + 1))
  if [[ "$TEX" =~ ^[0-9a-fA-F]{40,64}$ ]]; then URL="http://textures.minecraft.net/texture/$TEX"; else URL="$TEX"; fi
  if ! curl -fsS --retry 3 --retry-delay 2 --max-time 30 -o "$TMP/$MOB.png" "$URL"; then
    echo "::error::cabeça de $MOB: a Mojang não tem a textura $URL"; FAIL=1; continue
  fi
  INFO="$(file -b "$TMP/$MOB.png")"
  if [[ "$INFO" != PNG\ image\ data,\ 64\ x\ 64* && "$INFO" != PNG\ image\ data,\ 64\ x\ 32* ]]; then
    echo "::error::cabeça de $MOB: $URL não é uma skin 64x64/64x32 ($INFO)"; FAIL=1; continue
  fi
  echo "ok  $MOB  ($INFO)"
  sleep 0.3
done < "$FILE"
echo "$N texturas conferidas."
[ "$N" -gt 0 ] || { echo "::error::lista de cabeças vazia"; FAIL=1; }
exit $FAIL
