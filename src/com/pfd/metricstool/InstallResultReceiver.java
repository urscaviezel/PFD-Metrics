package com.pfd.metricstool;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

/** Receives PackageInstaller results for the embedded HUD service. */
public final class InstallResultReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                if (intent.getBooleanExtra("app_update", false)) {
                    UpdateConfirmation.pending(ctx, confirm);
                    return;
                }
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(confirm);
            }
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            UpdateConfirmation.clear(ctx);
            boolean update = intent.getBooleanExtra("app_update", false);
            String result = update ? "App + HUD: " + I18n.t(ctx, "install_ok") : I18n.t(ctx, "install_ok");
            if (update) ctx.getSharedPreferences("updates", 0).edit().putString("status", result).apply();
            Toast.makeText(ctx, result, Toast.LENGTH_LONG).show();
            if (!update) RemoteHud.call(ctx, "start_service", null, null);
            Intent ui = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
            if (ui != null) ctx.startActivity(ui.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        } else {
            UpdateConfirmation.clear(ctx);
            String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            if (intent.getBooleanExtra("app_update", false))
                ctx.getSharedPreferences("updates", 0).edit().putString("status", I18n.t(ctx, "install_failed") + msg).apply();
            Toast.makeText(ctx, I18n.t(ctx, "install_failed") + msg, Toast.LENGTH_LONG).show();
        }
    }
}
