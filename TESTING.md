# Testing 1.0.1

English first · [Deutsch](#deutsch)

Status: 17 October 2026.

- Both APKs built from the provided Java source and verified with apksigner.
- Windows installer tested with simulated ADB: success, paths containing spaces, multiple devices and explicit selection, missing/offline/unauthorized devices, unsupported model, installation failures, denied/missing permissions, incorrect AppOps and missing/corrupted APKs. Failures never report success or launch the app.
- A clean installation of private test build 2.9.2 succeeded on PFDM MR / Android SDK 34, including automatic ADB download, both APKs and permission verification. The user confirmed live HUD values after closing the app window.
- Public 1.0.0 (build 100) successfully updated that private version. Its monitoring/log-reader logic was unchanged; build information and updating were added.
- The log reader and HUD were user-tested across reboot, closing the control window, repeated monitoring off/on and display timeout/wakeup.
- Java regression tests cover version comparison, the transition from private builds to build 100, downgrade rejection and GitHub asset URL validation.
- Separate Android instrumentation verified both APKs including signing certificates and rejected wrong package names and version codes. This instrumentation is not part of the production app.
- A pending multi-package installation confirmation successfully resumed when reopening the control app, with Android's PackageInstallerActivity in the foreground.
- The installed 1.0.0 correctly recognized the public GitHub release as current.
- **The real 1.0.0 → 1.0.1 update succeeded entirely through the app:** GitHub release lookup, update prompt, download, Android confirmation and installation of both APKs. The user confirmed both dialogs and successful installation. Both packages reported 1.0.1 / build 101 with the same installation timestamp. READ_LOGS, DUMP, POST_NOTIFICATIONS and all four setup AppOps remained granted. No ADB installation command was used for this version change.
- After updating, the user confirmed that another update check reported no newer version.

This is not a test of every firmware version. Panel detachment uses an internal YVR interface. A headset system crash occurred during an earlier development test; its cause was not conclusively identified.

## Permissions and session behavior

Setup grants READ_LOGS (runtime logs), DUMP (system diagnostics) and POST_NOTIFICATIONS (service notification) to the HUD. AppOps allow SYSTEM_ALERT_WINDOW (HUD), GET_USAGE_STATS (foreground app), MANAGE_EXTERNAL_STORAGE (existing file-access functionality) and WRITE_SETTINGS (existing system-settings functionality). Additional Android log access approval is given by the user inside the headset.

Disabling monitoring retains the approved log reader but pauses sampling, HUD and CSV recording. Force-stop ends the process and its approved session. No external shell helper is started.

---

# Deutsch

# Prüfung von 1.0.1

Stand: 17. Oktober 2026.

- Beide APKs aus dem enthaltenen Java-Quellcode gebaut und mit apksigner verifiziert.
- PowerShell-Installer mit simuliertem ADB geprüft: Erfolg, Leerzeichen in Dateipfaden, mehrere Geräte und explizite Auswahl, fehlendes/offline/unautorisiertes Gerät, falsches Modell, Installationsfehler, verweigerte bzw. fehlende Berechtigungen, falsche AppOps, fehlende/beschädigte APKs. Fehler melden keinen Erfolg und öffnen die App nicht.
- Echte Neuinstallation des Vorgänger-Teststands 2.9.2 auf PFDM MR / Android SDK 34 erfolgreich: beide Pakete waren zuvor nicht installiert. ADB automatisch von Google heruntergeladen, beide APKs installiert, Versionen, drei Berechtigungen und vier AppOps geprüft; Bedien-App geöffnet. Der Nutzer bestätigte Live-Werte auch nach Schließen des Fensters.
- Anschließend 1.0.0 mit interner Buildnummer 100 erfolgreich über 2.9.2 installiert und alle Setup-Rechte erneut geprüft. Die Log-Reader-/HUD-Laufzeitlogik ist unverändert; hinzu kamen Build-Anzeige und Update-Funktion.
- Die zugrunde liegende Log-Reader-/HUD-Implementierung wurde vom Nutzer bereits mit Neustart, Fenster schließen, mehrfach Monitoring aus/ein und Display-Timeout/Wakeup erfolgreich geprüft.
- Java-Regressionstests prüfen Versionsvergleich (einschließlich Übergang von privaten Builds zu 100), Downgrade-Abweisung und GitHub-Asset-URL-Prüfung.
- Separate Android-Instrumentierung prüft auf dem HMD beide echten APKs einschließlich Zertifikat und weist falsche Paketnamen sowie Versionscodes zurück. Diese Testinstrumentierung ist kein Bestandteil der Produktions-App.
- Gemeinsame Installation beider Pakete als Multi-Package-Session auf dem HMD angelegt; die bei geschlossener App ausstehende Bestätigung wird beim Öffnen fortgesetzt. Androids PackageInstallerActivity ist dabei nachweislich im Vordergrund. Der anschließende echte Update-Test auf 1.0.1 wurde vom Nutzer erfolgreich bestätigt.
- Die veröffentlichte 1.0.0 wurde auf dem HMD über die echte GitHub-Abfrage korrekt als aktuell erkannt.
- 1.0.1 erhöht beide Android-Buildnummern von 100 auf 101. Die App-Logik bleibt unverändert. Der vollständige Update-Test von 1.0.0 auf 1.0.1 über GitHub und die Android-Bestätigung ist erfolgreich: Der Nutzer bestätigte die Update-Nachfrage und die Installation im HMD. Danach meldeten beide Pakete Version 1.0.1 / Build 101 mit identischem Installationszeitpunkt. READ_LOGS, DUMP, POST_NOTIFICATIONS und alle vier Setup-AppOps blieben erhalten. Es wurde kein ADB-Installationsbefehl für diesen Versionswechsel verwendet.

Nach dem Update bestätigte der Nutzer bei einer erneuten Prüfung: keine neuere Version verfügbar.

Nicht als universeller Firmware-Test zu verstehen. Die Panel-Abmeldung nutzt eine herstellerinterne YVR-Schnittstelle. In einer früheren Testphase wurde ein HMD-Systemabsturz beobachtet; seine Ursache wurde nicht abschließend nachgewiesen.

## Rechte

Das Setup richtet für das HUD READ_LOGS (Runtime-Protokolle), DUMP (Systemdiagnose) und POST_NOTIFICATIONS (Dienstbenachrichtigung) ein. AppOps erlauben SYSTEM_ALERT_WINDOW (HUD), GET_USAGE_STATS (Vordergrund-App), MANAGE_EXTERNAL_STORAGE (Dateizugriff der vorhandenen Implementierung) und WRITE_SETTINGS (vorhandene Systemeinstellungsfunktionen). Die zusätzliche Android-Log-Freigabe erfolgt durch den Nutzer im HMD.

Monitoring aus hält den genehmigten Log-Reader am Leben, pausiert jedoch Sampling, HUD und CSV. Ein Force-Stop beendet diesen Prozess und damit die Sitzung. Es wird kein externer Shell-Helper gestartet.
