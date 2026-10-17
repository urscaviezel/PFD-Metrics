package com.pfd.metricstool;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import java.lang.ref.WeakReference;

/** Android may block a background receiver from opening the installer. Resume from the UI. */
final class UpdateConfirmation {
    private static WeakReference<Activity> visible = new WeakReference<>(null);

    static void resume(Activity activity) {
        visible = new WeakReference<>(activity);
        String pending = activity.getSharedPreferences("updates", 0).getString("pendingConfirmation", null);
        if (pending == null) return;
        try {
            Intent intent = Intent.parseUri(pending, 0);
            activity.startActivity(intent);
            activity.getSharedPreferences("updates", 0).edit().remove("pendingConfirmation").apply();
        } catch (Exception e) {
            activity.getSharedPreferences("updates", 0).edit()
                    .putString("status", I18n.t(activity, "install_failed") + e.getMessage()).apply();
        }
    }

    static void pause(Activity activity) {
        if (visible.get() == activity) visible.clear();
    }

    static void pending(Context context, Intent intent) {
        context.getSharedPreferences("updates", 0).edit()
                .putString("pendingConfirmation", intent.toUri(0)).apply();
        Activity activity = visible.get();
        if (activity != null && !activity.isFinishing()) resume(activity);
    }

    static void clear(Context context) {
        context.getSharedPreferences("updates", 0).edit().remove("pendingConfirmation").apply();
    }
}
