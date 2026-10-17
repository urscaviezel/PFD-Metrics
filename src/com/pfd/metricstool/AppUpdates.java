package com.pfd.metricstool;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.net.Uri;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** GitHub stable releases; no download or installation until explicit approval. */
public final class AppUpdates {
    private final Activity activity;
    private final SharedPreferences prefs;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private JSONObject build = new JSONObject();
    private TextView status;
    private volatile boolean closed, busy;
    private boolean checkedThisOpen;

    AppUpdates(Activity activity) {
        this.activity = activity;
        prefs = activity.getSharedPreferences("updates", 0);
        try (InputStream in = activity.getAssets().open("build-info.json")) {
            build = new JSONObject(new String(read(in, 16384), StandardCharsets.UTF_8));
        } catch (Exception ignored) { }
    }

    private String t(String de, String en, String fr) {
        String lang = I18n.lang(activity);
        return "de".equals(lang) ? de : "fr".equals(lang) ? fr : en;
    }

    String summary() {
        try {
            PackageInfo ui = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            String hud;
            try {
                PackageInfo h = activity.getPackageManager().getPackageInfo(RemoteHud.HUD_PACKAGE, 0);
                hud = h.versionName + " (" + h.getLongVersionCode() + ")";
            } catch (Exception e) { hud = "—"; }
            return "App " + ui.versionName + " (" + ui.getLongVersionCode() + ") · HUD " + hud
                    + "\nBuild: " + build.optString("builtAt", "—") + " · " + build.optString("sourceId", "—");
        } catch (Exception e) { return "Build: —"; }
    }

