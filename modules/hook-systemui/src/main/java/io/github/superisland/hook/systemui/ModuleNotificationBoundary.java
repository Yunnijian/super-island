package io.github.superisland.hook.systemui;

/** Full-SBN boundary for app-owned Focus and the fixed SystemUI resident host identity. */
final class ModuleNotificationBoundary {
    private static final String MODULE_PACKAGE = "io.github.superisland";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String RESIDENT_CHANNEL = "focus_notification";
    private static final int RESIDENT_NOTIFICATION_ID = 0x535249;
    private static final int ANDROID_UIDS_PER_USER = 100_000;

    private ModuleNotificationBoundary() {}

    static boolean matches(
            String sourcePackage,
            String targetPackage,
            String opPackage,
            String key,
            String channelId,
            int notificationId,
            String notificationTag,
            int uid,
            int userId,
            int resolvedModuleUid,
            int resolvedSystemUiUid,
            long postTime,
            boolean hasFocusPayload) {
        if (!nonBlank(key)
                || !nonBlank(channelId)
                || uid <= 0
                || userId < 0
                || uid / ANDROID_UIDS_PER_USER != userId
                || postTime <= 0L
                || !hasFocusPayload) {
            return false;
        }
        boolean appOwned = MODULE_PACKAGE.equals(sourcePackage)
                && MODULE_PACKAGE.equals(targetPackage)
                && MODULE_PACKAGE.equals(opPackage)
                && uid == resolvedModuleUid;
        boolean residentHosted = SYSTEM_UI_PACKAGE.equals(sourcePackage)
                && MODULE_PACKAGE.equals(targetPackage)
                && SYSTEM_UI_PACKAGE.equals(opPackage)
                && uid == resolvedSystemUiUid
                && notificationId == RESIDENT_NOTIFICATION_ID
                && notificationTag == null
                && RESIDENT_CHANNEL.equals(channelId);
        return appOwned || residentHosted;
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
