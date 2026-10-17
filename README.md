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
