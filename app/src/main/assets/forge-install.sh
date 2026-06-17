#!/data/data/com.termux/files/usr/bin/bash
# =============================================================================
# forge-install.sh  --  INSTALLATION (chaine NATIVE, sans qemu)
# A coller et lancer dans TERMUX.
#
# Met en place la chaine de compilation NATIVE :
#   - paquets Termux (git, wget, p7zip, openjdk-21),
#   - SDK Android aarch64 (aapt2 ARM natif + platforms) via setup-termux-native,
#   - outils android-build-tools + serveur de build,
#   - puis lance le serveur (dans Termux, sans proot).
#
# Idempotent : relancable sans tout casser.
# =============================================================================
set -e

echo "######################################################"
echo "#  APKforge - installation (chaine native, sans qemu) #"
echo "######################################################"

HOME_DIR="/data/data/com.termux/files/home"
TOOLS="$HOME_DIR/android-build-tools"
SERVER_DIR="$HOME_DIR/buildserver"
ABT_REPO="https://github.com/lonelykonny/android-build-tools.git"

# --- 1. Termux : paquets de base ---------------------------------------------
echo "== [1/4] Termux : paquets de base =="
yes | pkg update || true
yes | pkg install -y git wget p7zip openjdk-21
termux-setup-storage || true   # acces Telechargements pour deposer les APK

# --- 2. Outils android-build-tools -------------------------------------------
echo "== [2/4] Outils android-build-tools =="
if [ -d "$TOOLS/.git" ]; then
    git -C "$TOOLS" pull -q || true
else
    git clone --depth 1 "$ABT_REPO" "$TOOLS" \
        || echo "  (clone impossible : depose les scripts manuellement dans $TOOLS)"
fi

# --- 3. Chaine native (SDK aarch64 + aapt2 ARM + config Gradle) --------------
echo "== [3/4] Chaine native (SDK aarch64, aapt2 ARM) =="
if [ -f "$TOOLS/setup-termux-native.sh" ]; then
    bash "$TOOLS/setup-termux-native.sh"
else
    echo "  ERREUR: setup-termux-native.sh introuvable dans $TOOLS"
    exit 1
fi

# --- 4. Serveur de build + lancement -----------------------------------------
echo "== [4/4] Serveur de build =="
mkdir -p "$SERVER_DIR"
[ -f "$TOOLS/buildserver.py" ] && cp "$TOOLS/buildserver.py" "$SERVER_DIR/" || true

echo
echo "Installation terminee. Demarrage du serveur..."
echo "Laisse Termux ouvert (ou en arriere-plan), puis 'Reessayer' dans APKforge."
echo
exec python3 "$SERVER_DIR/buildserver.py"
