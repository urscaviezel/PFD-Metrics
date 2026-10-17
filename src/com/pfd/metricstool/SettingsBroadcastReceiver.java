package com.pfd.metricstool;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * adb control, API-compatible with OVR Metrics Tool. Examples:
 *   adb shell am broadcast -n com.pfd.metricstool/.SettingsBroadcastReceiver -a com.pfd.metricstool.ENABLE_OVERLAY --ef scale 1.2 --ef yaw 20
 *   adb shell am broadcast -n com.pfd.metricstool/.SettingsBroadcastReceiver -a com.pfd.metricstool.ENABLE_STAT --es stat gpu_utilization_percentage
 *   adb shell am broadcast -n com.pfd.metricstool/.SettingsBroadcastReceiver -a com.pfd.metricstool.ENABLE_CSV
 * The com.oculus.ovrmonitormetricsservice.* action names are accepted as well.
 */
public final class SettingsBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "PFDMetrics";

    @Override public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        String cmd = action.substring(action.lastIndexOf('.') + 1);
        Settings s = new Settings(ctx);
        int stat = statFrom(intent);
        Log.i(TAG, "broadcast " + cmd + (stat >= 0 ? " stat=" + Metric.ALL[stat].key : ""));
        switch (cmd) {
            case "ENABLE_OVERLAY":
                if (intent.hasExtra("headlocked")) s.setHeadLocked(intent.getBooleanExtra("headlocked", s.headLocked()));
                if (intent.hasExtra("scale")) s.setScale(floatExtra(intent, "scale", s.scale()));
                if (intent.hasExtra("distance")) s.setDistance(floatExtra(intent, "distance", s.distance()));
                if (intent.hasExtra("pitch")) s.setPitch(floatExtra(intent, "pitch", s.pitch()));
                if (intent.hasExtra("yaw")) s.setYaw(floatExtra(intent, "yaw", s.yaw()));
                if (intent.hasExtra("mode")) s.setHudMode(intent.getStringExtra("mode"));
                s.setOverlay(true);
                ensureService(ctx, s);
                break;
            case "DISABLE_OVERLAY": s.setOverlay(false); break;
            case "ENABLE_GRAPH": case "ENABLE_GRAPHS":
                s.setGraphs(true);
                if (stat >= 0) s.setGraphEnabled(stat, true);
                break;
            case "DISABLE_GRAPH": case "DISABLE_GRAPHS":
                if (stat >= 0) s.setGraphEnabled(stat, false); else s.setGraphs(false);
                break;
            case "SET_GRAPH_STAT": if (stat >= 0) s.setOnlyGraph(stat); break;
            case "ENABLE_STATS": s.setStats(true); break;
            case "DISABLE_STATS": s.setStats(false); break;
            case "ENABLE_STAT": if (stat >= 0) s.setStatEnabled(stat, true); break;
            case "DISABLE_STAT": if (stat >= 0) s.setStatEnabled(stat, false); break;
            case "ENABLE_CSV": s.setCsv(true); ensureService(ctx, s); break;
            case "DISABLE_CSV": s.setCsv(false); break;
            case "START_SERVICE": s.setServiceEnabled(true); MetricsService.start(ctx); break;
            case "STOP_SERVICE": s.setServiceEnabled(false); MetricsService.stop(ctx); break;
            case "ENABLE_SYSTEM_HUD": case "DISABLE_SYSTEM_HUD": {
                String err = SystemHud.set(ctx, cmd.startsWith("ENABLE"), SystemHud.maskFor(s));
                if (err != null) Log.w(TAG, "System HUD needs adb: " + err);
                break;
            }
            case "DUMP_SYSTEM": Diagnostics.dump(ctx); break;
            case "RESET_STATS": MetricStore.get().resetStats(); break;
            case "LOG_STATE": {
                long tag = intent.getLongExtra("tag", System.currentTimeMillis());
                String json = s.asJson();
                String t = TAG + ".Info." + tag;
                Log.i(t, "Start::");
                for (int i = 0; i < json.length(); i += 2048) Log.i(t, json.substring(i, Math.min(json.length(), i + 2048)));
                Log.i(t, "End::");
                break;
            }
            default: Log.w(TAG, "unknown action " + action);
        }
    }

    private static void ensureService(Context ctx, Settings s) {
        s.setServiceEnabled(true);
        try { MetricsService.start(ctx); } catch (Exception e) { Log.w(TAG, "cannot start service from broadcast: " + e); }
    }

    private static int statFrom(Intent intent) {
        String name = intent.getDataString();
        if (intent.hasExtra("stat")) {
            Object o = intent.getExtras().get("stat");
            if (o != null) name = String.valueOf(o);
        }
        return Metric.find(name);
    }

    private static float floatExtra(Intent i, String key, float def) {
        Object o = i.getExtras() != null ? i.getExtras().get(key) : null;
        if (o instanceof Number) return ((Number) o).floatValue();
        if (o instanceof String) try { return Float.parseFloat((String) o); } catch (NumberFormatException ignored) { }
        return def;
    }
}
