package com.pfd.metricstool;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/** Persistent settings, mirroring OVR Metrics Tool's PersistentSettingsManager surface. */
public final class Settings {
    public static final String PREFS = "pfd_metrics";
    public static final String K_OVERLAY = "overlay_enabled";
    public static final String K_STATS = "stats_enabled";
    public static final String K_GRAPHS = "graphs_enabled";
    public static final String K_CSV = "csv_enabled";
    public static final String K_HEADLOCKED = "overlay_headlocked";
    public static final String K_SCALE = "overlay_scale";
    public static final String K_DISTANCE = "overlay_distance";
    public static final String K_PITCH = "overlay_pitch";
    public static final String K_YAW = "overlay_yaw";
    public static final String K_INTERVAL = "sample_interval_ms";
    public static final String K_SERVICE = "service_enabled";
    public static final String K_HUD_MODE = "hud_mode";   // "vr" (YVR widget, immersive) | "2d" (Android overlay window)
    private static final String K_STAT_PREFIX = "stat_";
    private static final String K_GRAPH_PREFIX = "graph_";

    private final SharedPreferences sp;

    public Settings(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!sp.getBoolean("scale_v2", false)) {
            // v2.1: 100 % now equals the former 50 % – start from the new default.
            sp.edit().putFloat(K_SCALE, 1.0f).putBoolean("scale_v2", true).apply();
        }
        if (!sp.getBoolean("initialized", false)) {
            SharedPreferences.Editor e = sp.edit().putBoolean("initialized", true);
            for (int id : Metric.DEFAULT_STATS) e.putBoolean(K_STAT_PREFIX + Metric.ALL[id].key, true);
            for (int id : Metric.DEFAULT_GRAPHS) e.putBoolean(K_GRAPH_PREFIX + Metric.ALL[id].key, true);
            e.putBoolean(K_STATS, true).putBoolean(K_GRAPHS, true).apply();
        }
    }

    public SharedPreferences prefs() { return sp; }

    public boolean overlay() { return sp.getBoolean(K_OVERLAY, false); }
    public void setOverlay(boolean v) { sp.edit().putBoolean(K_OVERLAY, v).apply(); }
    public boolean stats() { return sp.getBoolean(K_STATS, true); }
    public void setStats(boolean v) { sp.edit().putBoolean(K_STATS, v).apply(); }
    public boolean graphs() { return sp.getBoolean(K_GRAPHS, true); }
    public void setGraphs(boolean v) { sp.edit().putBoolean(K_GRAPHS, v).apply(); }
    public boolean csv() { return sp.getBoolean(K_CSV, false); }
    public void setCsv(boolean v) { sp.edit().putBoolean(K_CSV, v).apply(); }
    public boolean serviceEnabled() { return sp.getBoolean(K_SERVICE, false); }
    public void setServiceEnabled(boolean v) { sp.edit().putBoolean(K_SERVICE, v).apply(); }

    public String hudMode() { return "vr"; }   // the 2D overlay window is not visible on YVR OS
    public void setHudMode(String m) { sp.edit().putString(K_HUD_MODE, "2d".equals(m) ? "2d" : "vr").apply(); }

    public boolean headLocked() { return sp.getBoolean(K_HEADLOCKED, true); }
    public void setHeadLocked(boolean v) { sp.edit().putBoolean(K_HEADLOCKED, v).apply(); }
    public float scale() { return sp.getFloat(K_SCALE, 1.0f); }
    public void setScale(float v) { sp.edit().putFloat(K_SCALE, clamp(v, 0.25f, 4f)).apply(); }
    public float distance() { return sp.getFloat(K_DISTANCE, 1.0f); }
    public void setDistance(float v) { sp.edit().putFloat(K_DISTANCE, clamp(v, 0.25f, 4f)).apply(); }
    /** Pitch / yaw in degrees; on PFD they move the 2D overlay within the panel (±45° → edge). */
    public float pitch() { return clamp(sp.getFloat(K_PITCH, 0f), -MAX_PITCH, MAX_PITCH); }
    public void setPitch(float v) { sp.edit().putFloat(K_PITCH, clamp(v, -MAX_PITCH, MAX_PITCH)).apply(); }
    public static final float MAX_YAW = 55f, MAX_PITCH = 50f;
    public String language() { return sp.getString("ui_lang", I18n.defaultLang()); }
    public void setLanguage(String l) { sp.edit().putString("ui_lang", l).apply(); }
    public int columns() { return Math.max(1, Math.min(5, sp.getInt("hud_columns", 2))); }
    public void setColumns(int c) { sp.edit().putInt("hud_columns", Math.max(1, Math.min(5, c))).apply(); }
    /** Experimental: vertical offset of the YVR widget quad (raw translationY, small values only). */
    public float quadOffsetY() { return clamp(sp.getFloat("quad_offset_y", 0f), -3f, 3f); }
    public void setQuadOffsetY(float v) { sp.edit().putFloat("quad_offset_y", clamp(v, -3f, 3f)).apply(); }
    /** Background opacity of the HUD panel in percent (0 = fully transparent, 100 = opaque). */
    /** HUD background slider 0–100: 0 = lightest still-readable background, 100 = fully opaque (default). */
    public int bgOpacity() { return Math.max(0, Math.min(100, sp.getInt("hud_bg_level", 100))); }
    public void setBgOpacity(int v) { sp.edit().putInt("hud_bg_level", Math.max(0, Math.min(100, v))).apply(); }
    public boolean showFrame() { return sp.getBoolean("hud_show_frame", false); }
    public void setShowFrame(boolean v) { sp.edit().putBoolean("hud_show_frame", v).apply(); }
    public float yaw() { return clamp(sp.getFloat(K_YAW, 0f), -MAX_YAW, MAX_YAW); }
    public void setYaw(float v) { sp.edit().putFloat(K_YAW, clamp(v, -MAX_YAW, MAX_YAW)).apply(); }
    public int intervalMs() { return sp.getInt(K_INTERVAL, 1000); }
    public void setIntervalMs(int v) { sp.edit().putInt(K_INTERVAL, Math.max(250, Math.min(10000, v))).apply(); }

    public boolean statEnabled(int id) { return sp.getBoolean(K_STAT_PREFIX + Metric.ALL[id].key, false); }
    public void setStatEnabled(int id, boolean v) { sp.edit().putBoolean(K_STAT_PREFIX + Metric.ALL[id].key, v).apply(); }
    public boolean graphEnabled(int id) { return sp.getBoolean(K_GRAPH_PREFIX + Metric.ALL[id].key, false); }
    public void setGraphEnabled(int id, boolean v) { sp.edit().putBoolean(K_GRAPH_PREFIX + Metric.ALL[id].key, v).apply(); }

    /** SET_GRAPH_STAT: show exactly this one metric as graph. */
    public void setOnlyGraph(int id) {
        SharedPreferences.Editor e = sp.edit();
        for (Metric m : Metric.ALL) e.putBoolean(K_GRAPH_PREFIX + m.key, m.id == id);
        e.putBoolean(K_GRAPHS, true).apply();
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    public String asJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("overlay", overlay()).put("stats", stats()).put("graphs", graphs()).put("csv", csv())
             .put("headlocked", headLocked()).put("scale", scale()).put("distance", distance())
             .put("pitch", pitch()).put("yaw", yaw()).put("interval_ms", intervalMs()).put("hud_mode", hudMode());
            JSONArray st = new JSONArray(), gr = new JSONArray();
            for (Metric m : Metric.ALL) {
                if (statEnabled(m.id)) st.put(m.key);
                if (graphEnabled(m.id)) gr.put(m.key);
            }
            o.put("enabled_stats", st).put("enabled_graphs", gr);
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }
}
