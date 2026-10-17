package com.pfd.metricstool.test;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import com.pfd.metricstool.UpdateInstaller;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Separate, locally signed instrumentation APK. Never part of the production application. */
public final class DeviceUpdateTest extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context target = getTargetContext();
            // Re-running the harness must not leave earlier unconfirmed test sessions behind.
            for (android.content.pm.PackageInstaller.SessionInfo session : target.getPackageManager().getPackageInstaller().getMySessions()) {
                if (session.isMultiPackage()) target.getPackageManager().getPackageInstaller().abandonSession(session.getSessionId());
            }
            File ui = copy("PFDMetricsTool.apk"), hud = copy("PFDMetricsHUD.apk");
            String version = target.getPackageManager().getPackageArchiveInfo(ui.getPath(), 0).versionName;
            long code = target.getPackageManager().getPackageArchiveInfo(ui.getPath(), 0).getLongVersionCode();
            UpdateInstaller.verify(target, ui, "com.pfd.metricstool", version, code);
            UpdateInstaller.verify(target, hud, "com.pfd.metricstool.hud", version, code);
            boolean rejected = false;
            try { UpdateInstaller.verify(target, ui, "com.pfd.metricstool.hud", version, code); }
            catch (Exception expected) { rejected = true; }
            if (!rejected) throw new AssertionError("Wrong package accepted");
            rejected = false;
            try { UpdateInstaller.verify(target, ui, "com.pfd.metricstool", version, code + 1); }
            catch (Exception expected) { rejected = true; }
            if (!rejected) throw new AssertionError("Wrong version accepted");
            if (!target.getPackageManager().canRequestPackageInstalls()) {
                result.putString("result", "VALIDATION_OK; UNKNOWN_SOURCES_PERMISSION_REQUIRED");
            } else {
                UpdateInstaller.install(target, ui, hud);
                result.putString("result", "VALIDATION_OK; ATOMIC_INSTALL_SUBMITTED; USER_CONFIRMATION_REQUIRED");
                // Keep the instrumentation process alive until its callback can persist confirmation.
                Thread.sleep(1500);
            }
            ui.delete(); hud.delete();
            finish(-1, result);
        } catch (Throwable e) {
            result.putString("error", e.toString()); finish(0, result);
        }
    }
    private File copy(String name) throws Exception {
        File file = new File(getTargetContext().getCacheDir(), "test-" + name);
        try (InputStream in = getContext().getAssets().open(name); FileOutputStream out = new FileOutputStream(file)) {
            byte[] buffer = new byte[65536]; int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        }
        return file;
    }
}
