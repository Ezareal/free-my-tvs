# Technology Stack

## Android application

- Plain Java 8 source targeting Android TV and Google TV.
- Android `minSdkVersion` 31 (Android 12) and `targetSdkVersion` 33.
- No Gradle and no third-party runtime libraries.
- Custom `Canvas` rendering, with AGSL shaders on Android 13+ and plain-fill fallback on Android 12.
- Local configuration generated from ignored `config.properties` into `out/gen/io/freemytv/Config.java`.

## Build and packaging

- Bash build pipeline in `build.sh`.
- GitVersion uses `GitVersion.yml`, GitFlow branches, and Conventional Commits for semantic versions.
- `.github/workflows/version.yml` automatically tags pushes to develop, stable, QA, test, and staging branches.
- JDK 17 or newer (`javac`, `keytool`).
- Android SDK platform 34 or newer.
- Android Build Tools 34 or newer: `aapt2`, `d8`, `zipalign`, and `apksigner`.
- APK signed with local ignored `freemytv.keystore`; same key must be retained for upgrades.
- `build.sh` embeds repository URL, latest reachable version tag, and exact Git commit SHA in the settings screen.
- ADB used to install APK on Android TV over local network.
- `deploy.sh` validates TV power/ADB state, builds, installs, and reloads the active Free My TV launcher.

## Runtime services

- TMDB for trending and streaming-provider metadata; requires private user API key.
- Open-Meteo for weather, sunrise, and sunset.
- Localhost ADB on TV for reversible launcher, screensaver, and package-state changes.

## Website

- Static HTML, CSS, and JavaScript in `docs/`.
- GitHub Pages deployment through `.github/workflows/pages.yml`.
- English and Brazilian Portuguese variants must remain aligned.

## Operational constraints

- Never commit `config.properties`, signing keys, APKs, or generated `out/` files.
- Never uninstall or clear system apps; Free My TV only disables approved packages for user 0.
- Preserve Original-mode rollback and watchdog behavior.
- Keep drawing allocation-free and avoid UI-thread work to maintain 60 fps.
