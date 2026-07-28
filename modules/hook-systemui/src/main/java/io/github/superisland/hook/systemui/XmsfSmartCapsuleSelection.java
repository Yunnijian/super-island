package io.github.superisland.hook.systemui;

import android.content.SharedPreferences;
import android.os.Process;
import io.github.superisland.model.SmartCapsuleRemoteSlot;
import io.github.superisland.model.SmartCapsuleRemoteSnapshot;
import io.github.superisland.model.SmartCapsuleRemoteSnapshotSelector;
import io.github.superisland.model.SystemUiSmartCapsuleContract;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.Objects;

/** Immutable RemotePreferences selection used by the XMSF Focus error boundary. */
final class XmsfSmartCapsuleSelection {
    private static final String CONFIG_PREFIX = "config.";
    private static final int ANDROID_UIDS_PER_USER = 100_000;
    private static final ThreadPoolExecutor RELOAD_EXECUTOR = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1),
            runnable -> {
                Thread thread = new Thread(runnable, "SuperIslandXmsfConfig");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.DiscardOldestPolicy());

    private final SharedPreferences preferences;
    private final int processUserId;
    private volatile SmartCapsuleRemoteSnapshot accepted;
    private volatile Consumer<SmartCapsuleRemoteSnapshot> acceptanceReporter = ignored -> {};

    private final SharedPreferences.OnSharedPreferenceChangeListener listener =
            (ignored, key) -> {
                if (key == null || key.startsWith(CONFIG_PREFIX)) reloadAsync();
            };

    XmsfSmartCapsuleSelection(SharedPreferences preferences) {
        this.preferences = preferences;
        this.processUserId = Process.myUid() / ANDROID_UIDS_PER_USER;
        preferences.registerOnSharedPreferenceChangeListener(listener);
        reload();
    }

    boolean matches(XmsfFocusAuthContract.FocusRequest request) {
        SmartCapsuleRemoteSnapshot snapshot = accepted;
        long publishedRevision = preferences.getLong(
                SystemUiSmartCapsuleContract.KEY_ACTIVE_REVISION,
                -1L);
        if (snapshot == null || publishedRevision > snapshot.getRevision()) {
            reloadAsync();
            return false;
        }
        return request != null
                && snapshot != null
                && snapshot.matchesPackage(request.packageName(), request.userId());
    }

    SmartCapsuleRemoteSnapshot current() {
        return accepted;
    }

    void close() {
        acceptanceReporter = ignored -> {};
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
    }

    void setAcceptanceReporter(Consumer<SmartCapsuleRemoteSnapshot> reporter) {
        acceptanceReporter = Objects.requireNonNull(reporter);
    }

    void reloadAsync() {
        RELOAD_EXECUTOR.execute(() -> {
            SmartCapsuleRemoteSnapshot snapshot = reload();
            if (snapshot != null) {
                try {
                    acceptanceReporter.accept(snapshot);
                } catch (RuntimeException ignored) {
                    // Reporting is diagnostic IPC; an unavailable App must not terminate XMSF.
                }
            }
        });
    }

    synchronized SmartCapsuleRemoteSnapshot reload() {
        SmartCapsuleRemoteSnapshot previous = accepted;
        long minimumRevision = previous != null ? previous.getRevision() : 0L;
        SmartCapsuleRemoteSlot selected = SmartCapsuleRemoteSnapshotSelector.selectPublishedOrNull(
                preferences.getInt(SystemUiSmartCapsuleContract.KEY_ACTIVE_SLOT, -1),
                preferences.getLong(SystemUiSmartCapsuleContract.KEY_ACTIVE_REVISION, -1L),
                preferences.getString(
                        SystemUiSmartCapsuleContract.slotSnapshotJsonKey(
                                SystemUiSmartCapsuleContract.SLOT_A),
                        null),
                preferences.getString(
                        SystemUiSmartCapsuleContract.slotSnapshotJsonKey(
                                SystemUiSmartCapsuleContract.SLOT_B),
                        null),
                processUserId,
                minimumRevision);
        if (selected == null) return accepted;
        SmartCapsuleRemoteSnapshot candidate = selected.getSnapshot();
        if (previous != null
                && candidate.getRevision() == previous.getRevision()
                && !candidate.getDigest().equals(previous.getDigest())) {
            return accepted;
        }
        accepted = candidate;
        return accepted;
    }
}
