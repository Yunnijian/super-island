package io.github.superisland.hook.systemui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ModuleNotificationBoundaryTest {
    private static final String MODULE = "io.github.superisland";
    private static final String SYSTEM_UI = "com.android.systemui";

    @Test
    public void acceptsOnlyCompleteModuleOwnedBoundary() {
        assertTrue(matches(MODULE, MODULE, MODULE, 10_123, 0, 10_123, true));
        assertFalse(matches("attacker", MODULE, MODULE, 10_123, 0, 10_123, true));
        assertFalse(matches(MODULE, "attacker", MODULE, 10_123, 0, 10_123, true));
        assertFalse(matches(MODULE, MODULE, "attacker", 10_123, 0, 10_123, true));
        assertFalse(matches(MODULE, MODULE, MODULE, 10_123, 0, 10_124, true));
        assertFalse(matches(MODULE, MODULE, MODULE, 110_123, 0, 110_123, true));
        assertFalse(matches(MODULE, MODULE, MODULE, 110_123, 1, 110_123, false));
    }

    @Test
    public void acceptsOnlyTheFixedSystemUiResidentHostIdentity() {
        assertTrue(matchesResident(SYSTEM_UI, MODULE, SYSTEM_UI, 10_224, 0, 10_224, 0x535249, null,
                "focus_notification", true));
        assertFalse(matchesResident(SYSTEM_UI, MODULE, SYSTEM_UI, 10_224, 0, 10_224, 0x535250, null,
                "focus_notification", true));
        assertFalse(matchesResident(SYSTEM_UI, MODULE, SYSTEM_UI, 10_224, 0, 10_224, 0x535249, "tag",
                "focus_notification", true));
        assertFalse(matchesResident(SYSTEM_UI, MODULE, SYSTEM_UI, 10_224, 0, 10_224, 0x535249, null,
                "other", true));
        assertFalse(matchesResident(SYSTEM_UI, MODULE, MODULE, 10_224, 0, 10_224, 0x535249, null,
                "focus_notification", true));
        assertFalse(matchesResident(SYSTEM_UI, MODULE, SYSTEM_UI, 10_224, 0, 10_225, 0x535249, null,
                "focus_notification", true));
        assertFalse(matchesResident(SYSTEM_UI, MODULE, SYSTEM_UI, 110_224, 0, 110_224, 0x535249, null,
                "focus_notification", true));
    }

    @Test
    public void acceptsOnlyTheFixedSystemUiLyricIdentity() {
        assertTrue(matchesLyric(SYSTEM_UI, MODULE, SYSTEM_UI, 10_224, 0, 10_224,
                0x4C5952, null, "focus_lyric_island", "super_island_lyric", true));
        assertFalse(
                "The legacy boundary overload must not authorize lyric identity",
                ModuleNotificationBoundary.matches(
                        SYSTEM_UI, MODULE, SYSTEM_UI, "lyric-key", "focus_lyric_island", 0x4C5952,
                        null, 10_224, 0, -1, 10_224, 1L, true));
        assertFalse(matchesLyric(SYSTEM_UI, MODULE, SYSTEM_UI, 10_224, 0, 10_224,
                0x4C5953, null, "focus_lyric_island", "super_island_lyric", true));
        assertFalse(matchesLyric(SYSTEM_UI, MODULE, SYSTEM_UI, 10_224, 0, 10_224,
                0x4C5952, "tag", "focus_lyric_island", "super_island_lyric", true));
        assertFalse(matchesLyric(SYSTEM_UI, MODULE, SYSTEM_UI, 10_224, 0, 10_224,
                0x4C5952, null, "focus_notification", "super_island_lyric", true));
        assertFalse(matchesLyric(SYSTEM_UI, MODULE, SYSTEM_UI, 10_224, 0, 10_224,
                0x4C5952, null, "focus_lyric_island", "super_island_status", true));
        assertFalse(matchesLyric(SYSTEM_UI, MODULE, SYSTEM_UI, 10_224, 0, 10_224,
                0x4C5952, null, "focus_lyric_island", "super_island_lyric", false));
        assertFalse(matchesLyric(SYSTEM_UI, MODULE, MODULE, 10_224, 0, 10_224,
                0x4C5952, null, "focus_lyric_island", "super_island_lyric", true));
        assertFalse(matchesLyric(SYSTEM_UI, MODULE, SYSTEM_UI, 110_224, 0, 110_224,
                0x4C5952, null, "focus_lyric_island", "super_island_lyric", true));
    }

    @Test
    public void validatesKeyChannelAndPostTime() {
        assertFalse(ModuleNotificationBoundary.matches(
                MODULE, MODULE, MODULE, "", "channel", 1, null,
                10_123, 0, 10_123, -1, 1L, true));
        assertFalse(ModuleNotificationBoundary.matches(
                MODULE, MODULE, MODULE, "key", "", 1, null,
                10_123, 0, 10_123, -1, 1L, true));
        assertFalse(ModuleNotificationBoundary.matches(
                MODULE, MODULE, MODULE, "key", "channel", 1, null,
                10_123, 0, 10_123, -1, 0L, true));
    }

    @Test
    public void extractsOnlyBoundedFocusBusinessMarkers() {
        assertEquals(
                "super_island_lyric",
                SystemUiFocusSupportBridge.focusBusiness(
                        "{\"param_v2\":{\"business\":\"super_island_status\"}}",
                        "{\"business\":\"super_island_lyric\"}"));
        assertEquals(
                "super_island_lyric",
                SystemUiFocusSupportBridge.focusBusiness(
                        "{\"param_v2\":{\"business\":\"super_island_lyric\"}}", null));
        assertNull(SystemUiFocusSupportBridge.focusBusiness("{\"param_v2\":{}}", null));
        assertNull(SystemUiFocusSupportBridge.focusBusiness("x".repeat(16_385), null));
    }

    private static boolean matches(
            String source,
            String target,
            String opPackage,
            int uid,
            int userId,
            int resolvedUid,
            boolean payload) {
        return ModuleNotificationBoundary.matches(
                source,
                target,
                opPackage,
                "key",
                "channel",
                1,
                null,
                uid,
                userId,
                resolvedUid,
                -1,
                1L,
                payload);
    }

    private static boolean matchesResident(
            String source,
            String target,
            String opPackage,
            int uid,
            int userId,
            int resolvedSystemUiUid,
            int notificationId,
            String tag,
            String channel,
            boolean payload) {
        return ModuleNotificationBoundary.matches(
                source,
                target,
                opPackage,
                "resident-key",
                channel,
                notificationId,
                tag,
                uid,
                userId,
                -1,
                resolvedSystemUiUid,
                1L,
                payload);
    }

    private static boolean matchesLyric(
            String source,
            String target,
            String opPackage,
            int uid,
            int userId,
            int resolvedSystemUiUid,
            int notificationId,
            String tag,
            String channel,
            String business,
            boolean payload) {
        return ModuleNotificationBoundary.matches(
                source,
                target,
                opPackage,
                "lyric-key",
                channel,
                notificationId,
                tag,
                uid,
                userId,
                -1,
                resolvedSystemUiUid,
                1L,
                payload,
                business);
    }
}
