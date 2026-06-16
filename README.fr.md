# ⚡ APKforge

**Interface Android de compilation d'APK, pilotée depuis le téléphone.**

> [🇬🇧 English](README.md) &nbsp;•&nbsp; 🇫🇷 Français


APKforge est une application Android (Kotlin / Jetpack Compose, Material 3
Expressive, couleurs Material You dynamiques) qui pilote une chaîne de
compilation locale tournant dans Termux. On colle l'URL d'un dépôt git, on
appuie sur **Compiler**, les logs défilent en direct, et l'APK produit est
récupérable à la fin — le tout sans quitter le téléphone.

<p align="left">
  <img alt="Plateforme" src="https://img.shields.io/badge/plateforme-Android%2012%2B-3DDC84">
  <img alt="Langage" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF">
  <img alt="minSdk" src="https://img.shields.io/badge/minSdk-26-blue">
  <img alt="compileSdk" src="https://img.shields.io/badge/compileSdk-36-blue">
</p>

---

## Fonctionnement

APKforge ne fait que l'**interface**. Toute la compilation se déroule côté
serveur, là où vit la chaîne de build. C'est une nécessité technique : une
application Android isolée dans son bac à sable ne peut pas lancer un
compilateur elle-même.

```
  App APKforge (Compose)  ──HTTP 127.0.0.1:8765──▶  buildserver.py (Termux)
        │                                                  │
   saisie de l'URL,                       build natif (aapt2 ARM, sans qemu)
   logs en direct,                            └─ en cas d'échec de chaîne seulement,
   bouton installer                              bascule sur Debian + qemu
```

Le build s'exécute **nativement dans Termux par défaut** (aapt2 ARM natif, sans
émulation — rapide). Un proot Debian + `qemu` n'existe qu'en **secours**,
utilisé uniquement lorsqu'un build natif échoue pour une raison liée à la chaîne
elle-même (et non au code du projet). Ce secours est **installé à la demande**,
la première fois qu'il devient réellement nécessaire : tant que le natif
fonctionne, aucun proot n'occupe le disque.

Le projet compagnon qui exécute réellement les builds est
[`android-build-tools`](https://github.com/Pandarte/android-build-tools).

## Prérequis

À configurer une fois sur le téléphone :

1. **Termux** installé. Le bouton **Installer la chaîne** de l'app lance
   `forge-install.sh`, qui clone
   [`android-build-tools`](https://github.com/Pandarte/android-build-tools),
   met en place la chaîne **native** (`setup-termux-native.sh`) et démarre le
   serveur — le tout dans Termux, sans proot.
2. **Le serveur lancé** (démarré automatiquement par l'installation, ou à la
   main) :
   ```bash
   python3 ~/buildserver/buildserver.py
   ```
3. **APKforge** installée et lancée. Elle teste la connexion au serveur au
   démarrage.

Au premier lancement, si la chaîne n'est pas détectée, l'application propose un
bouton **Installer la chaîne** qui déclenche l'installation côté serveur et en
affiche les logs. Le secours Debian n'est **pas** installé à ce moment : il ne
l'est que plus tard, si un build natif échoue un jour pour une raison de chaîne.

## Utilisation

1. Coller l'URL d'un dépôt git Android dans le champ en haut.
2. Appuyer sur **Compiler**.
3. Suivre les logs en direct dans la console.
4. Récupérer l'APK produit (`APKforge.apk`) à la fin.

## Compiler APKforge

APKforge se compile avec la chaîne qu'elle pilote — ou via l'intégration
continue.

**GitHub Actions** (le plus simple) : un push sur `main`/`master` déclenche le
workflow [`build.yml`](.github/workflows/build.yml), qui produit l'APK debug et
le met à disposition en artifact.

**En local** depuis Termux :
```bash
bash ~/android-build-tools/build-android-local.sh ~/APKforge
```

Projet Android natif : `compileSdk 36`, `minSdk 26`, AGP 8.13, Material 3
Expressive (`material3:1.4.0-alpha10`).

## Détails techniques

- **Material You dynamique** : actif sur Android 12 et plus ; repli sur une
  palette neutre en dessous.
- **Localisation** : français et anglais, suivant la langue du système, avec un
  sélecteur de langue dans l'app (Système / Français / English) dans la barre du
  haut.
- **Sécurité réseau** : le trafic en clair est restreint à `127.0.0.1` (voir
  [`network_security_config.xml`](app/src/main/res/xml/network_security_config.xml)).
  Rien ne quitte le téléphone.
- **Dépendances alpha** : Material 3 Expressive s'appuie sur des versions alpha
  qui évoluent vite. Si Gradle refuse une version, utiliser la dernière
  `material3` alpha disponible et le `compose-bom` correspondant.

## Structure du projet

```
app/src/main/
├── AndroidManifest.xml
├── assets/
│   ├── forge-install.sh          install chaîne native + démarrage serveur
│   └── forge-start.sh            démarrage du serveur de build
├── java/fr/buildtool/app/
│   ├── MainActivity.kt           thème Material You + edge-to-edge
│   ├── BuildScreen.kt            UI : URL, bouton, console de logs, animation
│   ├── BuildViewModel.kt         état + polling des logs
│   ├── BuildClient.kt            client HTTP du serveur local
│   └── TermuxHelper.kt           interactions avec Termux
└── res/
    ├── drawable/                 icône adaptative (enclume + Android)
    ├── values/ values-en/        chaînes localisées (FR par défaut, EN)
    ├── xml/network_security_config.xml
    └── xml/locales_config.xml    langues prises en charge
```

## Projets liés

- [`android-build-tools`](https://github.com/Pandarte/android-build-tools) — la
  chaîne de compilation et le serveur HTTP qu'APKforge pilote.
