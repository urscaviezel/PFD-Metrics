package com.pfd.metricstool;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Developer diagnostics: copies readable YVR system components into the app's external dir
 * (for offline reverse engineering of the VR overlay path) and writes a reflection report.
 * Trigger: adb shell am broadcast -n com.pfd.metricstool/.SettingsBroadcastReceiver -a com.pfd.metricstool.DUMP_SYSTEM
 */
public final class Diagnostics {
    private static final String TAG = "PFDMetrics";
    private static final String[] FILES = {
        "/system/framework/framework.jar",
        "/system/framework/framework-res.apk",
        "/system/framework/services.jar",
        "/system/app/VrSystemUI/VrSystemUI.apk",
        "/system/priv-app/RomeoXR/RomeoXR.apk",
        "/system/app/GuardianService/GuardianService.apk",
        "/system/app/YvrNotification/YvrNotification.apk",
        "/system/app/DialogService/DialogService.apk",
        "/system/app/VrCore/VrCore.apk",
    };
    private static final String[] CLASSES = {
        "com.yvr.internal.IYvrWindowManager",
        "com.yvr.internal.IYvrWindowManager$Stub",
        "android.os.IYvrVrVirtualDisplayManager",
        "android.os.IYvrRuntimeManager",
        "android.os.IYvrStrategyManager",
        "android.os.IYvrDeviceStateManager",
        "android.view.YvrExtViewRootImpl",
        "android.view.WindowManager$LayoutParams",
        "android.app.YvrWindowManager",
        "android.view.YvrWindowManager",
    };

    public static void dump(final Context ctx) {
        new Thread(() -> {
            File base = new File(ctx.getExternalFilesDir(null), "dump");
            base.mkdirs();
            StringBuilder rep = new StringBuilder();
            for (String path : FILES) {
                File src = new File(path);
                File dst = new File(base, src.getName());
                try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dst)) {
                    byte[] buf = new byte[1 << 16];
                    int n; long total = 0;
                    while ((n = in.read(buf)) > 0) { out.write(buf, 0, n); total += n; }
                    rep.append("COPIED ").append(path).append(" ").append(total).append('\n');
                } catch (Exception e) {
                    dst.delete();
                    rep.append("DENIED ").append(path).append(" ").append(e.getMessage()).append('\n');
                }
            }
            rep.append('\n');
            for (String cn : CLASSES) {
                try {
                    Class<?> c = Class.forName(cn);
                    rep.append("CLASS ").append(cn).append('\n');
                    for (Method m : c.getDeclaredMethods()) rep.append("  M ").append(m.toGenericString()).append('\n');
                    for (Field f : c.getDeclaredFields()) {
                        rep.append("  F ").append(f.toGenericString());
                        if (Modifier.isStatic(f.getModifiers()) && (f.getType() == int.class || f.getType() == String.class)) {
                            try { f.setAccessible(true); rep.append(" = ").append(f.get(null)); } catch (Throwable ignored) { }
                        }
                        rep.append('\n');
                    }
                } catch (Throwable e) {
                    rep.append("NOCLASS ").append(cn).append(" ").append(e).append('\n');
                }
            }
            try (FileWriter w = new FileWriter(new File(base, "report.txt"))) { w.write(rep.toString()); } catch (Exception ignored) { }
            Log.i(TAG, "system dump written to " + base);
        }, "pfd-dump").start();
    }
}
