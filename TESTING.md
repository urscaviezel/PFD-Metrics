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

Nicht als universeller Firmware-Test zu verstehen. Die Panel-Abmeldung nutzt eine herstellerinterne YVR-Schnittstelle. In einer früheren Testphase wurde ein HMD-Systemabsturz beobachtet; seine Ursache wurde nicht abschließend nachgewiesen.

## Rechte

Das Setup richtet für das HUD READ_LOGS (Runtime-Protokolle), DUMP (Systemdiagnose) und POST_NOTIFICATIONS (Dienstbenachrichtigung) ein. AppOps erlauben SYSTEM_ALERT_WINDOW (HUD), GET_USAGE_STATS (Vordergrund-App), MANAGE_EXTERNAL_STORAGE (Dateizugriff der vorhandenen Implementierung) und WRITE_SETTINGS (vorhandene Systemeinstellungsfunktionen). Die zusätzliche Android-Log-Freigabe erfolgt durch den Nutzer im HMD.

Monitoring aus hält den genehmigten Log-Reader am Leben, pausiert jedoch Sampling, HUD und CSV. Ein Force-Stop beendet diesen Prozess und damit die Sitzung. Es wird kein externer Shell-Helper gestartet.
