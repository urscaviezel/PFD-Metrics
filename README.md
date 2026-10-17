# PFD Metrics 1.0.1

English first · [Deutsch](#deutsch)

A performance HUD for **Play for Dream MR**, with live graphs and CSV recording. The control app and HUD run as separate Android packages. Tested on PFDM MR with Android 14 / SDK 34. Firmware changes may affect the internal YVR interfaces used by the app.

**[Download the latest release](https://github.com/urscaviezel/PFD-Metrics/releases/latest)**

## Windows installation

1. Download and **extract the entire Windows release ZIP**. Installing the two APKs alone does not grant the required permissions.
2. Enable developer mode and USB debugging on your headset. Connect it using a USB data cable, wake it up and approve the computer's USB debugging request inside the headset.
3. Double-click **setup.cmd**. If ADB is missing, setup downloads Android Platform-Tools directly from Google. Internet access is needed for this initial download. Windows 10/11 with Windows PowerShell 5.1 is required.
4. Wait for **DONE / FERTIG**. Setup installs both APKs, grants and verifies the required permissions, then opens the control app.
5. Enable monitoring in the headset and allow access to device logs. You can then disconnect USB.

After rebooting the headset, approve log access again inside the headset; reconnecting to a PC is not required. Turning monitoring off pauses measurement and the HUD while retaining the approved log reader for the current session. You can close the app window. Android terminating the process, force-stopping the app or installing an update can also end that session.

`setup.cmd` replaces the old installer. No additional setup.exe or shell log helper is needed. Run setup again to repair permissions or update the installation. It never automatically uninstalls existing apps.

## Troubleshooting

- No device / offline: check the data cable, developer mode and Windows USB driver; wake the headset and reconnect USB.
- Unauthorized: approve USB debugging in the headset, then run setup again.
- Multiple devices: disconnect the others or use `setup.cmd -Serial DEVICE`.
- Existing ADB: `setup.cmd -AdbPath "C:\Android\platform-tools\adb.exe" -NoDownload`.
- Offline setup: download official Platform-Tools beforehand and extract them into `tools/platform-tools/` beside setup.cmd.
- INSTALL_FAILED_UPDATE_INCOMPATIBLE: the installed apps use a different signing key. Updates must use the same key. Manually uninstalling deletes settings and is not an automatic repair step.
- Any other failure: fix the reported problem and run setup again. Only the final green DONE / FERTIG message confirms successful verification.

## Build information and updates

The top of the app shows the installed app and HUD versions, Android build numbers, build time in UTC and a short source ID. The first public release was **1.0.0**, with internal build number **100** so it could update earlier private test versions. Version **1.0.1** uses build **101**.

The app checks for stable GitHub releases when opened, at most once per 24 hours. You can disable automatic checks or select **Check for updates** at any time. The update source is [urscaviezel/PFD-Metrics](https://github.com/urscaviezel/PFD-Metrics). No GitHub account or token is required. Missing releases and connection errors are shown in the app.

When an update is available, the app displays its version and release notes. Only after **Download & install** does it download both APKs and check their SHA-256 hashes, package names, versions and signing certificate. Android then asks you to confirm installation. The first time, allow this app to install unknown apps, return to the control app, select **Check for updates** again and approve the update. **Later** downloads and installs nothing.

Both APKs are installed in one Android transaction. If you close the window before Android's confirmation, the pending confirmation resumes the next time you open the app. Updates interrupt monitoring; afterwards, open the app, enable monitoring and approve log access again if requested. Permissions granted by PC setup remain in place during a normal update. Updates are never silently installed in the background.

The complete **1.0.0 → 1.0.1** update was tested on the headset: GitHub check, update prompt, download, Android confirmation and installation of both APKs succeeded. Permissions were retained, and a subsequent check reported no newer version.

## Source code and license

See [BUILDING.md](BUILDING.md) for build and release instructions and [TESTING.md](TESTING.md) for test results and limitations. Packages: `com.pfd.metricstool` and `com.pfd.metricstool.hud`. App UI languages: English, German and French.

The Windows ZIP includes the corresponding source in `source/`; a separate Source ZIP is also available. Build outputs, private signing keys, ADB and device logs are excluded from the source repository.

Project source is licensed under **GNU GPL version 3 only (SPDX: GPL-3.0-only)**; see [LICENSE](LICENSE). Redistribution and modification are subject to that license. No warranty. This is not an official Play for Dream or Meta application.

Android SDK, JDK and ADB are separate tools with their own licenses; they are not distributed in these ZIPs. Setup obtains ADB directly from [Google's Platform-Tools distribution](https://developer.android.com/tools/releases/platform-tools).

---

# Deutsch

# PFD Metrics 1.0.1

Leistungsdaten als HUD auf dem Play for Dream MR. Bedien-App und HUD laufen als getrennte Android-Pakete. Unterstützt und geprüft: PFDM MR mit Android 14 / SDK 34. Herstellerinterne YVR-Schnittstellen können sich mit Firmware-Updates ändern.

[Download der aktuellen Version](https://github.com/urscaviezel/PFD-Metrics/releases/latest)

## Installation unter Windows

1. Das **Windows-Release-ZIP vollständig entpacken**. Zwei APKs allein reichen nicht.
2. Entwicklermodus und USB-Debugging am Headset aktivieren. HMD mit einem USB-Datenkabel verbinden, aufwecken und die Computerfreigabe im Headset bestätigen.
3. **setup.cmd** doppelklicken. Falls ADB fehlt, lädt das Setup die Android Platform-Tools direkt von Google. Dafür ist einmalig Internet nötig. Windows 10/11 mit Windows PowerShell 5.1 wird vorausgesetzt.
4. Auf die Meldung **FERTIG** warten. Das Setup installiert beide APKs, erteilt die erforderlichen Rechte, prüft sie und öffnet die Bedien-App.
5. Im HMD Monitoring einschalten und den Zugriff auf Geräteprotokolle erlauben. Danach kann das USB-Kabel entfernt werden.

Nach einem HMD-Neustart wird die Log-Freigabe erneut im Headset bestätigt. Es ist kein erneuter Anschluss an den PC erforderlich. Monitoring ausschalten pausiert die Anzeige und Messung; der bereits freigegebene Log-Reader bleibt für diese Sitzung erhalten. Das App-Fenster kann geschlossen werden. Android-Prozessbeendigung, Force-Stop oder ein App-Update können die Sitzung ebenfalls beenden.

`setup.cmd` ersetzt den alten Installer. Eine zusätzliche setup.exe oder ein Shell-Log-Helper ist nicht erforderlich. Das Setup kann zur Reparatur erneut ausgeführt werden. Es deinstalliert bestehende Apps nicht automatisch.

## Fehlersuche

- Kein Gerät / offline: Datenkabel, Entwicklermodus und Windows-USB-Treiber prüfen, Headset aufwecken und USB neu verbinden.
- Unauthorized: Computerfreigabe im HMD bestätigen, Setup erneut starten.
- Mehrere Geräte: andere Geräte trennen oder `setup.cmd -Serial GERAET` verwenden.
- Eigenes ADB: `setup.cmd -AdbPath "C:\Android\platform-tools\adb.exe" -NoDownload`.
- Ohne Internet: offizielle Platform-Tools vorab nach `tools/platform-tools/` im entpackten Release legen.
- INSTALL_FAILED_UPDATE_INCOMPATIBLE: vorhandene Installation wurde mit einem anderen Schlüssel signiert. Für Updates denselben Signierschlüssel benutzen. Eine manuelle Deinstallation löscht Einstellungen und ist keine automatische Reparaturmaßnahme.
- Abbruch: Fehlermeldung beheben und Setup erneut ausführen. Nur die grüne FERTIG-Meldung bestätigt die geprüfte Einrichtung.

## Version und Updates

Oben in der App stehen die tatsächlich installierten Versionen von App und HUD, ihre Android-Buildnummern, der Build-Zeitpunkt (UTC) und eine kurze Quellstand-ID. Die erste öffentliche Version heißt **1.0.0**. Die interne Buildnummer startet bei **100**, damit sie die bisherigen privaten Testversionen ohne Deinstallation aktualisieren kann.

Die App prüft beim Öffnen höchstens einmal pro 24 Stunden auf ein neues stabiles GitHub-Release. Die automatische Prüfung ist abschaltbar; **Nach Updates suchen** prüft jederzeit manuell. Update-Quelle: [urscaviezel/PFD-Metrics](https://github.com/urscaviezel/PFD-Metrics). Solange das Repository oder ein öffentliches Release fehlt, zeigt die App dies an. Es wird kein GitHub-Konto oder Token benötigt.

Bei einem Update zeigt die App Version und Release-Notizen. Erst nach **Herunterladen & installieren** lädt sie beide APKs, prüft SHA-256, Paketnamen, Versionen und den Signierschlüssel und übergibt sie gemeinsam an Android. Android fragt zusätzlich nach der Installation. Beim ersten Mal muss dieser App unter „Unbekannte Apps installieren“ das Installieren erlaubt werden; anschließend erneut **Nach Updates suchen** antippen und zustimmen. „Später“ lädt und installiert nichts.

App und HUD werden in einer gemeinsamen Android-Transaktion installiert. Falls das Fenster vor der Android-Bestätigung geschlossen wurde, wird die ausstehende Bestätigung beim nächsten Öffnen fortgesetzt. Monitoring wird durch ein Update unterbrochen; danach die App öffnen, Monitoring einschalten und gegebenenfalls die Log-Freigabe erneut bestätigen. Die einmal per PC vergebenen Rechte bleiben bei einem regulären Update bestehen. Die App installiert keine Updates still im Hintergrund.

## Quellcode und Veröffentlichung

Dieser Ordner ist der veröffentlichbare Quellcode. APKs gehören in GitHub Releases; Build-Ausgaben, private Schlüssel, ADB und Diagnoseprotokolle sind ausgeschlossen. Das Windows-ZIP enthält unter `source/` auch den zugehörigen Quellcode einschließlich Bauanleitung. Zusätzlich wird ein separates Source-ZIP erstellt.

Siehe [BUILDING.md](BUILDING.md) zum Bauen und Paketieren und [TESTING.md](TESTING.md) für den Prüfstand. App-Pakete: `com.pfd.metricstool` und `com.pfd.metricstool.hud`.

## Lizenz und Fremdwerkzeuge

Der Projektquellcode wird unter **GNU GPL Version 3 ausschließlich (SPDX: GPL-3.0-only)** veröffentlicht; siehe [LICENSE](LICENSE). Änderungen und Weitergabe sind nach den dortigen Bedingungen möglich. Keine Gewährleistung. Keine offizielle Anwendung von Play for Dream oder Meta.

Android SDK, JDK und ADB werden nicht als Bestandteil dieses Quellcodes oder der ZIP-Pakete verteilt und behalten ihre eigenen Lizenzbedingungen. Der Installer bezieht ADB bei Bedarf direkt von [Googles Platform-Tools-Seite](https://developer.android.com/tools/releases/platform-tools). Die GPL erstreckt sich nicht auf diese separat bezogenen Werkzeuge.
