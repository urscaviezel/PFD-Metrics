package com.pfd.metricstool;

import android.content.Context;
import android.provider.Settings.System;
import android.util.Log;

/**
 * The YVR OS ships its own metrics HUD inside VrSystemUI (found via REA): it is driven by
 * Settings.System keys "vr_metricstool_enabled" (0/1), "vr_metricstool_visible_mode" (bitmask,
 * 0x365BFF = all) and "metrics_v_fov_value" (float, default 20). Writing these normally needs adb
 * (settings put system ...); we try from the app (WRITE_SETTINGS) and report if Android refuses.
 */
public final class SystemHud {
    private static final String TAG = "PFDMetrics";
    public static final String KEY_ENABLED = "vr_metricstool_enabled";
    public static final String KEY_MODE = "vr_metricstool_visible_mode";
    public static final int ALL = 0x365BFF;

    // SysStatsInfoMode bits (com.yvr.vrsystemui.SysStatsInfoMode)
    public static final int FPS = 0x1, STALE = 0x2, EARLY = 0x4, EXTRA_LATENCY = 0x8, PRD = 0x10,
            CPU_USAGE = 0x20, GPU_USAGE = 0x40, CPU_LEVEL = 0x80, GPU_LEVEL = 0x100, MEM_AVAILABLE = 0x200,
            FOV = 0x400, ATW = 0x2000, DRR = 0x4000, LAYERS = 0x8000, GD = 0x10000, BATTERY = 0x40000;

    public static boolean isEnabled(Context ctx) {
        return System.getInt(ctx.getContentResolver(), KEY_ENABLED, 0) == 1;
    }

    /** Map our selected HUD stats to the YVR bitmask. */
    public static int maskFor(Settings s) {
        int m = 0;
        if (s.statEnabled(Metric.AVERAGE_FRAME_RATE)) m |= FPS;
        if (s.statEnabled(Metric.STALE_FRAME_COUNT)) m |= STALE;
        if (s.statEnabled(Metric.EARLY_FRAME_COUNT)) m |= EARLY;
        if (s.statEnabled(Metric.EXTRA_LATENCY_MODE)) m |= EXTRA_LATENCY;
        if (s.statEnabled(Metric.PREDICTION_MS)) m |= PRD;
        if (s.statEnabled(Metric.CPU_UTILIZATION)) m |= CPU_USAGE;
        if (s.statEnabled(Metric.GPU_UTILIZATION)) m |= GPU_USAGE;
        if (s.statEnabled(Metric.CPU_LEVEL)) m |= CPU_LEVEL;
        if (s.statEnabled(Metric.GPU_LEVEL)) m |= GPU_LEVEL;
        if (s.statEnabled(Metric.AVAILABLE_MEMORY_MB)) m |= MEM_AVAILABLE;
        if (s.statEnabled(Metric.FOVEATION_LEVEL)) m |= FOV;
        if (s.statEnabled(Metric.TIMEWARP_GPU_TIME_US)) m |= ATW;
        if (s.statEnabled(Metric.DISPLAY_REFRESH_RATE)) m |= DRR;
        if (s.statEnabled(Metric.GUARDIAN_GPU_TIME_US)) m |= GD;
        if (s.statEnabled(Metric.BATTERY_LEVEL)) m |= BATTERY;
        return m == 0 ? ALL : m;
    }

    /** @return null on success, otherwise the adb command the user has to run. */
    public static String set(Context ctx, boolean enable, int mask) {
        try {
            if (!System.canWrite(ctx)) throw new SecurityException("WRITE_SETTINGS not granted");
            System.putInt(ctx.getContentResolver(), KEY_MODE, mask);
            System.putInt(ctx.getContentResolver(), KEY_ENABLED, enable ? 1 : 0);
            return null;
        } catch (Throwable e) {
            Log.w(TAG, "System HUD write refused: " + e);
            return "adb shell settings put system " + KEY_MODE + " " + mask
                    + " && adb shell settings put system " + KEY_ENABLED + " " + (enable ? 1 : 0);
        }
    }
}
