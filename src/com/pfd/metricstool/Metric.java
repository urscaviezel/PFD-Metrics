package com.pfd.metricstool;

import java.util.HashMap;
import java.util.Map;

/**
 * Metric catalogue. Keys follow the OVR Metrics Tool naming where an equivalent exists,
 * so CSV files and adb commands (e.g. --es stat average_frame_rate) stay compatible.
 */
public final class Metric {
    public final int id;
    public final String key;      // CSV column / adb stat name
    public final String label;    // short HUD label
    public final String unit;
    public final float graphMax;  // default upper bound for graphs (auto-scales if exceeded)
    public final int decimals;
    public final String source;   // "runtime", "compositor", "system"

    private Metric(int id, String key, String label, String unit, float graphMax, int decimals, String source) {
        this.id = id; this.key = key; this.label = label; this.unit = unit;
        this.graphMax = graphMax; this.decimals = decimals; this.source = source;
    }

    // --- YVR runtime (PerfStats.cpp, per app, 1 Hz) ---
    public static final int AVERAGE_FRAME_RATE = 0;
    public static final int DISPLAY_REFRESH_RATE = 1;
    public static final int PREDICTION_MS = 2;
    public static final int EARLY_FRAME_COUNT = 3;
    public static final int STALE_FRAME_COUNT = 4;
    public static final int EXTRA_LATENCY_MODE = 5;
    public static final int FOVEATION_LEVEL = 6;
    public static final int CPU_LEVEL = 7;
    public static final int GPU_LEVEL = 8;
    public static final int AVAILABLE_MEMORY_MB = 9;
    public static final int TIMEWARP_GPU_TIME_US = 10;
    public static final int APP_GPU_TIME_US = 11;
    public static final int APP_CPU_TIME_US = 12;
    public static final int GUARDIAN_GPU_TIME_US = 13;
    public static final int CPU_UTILIZATION = 14;
    public static final int GPU_UTILIZATION = 15;
    // --- YVR compositor (CompositorMonitor, 1 Hz) ---
    public static final int COMPOSITOR_FPS = 16;
    public static final int COMPOSITOR_CPU_TIME_US = 17;
    public static final int COMPOSITOR_GPU_TIME_US = 18;
    public static final int STALE_FRAMES_CONSECUTIVE = 19;
    public static final int SPACEWARP_MODE = 20;
    // --- System (sampled by this app) ---
    public static final int BATTERY_LEVEL = 21;
    public static final int BATTERY_TEMPERATURE = 22;
    public static final int BATTERY_CURRENT = 23;
    public static final int POWER_VOLTAGE = 24;
    public static final int POWER_WATTAGE = 25;
    public static final int CPU_FREQUENCY_MHZ = 26;
    public static final int CPU_FREQUENCY_BIG_MHZ = 27;
    public static final int GPU_FREQUENCY_MHZ = 28;
    public static final int SYS_AVAILABLE_MEMORY_MB = 29;
    public static final int THERMAL_HEADROOM = 30;
    public static final int THERMAL_STATUS = 31;
    public static final int SYS_CPU_UTILIZATION = 32;
    public static final int FRAME_TIME_MS = 33;

