package com.pfd.metricstool;

import java.net.URI;

/** Pure validation shared by the updater and host-side regression tests. */
public final class UpdatePolicy {
    public static boolean repository(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9-]*/[A-Za-z0-9_.-]+")
                && !value.endsWith("/.") && !value.endsWith("/..");
    }

    public static boolean newer(long ui, long hud, long installedUi, long installedHud) {
        return ui > installedUi && hud >= installedHud && ui <= 2100000000L && hud <= 2100000000L;
    }

    public static boolean assetUrl(String value, String repository) {
        try {
            URI u = new URI(value);
            return repository(repository) && "https".equals(u.getScheme())
                    && "github.com".equals(u.getHost()) && u.getUserInfo() == null
                    && u.getPort() == -1 && u.getQuery() == null && u.getFragment() == null
                    && u.getRawPath().startsWith("/" + repository + "/releases/download/")
                    && !u.getRawPath().contains("..") && !u.getRawPath().contains("%2f");
        } catch (Exception e) { return false; }
    }
}
