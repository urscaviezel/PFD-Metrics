package com.pfd.metricstool;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

/** HUD side: when the UI app is uninstalled, stop and ask the user to uninstall the HUD service too. */
public final class UiRemovedReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        Uri data = intent.getData();
        if (data == null || !"com.pfd.metricstool".equals(data.getSchemeSpecificPart())) return;
        Log.i("PFDMetrics", "UI app removed – removing HUD service as well");
        new Settings(ctx).setServiceEnabled(false);
        MetricsService.shutdown(ctx);
        try {
            ctx.startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + ctx.getPackageName()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Log.w("PFDMetrics", "self uninstall request failed: " + e);
        }
    }
}