    public static final Metric[] ALL = {
        new Metric(AVERAGE_FRAME_RATE, "average_frame_rate", "FPS", "", 120, 1, "runtime"),
        new Metric(DISPLAY_REFRESH_RATE, "display_refresh_rate", "Refresh", "Hz", 120, 0, "runtime"),
        new Metric(PREDICTION_MS, "average_prediction_milliseconds", "Pred", "ms", 50, 1, "runtime"),
        new Metric(EARLY_FRAME_COUNT, "early_frame_count", "Early", "", 120, 0, "runtime"),
        new Metric(STALE_FRAME_COUNT, "stale_frame_count", "Stale", "", 30, 0, "runtime"),
        new Metric(EXTRA_LATENCY_MODE, "extra_latency_mode", "ExtLat", "", 2, 0, "runtime"),
        new Metric(FOVEATION_LEVEL, "foveation_level", "Fov", "", 4, 0, "runtime"),
        new Metric(CPU_LEVEL, "cpu_level", "CPU Lvl", "", 5, 0, "runtime"),
        new Metric(GPU_LEVEL, "gpu_level", "GPU Lvl", "", 5, 0, "runtime"),
        new Metric(AVAILABLE_MEMORY_MB, "available_memory_MB", "Free", "MB", 16000, 0, "runtime"),
        new Metric(TIMEWARP_GPU_TIME_US, "timewarp_gpu_time_microseconds", "ATW", "us", 11111, 0, "runtime"),
        new Metric(APP_GPU_TIME_US, "app_gpu_time_microseconds", "App GPU", "us", 11111, 0, "runtime"),
        new Metric(APP_CPU_TIME_US, "app_cpu_time_microseconds", "App CPU", "us", 11111, 0, "runtime"),
        new Metric(GUARDIAN_GPU_TIME_US, "guardian_gpu_time_microseconds", "Guardian", "us", 2000, 0, "runtime"),
        new Metric(CPU_UTILIZATION, "cpu_utilization_percentage", "CPU", "%", 100, 0, "runtime"),
        new Metric(GPU_UTILIZATION, "gpu_utilization_percentage", "GPU", "%", 100, 0, "runtime"),
        new Metric(COMPOSITOR_FPS, "compositor_frame_rate", "Comp FPS", "", 120, 1, "compositor"),
        new Metric(COMPOSITOR_CPU_TIME_US, "compositor_cpu_time_microseconds", "Comp CPU", "us", 11111, 0, "compositor"),
        new Metric(COMPOSITOR_GPU_TIME_US, "compositor_gpu_time_microseconds", "Comp GPU", "us", 11111, 0, "compositor"),
        new Metric(STALE_FRAMES_CONSECUTIVE, "stale_frames_consecutive", "MaxStale", "", 10, 0, "compositor"),
        new Metric(SPACEWARP_MODE, "spacewarp_motion_vector_type", "SW", "", 3, 0, "compositor"),
        new Metric(BATTERY_LEVEL, "battery_level_percentage", "Batt", "%", 100, 0, "system"),
        new Metric(BATTERY_TEMPERATURE, "battery_temperature_celcius", "BattTemp", "C", 60, 1, "system"),
        new Metric(BATTERY_CURRENT, "battery_current_now_amps", "Current", "A", 5f, 2, "system"),
        new Metric(POWER_VOLTAGE, "power_voltage", "Volt", "V", 10f, 2, "system"),
        new Metric(POWER_WATTAGE, "power_wattage", "Power", "W", 15f, 2, "system"),
        new Metric(CPU_FREQUENCY_MHZ, "cpu_frequency_MHz", "CPU MHz", "", 3500, 0, "system"),
        new Metric(CPU_FREQUENCY_BIG_MHZ, "cpu_frequency_big_core_MHz", "Big MHz", "", 3500, 0, "system"),
        new Metric(GPU_FREQUENCY_MHZ, "gpu_frequency_MHz", "GPU MHz", "", 1200, 0, "system"),
        new Metric(SYS_AVAILABLE_MEMORY_MB, "system_available_memory_MB", "SysFree", "MB", 16000, 0, "system"),
        new Metric(THERMAL_HEADROOM, "thermal_headroom", "Thermal", "", 1.5f, 2, "system"),
        new Metric(THERMAL_STATUS, "thermal_status", "ThermSt", "", 6, 0, "system"),
        new Metric(SYS_CPU_UTILIZATION, "system_cpu_utilization_percentage", "SysCPU", "%", 100, 0, "system"),
        new Metric(FRAME_TIME_MS, "frame_time_milliseconds", "Frame", "ms", 33.3f, 2, "runtime"),
    };

    public static final int COUNT = ALL.length;

    private static final Map<String, Integer> BY_KEY = new HashMap<>();
    static {
        for (Metric m : ALL) {
            if (ALL[m.id] != m) throw new IllegalStateException("Metric table out of order at " + m.key);
            BY_KEY.put(m.key.toLowerCase(), m.id);
        }
        // OVR aliases
        BY_KEY.put("fps", AVERAGE_FRAME_RATE);
        BY_KEY.put("cpu_utilization", CPU_UTILIZATION);
        BY_KEY.put("gpu_utilization", GPU_UTILIZATION);
    }

    /** Resolve a metric by name (OVR key, case-insensitive) or numeric index. -1 if unknown. */
    public static int find(String nameOrIndex) {
        if (nameOrIndex == null || nameOrIndex.isEmpty()) return -1;
        Integer i = BY_KEY.get(nameOrIndex.trim().toLowerCase());
        if (i != null) return i;
        try {
            int n = Integer.parseInt(nameOrIndex.trim());
            return (n >= 0 && n < COUNT) ? n : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public String format(double v) {
        if (Double.isNaN(v)) return "--";
        switch (decimals) {
            case 0: return String.valueOf(Math.round(v));
            case 1: return String.format(java.util.Locale.US, "%.1f", v);
            default: return String.format(java.util.Locale.US, "%.2f", v);
        }
    }

    /** Default HUD selection, similar to OVR's "Basic" preset. */
    public static final int[] DEFAULT_STATS = {
        AVERAGE_FRAME_RATE, DISPLAY_REFRESH_RATE, FRAME_TIME_MS, STALE_FRAME_COUNT,
        APP_GPU_TIME_US, TIMEWARP_GPU_TIME_US, CPU_UTILIZATION, GPU_UTILIZATION,
        CPU_LEVEL, GPU_LEVEL, CPU_FREQUENCY_MHZ, AVAILABLE_MEMORY_MB,
        BATTERY_LEVEL, BATTERY_TEMPERATURE, POWER_WATTAGE
    };
    public static final int[] DEFAULT_GRAPHS = { AVERAGE_FRAME_RATE, GPU_UTILIZATION };
}
