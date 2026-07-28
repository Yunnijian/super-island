package io.github.superisland.hook.systemui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.ApplicationInfo;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public final class SystemUiScreenRecordingTileForceStopGuardTest {
    @Test
    public void blocksOnlyTheStoppedModuleRecordingTile() {
        assertTrue(SystemUiScreenRecordingTileForceStopGuard.shouldBlock(
                SystemUiScreenRecordingTileForceStopGuard.MODULE_PACKAGE,
                SystemUiScreenRecordingTileForceStopGuard.TILE_SERVICE_CLASS,
                ApplicationInfo.FLAG_STOPPED));
        assertFalse(SystemUiScreenRecordingTileForceStopGuard.shouldBlock(
                SystemUiScreenRecordingTileForceStopGuard.MODULE_PACKAGE,
                SystemUiScreenRecordingTileForceStopGuard.TILE_SERVICE_CLASS,
                0));
        assertFalse(SystemUiScreenRecordingTileForceStopGuard.shouldBlock(
                "com.example.other",
                SystemUiScreenRecordingTileForceStopGuard.TILE_SERVICE_CLASS,
                ApplicationInfo.FLAG_STOPPED));
        assertFalse(SystemUiScreenRecordingTileForceStopGuard.shouldBlock(
                SystemUiScreenRecordingTileForceStopGuard.MODULE_PACKAGE,
                "io.github.superisland.OtherTileService",
                ApplicationInfo.FLAG_STOPPED));
    }

    @Test
    public void acceptsOnlyTheAuditedWarsawSystemUiBuild() {
        assertTrue(SystemUiScreenRecordingTileForceStopGuard.isSupportedBuild(
                "warsaw",
                "Redmi/warsaw/warsaw:16/BP2A.250605.031.A3/OS3.0.306.0.WHPCNXM:user/release-keys",
                202_501_210L));
        assertFalse(SystemUiScreenRecordingTileForceStopGuard.isSupportedBuild(
                "warsaw",
                "different-fingerprint",
                202_501_210L));
        assertFalse(SystemUiScreenRecordingTileForceStopGuard.isSupportedBuild(
                "warsaw",
                "Redmi/warsaw/warsaw:16/BP2A.250605.031.A3/OS3.0.306.0.WHPCNXM:user/release-keys",
                1L));
    }

    @Test
    public void discardingQueuedClickPreservesOtherLifecycleMessages() {
        Set<Integer> queuedMessages = new HashSet<>();
        queuedMessages.add(0);
        queuedMessages.add(1);
        queuedMessages.add(2);
        queuedMessages.add(3);
        queuedMessages.add(4);

        SystemUiScreenRecordingTileForceStopGuard.discardQueuedClickMessage(queuedMessages);

        assertFalse(queuedMessages.contains(2));
        assertTrue(queuedMessages.contains(0));
        assertTrue(queuedMessages.contains(1));
        assertTrue(queuedMessages.contains(3));
        assertTrue(queuedMessages.contains(4));
    }
}
