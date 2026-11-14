#!/usr/bin/env bash
# Série de tests locale du mod XPack.
#
#   scripts/test-local.sh            vérifications rapides, hors ligne (défaut)
#   scripts/test-local.sh --jeu      en plus : lance le vrai client et surveille crash, erreur et gel
#   scripts/test-local.sh --jeu-seul uniquement le test en jeu
#
# Le test en jeu a besoin du réseau au premier lancement (le jeu télécharge ses ressources).
# Tout est écrit dans scripts/.sortie/ , ignoré par git.
set -u

RACINE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SORTIE="$RACINE/scripts/.sortie"
JOURNAL="$SORTIE/jeu.log"
GEL_S="${XPACK_GEL_S:-150}"        # gel : aucune ligne de journal pendant ce temps
LIMITE_S="${XPACK_LIMITE_S:-900}"  # durée totale maximale du test en jeu

RAPIDE=1
JEU=0
case "${1:-}" in
    --jeu) JEU=1 ;;
    --jeu-seul) JEU=1; RAPIDE=0 ;;
    --aide|-h) sed -n '2,9p' "${BASH_SOURCE[0]}" | cut -c3-; exit 0 ;;
    "") ;;
    *) echo "Option inconnue : $1 (voir --aide)"; exit 2 ;;
esac

mkdir -p "$SORTIE"
ECHECS=0
AVERTS=0

titre() { printf '\n== %s\n' "$*"; }
ok()    { printf '  OK    %s\n' "$*"; }
echec() { printf '  ECHEC %s\n' "$*"; ECHECS=$((ECHECS + 1)); }
avert() { printf '  ATTN  %s\n' "$*"; AVERTS=$((AVERTS + 1)); }

