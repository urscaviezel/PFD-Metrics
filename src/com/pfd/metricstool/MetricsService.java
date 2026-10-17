package com.pfd.metricstool;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

import java.io.File;
import java.util.Map;

/** Foreground service: collects metrics, drives the HUD overlay and CSV capture. */
public final class MetricsService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "PFDMetrics";
    private static final String CHANNEL = "metrics";
    private static volatile MetricsService instance;

    public static MetricsService instance() { return instance; }

    public static void start(Context ctx) {
        Intent i = new Intent(ctx, MetricsService.class);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i); else ctx.startService(i);
    }

    // Pausing monitoring must not destroy Android's approved logcat session.
    public static void stop(Context ctx) { new Settings(ctx).setServiceEnabled(false); }
    public static void shutdown(Context ctx) { ctx.stopService(new Intent(ctx, MetricsService.class)); }

    private final MetricStore store = MetricStore.get();
    private Settings settings;
    private SystemSampler sampler;
    private PerfLogParser parser;
    private LogSources logs;
    private CsvRecorder csv;
    private OverlayController overlay;
    private HandlerThread thread;
    private Handler handler;
    private long ticks = 0;

    private final Runnable tick = this::runTick;

    private void runTick() {
            long t0 = System.currentTimeMillis();
            try {
                if (settings.serviceEnabled()) {
                    sampler.sample();
                    store.commitSample();
                    if (settings.csv()) csv.write(store); else if (csv.isRecording()) csv.close();
                } else if (csv.isRecording()) csv.close();
                if (++ticks % 15 == 1) logStatus();
                if (ticks % 5 == 1) writeStatusFile();
                if (ticks % 15 == 0 && settings.serviceEnabled()) checkDataReminder();
            } catch (Throwable e) {
                Log.e(TAG, "tick failed", e);
            }
            long delay = Math.max(50, settings.intervalMs() - (System.currentTimeMillis() - t0));
            handler.postDelayed(tick, delay);
    }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        settings = new Settings(this);
        File ext = getExternalFilesDir(null);
        if (ext == null) ext = getFilesDir();
        ext.mkdirs();
        parser = new PerfLogParser(store);
        logs = new LogSources(parser, ext);
        csv = new CsvRecorder(ext);
        sampler = new SystemSampler(this, store);
        overlay = new OverlayController(this, settings, store);
        startAsForeground();
        thread = new HandlerThread("pfd-sampler");
        thread.start();
        handler = new Handler(thread.getLooper());
        logs.start();
        handler.post(tick);
        settings.prefs().registerOnSharedPreferenceChangeListener(this);
        overlay.sync();
        Log.i(TAG, "MetricsService started; READ_LOGS=" + has(Manifest.permission.READ_LOGS)
                + " DUMP=" + has(Manifest.permission.DUMP) + " overlayPermission=" + overlay.canDraw()
                + " helperFile=" + logs.helperFile());
        for (Map.Entry<String, String> e : sampler.diagnostics().entrySet())
            Log.i(TAG, "probe " + e.getKey() + ": " + e.getValue());
    }

    private boolean reminderShown = false;

    /** After a headset reboot the adb log helper is gone: remind the user once runtime data is missing. */
    private void checkDataReminder() {
        boolean noData = System.currentTimeMillis() - store.lastRuntimeUpdate() > 30_000;
        boolean helperDead = !logs.helperFile().exists()
                || System.currentTimeMillis() - logs.helperFile().lastModified() > 30_000;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (noData && helperDead) {
            if (!reminderShown) {
                nm.notify(2, new Notification.Builder(this, CHANNEL)
                        .setContentTitle(I18n.t(this, "notif_nodata_title"))
                        .setContentText(I18n.t(this, "notif_nodata_text"))
                        .setStyle(new Notification.BigTextStyle().bigText(I18n.t(this, "notif_nodata_text")))
                        .setSmallIcon(android.R.drawable.stat_sys_warning)
                        .build());
                reminderShown = true;
            }
        } else if (reminderShown) {
            nm.cancel(2);
            reminderShown = false;
        }
    }

    private void startAsForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Metrics", NotificationManager.IMPORTANCE_LOW));
        Intent open = getPackageManager().getLaunchIntentForPackage("com.pfd.metricstool");
        if (open == null) open = new Intent();
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setContentTitle("PFD Metrics Tool")
                .setContentText(I18n.t(this, settings.serviceEnabled() ? "notif_active" : "notif_paused"))
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(1, n);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        overlay.sync();
        return START_STICKY;
    }

    @Override public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        overlay.sync();
        if (Settings.K_SERVICE.equals(key)) {
            startAsForeground();
            if (!settings.serviceEnabled()) {
                handler.post(csv::close);
                getSystemService(NotificationManager.class).cancel(2);
                reminderShown = false;
            }
            Log.i(TAG, "Monitoring " + (settings.serviceEnabled() ? "resumed" : "paused")
                    + "; approvedReader=" + logs.hasAuthorizedReader());
        }
        if (Settings.K_CSV.equals(key) && !settings.csv()) handler.post(csv::close);
    }

    @Override public void onDestroy() {
        settings.prefs().unregisterOnSharedPreferenceChangeListener(this);
        handler.removeCallbacksAndMessages(null);
        handler.post(csv::close);
        thread.quitSafely();
        logs.stop();
        overlay.destroy();
        instance = null;
        Log.i(TAG, "MetricsService stopped");
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private boolean has(String perm) { return checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED; }

    private void logStatus() {
        Log.i(TAG, "status logcatState=" + logs.logcatState() + " logcatLines=" + logs.logcatLines()
                + " helperLines=" + logs.helperLines() + " parsed=" + parser.linesParsed()
                + " fps=" + store.value(Metric.AVERAGE_FRAME_RATE) + " app=" + store.foregroundApp()
                + " overlay=" + overlay.state() + " csv=" + (csv.isRecording() ? csv.currentFile() : "off"));
    }

    /** status.json in the external files dir — readable via adb pull for remote diagnostics. */
    private void writeStatusFile() {
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("time", System.currentTimeMillis());
            o.put("monitoring_enabled", settings.serviceEnabled());
            o.put("approved_reader", logs.hasAuthorizedReader());
            o.put("read_logs", has(Manifest.permission.READ_LOGS));
            o.put("overlay_permission", overlay.canDraw());
            o.put("overlay_state", overlay.state());
            o.put("vr_widget_available", overlay.vrAvailable());
            o.put("system_hud_enabled", SystemHud.isEnabled(this));
            o.put("logcat_state", logs.logcatStatus());
            o.put("logcat_lines", logs.logcatLines());
            o.put("direct_data_fresh", logs.hasFreshDirectData());
            o.put("helper_lines", logs.helperLines());
            o.put("parsed_lines", parser.linesParsed());
            o.put("perf_pid", parser.lastPerfPid());
            o.put("foreground_app", store.foregroundApp());
            o.put("csv_file", csv.isRecording() ? String.valueOf(csv.currentFile()) : "");
            o.put("settings", new org.json.JSONObject(settings.asJson()));
            org.json.JSONObject probe = new org.json.JSONObject();
            for (Map.Entry<String, String> e : sampler.diagnostics().entrySet()) probe.put(e.getKey(), e.getValue());
            o.put("probe", probe);
            org.json.JSONObject vals = new org.json.JSONObject();
            double[] v = store.snapshot();
            for (Metric m : Metric.ALL) if (!Double.isNaN(v[m.id])) vals.put(m.key, v[m.id]);
            o.put("values", vals);
            File f = new File(getExternalFilesDir(null), "status.json");
            try (java.io.FileWriter w = new java.io.FileWriter(f)) { w.write(o.toString(2)); }
        } catch (Exception e) {
            Log.w(TAG, "status file failed: " + e);
        }
    }

    // --- status accessors for the UI ---
    public LogSources logSources() { return logs; }
    public PerfLogParser parser() { return parser; }
    public CsvRecorder csv() { return csv; }
    public OverlayController overlay() { return overlay; }
    public SystemSampler sampler() { return sampler; }
}
