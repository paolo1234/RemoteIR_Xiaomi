#!/bin/bash
# IRXiaomi - Download database LIRC completo
# Esegui: bash scripts/download_lirc.sh
# Richiede: curl, wget, o BusyBox

DIR="app/src/main/assets/lirc"
mkdir -p "$DIR"

echo "=== Download database LIRC ==="
echo "Scarica tutti i telecomandi dal database LIRC (SourceForge)."
echo ""

if command -v curl &>/dev/null; then
  echo "Usando curl..."

  # Ottieni lista marche dalla pagina principale
  BRANDS=$(curl -sL "https://lirc.sourceforge.net/remotes/" \
    | grep -oP 'href="\K[^"]+(?=/")' \
    | grep -v "Parent\|^$")

  TOTAL=$(echo "$BRANDS" | wc -l)
  COUNT=0

  for brand in $BRANDS; do
    COUNT=$((COUNT + 1))
    echo "[$COUNT/$TOTAL] $brand..."

    curl -sL "https://lirc.sourceforge.net/remotes/$brand/" \
      | grep -oP 'href="\K[^"]+(?=")' \
      | grep -v "?\|/\|Parent\|blank\|back\|^$\|\.\." \
      | while read file; do
        [ -f "$DIR/${brand}_${file}" ] && continue
        curl -sL "https://lirc.sourceforge.net/remotes/$brand/$file" \
          -o "$DIR/${brand}_${file}" 2>/dev/null
      done
  done

elif command -v wget &>/dev/null; then
  echo "Usando wget (ricorsivo)..."
  wget -r -np -nH --cut-dirs=2 -P "$DIR" \
    --accept "*.conf,*.lircd,*.lircrc,*.irman,*.tira" \
    --reject "index.html*" \
    -e robots=off \
    https://lirc.sourceforge.net/remotes/

else
  echo "ERRORE: né curl né wget trovati."
  echo "Installa curl o wget e riprova."
  exit 1
fi

echo ""
echo "=== DOWNLOAD COMPLETATO ==="
find "$DIR" -type f 2>/dev/null | wc -l
du -sh "$DIR" 2>/dev/null