    void addCard(LinearLayout root) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(16, 12, 16, 12);
        TextView info = new TextView(activity);
        info.setText(summary());
        info.setTextSize(13);
        info.setTextColor(Color.LTGRAY);
        info.setTextIsSelectable(true);
        card.addView(info);
        CheckBox automatic = new CheckBox(activity);
        automatic.setText(t("Beim Öffnen automatisch nach Updates suchen", "Check for updates when opening", "Vérifier les mises à jour à l'ouverture"));
        automatic.setChecked(prefs.getBoolean("auto", true));
        automatic.setOnCheckedChangeListener((b, on) -> prefs.edit().putBoolean("auto", on).apply());
        card.addView(automatic);
        Button check = new Button(activity);
        check.setText(t("Nach Updates suchen", "Check for updates", "Rechercher des mises à jour"));
        check.setOnClickListener(v -> check(true));
        card.addView(check);
        status = new TextView(activity);
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(12);
        status.setText(prefs.getString("status", repo().isEmpty()
                ? t("Update-Quelle noch nicht eingerichtet.", "Update source not configured yet.", "Source de mise à jour non configurée.")
                : "GitHub: " + repo()));
        card.addView(status);
        root.addView(card);
    }

    private String repo() { return build.optString("githubRepository", ""); }

    void onResume() {
        if (!checkedThisOpen) {
            checkedThisOpen = true;
            if (prefs.getBoolean("auto", true) && System.currentTimeMillis() - prefs.getLong("lastAttempt", 0) > 86400000L)
                check(false);
        }
    }

    private void showStatus(String text) {
        if (closed) return;
        prefs.edit().putString("status", text).apply();
        if (status != null) status.setText(text);
    }

    private void post(Runnable action) { activity.runOnUiThread(() -> { if (!closed && !activity.isFinishing()) action.run(); }); }

    private void check(boolean manual) {
        if (busy || closed) return;
        if (!UpdatePolicy.repository(repo())) {
            showStatus(t("Update-Quelle noch nicht eingerichtet.", "Update source not configured yet.", "Source de mise à jour non configurée."));
            return;
        }
        busy = true;
        prefs.edit().putLong("lastAttempt", System.currentTimeMillis()).apply();
        showStatus(t("Suche nach Updates …", "Checking for updates …", "Recherche de mises à jour …"));
        worker.execute(() -> {
            try {
                Release release = fetchRelease();
                long installed = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).getLongVersionCode();
                long hud = HudInstaller.installedVersion(activity);
                boolean newer = UpdatePolicy.newer(release.uiCode, release.hudCode, installed, hud);
                post(() -> {
                    busy = false;
                    if (newer) {
                        showStatus(t("Update verfügbar: ", "Update available: ", "Mise à jour disponible : ") + release.version);
                        if (activity.hasWindowFocus()) confirm(release);
                    } else showStatus(t("Keine neuere kompatible Version verfügbar.", "No newer compatible version available.", "Aucune version compatible plus récente disponible."));
                });
            } catch (Exception e) {
                post(() -> { busy = false; showStatus("HTTP 404".equals(e.getMessage())
                        ? t("Noch kein öffentliches Release unter ", "No public release yet at ", "Pas encore de version publique sur ") + repo()
                        : t("Update-Prüfung nicht möglich: ", "Cannot check for updates: ", "Vérification impossible : ") + e.getMessage()); });
            }
        });
    }

    private void confirm(Release release) {
        if (closed || busy) return;
        new AlertDialog.Builder(activity)
                .setTitle(t("Update auf ", "Update to ", "Mettre à jour vers ") + release.version)
                .setMessage(release.notes + "\n\n" + t("App und HUD gemeinsam herunterladen und installieren? Monitoring wird unterbrochen; anschließend kann eine neue Log-Freigabe nötig sein.",
                        "Download and install app and HUD together? Monitoring will be interrupted; log access may need approval again.",
                        "Télécharger et installer l'appli et le HUD ensemble ? La surveillance sera interrompue ; une nouvelle autorisation des journaux peut être nécessaire."))
                .setNegativeButton(t("Später", "Later", "Plus tard"), null)
                .setPositiveButton(t("Herunterladen & installieren", "Download & install", "Télécharger et installer"), (d, w) -> download(release)).show();
    }

    private void download(Release release) {
        if (busy || closed) return;
        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            showStatus(I18n.t(activity, "need_unknown_sources"));
            try { activity.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()))); }
            catch (Exception e) { showStatus(e.getMessage()); }
            return;
        }
        busy = true;
        showStatus(t("Update wird heruntergeladen und geprüft …", "Downloading and verifying update …", "Téléchargement et vérification …"));
        worker.execute(() -> {
            File ui = null, hud = null;
            try {
                ui = File.createTempFile("update-ui-", ".apk", activity.getCacheDir());
                hud = File.createTempFile("update-hud-", ".apk", activity.getCacheDir());
                downloadApk(release, "PFDMetricsTool.apk", ui);
                downloadApk(release, "PFDMetricsHUD.apk", hud);
                UpdateInstaller.verify(activity, ui, activity.getPackageName(), release.version, release.uiCode);
                UpdateInstaller.verify(activity, hud, RemoteHud.HUD_PACKAGE, release.version, release.hudCode);
                if (closed || Thread.currentThread().isInterrupted()) throw new Exception("Cancelled");
                UpdateInstaller.install(activity, ui, hud);
                post(() -> { busy = false; showStatus(t("Bitte die Android-Installation bestätigen.", "Please confirm installation in Android.", "Confirmez l'installation Android.")); });
            } catch (Exception e) {
                post(() -> { busy = false; showStatus(I18n.t(activity, "install_failed") + e.getMessage()); });
            } finally { if (ui != null) ui.delete(); if (hud != null) hud.delete(); }
        });
    }

    private Release fetchRelease() throws Exception {
        JSONObject latest = json("https://api.github.com/repos/" + repo() + "/releases/latest", 1024 * 1024);
        if (latest.optBoolean("draft") || latest.optBoolean("prerelease")) throw new Exception("No stable release");
        Map<String, String> assets = new HashMap<>();
        JSONArray list = latest.getJSONArray("assets");
        for (int i = 0; i < list.length(); i++) {
            JSONObject asset = list.getJSONObject(i);
            String name = asset.getString("name"), url = asset.getString("browser_download_url");
            if (UpdatePolicy.assetUrl(url, repo())) assets.put(name, url);
        }
        for (String name : new String[]{"release.json", "PFDMetricsTool.apk", "PFDMetricsHUD.apk"})
            if (!assets.containsKey(name)) throw new Exception("Release asset missing: " + name);
        JSONObject metadata = json(assets.get("release.json"), 65536);
        Release release = new Release();
        release.version = metadata.getString("version");
        if (!release.version.matches("[0-9]+\\.[0-9]+\\.[0-9]+") || !latest.getString("tag_name").equals("v" + release.version))
            throw new Exception("Release tag/version mismatch");
        release.uiCode = metadata.getLong("uiVersionCode");
        release.hudCode = metadata.getLong("hudVersionCode");
        release.hashes = metadata.getJSONObject("sha256");
        release.assets = assets;
        release.notes = latest.optString("body", "");
        if (release.notes.length() > 4000) release.notes = release.notes.substring(0, 4000);
        return release;
    }

    private void downloadApk(Release r, String name, File destination) throws Exception {
        String expected = r.hashes.getString(name);
        if (!expected.matches("[a-fA-F0-9]{64}")) throw new Exception("Invalid SHA-256");
        HttpURLConnection connection = connection(r.assets.get(name));
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[65536];
            int n; long total = 0;
            while ((n = in.read(buffer)) != -1) {
                if (closed || Thread.currentThread().isInterrupted()) throw new Exception("Cancelled");
                total += n;
                if (total > 64 * 1024 * 1024) throw new Exception("APK exceeds size limit");
                digest.update(buffer, 0, n); out.write(buffer, 0, n);
            }
        } finally { connection.disconnect(); }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        if (!expected.equalsIgnoreCase(hex.toString())) throw new Exception("SHA-256 mismatch: " + name);
    }

    private static JSONObject json(String url, int limit) throws Exception {
        HttpURLConnection c = connection(url);
        try (InputStream in = c.getInputStream()) {
            return new JSONObject(new String(read(in, limit), StandardCharsets.UTF_8));
        } finally { c.disconnect(); }
    }

    private static HttpURLConnection connection(String url) throws Exception {
        // Follow only HTTPS redirects; GitHub release downloads redirect to its asset CDN.
        for (int i = 0; i < 6; i++) {
            URL target = new URL(url);
            if (!"https".equals(target.getProtocol())) throw new Exception("HTTPS required");
            HttpURLConnection c = (HttpURLConnection) target.openConnection();
            c.setConnectTimeout(15000); c.setReadTimeout(20000); c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "PFD-Metrics-Updater");
            c.setRequestProperty("Accept", "application/json, application/octet-stream");
            int code = c.getResponseCode();
            if (code == 200) return c;
            String location = c.getHeaderField("Location");
            c.disconnect();
            if (code >= 300 && code <= 399 && location != null) url = new URL(target, location).toString();
            else throw new Exception("HTTP " + code);
        }
        throw new Exception("Too many redirects");
    }

    private static byte[] read(InputStream in, int limit) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; int n;
        while ((n = in.read(buffer)) != -1) {
            if (out.size() + n > limit) throw new Exception("Response exceeds size limit");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    void close() { closed = true; worker.shutdownNow(); }

    private static final class Release {
        String version, notes;
        long uiCode, hudCode;
        JSONObject hashes;
        Map<String, String> assets;
    }
}
