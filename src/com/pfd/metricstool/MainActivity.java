package com.pfd.metricstool;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings.Secure;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Companion UI: switches, live graphs, session statistics, metric selection, setup/diagnostics. */
public final class MainActivity extends Activity implements MetricStore.Listener {
    private static final int BG = 0xFF101318, CARD = 0xFF1A1E26, ACCENT = 0xFF3DDC84, MUTED = 0xFF90A4AE;
    private static final int[] STATS_TABLE = {
        Metric.AVERAGE_FRAME_RATE, Metric.FRAME_TIME_MS, Metric.STALE_FRAME_COUNT, Metric.APP_GPU_TIME_US,
        Metric.APP_CPU_TIME_US, Metric.TIMEWARP_GPU_TIME_US, Metric.CPU_UTILIZATION, Metric.GPU_UTILIZATION,
        Metric.CPU_LEVEL, Metric.GPU_LEVEL, Metric.CPU_FREQUENCY_MHZ, Metric.AVAILABLE_MEMORY_MB,
        Metric.BATTERY_TEMPERATURE, Metric.POWER_WATTAGE, Metric.THERMAL_HEADROOM
    };

    private final MetricStore store = MetricStore.get();
    private final java.util.concurrent.ExecutorService poller = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Settings settings;
    private TextView statusText, statsTable, csvText, sessionText;
    private GraphPanel graphPanel;
    private final List<TextView> metricValueViews = new ArrayList<>();
    private boolean bound;
    private AppUpdates updates;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        settings = new Settings(this);
        updates = new AppUpdates(this);
        // Notifications belong to the HUD service, not this companion package.
        hudInstalled = RemoteHud.installed(this);
        importRemoteSettings();
        if (settings.serviceEnabled()) RemoteHud.call(this, "start_service", null, null);
        setContentView(buildUi());
        settings.prefs().registerOnSharedPreferenceChangeListener(prefSync);
    }

    /** Mirror every local settings change into the HUD package (which owns the real settings). */
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener prefSync = (sp, key) -> {
        if (key == null) return;
        Object v = sp.getAll().get(key);
        poller.execute(() -> RemoteHud.set(this, key, v));
    };

    private void openLogConsent() {
        Bundle session = RemoteHud.call(this, "log_session", null, null);
        if (session != null && session.getBoolean("approved_reader", false)) return;
        try {
            startActivity(new Intent().setClassName(RemoteHud.HUD_PACKAGE,
                    "com.pfd.metricstool.LogConsentActivity"));
        } catch (android.content.ActivityNotFoundException | SecurityException e) {
            android.widget.Toast.makeText(this, tr("consent_update"), android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void importRemoteSettings() {
        Bundle r = RemoteHud.call(this, "get_settings", null, null);
        if (r == null) return;
        android.content.SharedPreferences.Editor e = settings.prefs().edit();
        for (String k : r.keySet()) {
            Object v = r.get(k);
            if (v instanceof Boolean) e.putBoolean(k, (Boolean) v);
            else if (v instanceof Float) e.putFloat(k, (Float) v);
            else if (v instanceof Integer) e.putInt(k, (Integer) v);
            else if (v instanceof Long) e.putLong(k, (Long) v);
            else if (v instanceof String) e.putString(k, (String) v);
        }
        e.commit();
    }

    private boolean hudInstalled;
    private volatile Bundle lastSnapshot;
    private final Runnable poll = new PollTask(this);

    void pollOnce() {
        Bundle b = RemoteHud.call(this, "snapshot", null, null);
        if (b != null) {
            lastSnapshot = b;
            store.applySnapshot(b);
        } else {
            ui.post(this::refresh);
        }
        if (bound) ui.postDelayed(poll, 1000);
    }

    void schedulePoll() { poller.execute(this::pollOnce); }

    @Override protected void onDestroy() {
        settings.prefs().unregisterOnSharedPreferenceChangeListener(prefSync);
        poller.shutdownNow();
        updates.close();
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        settings.prefs().unregisterOnSharedPreferenceChangeListener(prefSync);
        importRemoteSettings();
        setContentView(buildUi());
        settings.prefs().registerOnSharedPreferenceChangeListener(prefSync);
        store.addListener(this);
        bound = true;
        schedulePoll();
        refresh();
        updates.onResume();
        UpdateConfirmation.resume(this);
    }

    @Override protected void onPause() {
        UpdateConfirmation.pause(this);
        store.removeListener(this);
        bound = false;
        ui.removeCallbacks(poll);
        super.onPause();
    }

    @Override public void onSample(MetricStore s) { ui.post(this::refresh); }

    // ---------------------------------------------------------------- UI construction
    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = vertical();
        root.setPadding(dp(20), dp(16), dp(20), dp(32));
        scroll.addView(root);

        TextView title = text("PFD Metrics Tool", 26, Color.WHITE, true);
        root.addView(title);
        root.addView(text(tr("subtitle"), 13, MUTED, false));
        root.addView(languageSwitch());
        updates.addCard(root);
        installCard(root);

        LinearLayout status = card(root, "card_status");
        statusText = text("", 13, Color.WHITE, false);
        statusText.setTypeface(Typeface.MONOSPACE);
        status.addView(statusText);

        LinearLayout ctl = card(root, "card_control");
        TextView hint = text(tr(hudInstalled ? "hint_ok" : "hint_missing"), 12, hudInstalled ? MUTED : 0xFFFF6B6B, false);
        ctl.addView(hint);
        ctl.addView(toggle(tr("t_service"), settings.serviceEnabled(), (v, on) -> {
            settings.setServiceEnabled(on);
            if (on) openLogConsent();
        }));
        Button consent = new Button(this);
        consent.setText(tr("consent_button"));
        consent.setOnClickListener(v -> openLogConsent());
        ctl.addView(consent);
        ctl.addView(toggle(tr("t_hud"), settings.overlay(), (v, on) -> settings.setOverlay(on)));
        ctl.addView(toggle(tr("t_values"), settings.stats(), (v, on) -> settings.setStats(on)));
        ctl.addView(toggle(tr("t_graphs"), settings.graphs(), (v, on) -> settings.setGraphs(on)));
        ctl.addView(toggle(tr("t_csv"), settings.csv(), (v, on) -> settings.setCsv(on)));
        ctl.addView(slider(tr("s_size"), 25, 200, (int) (settings.scale() * 100), "%", 100, p -> settings.setScale(p / 100f)));
        ctl.addView(slider(tr("s_yaw"), (int) -Settings.MAX_YAW, (int) Settings.MAX_YAW, (int) settings.yaw(), "°", 0, p -> settings.setYaw(p)));
        ctl.addView(slider(tr("s_pitch"), (int) -Settings.MAX_PITCH, (int) Settings.MAX_PITCH, (int) settings.pitch(), "°", 0, p -> settings.setPitch(p)));
        ctl.addView(slider(tr("s_quad_y"), -30, 30, Math.round(settings.quadOffsetY() * 10), " /10", 0, p -> settings.setQuadOffsetY(p / 10f)));
        ctl.addView(slider(tr("s_opacity"), 0, 100, settings.bgOpacity(), "%", 100, p -> settings.setBgOpacity(p)));
        ctl.addView(slider(tr("s_columns"), 1, 5, settings.columns(), "", 2, p -> settings.setColumns(p)));
        ctl.addView(toggle(tr("t_frame"), settings.showFrame(), (v, on) -> settings.setShowFrame(on)));
        ctl.addView(slider(tr("s_interval"), 250, 5000, settings.intervalMs(), " ms", 1000, p -> settings.setIntervalMs(p)));

        LinearLayout graphs = card(root, "card_graphs");
        graphPanel = new GraphPanel(this, settings, store);
        graphs.addView(graphPanel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(440)));

        LinearLayout stats = card(root, "card_stats");
        sessionText = text("", 12, MUTED, false);
        stats.addView(sessionText);
        statsTable = text("", 13, Color.WHITE, false);
        statsTable.setTypeface(Typeface.MONOSPACE);
        stats.addView(statsTable);
        Button reset = button(tr("btn_reset"), v -> poller.execute(() -> RemoteHud.call(this, "reset_stats", null, null)));
        stats.addView(reset);

        LinearLayout metrics = card(root, "card_metrics");
        for (Metric m : Metric.ALL) metrics.addView(metricRow(m));

        LinearLayout csv = card(root, "card_csv");
        csvText = text("", 12, Color.WHITE, false);
        csvText.setTypeface(Typeface.MONOSPACE);
        csv.addView(csvText);

        LinearLayout setup = card(root, "card_setup");
        setup.addView(button(tr("btn_overlay_perm"), v -> openOverlaySettings()));
        setup.addView(button(tr("btn_uninstall_all"), v -> HudInstaller.uninstallAll(this)));
        TextView cmds = text(setupCommands(), 12, 0xFFCFD8DC, false);
        cmds.setTypeface(Typeface.MONOSPACE);
        cmds.setTextIsSelectable(true);
        setup.addView(cmds);
        return scroll;
    }

    private View metricRow(Metric m) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout labels = vertical();
        labels.addView(text(m.label + (m.unit.isEmpty() ? "" : " (" + m.unit + ")"), 14, Color.WHITE, false));
        labels.addView(text(m.key + " · " + m.source, 11, MUTED, false));
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView val = text("--", 14, ACCENT, true);
        val.setTag(m.id);
        val.setMinWidth(dp(80));
        val.setGravity(Gravity.END);
        metricValueViews.add(val);
        row.addView(val);
        CheckBox hud = check("HUD", settings.statEnabled(m.id), (b, on) -> settings.setStatEnabled(m.id, on));
        CheckBox gr = check(tr("chk_graph"), settings.graphEnabled(m.id), (b, on) -> settings.setGraphEnabled(m.id, on));
        row.addView(hud);
        row.addView(gr);
        row.setPadding(0, dp(4), 0, dp(4));
        return row;
    }

    // ---------------------------------------------------------------- refresh
    private void refresh() {
        if (!bound) return;
        Bundle snap = lastSnapshot;
        statusText.setText(snap == null ? tr(hudInstalled ? "connecting" : "not_installed")
                : snap.getString("status", ""));

        double[] v = store.snapshot();
        for (TextView tv : metricValueViews) {
            Metric m = Metric.ALL[(Integer) tv.getTag()];
            tv.setText(m.format(v[m.id]));
        }

        long mins = (System.currentTimeMillis() - store.sessionStart()) / 60000;
        sessionText.setText(String.format(Locale.US, tr("session"), mins));
        StringBuilder t = new StringBuilder(String.format(Locale.US, "%-9s %7s %7s %7s %7s %7s%n", "", tr("avg"), "Min", "Max", "1%", "99%"));
        for (int id : STATS_TABLE) {
            Metric m = Metric.ALL[id];
            MetricStore.Stats s = store.stats(id);
            t.append(String.format(Locale.US, "%-9s %7s %7s %7s %7s %7s%n", trim(m.label, 9),
                    m.format(s.avg), m.format(s.min), m.format(s.max), m.format(s.p1), m.format(s.p99)));
        }
        statsTable.setText(t.toString());

        if (snap != null) csvText.setText(snap.getString("csv", ""));
        graphPanel.invalidate();
    }

    private String tr(String key) { return I18n.t(settings.language(), key); }

    /** Language selector DE / EN / FR at the top of the UI (segmented switch). */
    private View languageSwitch() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, 0);
        row.addView(text(tr("language") + ":  ", 14, MUTED, false));
        for (String l : I18n.LANGS) {
            Button b = new Button(this);
            b.setText(l.toUpperCase(Locale.ROOT));
            b.setAllCaps(false);
            boolean sel = l.equals(settings.language());
            b.setTextColor(sel ? BG : Color.WHITE);
            b.setBackgroundColor(sel ? ACCENT : CARD);
            b.setOnClickListener(v -> { if (!l.equals(settings.language())) { settings.setLanguage(l); recreate(); } });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(64), dp(40));
            lp.rightMargin = dp(6);
            row.addView(b, lp);
        }
        return row;
    }

    /** Shown when the embedded HUD service is missing or older than the bundled one. */
    private void installCard(LinearLayout root) {
        int st = HudInstaller.state(this);
        if (st == HudInstaller.CURRENT) return;
        LinearLayout c = vertical();
        c.setBackgroundColor(0xFF3A2A12);
        c.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        root.addView(c, lp);
        c.addView(text(tr(st == HudInstaller.MISSING ? "hint_missing" : "hint_update"), 14, 0xFFFECA57, true));
        c.addView(button(tr("btn_install_hud"), v -> {
            String err = HudInstaller.install(this);
            android.widget.Toast.makeText(this, err == null ? tr("install_started") : err, android.widget.Toast.LENGTH_LONG).show();
        }));
    }

    private String perm(String p) { return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED ? "erteilt" : "fehlt"; }

    private String setupCommands() {
        String pkg = RemoteHud.HUD_PACKAGE;
        String rcv = pkg + "/com.pfd.metricstool.SettingsBroadcastReceiver";
        return tr("setup_adb_note") + "\n\n" + tr("setup_intro") + "\n\n"
            + "adb shell pm grant " + pkg + " android.permission.READ_LOGS\n"
            + "adb shell appops set " + pkg + " SYSTEM_ALERT_WINDOW allow\n"
            + "adb shell appops set " + pkg + " GET_USAGE_STATS allow\n"
            + "adb shell appops set " + pkg + " WRITE_SETTINGS allow\n\n"
            + tr("setup_control") + "\n"
            + "adb shell am broadcast -n " + rcv + " -a com.pfd.metricstool.ENABLE_OVERLAY\n"
            + "adb shell am broadcast -n " + rcv + " -a com.pfd.metricstool.ENABLE_CSV\n"
            + "adb shell am broadcast -n " + rcv + " -a com.pfd.metricstool.SET_GRAPH_STAT --es stat average_frame_rate\n\n"
            + tr("setup_syshud") + "\n"
            + "adb shell settings put system vr_metricstool_visible_mode 3562495\n"
            + "adb shell settings put system vr_metricstool_enabled 1\n\n"
            + tr("setup_pull") + "\nadb pull /sdcard/Android/data/" + pkg + "/files/CapturedMetrics .";
    }

    private void openOverlaySettings() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + RemoteHud.HUD_PACKAGE)));
        } catch (Exception ignored) { }
    }

    // ---------------------------------------------------------------- small widget helpers

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    /** Collapsible card: tap the title to open/close; state is remembered (UI-only prefs). */
    private LinearLayout card(LinearLayout parent, String title) {
        LinearLayout outer = vertical();
        outer.setBackgroundColor(CARD);
        outer.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        parent.addView(outer, lp);
        android.content.SharedPreferences uiState = getSharedPreferences("ui_state", MODE_PRIVATE);
        String key = "open_" + title;
        boolean open = uiState.getBoolean(key, "card_status".equals(title) || "card_control".equals(title));
        TextView t = text((open ? "\u25BE  " : "\u25B8  ") + tr(title), 16, ACCENT, true);
        t.setPadding(0, dp(2), 0, dp(6));
        outer.addView(t);
        LinearLayout body = vertical();
        body.setVisibility(open ? View.VISIBLE : View.GONE);
        outer.addView(body);
        t.setOnClickListener(v -> {
            boolean nowOpen = body.getVisibility() != View.VISIBLE;
            body.setVisibility(nowOpen ? View.VISIBLE : View.GONE);
            t.setText((nowOpen ? "\u25BE  " : "\u25B8  ") + tr(title));
            uiState.edit().putBoolean(key, nowOpen).apply();
        });
        return body;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Switch toggle(String label, boolean on, CompoundButton.OnCheckedChangeListener l) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextColor(Color.WHITE);
        s.setTextSize(15);
        s.setChecked(on);
        s.setOnCheckedChangeListener(l);
        s.setPadding(0, dp(6), 0, dp(6));
        return s;
    }

    private CheckBox check(String label, boolean on, CompoundButton.OnCheckedChangeListener l) {
        CheckBox c = new CheckBox(this);
        c.setText(label);
        c.setTextColor(MUTED);
        c.setChecked(on);
        c.setOnCheckedChangeListener(l);
        return c;
    }

    private Button button(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    /** Slider with a "reset to default" button on the right. */
    private View slider(String label, int min, int max, int value, String unit, int def, SliderListener.IntCallback cb) {
        LinearLayout l = vertical();
        TextView t = text(label + ": " + value + unit, 13, MUTED, false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(Math.max(0, Math.min(max - min, value - min)));
        sb.setOnSeekBarChangeListener(new SliderListener(p -> { t.setText(label + ": " + (p + min) + unit); }, p -> cb.on(p + min)));
        row.addView(sb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button reset = new Button(this);
        reset.setText("\u21BA");
        reset.setTextSize(18);
        reset.setContentDescription(tr("reset_default") + " (" + def + unit + ")");
        reset.setOnClickListener(v -> {
            sb.setProgress(def - min);           // updates the label
            cb.on(def);                           // and the setting
            android.widget.Toast.makeText(this, tr("reset_default") + ": " + def + unit, android.widget.Toast.LENGTH_SHORT).show();
        });
        row.addView(reset, new LinearLayout.LayoutParams(dp(56), dp(44)));
        l.addView(t);
        l.addView(row);
        l.setPadding(0, dp(4), 0, dp(4));
        return l;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private static String trim(String s, int n) { return s.length() > n ? s.substring(0, n) : s; }
}