# Cherche un JDK d'une version donnée ; renvoie vide s'il n'y en a pas.
trouve_jdk() {
    local version="$1" c
    for c in "/c/Program Files/Eclipse Adoptium"/jdk-"$version"* "/c/Program Files/Java"/jdk-"$version"* \
             /usr/lib/jvm/*"-$version"-* /usr/lib/jvm/java-"$version"-*; do
        if [ -x "$c/bin/java" ] || [ -x "$c/bin/java.exe" ]; then echo "$c"; return; fi
    done
}

# --------------------------------------------------------------------- rapide
if [ "$RAPIDE" = 1 ]; then
    titre "Marqueurs de conflit laissés dans le code"
    restes="$(git -C "$RACINE" grep -lE '^(<<<<<<<|>>>>>>>) ' -- . ':!*/build/*' 2>/dev/null || true)"
    if [ -n "$restes" ]; then echec "marqueurs de fusion : $restes"; else ok "aucun"; fi

    titre "Clés de langue (une clé manquante affiche « xpack.settings.x » comme libellé)"
    LANG_DIR="$RACINE/shared/src/main/resources/assets/xpack/lang"
    grep -oE '"xpack\.[^"]*"' "$LANG_DIR/en_us.json" | tr -d '"' | sort -u > "$SORTIE/cles-en.txt"
    grep -oE '"xpack\.[^"]*"' "$LANG_DIR/fr_fr.json" | tr -d '"' | sort -u > "$SORTIE/cles-fr.txt"
    manque_fr="$(comm -23 "$SORTIE/cles-en.txt" "$SORTIE/cles-fr.txt")"
    manque_en="$(comm -13 "$SORTIE/cles-en.txt" "$SORTIE/cles-fr.txt")"
    if [ -n "$manque_fr" ]; then echec "absentes de fr_fr.json : $(echo $manque_fr)"; fi
    if [ -n "$manque_en" ]; then echec "absentes de en_us.json : $(echo $manque_en)"; fi
    if [ -z "$manque_fr$manque_en" ]; then
        ok "en_us.json et fr_fr.json ont les mêmes clés ($(wc -l < "$SORTIE/cles-en.txt" | tr -d ' '))"
    fi

    # Clés citées en dur par le code mais jamais traduites. On écarte les préfixes construits à
    # l'exécution (ils finissent par un point) et les propriétés système, qui n'en sont pas.
    grep -rhoE '"xpack\.[a-zA-Z0-9_.]+"' --include=*.java \
        "$RACINE/common/src/main" "$RACINE/shared/src" "$RACINE/legacy/src" \
        "$RACINE/fabric/src" "$RACINE/neoforge/src" "$RACINE/forge/src" \
        "$RACINE/forge1122/src/main" "$RACINE/forge1710/src/main" 2>/dev/null \
        | tr -d '"' | grep -vE '\.$' \
        | grep -vxE 'xpack\.(smoke|smoke\.ci|credentials|json)' \
        | sort -u > "$SORTIE/cles-code.txt"
    orphelines="$(comm -23 "$SORTIE/cles-code.txt" "$SORTIE/cles-en.txt")"
    if [ -n "$orphelines" ]; then
        echec "citées par le code mais non traduites : $(echo $orphelines)"
    else
        ok "toutes les clés citées en dur par le code sont traduites"
    fi

    titre "Cibles des mixins (un mixin orphelin empêche le jeu de démarrer)"
    mixins_ko=0
    for conf in $(git -C "$RACINE" ls-files '*mixins.json'); do
        paquet="$(grep -oE '"package"[^,]*' "$RACINE/$conf" | head -1 | sed 's/.*: *"//; s/"//')"
        chemin="${paquet//.//}"
        for classe in $(grep -oE '"[A-Za-z0-9_$]+(Mixin|Accessor)[A-Za-z0-9_$]*"' "$RACINE/$conf" | tr -d '"' | sort -u); do
            if ! git -C "$RACINE" ls-files --error-unmatch "*/$chemin/$classe.java" >/dev/null 2>&1; then
                echec "$conf cite $classe, introuvable dans $paquet"
                mixins_ko=1
            fi
        done
    done
    [ "$mixins_ko" = 0 ] && ok "toutes les classes de mixin citées existent"

    titre "Tests unitaires du cœur"
    if (cd "$RACINE" && ./gradlew :common:test --offline -q >"$SORTIE/tests.log" 2>&1); then
        ok ":common:test"
    else
        echec ":common:test — voir $SORTIE/tests.log"
        tail -25 "$SORTIE/tests.log"
    fi

    titre "Compilation 1.21.1 / 1.20.1"
    if (cd "$RACINE" && ./gradlew :fabric:compileJava :neoforge:compileJava :forge:compileJava --offline -q >"$SORTIE/compil.log" 2>&1); then
        ok "fabric, neoforge, forge"
    else
        echec "compilation — voir $SORTIE/compil.log"
        tail -25 "$SORTIE/compil.log"
    fi

    titre "Compilation des portages anciens"
    jdk8="$(trouve_jdk 8)"
    if [ -n "$jdk8" ]; then
        if (cd "$RACINE/forge1122" && JAVA_HOME="$jdk8" ./gradlew compileJava --offline -q >"$SORTIE/compil-1122.log" 2>&1); then
            ok "forge 1.12.2 (JDK 8)"
        else
            echec "forge 1.12.2 — voir $SORTIE/compil-1122.log"
            tail -15 "$SORTIE/compil-1122.log"
        fi
    else
        avert "forge 1.12.2 ignoré : aucun JDK 8 trouvé (ForgeGradle 2.3 l'exige)"
    fi
    jdk25="$(trouve_jdk 25)"
    if [ -n "$jdk25" ]; then
        if (cd "$RACINE/forge1710" && JAVA_HOME="$jdk25" ./gradlew compileJava --offline -q >"$SORTIE/compil-1710.log" 2>&1); then
            ok "forge 1.7.10 (JDK 25)"
        else
            echec "forge 1.7.10 — voir $SORTIE/compil-1710.log"
            tail -15 "$SORTIE/compil-1710.log"
        fi
    else
        avert "forge 1.7.10 ignoré : aucun JDK 25 trouvé (RetroFuturaGradle l'exige)"
    fi
fi

# --------------------------------------------------------------------- en jeu
if [ "$JEU" = 1 ]; then
    titre "Test en jeu (vrai client Fabric)"
    CRASHS="$RACINE/fabric/run-smoke/crash-reports"
    rm -rf "$CRASHS"
    : > "$JOURNAL"
    echo "  journal : $JOURNAL"
    if [ -d "$RACINE/fabric/run-smoke/saves/SmokeWorld" ]; then
        echo "  monde SmokeWorld présent : scénario complet"
    else
        echo "  pas de monde SmokeWorld : audit des écrans et des options seulement."
        echo "  Pour le scénario complet (triche, alerte, progrès), créer une partie nommée"
        echo "  SmokeWorld via ./gradlew :fabric:runSmoke, puis relancer."
    fi
    (cd "$RACINE" && ./gradlew :fabric:runSmoke --console=plain >"$JOURNAL" 2>&1) &
    PID=$!

    debut=$(date +%s)
    verdict=""
    while kill -0 "$PID" 2>/dev/null; do
        sleep 5
        maintenant=$(date +%s)
        if [ $((maintenant - debut)) -ge "$LIMITE_S" ]; then verdict="limite"; break; fi
        # Gel : le processus vit toujours mais n'écrit plus rien.
        modif=$(date -r "$JOURNAL" +%s 2>/dev/null || echo "$debut")
        if [ $((maintenant - modif)) -ge "$GEL_S" ]; then verdict="gel"; break; fi
    done

    if [ -n "$verdict" ]; then
        kill "$PID" 2>/dev/null
        wait "$PID" 2>/dev/null
        code=124
    else
        wait "$PID"
        code=$?
    fi

    case "$verdict" in
        gel)
            echec "GEL : plus aucune ligne de journal depuis ${GEL_S}s. Dernières lignes :"
            tail -12 "$JOURNAL" | sed 's/^/      /' ;;
        limite)
            echec "DÉLAI DÉPASSÉ : le scénario n'a pas fini en ${LIMITE_S}s" ;;
    esac

    if [ -d "$CRASHS" ] && [ -n "$(ls -A "$CRASHS" 2>/dev/null)" ]; then
        echec "CRASH : rapport dans $CRASHS"
        head -30 "$CRASHS"/* | sed 's/^/      /'
    elif [ -z "$verdict" ] && [ "$code" != 0 ]; then
        echec "le client s'est arrêté en erreur (code $code)"
        tail -20 "$JOURNAL" | sed 's/^/      /'
    fi

    # Problèmes annoncés par le scénario lui-même (SmokeTest).
    if grep -q '\[SMOKE\] FAIL' "$JOURNAL"; then
        echec "le scénario a signalé un problème :"
        grep '\[SMOKE\] FAIL' "$JOURNAL" | sed 's/^/      /'
    fi

    # Exceptions imputables au mod : une trace de pile passant par net.xpack, ou une ligne
    # de niveau ERROR le citant. Les avertissements du mod (API injoignable hors ligne, par
    # exemple) sont normaux ici et ne comptent pas.
    exc="$(grep -nE '^[[:space:]]*at net\.xpack\.|ERROR.*[Xx][Pp]ack|xpack.*ERROR' "$JOURNAL" | grep -v '\[SMOKE\]' || true)"
    if [ -n "$exc" ]; then
        echec "erreurs imputables au mod dans le journal :"
        echo "$exc" | head -10 | sed 's/^/      /'
    fi

    if grep -q '\[SMOKE\] quitting' "$JOURNAL"; then
        ok "le scénario est allé jusqu'au bout"
        grep '\[SMOKE\]' "$JOURNAL" | sed 's/^/      /'
    elif [ -z "$verdict" ]; then
        echec "le scénario ne s'est pas terminé normalement (pas de « quitting »)"
    fi
fi

# --------------------------------------------------------------------- bilan
titre "Bilan"
if [ "$ECHECS" = 0 ]; then
    echo "  aucun échec, $AVERTS avertissement(s)"
    exit 0
fi
echo "  $ECHECS échec(s), $AVERTS avertissement(s)"
exit 1
