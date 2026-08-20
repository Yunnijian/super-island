package io.github.superisland.hook.systemui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
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
        assertTrue(SystemUiScreenRecordingTileForceStopGuard.isSupportedBuild(
                "songyuan",
                "different-fingerprint",
                202_501_210L));
        assertTrue(SystemUiScreenRecordingTileForceStopGuard.isSupportedBuild(
                "warsaw",
                "Redmi/warsaw/warsaw:16/BP2A.250605.031.A3/OS3.0.306.0.WHPCNXM:user/release-keys",
                1L));
        assertTrue(SystemUiScreenRecordingTileForceStopGuard.isSupportedBuild(
                "warsaw",
                "different-fingerprint",
                202_602_260L));
    }

    @Test
    public void explicitRecoveryRequiresTheExactStoppedEnabledTileAndUserClick() {
        int stopped = ApplicationInfo.FLAG_STOPPED;
        assertTrue(SystemUiScreenRecordingTileForceStopGuard.canRecoverExplicitClick(
                SystemUiScreenRecordingTileForceStopGuard.MODULE_PACKAGE,
                SystemUiScreenRecordingTileForceStopGuard.TILE_SERVICE_CLASS,
                SystemUiScreenRecordingTileForceStopGuard.MODULE_PACKAGE,
                stopped,
                true,
                true,
                true,
                true));
        assertFalse(SystemUiScreenRecordingTileForceStopGuard.canRecoverExplicitClick(
                SystemUiScreenRecordingTileForceStopGuard.MODULE_PACKAGE,
                SystemUiScreenRecordingTileForceStopGuard.TILE_SERVICE_CLASS,
                SystemUiScreenRecordingTileForceStopGuard.MODULE_PACKAGE,
                0,
                true,
                true,
                true,
                true));
        assertFalse(SystemUiScreenRecordingTileForceStopGuard.canRecoverExplicitClick(
                SystemUiScreenRecordingTileForceStopGuard.MODULE_PACKAGE,
                SystemUiScreenRecordingTileForceStopGuard.TILE_SERVICE_CLASS,
                SystemUiScreenRecordingTileForceStopGuard.MODULE_PACKAGE,
                stopped | ApplicationInfo.FLAG_SUSPENDED,
                true,
                true,
                true,
                true));
        assertFalse(SystemUiScreenRecordingTileForceStopGuard.canRecoverExplicitClick(
                SystemUiScreenRecordingTileForceStopGuard.MODULE_PACKAGE,
                SystemUiScreenRecordingTileForceStopGuard.TILE_SERVICE_CLASS,
                SystemUiScreenRecordingTileForceStopGuard.MODULE_PACKAGE,
                stopped,
                false,
                true,
                true,
                true));
        assertFalse(SystemUiScreenRecordingTileForceStopGuard.canRecoverExplicitClick(
                SystemUiScreenRecordingTileForceStopGuard.MODULE_PACKAGE,
                SystemUiScreenRecordingTileForceStopGuard.TILE_SERVICE_CLASS,
                SystemUiScreenRecordingTileForceStopGuard.MODULE_PACKAGE,
                stopped,
                true,
                false,
                true,
                true));
        assertFalse(SystemUiScreenRecordingTileForceStopGuard.canRecoverExplicitClick(
                SystemUiScreenRecordingTileForceStopGuard.MODULE_PACKAGE,
                SystemUiScreenRecordingTileForceStopGuard.TILE_SERVICE_CLASS,
                SystemUiScreenRecordingTileForceStopGuard.MODULE_PACKAGE,
                stopped,
                true,
                true,
                false,
                true));
        assertFalse(SystemUiScreenRecordingTileForceStopGuard.canRecoverExplicitClick(
                SystemUiScreenRecordingTileForceStopGuard.MODULE_PACKAGE,
                SystemUiScreenRecordingTileForceStopGuard.TILE_SERVICE_CLASS,
                SystemUiScreenRecordingTileForceStopGuard.MODULE_PACKAGE,
                stopped,
                true,
                true,
                true,
                false));
    }

    @Test
    public void recoveredReadbackMustBeEnabledUnsuspendedAndNoLongerStopped() {
        assertTrue(SystemUiScreenRecordingTileForceStopGuard.isRecoveredApplication(
                SystemUiScreenRecordingTileForceStopGuard.MODULE_PACKAGE,
                0,
                true,
                true));
        assertFalse(SystemUiScreenRecordingTileForceStopGuard.isRecoveredApplication(
                SystemUiScreenRecordingTileForceStopGuard.MODULE_PACKAGE,
                ApplicationInfo.FLAG_STOPPED,
                true,
                true));
        assertFalse(SystemUiScreenRecordingTileForceStopGuard.isRecoveredApplication(
                SystemUiScreenRecordingTileForceStopGuard.MODULE_PACKAGE,
                ApplicationInfo.FLAG_SUSPENDED,
                true,
                true));
        assertFalse(SystemUiScreenRecordingTileForceStopGuard.isRecoveredApplication(
                SystemUiScreenRecordingTileForceStopGuard.MODULE_PACKAGE,
                0,
                false,
                true));
        assertFalse(SystemUiScreenRecordingTileForceStopGuard.isRecoveredApplication(
                "com.example.other",
                0,
                true,
                true));
        assertFalse(SystemUiScreenRecordingTileForceStopGuard.isRecoveredApplication(
                SystemUiScreenRecordingTileForceStopGuard.MODULE_PACKAGE,
                0,
                true,
                false));
    }

    @Test
    public void explicitClickPermitIsExactAndCanBeConsumedOnlyOnce() {
        SystemUiScreenRecordingTileForceStopGuard.ExplicitClickProvenance provenance =
                new SystemUiScreenRecordingTileForceStopGuard.ExplicitClickProvenance();
        Object lifecycle = new Object();
        Object binder = new Object();
        SystemUiScreenRecordingTileForceStopGuard.ExplicitClickPermit permit =
                provenance.begin(lifecycle, binder);

        assertTrue(provenance.consume(lifecycle, binder));
        assertFalse(provenance.consume(lifecycle, binder));

        provenance.end(permit);
        assertFalse(provenance.consume(lifecycle, binder));
    }

    @Test
    public void mismatchedExplicitClickBurnsPermitFailClosed() {
        SystemUiScreenRecordingTileForceStopGuard.ExplicitClickProvenance provenance =
                new SystemUiScreenRecordingTileForceStopGuard.ExplicitClickProvenance();
        Object lifecycle = new Object();
        Object binder = new Object();
        provenance.begin(lifecycle, binder);

        assertFalse(provenance.consume(new Object(), binder));
        assertFalse(provenance.consume(lifecycle, binder));

        provenance.begin(lifecycle, binder);
        assertFalse(provenance.consume(lifecycle, new Object()));
        assertFalse(provenance.consume(lifecycle, binder));
    }

    @Test
    public void endingExplicitClickPermitBlocksLaterConnectionReplay() {
        SystemUiScreenRecordingTileForceStopGuard.ExplicitClickProvenance provenance =
                new SystemUiScreenRecordingTileForceStopGuard.ExplicitClickProvenance();
        Object lifecycle = new Object();
        Object binder = new Object();
        SystemUiScreenRecordingTileForceStopGuard.ExplicitClickPermit permit =
                provenance.begin(lifecycle, binder);

        provenance.end(permit);

        assertFalse(provenance.consume(lifecycle, binder));
    }

    @Test
    public void acceptedBindWithoutConnectionCannotCompleteRecovery() {
        SystemUiScreenRecordingTileForceStopGuard.RecoveryAttemptRegistry registry =
                new SystemUiScreenRecordingTileForceStopGuard.RecoveryAttemptRegistry();
        Object lifecycle = new Object();
        Object binder = new Object();
        SystemUiScreenRecordingTileForceStopGuard.RecoveryAttempt attempt = registry.begin(
                lifecycle,
                binder,
                () -> {},
                () -> {});

        assertNotNull(attempt);
        assertSame(attempt, registry.take(lifecycle, attempt));
    }

    @Test
    public void exactConnectionMustConsumeTheCurrentClickBeforeSuccess() {
        SystemUiScreenRecordingTileForceStopGuard.RecoveryAttemptRegistry registry =
                new SystemUiScreenRecordingTileForceStopGuard.RecoveryAttemptRegistry();
        Object lifecycle = new Object();
        Object binder = new Object();
        registry.begin(lifecycle, binder, () -> {}, () -> {});
        SystemUiScreenRecordingTileForceStopGuard.RecoveryConnection connection =
                registry.beginConnection(lifecycle, true);

        registry.noteClickDispatch(lifecycle, binder, false);
        SystemUiScreenRecordingTileForceStopGuard.RecoveryAttemptResult result =
                registry.finishConnection(connection, true, false);

        assertNotNull(result);
        assertTrue(result.successful);
    }

    @Test
    public void connectionWithoutClickConsumptionFailsClosed() {
        SystemUiScreenRecordingTileForceStopGuard.RecoveryAttemptRegistry registry =
                new SystemUiScreenRecordingTileForceStopGuard.RecoveryAttemptRegistry();
        Object lifecycle = new Object();
        Object binder = new Object();
        registry.begin(lifecycle, binder, () -> {}, () -> {});
        SystemUiScreenRecordingTileForceStopGuard.RecoveryConnection connection =
                registry.beginConnection(lifecycle, true);

        SystemUiScreenRecordingTileForceStopGuard.RecoveryAttemptResult result =
                registry.finishConnection(connection, true, true);

        assertNotNull(result);
        assertFalse(result.successful);
    }

    @Test
    public void wrongComponentConnectionCannotCompleteTheRecovery() {
        SystemUiScreenRecordingTileForceStopGuard.RecoveryAttemptRegistry registry =
                new SystemUiScreenRecordingTileForceStopGuard.RecoveryAttemptRegistry();
        Object lifecycle = new Object();
        Object binder = new Object();
        registry.begin(lifecycle, binder, () -> {}, () -> {});
        SystemUiScreenRecordingTileForceStopGuard.RecoveryConnection connection =
                registry.beginConnection(lifecycle, false);

        registry.noteClickDispatch(lifecycle, binder, false);
        SystemUiScreenRecordingTileForceStopGuard.RecoveryAttemptResult result =
                registry.finishConnection(connection, true, false);

        assertNotNull(result);
        assertFalse(result.successful);
    }

    @Test
    public void connectionCallbackBeforeTheAttemptCannotAuthorizeAFutureClick() {
        SystemUiScreenRecordingTileForceStopGuard.RecoveryAttemptRegistry registry =
                new SystemUiScreenRecordingTileForceStopGuard.RecoveryAttemptRegistry();
        Object lifecycle = new Object();
        Object binder = new Object();
        SystemUiScreenRecordingTileForceStopGuard.RecoveryConnection oldConnection =
                registry.beginConnection(lifecycle, true);

        SystemUiScreenRecordingTileForceStopGuard.RecoveryAttempt attempt = registry.begin(
                lifecycle,
                binder,
                () -> {},
                () -> {});

        assertNull(oldConnection);
        assertNull(registry.finishConnection(oldConnection, true, false));
        assertSame(attempt, registry.take(lifecycle, attempt));
    }

    @Test
    public void staleTimeoutCannotRemoveANewerRecoveryGeneration() {
        SystemUiScreenRecordingTileForceStopGuard.RecoveryAttemptRegistry registry =
                new SystemUiScreenRecordingTileForceStopGuard.RecoveryAttemptRegistry();
        Object lifecycle = new Object();
        Object binder = new Object();
        SystemUiScreenRecordingTileForceStopGuard.RecoveryAttempt first = registry.begin(
                lifecycle,
                binder,
                () -> {},
                () -> {});
        assertSame(first, registry.take(lifecycle, first));
        SystemUiScreenRecordingTileForceStopGuard.RecoveryAttempt second = registry.begin(
                lifecycle,
                binder,
                () -> {},
                () -> {});

        assertNull(registry.take(lifecycle, first));
        assertSame(second, registry.take(lifecycle, second));
    }

    @Test
    public void lateConnectionFromAnOldGenerationCannotCompleteTheNewClick() {
        SystemUiScreenRecordingTileForceStopGuard.RecoveryAttemptRegistry registry =
                new SystemUiScreenRecordingTileForceStopGuard.RecoveryAttemptRegistry();
        Object lifecycle = new Object();
        Object binder = new Object();
        SystemUiScreenRecordingTileForceStopGuard.RecoveryAttempt first = registry.begin(
                lifecycle,
                binder,
                () -> {},
                () -> {});
        SystemUiScreenRecordingTileForceStopGuard.RecoveryConnection oldConnection =
                registry.beginConnection(lifecycle, true);
        assertSame(first, registry.take(lifecycle, first));
        SystemUiScreenRecordingTileForceStopGuard.RecoveryAttempt second = registry.begin(
                lifecycle,
                binder,
                () -> {},
                () -> {});

        assertNull(registry.finishConnection(oldConnection, true, false));
        assertSame(second, registry.take(lifecycle, second));
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
