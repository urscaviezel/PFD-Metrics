package com.pfd.metricstool;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Single-APK setup: the UI APK carries the headless HUD service APK in assets/ and installs it via
 * PackageInstaller (the user confirms once). "Uninstall everything" removes both packages.
 */
public final class HudInstaller {
    public static final String ASSET = "PFDMetricsHUD.apk";
    public static final int MISSING = 0, OUTDATED = 1, CURRENT = 2;

    public static int state(Context ctx) {
        long installed = installedVersion(ctx);
        if (installed < 0) return MISSING;
        long embedded = embeddedVersion(ctx);
        return embedded > installed ? OUTDATED : CURRENT;
    }

    static long installedVersion(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(RemoteHud.HUD_PACKAGE, 0).getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    private static File embeddedFile(Context ctx) throws Exception {
        File f = new File(ctx.getCacheDir(), ASSET);
        try (InputStream in = ctx.getAssets().open(ASSET); OutputStream out = new FileOutputStream(f)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return f;
    }

    static long embeddedVersion(Context ctx) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageArchiveInfo(embeddedFile(ctx).getPath(), 0);
            return pi == null ? -1 : pi.getLongVersionCode();
        } catch (Exception e) {
            return -1;
        }
    }

    /** @return null if the install was started, otherwise a message for the user. */
    public static String install(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        if (!pm.canRequestPackageInstalls()) {
            try {
                ctx.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + ctx.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) { }
            return I18n.t(ctx, "need_unknown_sources");
        }
        try {
            PackageInstaller pi = pm.getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(RemoteHud.HUD_PACKAGE);
            int id = pi.createSession(params);
            try (PackageInstaller.Session session = pi.openSession(id)) {
                try (InputStream in = ctx.getAssets().open(ASSET); OutputStream out = session.openWrite("hud.apk", 0, -1)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    session.fsync(out);
                }
                Intent cb = new Intent(ctx, InstallResultReceiver.class);
                PendingIntent sender = PendingIntent.getBroadcast(ctx, id, cb,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                session.commit(sender.getIntentSender());
            }
            return null;
        } catch (Exception e) {
            return I18n.t(ctx, "install_failed") + e.getMessage();
        }
    }

    public static void uninstallAll(Context ctx) {
        Intent hud = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + RemoteHud.HUD_PACKAGE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Intent self = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + ctx.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (installedVersion(ctx) >= 0) ctx.startActivities(new Intent[]{self, hud}); // HUD dialog first, then the app
        else ctx.startActivity(self);
    }
}
