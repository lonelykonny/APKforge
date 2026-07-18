#!/data/data/com.termux/files/usr/bin/bash
# =============================================================================
# forge-start.sh  --  DEMARRAGE RAPIDE (chaine NATIVE)
# A coller et lancer dans TERMUX. Suppose que l'installation a deja ete faite.
# Verifie la chaine native, la repare si besoin, puis lance le serveur.
#
# Pas de proot, pas de qemu : tout tourne dans Termux. Le fallback Debian
# n'est PAS demarre ici ; le serveur le provisionne lui-meme en cas de besoin.
# =============================================================================
set -u

echo "=== APKforge - demarrage rapide (natif) ==="

HOME_DIR="/data/data/com.termux/files/home"
TOOLS="$HOME_DIR/android-build-tools"
ANDROID_HOME="$HOME_DIR/android-sdk"
AAPT2="$ANDROID_HOME/build-tools/35.0.0/aapt2"
GRADLE_PROPS="$HOME_DIR/.gradle/gradle.properties"
SERVER="$HOME_DIR/buildserver/buildserver.py"

# --- verification de la chaine native ----------------------------------------
echo "-- verification de la chaine native --"
need_setup=0
[ -x "$AAPT2" ] || { echo "  aapt2 ARM manquant"; need_setup=1; }
if [ -x "$AAPT2" ] && ! "$AAPT2" version 2>&1 | grep -q "Android Asset Packaging Tool"; then
    echo "  aapt2 present mais ne repond pas nativement"; need_setup=1
fi
grep -q "android.aapt2FromMavenOverride" "$GRADLE_PROPS" 2>/dev/null \
    || { echo "  override Gradle aapt2 manquant"; need_setup=1; }

if [ "$need_setup" = 1 ]; then
    echo "-- reparation de la chaine native (setup) --"
    if [ -f "$TOOLS/setup-termux-native.sh" ]; then
        bash "$TOOLS/setup-termux-native.sh" || {
            echo "ECHEC du setup natif. Relance l'installation complete (forge-install.sh)."
            exit 1; }
    else
        echo "ERREUR: $TOOLS/setup-termux-native.sh introuvable."
        echo "Relance l'installation complete (forge-install.sh)."
        exit 1
    fi
else
    echo "  chaine native OK."
    echo "  aapt2 : $("$AAPT2" version 2>&1)"
fi

# --- serveur -----------------------------------------------------------------
if [ ! -f "$SERVER" ]; then
    echo "ERREUR: serveur introuvable a $SERVER"
    echo "Re-depose buildserver.py (ou relance forge-install.sh)."
    exit 1
fi

echo "-- demarrage du serveur --"
echo "Laisse Termux ouvert. Retourne dans APKforge et appuie sur 'Reessayer'."
echo
exec python3 "$SERVER"
