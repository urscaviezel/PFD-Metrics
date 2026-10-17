package com.pfd.metricstool;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

/** UI-side client of the headless HUD package's HudProvider. */
public final class RemoteHud {
    private static final String TAG = "PFDMetrics";
    private static final Uri URI = Uri.parse("content://" + HudProvider.AUTHORITY);
    public static final String HUD_PACKAGE = "com.pfd.metricstool.hud";

    public static boolean installed(Context ctx) {
        return ctx.getPackageManager().resolveContentProvider(HudProvider.AUTHORITY, 0) != null;
    }

    public static Bundle call(Context ctx, String method, String arg, Bundle extras) {
        try {
            return ctx.getContentResolver().call(URI, method, arg, extras);
        } catch (Throwable e) {
            Log.w(TAG, "HUD call " + method + " failed: " + e);
            return null;
        }
    }

    public static void set(Context ctx, String key, Object value) {
        Bundle b = new Bundle();
        if (value instanceof Boolean) b.putBoolean("value", (Boolean) value);
        else if (value instanceof Float) b.putFloat("value", (Float) value);
        else if (value instanceof Integer) b.putInt("value", (Integer) value);
        else if (value instanceof Long) b.putLong("value", (Long) value);
        else if (value instanceof String) b.putString("value", (String) value);
        call(ctx, "set", key, b);
    }
}
