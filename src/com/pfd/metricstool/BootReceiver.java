package com.pfd.metricstool;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * HUD side: after a headset reboot or an (re)installation the monitoring service starts OFF.
 * The user switches it on in the PFD Metrics Tool.
 */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        String a = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            new Settings(ctx).prefs().edit().putBoolean(Settings.K_SERVICE, false).commit();
            MetricsService.shutdown(ctx);
        }
    }
}
