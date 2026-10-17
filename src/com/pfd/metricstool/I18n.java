package com.pfd.metricstool;

import android.content.Context;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Minimal DE / EN / FR string table. Language is a setting ("ui_lang") shared with the HUD service. */
public final class I18n {
    public static final String[] LANGS = {"de", "en", "fr"};
    private static final Map<String, String[]> T = new HashMap<>();

    private static void s(String key, String de, String en, String fr) { T.put(key, new String[]{de, en, fr}); }

    static {
        s("notif_paused", "Monitoring pausiert – Log-Freigabe bleibt aktiv", "Monitoring paused – log access retained", "Surveillance en pause – accès aux journaux conservé");
        s("consent_button", "Log-Zugriff freigeben", "Allow log access", "Autoriser les journaux");
        s("consent_intro", "Bitte den Android-Dialog zum Zugriff auf Geräteprotokolle bestätigen. Diese Seite geöffnet lassen, bis Live-Daten eintreffen.", "Allow access to device logs in the Android dialog. Keep this page open until live data arrives.", "Autorisez l'accès aux journaux dans le dialogue Android. Gardez cette page ouverte jusqu'à réception des données.");
        s("consent_retry", "Erneut anfordern", "Request again", "Redemander");
        s("consent_back", "Zurück zur Bedien-App", "Back to controls", "Retour aux commandes");
        s("consent_ok", "Log-Zugriff aktiv: frische Runtime-Daten empfangen. Du kannst jetzt zur Bedien-App zurückkehren.", "Log access active: fresh runtime data received. You can return to the controls.", "Accès actif : données récentes reçues. Vous pouvez revenir aux commandes.");
        s("consent_wait", "Noch keine direkten Runtime-Daten. Falls du abgelehnt hast, mindestens eine Minute warten und erneut anfordern. Läuft eine VR-Anwendung?", "No direct runtime data yet. If denied, wait at least a minute and request again. Is a VR app running?", "Aucune donnée directe. Après un refus, attendez au moins une minute puis réessayez. Une appli VR est-elle active ?");
        s("consent_permission", "READ_LOGS fehlt. Einmalig die Einrichtung per PC ausführen; danach ist die Freigabe hier möglich.", "READ_LOGS is missing. Run the PC setup once before using this page.", "READ_LOGS manque. Effectuez la configuration PC une fois.");
        s("consent_update", "Bitte zuerst den enthaltenen HUD-Dienst aktualisieren.", "Update the embedded HUD service first.", "Mettez d'abord à jour le service HUD inclus.");
        s("subtitle", "Performance-Overlay & Aufzeichnung für Play for Dream MR – Daten vom YVR-Runtime",
                "Performance overlay & capture for Play for Dream MR – data from the YVR runtime",
                "Overlay de performances & enregistrement pour Play for Dream MR – données du runtime YVR");
        s("language", "Sprache", "Language", "Langue");
        s("card_status", "Status", "Status", "État");
        s("card_control", "Steuerung", "Controls", "Commandes");
        s("card_graphs", "Live-Graphen", "Live graphs", "Graphiques en direct");
        s("card_stats", "Statistik (Sitzung)", "Statistics (session)", "Statistiques (session)");
        s("card_metrics", "Metriken (HUD / Graph)", "Metrics (HUD / graph)", "Métriques (HUD / graphique)");
        s("card_csv", "CSV-Dateien", "CSV files", "Fichiers CSV");
        s("card_setup", "Einrichtung & Diagnose", "Setup & diagnostics", "Installation & diagnostic");
        s("hint_ok", "Das HUD läuft im separaten Dienst \"PFD Metrics HUD\" weiter – dieses Fenster kann jederzeit geschlossen werden.",
                "The HUD keeps running in the separate \"PFD Metrics HUD\" service – this window can be closed at any time.",
                "Le HUD continue de tourner dans le service séparé « PFD Metrics HUD » – cette fenêtre peut être fermée à tout moment.");
        s("hint_missing", "Der HUD-Dienst ist nicht installiert.", "The HUD service is not installed.", "Le service HUD n'est pas installé.");
        s("hint_update", "Eine neuere Version des HUD-Dienstes ist enthalten.", "A newer version of the HUD service is included.",
                "Une version plus récente du service HUD est incluse.");
        s("btn_install_hud", "HUD-Dienst installieren / aktualisieren", "Install / update HUD service", "Installer / mettre à jour le service HUD");
        s("btn_uninstall_all", "Alles deinstallieren (App + HUD-Dienst)", "Uninstall everything (app + HUD service)",
                "Tout désinstaller (appli + service HUD)");
        s("need_unknown_sources", "Bitte erlaube dieser App, Apps zu installieren, und tippe dann erneut.",
                "Please allow this app to install apps, then tap again.",
                "Autorisez cette appli à installer des applis, puis appuyez à nouveau.");
        s("install_started", "Installation gestartet …", "Installation started …", "Installation lancée …");
        s("install_ok", "HUD-Dienst installiert.", "HUD service installed.", "Service HUD installé.");
        s("install_failed", "Installation fehlgeschlagen: ", "Installation failed: ", "Échec de l'installation : ");
        s("t_service", "Monitoring-Dienst", "Monitoring service", "Service de surveillance");
        s("t_hud", "HUD anzeigen", "Show HUD", "Afficher le HUD");
        s("t_vr", "HUD im immersiven VR (empfohlen; aus = nur 2D-Fenster im Home)", "HUD in immersive VR (recommended; off = 2D window in Home only)",
                "HUD en VR immersive (recommandé ; désactivé = fenêtre 2D dans l'accueil uniquement)");
        s("syshud_help", "Das eingebaute YVR-System-HUD lässt sich von Apps aus nicht schalten (Android-Schutz). Am PC einfach system_hud_an.bat bzw. system_hud_aus.bat aus dem Ordner ausführen – oder diese Befehle:",
                "The built-in YVR system HUD cannot be switched by apps (Android protection). On the PC simply run system_hud_an.bat / system_hud_aus.bat from the folder – or these commands:",
                "Le HUD système YVR intégré ne peut pas être activé par une appli (protection Android). Sur le PC, lancez system_hud_an.bat / system_hud_aus.bat du dossier – ou ces commandes :");
        s("t_syshud", "YVR-System-HUD (eingebaut, gleiche Werte-Auswahl)", "YVR system HUD (built-in, same selection)",
                "HUD système YVR (intégré, même sélection)");
        s("t_values", "Werte im Overlay", "Values in overlay", "Valeurs dans l'overlay");
        s("t_graphs", "Graphen im Overlay", "Graphs in overlay", "Graphiques dans l'overlay");
        s("t_csv", "CSV-Aufzeichnung", "CSV recording", "Enregistrement CSV");
        s("s_size", "Overlay-Größe", "Overlay size", "Taille de l'overlay");
        s("s_yaw", "Horizontal (Yaw, + = rechts)", "Horizontal (yaw, + = right)", "Horizontal (lacet, + = droite)");
        s("s_pitch", "Vertikal (Pitch, + = oben)", "Vertical (pitch, + = up)", "Vertical (tangage, + = haut)");
        s("s_quad_y", "HUD-Fläche vertikal verschieben", "Move HUD surface vertically",
                "Déplacer la surface HUD verticalement");
        s("notif_active", "Performance-Monitoring aktiv", "Performance monitoring active", "Surveillance des performances active");
        s("notif_nodata_title", "PFD Metrics: keine Runtime-Daten", "PFD Metrics: no runtime data", "PFD Metrics : pas de données runtime");
        s("notif_nodata_text", "In der App Log-Zugriff freigeben. Nach einer Neuinstallation zuerst setup.cmd am PC ausführen.", "Allow log access in the app. After a fresh install, run setup.cmd on a PC once.", "Autorisez les journaux dans l’application. Après une réinstallation, lancez setup.cmd sur PC une fois.");
        s("s_opacity", "HUD-Hintergrund (0 = durchscheinend, 100 = deckend)", "HUD background (0 = see-through, 100 = opaque)", "Fond du HUD (0 = transparent, 100 = opaque)");
        s("reset_default", "Auf Standard zurückgesetzt", "Reset to default", "Réinitialisé par défaut");
        s("s_columns", "Spalten im HUD", "HUD columns", "Colonnes du HUD");
        s("t_frame", "Rahmen der HUD-Fläche anzeigen (Kalibrierung)", "Show HUD surface frame (calibration)",
                "Afficher le cadre de la surface HUD (calibrage)");
        s("s_interval", "Abtastintervall", "Sampling interval", "Intervalle d'échantillonnage");
        s("syshud_blocked", "Android blockiert das – per adb:\n", "Android blocks this – use adb:\n", "Android bloque ceci – via adb :\n");
        s("hud_unreachable", "HUD-Dienst nicht erreichbar", "HUD service not reachable", "Service HUD injoignable");
        s("btn_reset", "Statistik zurücksetzen", "Reset statistics", "Réinitialiser les statistiques");
        s("btn_overlay_perm", "Overlay-Berechtigung öffnen", "Open overlay permission", "Ouvrir l'autorisation d'overlay");
        s("chk_graph", "Graph", "Graph", "Graphe");
        s("connecting", "Verbinde mit HUD-Dienst …", "Connecting to HUD service …", "Connexion au service HUD …");
        s("not_installed", "HUD-Dienst nicht installiert.", "HUD service not installed.", "Service HUD non installé.");
        s("session", "Seit %d min · Werte: Ø / Min / Max / 1%%-Tief / 99%%", "Last %d min · values: avg / min / max / 1%% low / 99%%",
                "Depuis %d min · valeurs : moy / min / max / 1%% bas / 99%%");
        s("avg", "Ø", "avg", "moy");
        s("setup_intro", "setup.cmd im vollständigen Installationspaket richtet beide Apps und alle Rechte ein. Einzelbefehle:", "setup.cmd in the complete release package installs both apps and permissions. Individual commands:", "setup.cmd du paquet complet installe les deux applis et les autorisations. Commandes :");
        s("setup_adb_note", "Eine reine APK-Installation reicht nicht: Nach Neuinstallation einmal setup.cmd am PC ausführen. Nach Headset-Neustarts genügt die Log-Freigabe im HMD. Monitoring aus pausiert die Messung und behält die Log-Sitzung.", "Installing APKs alone is insufficient: run setup.cmd on a PC once after installation. After headset reboots, approve log access in the HMD. Monitoring off pauses measurement and keeps the log session.", "Les APK seules ne suffisent pas : lancez setup.cmd sur PC après installation. Après redémarrage, autorisez les journaux dans le casque. La pause conserve cette session.");
        s("setup_control", "Steuerung per adb (wie OVR Metrics):", "Control via adb (like OVR Metrics):", "Contrôle via adb (comme OVR Metrics) :");
        s("setup_syshud", "YVR-System-HUD (eingebaut in VrSystemUI):", "YVR system HUD (built into VrSystemUI):", "HUD système YVR (intégré à VrSystemUI) :");
        s("setup_pull", "CSV abholen:", "Fetch CSV files:", "Récupérer les CSV :");
        s("toggle_on", "PFD HUD an", "PFD HUD on", "PFD HUD activé");
        s("toggle_off", "PFD HUD aus", "PFD HUD off", "PFD HUD désactivé");
        // status text (built in the HUD service)
        s("st_service", "HUD-Dienst", "HUD service", "Service HUD");
        s("st_running", "läuft", "running", "actif");
        s("st_stopped", "gestoppt", "stopped", "arrêté");
        s("st_runtime", "Runtime-Daten", "Runtime data", "Données runtime");
        s("st_none", "noch keine", "none yet", "aucune");
        s("st_live", "live", "live", "en direct");
        s("st_last", "letzte vor %d s", "last %d s ago", "dernières il y a %d s");
        s("st_fg", "Vordergrund", "Foreground", "Premier plan");
        s("st_granted", "erteilt", "granted", "accordée");
        s("st_missing", "fehlt", "missing", "manquante");
        s("st_overlay", "Overlay-Recht", "Overlay perm.", "Droit overlay");
        s("st_yes", "ja", "yes", "oui");
        s("st_no", "nein", "no", "non");
        s("st_logcat", "Logcat direkt", "Logcat direct", "Logcat direct");
        s("st_helper", "Log-Helper", "Log helper", "Assistant log");
        s("st_active", "aktiv", "active", "actif");
        s("st_not_started", "nicht aktiv (bei direkter Freigabe nicht erforderlich)", "inactive (not required with direct log access)", "inactif (inutile avec accès direct)");
        s("st_not_needed", "nicht nötig (Logcat direkt aktiv)", "not needed (logcat direct active)", "inutile (logcat direct actif)");
        s("st_lines", "Zeilen", "lines", "lignes");
        s("st_cpufreq", "CPU-Takt", "CPU clock", "Fréq. CPU");
        s("st_cores", "%d Kerne lesbar", "%d cores readable", "%d cœurs lisibles");
        s("st_unreadable", "nicht lesbar", "not readable", "illisible");
        s("lc_running", "läuft", "running", "actif");
        s("lc_stopped", "gestoppt", "stopped", "arrêté");
        s("lc_no_access", "keine frischen Daten – Log-Freigabe prüfen", "no fresh data – check log access", "aucune donnée récente – vérifier les journaux");
        s("lc_error", "Fehler", "error", "erreur");
        s("csv_rec", "Aufnahme läuft: ", "Recording: ", "Enregistrement : ");
        s("csv_off", "Aufnahme aus", "Recording off", "Enregistrement arrêté");
        s("csv_none", "Noch keine Dateien.", "No files yet.", "Aucun fichier pour l'instant.");
        s("csv_folder", "Ordner: ", "Folder: ", "Dossier : ");
        s("csv_svc_off", "HUD-Dienst läuft nicht.", "HUD service is not running.", "Le service HUD ne tourne pas.");
    }

    public static String defaultLang() {
        String l = Locale.getDefault().getLanguage();
        return "de".equals(l) || "fr".equals(l) ? l : "en";
    }

    public static String lang(Context ctx) { return new Settings(ctx).language(); }

    public static String t(String lang, String key) {
        String[] v = T.get(key);
        if (v == null) return key;
        int i = "de".equals(lang) ? 0 : "fr".equals(lang) ? 2 : 1;
        return v[i];
    }

    public static String t(Context ctx, String key) { return t(lang(ctx), key); }
}
