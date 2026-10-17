package com.pfd.metricstool;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/**
 * Lives in the headless HUD package (com.pfd.metricstool.hud). The companion UI app talks to it via
 * ContentResolver.call(); protected by a signature permission so only our own UI may use it.
 */
public final class HudProvider extends ContentProvider {
    public static final String AUTHORITY = "com.pfd.metricstool.hud.provider";

    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        Context ctx = getContext();
        Settings settings = new Settings(ctx);
        Bundle out = new Bundle();
        switch (method) {
            case "snapshot": {
                out = MetricStore.get().toSnapshot();
                out.putBoolean("running", MetricsService.instance() != null && settings.serviceEnabled());
                out.putString("status", statusText(ctx));
                out.putString("csv", csvText(settings));
                out.putBoolean("system_hud", SystemHud.isEnabled(ctx));
                break;
            }
            case "log_session": {
                MetricsService svc = MetricsService.instance();
                out.putBoolean("approved_reader", svc != null && svc.logSources().hasAuthorizedReader());
                break;
            }
            case "get_settings": {
                for (Map.Entry<String, ?> e : settings.prefs().getAll().entrySet()) put(out, e.getKey(), e.getValue());
                break;
            }
            case "set": {
                if (extras == null || arg == null) break;
                SharedPreferences.Editor ed = settings.prefs().edit();
                Object v = extras.get("value");
                if (v instanceof Boolean) ed.putBoolean(arg, (Boolean) v);
                else if (v instanceof Float) ed.putFloat(arg, (Float) v);
                else if (v instanceof Integer) ed.putInt(arg, (Integer) v);
                else if (v instanceof Long) ed.putLong(arg, (Long) v);
                else if (v instanceof String) ed.putString(arg, (String) v);
                else ed.remove(arg);
                ed.apply();
                if (Settings.K_SERVICE.equals(arg)) {
                    if (Boolean.TRUE.equals(v)) MetricsService.start(ctx); else MetricsService.stop(ctx);
                }
                break;
            }
            case "start_service":
                settings.setServiceEnabled(true);
                MetricsService.start(ctx);
                break;
            case "reset_stats":
                MetricStore.get().resetStats();
                break;
            case "system_hud": {
                String err = SystemHud.set(ctx, "1".equals(arg), SystemHud.maskFor(settings));
                if (err != null) out.putString("error", err);
                break;
            }
            default:
                out.putString("error", "unknown method " + method);
        }
        return out;
    }

    private static void put(Bundle b, String k, Object v) {
        if (v instanceof Boolean) b.putBoolean(k, (Boolean) v);
        else if (v instanceof Float) b.putFloat(k, (Float) v);
        else if (v instanceof Integer) b.putInt(k, (Integer) v);
        else if (v instanceof Long) b.putLong(k, (Long) v);
        else if (v instanceof String) b.putString(k, (String) v);
    }

    private static String row(String label, String value) {
        return String.format(Locale.US, "%-15s %s%n", label + ":", value);
    }

    private static String logcatText(String l, String state) {
        if ("running".equals(state)) return I18n.t(l, "lc_running");
        if ("stopped".equals(state)) return I18n.t(l, "lc_stopped");
        if ("no_access".equals(state)) return I18n.t(l, "lc_no_access");
        return I18n.t(l, "lc_error") + ": " + state.replaceFirst("^error: ", "");
    }

    static String statusText(Context ctx) {
        String l = I18n.lang(ctx);
        MetricStore store = MetricStore.get();
        MetricsService svc = MetricsService.instance();
        StringBuilder sb = new StringBuilder();
        long age = System.currentTimeMillis() - store.lastRuntimeUpdate();
        sb.append(row(I18n.t(l, "st_service"), I18n.t(l, svc != null && new Settings(ctx).serviceEnabled() ? "st_running" : "st_stopped")));
        sb.append(row(I18n.t(l, "st_runtime"), store.lastRuntimeUpdate() == 0 ? I18n.t(l, "st_none")
                : age < 3000 ? I18n.t(l, "st_live") : String.format(Locale.US, I18n.t(l, "st_last"), age / 1000)));
        sb.append(row(I18n.t(l, "st_fg"), store.foregroundApp().isEmpty() ? "?" : store.foregroundApp()));
        sb.append(row("READ_LOGS", I18n.t(l, ctx.checkSelfPermission(android.Manifest.permission.READ_LOGS) == 0 ? "st_granted" : "st_missing")));
        sb.append(row(I18n.t(l, "st_overlay"), I18n.t(l, android.provider.Settings.canDrawOverlays(ctx) ? "st_yes" : "st_no")));
        if (svc != null) {
            LogSources ls = svc.logSources();
            sb.append(row(I18n.t(l, "st_logcat"), logcatText(l, ls.logcatStatus()) + " (" + ls.logcatLines() + " " + I18n.t(l, "st_lines") + ")"));
            boolean helperOn = ls.helperFile().exists() && System.currentTimeMillis() - ls.helperFile().lastModified() < 10_000;
            sb.append(row(I18n.t(l, "st_helper"), I18n.t(l, helperOn ? "st_active" : ls.hasFreshDirectData() ? "st_not_needed" : "st_not_started")
                    + " (" + ls.helperLines() + " " + I18n.t(l, "st_lines") + ")"));
            sb.append(row("HUD", svc.overlay().state()));
            int cores = svc.sampler().cpuFreqCores();
            sb.append(row(I18n.t(l, "st_cpufreq"), cores > 0 ? String.format(Locale.US, I18n.t(l, "st_cores"), cores) : I18n.t(l, "st_unreadable")));
        }
        return sb.toString().trim();
    }

    static String csvText(Settings settings) {
        String l = settings.language();
        MetricsService svc = MetricsService.instance();
        if (svc == null) return I18n.t(l, "csv_svc_off");
        File dir = svc.csv().directory();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".csv"));
        StringBuilder c = new StringBuilder();
        c.append(settings.csv() ? I18n.t(l, "csv_rec") + (svc.csv().currentFile() != null
                ? svc.csv().currentFile().getName() + " (" + svc.csv().rows() + " " + I18n.t(l, "st_lines") + ")" : "…")
                : I18n.t(l, "csv_off")).append("\n\n");
        if (files != null && files.length > 0) {
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (int i = 0; i < Math.min(10, files.length); i++)
                c.append(files[i].getName()).append("  ").append(files[i].length() / 1024).append(" KB\n");
        } else c.append(I18n.t(l, "csv_none")).append("\n");
        c.append("\n").append(I18n.t(l, "csv_folder")).append(dir.getAbsolutePath());
        return c.toString();
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
