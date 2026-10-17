package com.pfd.metricstool;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

/** Launcher entry "PFD HUD an/aus" (UI package): toggles the HUD in the separate HUD service. */
public final class HudToggleActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Bundle r = RemoteHud.call(this, "get_settings", null, null);
        boolean on = r == null || !r.getBoolean(Settings.K_OVERLAY, false);
        RemoteHud.set(this, Settings.K_SERVICE, true);
        RemoteHud.set(this, Settings.K_OVERLAY, on);
        new Settings(this).prefs().edit().putBoolean(Settings.K_OVERLAY, on).apply();
        Toast.makeText(this, I18n.t(this, r == null ? "not_installed" : (on ? "toggle_on" : "toggle_off")), Toast.LENGTH_SHORT).show();
        finishAndRemoveTask();
    }
}
