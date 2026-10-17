# Building and publishing

English first · [Deutsch](#deutsch)

Tested on Windows with Python 3, JDK 21, Android Platform 34 and Android Build-Tools 35.0.1. Use Python 3.9 or newer. No Gradle or Maven dependencies. Once SDK and JDK are installed, the build works offline.

## Requirements

- [Android SDK](https://developer.android.com/tools): `platforms;android-34` and `build-tools;35.0.1` or newer build-tools. The older D8 in build-tools 34 is unsuitable for the JDK used here.
- JDK 21; set `JAVA_HOME` to its directory.
- Set `ANDROID_SDK_ROOT` to the SDK directory.
- Keep a private APK signing key outside the repository. Both APKs must use the same key, and updates must retain the key used by previous releases.

Generate a key for your own fork (passwords are prompted interactively):

```powershell
keytool -genkeypair -keystore ..\private-release.jks -alias release -keyalg RSA -keysize 4096 -validity 10000
```

Back up your key securely. Never commit keys or real passwords. Your own key cannot update an existing installation signed with a different key.

## Build

Check `release-config.json`: `githubRepository` is `urscaviezel/PFD-Metrics`. Forks should use their own public repository. This setting does not create a repository. Set matching `versionName` values in both manifests and **increase both versionCode values for every release**. Version 1.0.0 uses 100; 1.0.1 uses 101. The updater compares these internal numbers to prevent downgrades.

Run from the source directory. Set passwords only for the local shell session; do not save them in scripts. PowerShell 7 also supports `Read-Host -MaskInput`.

```powershell
$env:PFD_STORE_PASSWORD = Read-Host 'Keystore password'
$env:PFD_KEY_PASSWORD = Read-Host 'Key password'
python build.py --keystore ..\private-release.jks --alias release --out build-release
Remove-Item Env:PFD_STORE_PASSWORD, Env:PFD_KEY_PASSWORD
```

Optional arguments: `--sdk PATH`, `--jdk PATH`, `--build-tools-dir PATH`. The output directory must not already exist; use a new name for another build. The script compiles Java, creates DEX, signs and verifies both APKs, and embeds the signed HUD APK in the control app's assets.

## Tests and packaging

```powershell
python -m unittest discover -s tests -v
python scripts/package.py --release build-release/release --out dist
```

Packaging verifies APK hashes and includes source files from an explicit allowlist. It produces six files: a Windows ZIP with corresponding source, a Source ZIP, both APKs, release.json and SHA256SUMS.txt. ADB is not bundled. APKs must correspond to the included application source; rebuild and test after application changes.

Only commit source files. `.gitignore` excludes local outputs and signing keys; inspect `git status` and staged changes before pushing. Distribute the complete Windows ZIP so its corresponding source stays available.

## GitHub releases for the updater

1. Push source to your public repository. The official repository is `urscaviezel/PFD-Metrics`.
2. Create a release at the matching source commit, with a tag such as **v1.0.1**. The tag must equal `v` followed by `versionName`. Drafts and prereleases are not used by the stable update channel.
3. Attach **all six files from dist/**: Windows ZIP, Source ZIP, `PFDMetricsTool.apk`, `PFDMetricsHUD.apk`, `release.json` and `SHA256SUMS.txt`. Preserve the APK and metadata filenames. Source must match the APKs.
4. Write release notes in **English first, then German**, and publish as the latest stable release. The app uses GitHub's `/releases/latest` API and shows the release notes in its confirmation dialog.

An installed release only offers an update with a higher UI versionCode. Increase both codes and retain the signing key. Key rotation is currently not supported by the additional certificate check. API rate limits, unavailable networking and incomplete releases produce visible errors instead of an installation.

References: [GitHub Releases API](https://docs.github.com/en/rest/releases/releases), [Android PackageInstaller.SessionParams](https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams).

---

# Deutsch

# Bauen und Release erstellen

Geprüft unter Windows mit Python 3, JDK 21, Android Platform 34 und Android Build-Tools 35.0.1. Python 3.9 oder neuer verwenden. Keine Gradle- oder Maven-Abhängigkeiten. SDK/JDK müssen vorher installiert sein; danach läuft der Build offline.

## Voraussetzungen

- [Android SDK](https://developer.android.com/tools): `platforms;android-34` und `build-tools;35.0.1` (oder neuere Build-Tools). Älteres D8 aus Build-Tools 34 ist mit dem verwendeten JDK nicht geeignet.
- JDK 21 mit `JAVA_HOME` auf den JDK-Ordner.
- `ANDROID_SDK_ROOT` auf den SDK-Ordner.
- Ein privater APK-Signierschlüssel außerhalb dieses Repositorys. Beide Apps müssen mit demselben Schlüssel signiert werden. Für Updates muss auch gegenüber früheren Releases derselbe Schlüssel verwendet werden.

Eigener Schlüssel für einen eigenen Fork (fragt Passwörter interaktiv ab):

```powershell
keytool -genkeypair -keystore ..\private-release.jks -alias release -keyalg RSA -keysize 4096 -validity 10000
```

Den privaten Schlüssel sicher aufbewahren. Niemals Schlüssel oder echte Passwörter committen. Ein eigener Schlüssel kann eine anders signierte bestehende Installation nicht als Update ersetzen.

## Build

Vor einem Release `release-config.json` prüfen: `githubRepository` steht auf `urscaviezel/PFD-Metrics`. Für einen Fork das eigene öffentliche Repository eintragen. Das Repository wird dadurch nicht angelegt. In beiden Manifesten denselben sichtbaren `versionName` setzen und **beide versionCode-Werte gegenüber dem letzten Release erhöhen** (1.0.0 beginnt mit 100). Der Updater vergleicht diese internen Nummern und verhindert Downgrades.

Im Quellcodeordner ausführen. Passwörter nur in dieser lokalen Sitzung setzen, beispielsweise mit `Read-Host` (PowerShell 7 bietet zusätzlich `-MaskInput`). Keine Passwörter in Skripte schreiben.

```powershell
$env:PFD_STORE_PASSWORD = Read-Host 'Keystore-Passwort'
$env:PFD_KEY_PASSWORD = Read-Host 'Schluessel-Passwort'
python build.py --keystore ..\private-release.jks --alias release --out build-release
Remove-Item Env:PFD_STORE_PASSWORD, Env:PFD_KEY_PASSWORD
```

Optional: `--sdk PFAD`, `--jdk PFAD`, `--build-tools-dir PFAD`. Das Ausgabeverzeichnis darf noch nicht existieren; für einen weiteren Build einen neuen Namen verwenden. Der Build kompiliert alle Java-Quellen, erstellt DEX, signiert und prüft beide APKs. Die signierte HUD-APK wird zusätzlich als Asset der Bedien-App eingebettet.

## Tests und Paketierung

```powershell
python -m unittest discover -s tests -v
python scripts/package.py --release build-release/release --out dist
```

Die Paketierung kontrolliert APK-Prüfsummen und nimmt Quellcode nur aus einer expliziten Dateiliste auf. Sie erzeugt ein Windows-ZIP einschließlich vollständigem Quellcode, ein Source-ZIP, zwei einzelne APKs, release.json sowie SHA256SUMS.txt. ADB wird nicht eingebunden. Veröffentlichte APKs müssen aus genau diesem Quellstand gebaut sein; nach Quelländerungen erneut bauen und testen.

Für Git nur den Quellcodeordner verwenden; `.gitignore` schließt lokale Ausgaben und Schlüssel aus. Vor dem Push `git status` und die vorgemerkten Dateien prüfen. Das Windows-ZIP enthält den zugehörigen Quellcode bereits und sollte vollständig weitergegeben werden.

## GitHub-Releases für den Updater

1. Das öffentliche Repository `urscaviezel/PFD-Metrics` anlegen und den Quellstand hochladen.
2. Ein Release mit Tag **v1.0.0** (später z. B. v1.0.1) am passenden Quellstand erstellen. Tag und `versionName` müssen zusammenpassen. Keine Draft-/Pre-Release-Version für den stabilen Updatekanal verwenden.
3. **Alle sechs Dateien aus dist/** anhängen: Windows-ZIP, Source-ZIP, `PFDMetricsTool.apk`, `PFDMetricsHUD.apk`, `release.json`, `SHA256SUMS.txt`. Dateinamen der APKs und release.json beibehalten. Das Source-ZIP muss genau zu den APKs gehören.
4. Release-Notizen auf Englisch, danach auf Deutsch schreiben und das Release als neuestes stabiles Release veröffentlichen. Der Updater verwendet GitHubs `/releases/latest`-API.

Die ersten installierten 1.0.0-Apps melden erst bei einem höheren UI-versionCode ein Update. Für jeden neuen Build beide Codes erhöhen und weiterhin denselben privaten Schlüssel verwenden. Schlüsselrotation wird von der zusätzlichen Zertifikatsprüfung derzeit nicht unterstützt. API-Rate-Limits, fehlende Verbindung und unvollständige Releases führen zu einer sichtbaren Fehlermeldung, nicht zu einer Installation.

Technische Referenzen: [GitHub Releases API](https://docs.github.com/en/rest/releases/releases), [Android PackageInstaller.SessionParams](https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams).
