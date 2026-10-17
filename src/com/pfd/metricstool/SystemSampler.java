package com.pfd.metricstool;

import android.app.ActivityManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Samples metrics this app can read itself (battery, memory, clocks, thermal, foreground app). */
public final class SystemSampler {
    private static final String[] GPU_FREQ_PATHS = {
        "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
        "/sys/class/kgsl/kgsl-3d0/gpuclk",
        "/sys/class/devfreq/3d00000.qcom,kgsl-3d0/cur_freq",
        "/sys/kernel/gpu/gpu_clock",
    };

    private final Context ctx;
    private final MetricStore store;
    private final ActivityManager am;
    private final BatteryManager bm;
    private final PowerManager pm;
    private final UsageStatsManager usm;
    private final List<String> cpuFreqPaths = new ArrayList<>();
    private String gpuFreqPath;
    private boolean procStatReadable;
    private long[] lastCpuTotals;
    private long lastUsageQuery = 0;
    private final Map<String, String> diagnostics = new LinkedHashMap<>();

    public SystemSampler(Context ctx, MetricStore store) {
        this.ctx = ctx.getApplicationContext();
        this.store = store;
        am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
        pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        usm = (UsageStatsManager) ctx.getSystemService(Context.USAGE_STATS_SERVICE);
        probe();
    }

    private void probe() {
        for (int i = 0; i < 16; i++) {
            String p = "/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq";
            if (readLong(p) > 0) cpuFreqPaths.add(p);
        }
        diagnostics.put("CPU-Takt (cpufreq)", cpuFreqPaths.isEmpty() ? "nicht lesbar" : cpuFreqPaths.size() + " Kerne lesbar");
        for (String p : GPU_FREQ_PATHS) {
            if (readLong(p) > 0) { gpuFreqPath = p; break; }
        }
        diagnostics.put("GPU-Takt (kgsl)", gpuFreqPath == null ? "nicht lesbar (SELinux)" : gpuFreqPath);
        procStatReadable = readCpuTotals() != null;
        diagnostics.put("/proc/stat", procStatReadable ? "lesbar" : "nicht lesbar (CPU% kommt vom Runtime)");
    }

    public Map<String, String> diagnostics() { return diagnostics; }
    public int cpuFreqCores() { return cpuFreqPaths.size(); }

    public void sample() {
        sampleBattery();
        sampleMemory();
        sampleClocks();
        sampleThermal();
        sampleCpuUtil();
        sampleForeground();
    }

    private void sampleBattery() {
        Intent b = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (b != null) {
            int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (level >= 0 && scale > 0) store.put(Metric.BATTERY_LEVEL, level * 100.0 / scale);
            int temp = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            if (temp != Integer.MIN_VALUE) store.put(Metric.BATTERY_TEMPERATURE, temp / 10.0);
            int mv = b.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            if (mv > 0) store.put(Metric.POWER_VOLTAGE, mv / 1000.0);   // V
            long ua = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            if (ua != Long.MIN_VALUE) {
                // BATTERY_PROPERTY_CURRENT_NOW is µA on this device (verified: ~330 000 µA at ~2.8 W).
                // The former "small value = mA" guess turned low currents (e.g. 17 mA) into 17 A / 150 W.
                double amps = ua / 1e6;
                store.put(Metric.BATTERY_CURRENT, amps);                                  // A
                if (mv > 0) store.put(Metric.POWER_WATTAGE, Math.abs(amps) * mv / 1000.0);  // W
            }
        }
    }

    private void sampleMemory() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        store.put(Metric.SYS_AVAILABLE_MEMORY_MB, mi.availMem / 1048576.0);
    }

    private void sampleClocks() {
        if (!cpuFreqPaths.isEmpty()) {
            long sum = 0, maxKhz = 0; int n = 0; long big = 0;
            for (int i = 0; i < cpuFreqPaths.size(); i++) {
                long khz = readLong(cpuFreqPaths.get(i));
                if (khz > 0) { sum += khz; n++; if (khz > maxKhz) maxKhz = khz; big = khz; }
            }
            if (n > 0) {
                store.put(Metric.CPU_FREQUENCY_MHZ, maxKhz / 1000.0);
                store.put(Metric.CPU_FREQUENCY_BIG_MHZ, big / 1000.0);
            }
        }
        if (gpuFreqPath != null) {
            long f = readLong(gpuFreqPath);
            if (f > 0) store.put(Metric.GPU_FREQUENCY_MHZ, f > 100000 ? f / 1e6 : f);
        }
    }

    private void sampleThermal() {
        if (Build.VERSION.SDK_INT >= 30) {
            float h = pm.getThermalHeadroom(10);
            if (!Float.isNaN(h)) store.put(Metric.THERMAL_HEADROOM, h);
        }
        store.put(Metric.THERMAL_STATUS, pm.getCurrentThermalStatus());
    }

    private void sampleCpuUtil() {
        if (!procStatReadable) return;
        long[] t = readCpuTotals();
        if (t == null) return;
        if (lastCpuTotals != null) {
            long dTotal = t[0] - lastCpuTotals[0], dIdle = t[1] - lastCpuTotals[1];
            if (dTotal > 0) store.put(Metric.SYS_CPU_UTILIZATION, 100.0 * (dTotal - dIdle) / dTotal);
        }
        lastCpuTotals = t;
    }

    private void sampleForeground() {
        long now = System.currentTimeMillis();
        if (usm == null || now - lastUsageQuery < 1000) return;
        try {
            UsageEvents ev = usm.queryEvents(Math.max(lastUsageQuery, now - 60_000) - 1000, now);
            lastUsageQuery = now;
            UsageEvents.Event e = new UsageEvents.Event();
            String latest = null;
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                if (e.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED
                        && !ctx.getPackageName().equals(e.getPackageName())) {
                    latest = e.getPackageName();
                }
            }
            if (latest != null) store.setForegroundApp(latest);
        } catch (Exception ignored) {
            lastUsageQuery = now;
        }
    }

    private static long[] readCpuTotals() {
        String s = readString("/proc/stat");
        if (s == null || !s.startsWith("cpu ")) return null;
        String[] f = s.substring(0, s.indexOf('\n')).trim().split("\\s+");
        long total = 0;
        for (int i = 1; i < f.length; i++) total += Long.parseLong(f[i]);
        long idle = Long.parseLong(f[4]) + (f.length > 5 ? Long.parseLong(f[5]) : 0);
        return new long[]{total, idle};
    }

    static long readLong(String path) {
        String s = readString(path);
        if (s == null) return -1;
        try { return Long.parseLong(s.trim().split("\\s+")[0]); } catch (Exception e) { return -1; }
    }

    static String readString(String path) {
        File f = new File(path);
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[4096];
            int n = in.read(buf);
            return n > 0 ? new String(buf, 0, n, StandardCharsets.US_ASCII) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
