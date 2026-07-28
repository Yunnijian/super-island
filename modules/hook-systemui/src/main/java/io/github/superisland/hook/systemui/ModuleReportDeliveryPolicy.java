package io.github.superisland.hook.systemui;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

/** Prevents host-side status reports from reviving a force-stopped module app. */
final class ModuleReportDeliveryPolicy {
    private static final String MODULE_PACKAGE = "io.github.superisland";

    private ModuleReportDeliveryPolicy() {}

    static boolean canDeliver(Context context) {
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(
                    MODULE_PACKAGE,
                    PackageManager.ApplicationInfoFlags.of(0L));
            return canDeliverForFlags(info.flags);
        } catch (PackageManager.NameNotFoundException | RuntimeException error) {
            return false;
        }
    }

    static boolean canDeliverForFlags(int flags) {
        return (flags & ApplicationInfo.FLAG_STOPPED) == 0;
    }
}
