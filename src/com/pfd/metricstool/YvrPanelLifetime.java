package com.pfd.metricstool;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import java.lang.reflect.Method;

/** Remove only our own HUD package from YVR's historical panel package set.
 * RomeoXR otherwise force-stops all packages ever opened in the closed panel.
 * Uses the firmware's typed Binder proxy; no guessed transaction numbers.
 */
final class YvrPanelLifetime implements ServiceConnection {
    private final Context context;
    private final int displayId;
    private Object service;
    private Class<?> api;
    private boolean bound;
    private boolean detached;
    private String error = "YVR service not connected";

    YvrPanelLifetime(Context context, int displayId) {
        this.context = context.getApplicationContext();
        this.displayId = displayId;
    }

    void connect() {
        try {
            Intent intent = new Intent().setComponent(new ComponentName("com.yvr.framework2d",
                    "com.android.server.display.xros.YvrXRService"));
            bound = context.bindService(intent, this, Context.BIND_AUTO_CREATE);
            if (!bound) error = "YVR service binding rejected";
        } catch (Exception e) { failed(e); }
    }

    @Override public void onServiceConnected(ComponentName name, IBinder binder) {
        try {
            String descriptor = binder.getInterfaceDescriptor();
            if (descriptor == null || !descriptor.startsWith("com.yvr."))
                throw new IllegalStateException("Unexpected YVR service: " + descriptor);
            api = Class.forName(descriptor);
            Class<?> stub = Class.forName(descriptor + "$Stub");
            service = stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
            Log.i("PFDMetrics", "Panel lifetime connected: " + descriptor + " display=" + displayId);
        } catch (Exception e) { failed(e); }
    }

    @Override public void onServiceDisconnected(ComponentName name) {
        service = null;
        error = "YVR service disconnected";
    }

    boolean detachOwnPackage() {
        if (detached) return true;
        if (service == null) return false;
        try {
            int panel = (Integer) api.getMethod("getDisplayPanelId", int.class).invoke(service, displayId);
            if (panel < 0) throw new IllegalStateException("No YVR panel for display " + displayId);
            Method set = api.getMethod("setApp2TcXR", String.class, boolean.class, int.class);
            set.invoke(service, context.getPackageName(), false, panel);
            detached = true;
            Log.i("PFDMetrics", "Detached own HUD package from panel=" + panel + " display=" + displayId);
            return true;
        } catch (Exception e) { failed(e); return false; }
    }

    String error() { return error; }

    private void failed(Exception e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        error = cause.toString();
        Log.w("PFDMetrics", "Panel lifetime failed: " + error);
    }

    void close() {
        if (bound) { context.unbindService(this); bound = false; }
        service = null;
    }
}
