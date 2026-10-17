package com.pfd.metricstool;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Both packages commit atomically; Android must obtain the user's approval. */
public final class UpdateInstaller {
    public static void verify(Context ctx, File apk, String pkg, String version, long code) throws Exception {
        PackageManager pm = ctx.getPackageManager();
        PackageInfo candidate = pm.getPackageArchiveInfo(apk.getPath(), PackageManager.GET_SIGNING_CERTIFICATES);
        PackageInfo current = pm.getPackageInfo(ctx.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
        if (candidate == null || !pkg.equals(candidate.packageName) || !version.equals(candidate.versionName)
                || candidate.getLongVersionCode() != code || candidate.signingInfo == null)
            throw new Exception("APK package/version mismatch: " + pkg);
        Set<Signature> expected = new HashSet<>(Arrays.asList(current.signingInfo.getApkContentsSigners()));
        Set<Signature> actual = new HashSet<>(Arrays.asList(candidate.signingInfo.getApkContentsSigners()));
        if (!expected.equals(actual)) throw new Exception("APK signing certificate mismatch: " + pkg);
    }

    public static void install(Context ctx, File ui, File hud) throws Exception {
        PackageInstaller installer = ctx.getPackageManager().getPackageInstaller();
        int parentId = -1;
        java.util.List<Integer> children = new java.util.ArrayList<>();
        boolean committed = false;
        try {
            PackageInstaller.SessionParams params = params();
            params.setMultiPackage();
            parentId = installer.createSession(params);
            try (PackageInstaller.Session parent = installer.openSession(parentId)) {
                for (File apk : new File[]{hud, ui}) {
                    int childId = installer.createSession(params());
                    children.add(childId);
                    try (PackageInstaller.Session child = installer.openSession(childId);
                         FileInputStream in = new FileInputStream(apk);
                         OutputStream out = child.openWrite("base.apk", 0, apk.length())) {
                        byte[] buffer = new byte[65536];
                        int n;
                        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                        child.fsync(out);
                    }
                    parent.addChildSessionId(childId);
                }
                Intent callback = new Intent(ctx, InstallResultReceiver.class).putExtra("app_update", true);
                PendingIntent sender = PendingIntent.getBroadcast(ctx, parentId, callback,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                parent.commit(sender.getIntentSender());
                committed = true;
            }
        } finally {
            if (!committed) {
                for (int id : children) try { installer.abandonSession(id); } catch (Exception ignored) { }
                if (parentId >= 0) try { installer.abandonSession(parentId); } catch (Exception ignored) { }
            }
        }
    }

    private static PackageInstaller.SessionParams params() {
        PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        if (android.os.Build.VERSION.SDK_INT >= 31)
            p.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
        return p;
    }
}
