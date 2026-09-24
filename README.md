# SolPlay Desktop

Version Windows de SolPlay (Kotlin + Compose Desktop, lecture vidéo via VLC/vlcj).

## Structure

```
build.gradle.kts / settings.gradle.kts / gradle.properties   Projet Gradle
src/main/kotlin/com/solplay/desktop/     Interface (Main, écrans, thème) + core (shims Android)
src/main/kotlin/com/solplay/iptv/        Logique IPTV (Xtream, M3U, TMDB, licences, playlists)
src/main/resources/                      Icône (solplay.ico, solplay_icon.png)
.github/workflows/build-windows-desktop.yml   Compilation automatique des installeurs
docs/FIREBASE_SETUP.md                   Configuration Firebase (licences)
outils/generate_license_codes.py         Générateur de codes de licence Pro
```

## Compiler

**Sur GitHub** : pousser le projet, puis onglet Actions > *Build Windows Desktop App* > Run workflow.
Les installeurs `.msi` et `.exe` sont dans les Artifacts du run.
(Optionnel : secret de dépôt `TMDB_API_KEY` pour les affiches TMDB.)

**En local** (Java 17 + Gradle 8.x) :

```
gradle run                        # lancer l'app
gradle packageMsi packageExe      # générer les installeurs
```

VLC doit être installé sur le PC qui exécute l'app (https://www.videolan.org).
