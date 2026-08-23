package io.github.superisland.hook.systemui.lyric;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;
import io.github.superisland.model.FocusNotificationRequest;
import io.github.superisland.publisher.focus.FocusNotificationPublisher;
import io.github.superisland.publisher.focus.LyricIslandContract;
import io.github.superisland.source.lyric.LyricIslandConfig;
import io.github.superisland.source.lyric.LyricIslandConfigCodec;
import io.github.superisland.source.lyric.LyriconBridge;
import io.github.superisland.source.lyric.LyricLine;
import io.github.superisland.source.lyric.LyricPlaybackClock;
import io.github.superisland.source.lyric.LyricPlaybackResolver;
import io.github.superisland.source.lyric.LyricProgress;
import io.github.superisland.source.lyric.LyricPayloadBuilder;
import io.github.superisland.source.lyric.LyricResolver;
import io.github.superisland.source.lyric.LyricSnapshot;
import io.github.superisland.source.lyric.LyricSourceMode;
import io.github.superisland.source.lyric.LyricSlot;
import io.github.superisland.source.lyric.SuperLyricBridge;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SystemUI-owned lyric island host.
 *
 * The host mirrors HyperLyric's RootLyricSink lifecycle while using the audited Xiaomi Focus
 * transport already used by Super Island. Rich Binder state is kept in SystemUI, positions are
 * interpolated from PlaybackState, and updates are throttled before posting a new SBN.
 */
public final class LyricIslandSystemUiHost {
    private static final String TAG = "SuperIslandLyricHost";
    private static final String CHANNEL_NAME = "超级岛歌词";
    private static final String MODULE_PACKAGE = "io.github.superisland";
    private static final long PUBLISH_INTERVAL_MS = 120L;
    private static final long POSITION_TICK_MS = 50L;
    private static final long FALLBACK_TICK_MS = 250L;

    private static boolean registered;
    private static Context appContext;
    private static SharedPreferences remotePreferences;
    private static volatile boolean enabled;
    private static volatile LyricIslandConfig config = new LyricIslandConfig();
    private static volatile LyricSnapshot latestSnapshot;
    private static volatile long lastPublishElapsed;
    private static volatile long lastPublishedPosition = Long.MIN_VALUE;
    private static volatile int lastPublishedContentHash;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final LyricPlaybackClock CLOCK = new LyricPlaybackClock();
    private static final ExecutorService FALLBACK_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SuperIslandLyricFallback");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean FALLBACK_QUERY_RUNNING = new AtomicBoolean();

    private static final Function1<LyricSnapshot, Unit> SNAPSHOT_LISTENER = snapshot -> {
        onSnapshot(snapshot, LyricSourceMode.SUPER_LYRIC);
        return Unit.INSTANCE;
    };
    private static final Function1<LyricSnapshot, Unit> LYRICON_SNAPSHOT_LISTENER = snapshot -> {
        onSnapshot(snapshot, LyricSourceMode.LYRICON);
        return Unit.INSTANCE;
    };

    private static final Runnable POSITION_TICK = new Runnable() {
        @Override
        public void run() {
            if (!enabled || (config.getSourceMode() != LyricSourceMode.SUPER_LYRIC
                    && config.getSourceMode() != LyricSourceMode.LYRICON)) return;
            LyricSnapshot snapshot = latestSnapshot;
            if (snapshot != null && !snapshot.getStopped() && CLOCK.isActive()) {
                publishIfNeeded(true);
                LyricIslandNativeRenderer.updatePositions();
                MAIN_HANDLER.postDelayed(this, POSITION_TICK_MS);
            }
        }
    };

    private static final Runnable FALLBACK_TICK = new Runnable() {
        @Override
        public void run() {
            if (!enabled || config.getSourceMode() == LyricSourceMode.SUPER_LYRIC) return;
            Context context = appContext;
            if (context != null && FALLBACK_QUERY_RUNNING.compareAndSet(false, true)) {
                FALLBACK_EXECUTOR.execute(() -> {
                    LyricSnapshot resolved;
                    try {
                        resolved = LyricResolver.INSTANCE.resolveSnapshot(context, null);
                    } catch (Throwable error) {
                        resolved = null;
                    } finally {
                        FALLBACK_QUERY_RUNNING.set(false);
                    }
                    final LyricSnapshot resolvedSnapshot = resolved;
                    MAIN_HANDLER.post(() -> {
                        if (!enabled || (config.getSourceMode() != LyricSourceMode.LYRIC_INFO)) return;
                        if (resolvedSnapshot == null) {
                            latestSnapshot = null;
                            CLOCK.reset();
                            cancelNotification();
                            return;
                        }
                        latestSnapshot = resolvedSnapshot;
                        CLOCK.update(resolvePlayback(resolvedSnapshot), android.os.SystemClock.elapsedRealtime());
                        publishIfNeeded(false);
                        if (CLOCK.isActive()) {
                            MAIN_HANDLER.removeCallbacks(POSITION_TICK);
                            MAIN_HANDLER.postDelayed(POSITION_TICK, POSITION_TICK_MS);
                        }
                    });
                });
            }
            MAIN_HANDLER.postDelayed(this, FALLBACK_TICK_MS);
        }
    };

    private LyricIslandSystemUiHost() {}

    /** Reads a libxposed RemotePreferences by name; implemented by the Xposed module. */
    public interface RemotePreferenceReader {
        SharedPreferences getRemotePreferences(String name);
    }

    public static synchronized void register(Context context, RemotePreferenceReader reader) {
        Context normalized = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        appContext = normalized;
        if (!registered) {
            registered = true;
            Log.i(TAG, "Lyric island host registered in SystemUI");
        }
        attachRemotePreferences(reader);
        applyPersistedSettings();
    }

    private static void attachRemotePreferences(RemotePreferenceReader reader) {
        if (remotePreferences != null || reader == null) return;
        try {
            SharedPreferences preferences =
                    reader.getRemotePreferences(LyricIslandContract.REMOTE_PREFERENCES);
            preferences.registerOnSharedPreferenceChangeListener((prefs, key) ->
                    MAIN_HANDLER.postDelayed(LyricIslandSystemUiHost::applyPersistedSettings, 80L));
            remotePreferences = preferences;
            Log.i(TAG, "Loaded lyric-island RemotePreferences");
        } catch (Throwable error) {
            Log.e(TAG, "Could not load lyric-island RemotePreferences", error);
        }
    }

    private static void applyPersistedSettings() {
        SharedPreferences preferences = remotePreferences;
        if (preferences == null) return;
        String raw = preferences.getString(LyricIslandContract.KEY_CONFIG_JSON, null);
        LyricIslandConfig nextConfig = LyricIslandConfigCodec.INSTANCE.decode(raw);
        // Keep compatibility with the original one-key switch written by older app builds.
        boolean nextEnabled = preferences.getBoolean(
                LyricIslandContract.KEY_ENABLED, nextConfig.getEnabled());
        nextConfig = nextConfig.withEnabled(nextEnabled);
        boolean wasEnabled = enabled;
        boolean sourceChanged = config.getSourceMode() != nextConfig.getSourceMode();
        boolean configChanged = !nextConfig.equals(config);
        if (!configChanged && nextEnabled == enabled) return;
        config = nextConfig;
        enabled = nextEnabled;
        if (enabled) {
            if (!wasEnabled || sourceChanged) {
                if (wasEnabled) stopListening();
                startSources();
            }
            publishIfNeeded(false);
        } else if (wasEnabled || configChanged) {
            stopListening();
        }
    }

    private static synchronized void startListening() {
        if (appContext == null) return;
        SuperLyricBridge.INSTANCE.removeSnapshotListener(SNAPSHOT_LISTENER);
        SuperLyricBridge.INSTANCE.addSnapshotListener(SNAPSHOT_LISTENER);
        SuperLyricBridge.INSTANCE.start();
        MAIN_HANDLER.removeCallbacks(POSITION_TICK);
        MAIN_HANDLER.post(POSITION_TICK);
        Log.i(TAG, "SuperLyric listener started");
    }

    private static synchronized void startSources() {
        MAIN_HANDLER.removeCallbacks(FALLBACK_TICK);
        LyriconBridge.INSTANCE.removeSnapshotListener(LYRICON_SNAPSHOT_LISTENER);
        LyriconBridge.INSTANCE.stop();
        if (config.getSourceMode() == LyricSourceMode.SUPER_LYRIC) {
            startListening();
        } else if (config.getSourceMode() == LyricSourceMode.LYRICON) {
            LyriconBridge.INSTANCE.addSnapshotListener(LYRICON_SNAPSHOT_LISTENER);
            LyriconBridge.INSTANCE.start(
                    appContext,
                    config.getLyriconProviderDelays(),
                    config.getLyriconProviderDelayMs());
            MAIN_HANDLER.removeCallbacks(POSITION_TICK);
            MAIN_HANDLER.post(POSITION_TICK);
            Log.i(TAG, "Lyricon subscriber started");
        } else {
            MAIN_HANDLER.post(FALLBACK_TICK);
            Log.i(TAG, "MediaSession lyric fallback started");
        }
    }

    private static synchronized void stopListening() {
        MAIN_HANDLER.removeCallbacks(POSITION_TICK);
        MAIN_HANDLER.removeCallbacks(FALLBACK_TICK);
        SuperLyricBridge.INSTANCE.removeSnapshotListener(SNAPSHOT_LISTENER);
        SuperLyricBridge.INSTANCE.stop();
        LyriconBridge.INSTANCE.removeSnapshotListener(LYRICON_SNAPSHOT_LISTENER);
        LyriconBridge.INSTANCE.stop();
        latestSnapshot = null;
        CLOCK.reset();
        lastPublishElapsed = 0L;
        lastPublishedPosition = Long.MIN_VALUE;
        lastPublishedContentHash = 0;
        cancelNotification();
        Log.i(TAG, "SuperLyric listener stopped");
    }

    private static void onSnapshot(LyricSnapshot snapshot, LyricSourceMode sourceMode) {
        if (!enabled || config.getSourceMode() != sourceMode || snapshot == null) return;
        final LyricSnapshot incoming = snapshot;
        MAIN_HANDLER.post(() -> {
            if (!enabled) return;
            LyricSnapshot effective = incoming;
            if (effective.getArtworkColors() == null || effective.getArtworkColors().isEmpty()) {
                try {
                    java.util.List<Integer> artworkColors =
                            LyricPlaybackResolver.INSTANCE.artworkColors(
                                    appContext,
                                    effective.getPublisher());
                    if (artworkColors != null && !artworkColors.isEmpty()) {
                        effective = effective.copy(
                                effective.getPublisher(),
                                effective.getLine(),
                                effective.getSecondary(),
                                effective.getTranslation(),
                                effective.getTitle(),
                                effective.getArtist(),
                                effective.getAlbum(),
                                artworkColors,
                                effective.getPlayback(),
                                effective.getStopped());
                    }
                } catch (Throwable ignored) {
                    // Artwork is optional; lyric delivery must continue without palette data.
                }
            }
            latestSnapshot = effective;
            if (effective.getStopped()) {
                CLOCK.reset();
                cancelNotification();
                LyricIslandNativeRenderer.clearAll();
                return;
            }
            if (effective.getPlayback() != null) {
                CLOCK.update(resolvePlayback(effective), android.os.SystemClock.elapsedRealtime());
            }
            publishIfNeeded(false);
            if (CLOCK.isActive()) {
                MAIN_HANDLER.removeCallbacks(POSITION_TICK);
                MAIN_HANDLER.postDelayed(POSITION_TICK, POSITION_TICK_MS);
            }
        });
    }

    private static void publishIfNeeded(boolean positionTick) {
        LyricSnapshot snapshot = latestSnapshot;
        Context context = appContext;
        if (!enabled || context == null || snapshot == null || snapshot.getStopped()) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (snapshot.getPlayback() != null) {
            CLOCK.update(resolvePlayback(snapshot), now);
        }
        LyricLine line = snapshot.getLine();
        long position = CLOCK.positionAt(now);
        int contentHash = contentHash(snapshot, config);
        boolean contentChanged = contentHash != lastPublishedContentHash;
        if (!contentChanged && position == lastPublishedPosition) return;
        if (positionTick && !contentChanged && now - lastPublishElapsed < PUBLISH_INTERVAL_MS) return;
        if (!positionTick && !contentChanged && now - lastPublishElapsed < 16L) return;
        publish(context, snapshot, line, position, contentHash);
    }

    private static io.github.superisland.source.lyric.LyricPlayback resolvePlayback(
            LyricSnapshot snapshot) {
        Context context = appContext;
        if (context == null || snapshot == null) {
            return snapshot == null || snapshot.getPlayback() == null
                    ? new io.github.superisland.source.lyric.LyricPlayback()
                    : snapshot.getPlayback();
        }
        return LyricPlaybackResolver.INSTANCE.resolve(
                context,
                snapshot.getPublisher(),
                snapshot.getPlayback());
    }

    private static void publish(
            Context context,
            LyricSnapshot snapshot,
            LyricLine line,
            long position,
            int contentHash) {
        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) return;
            LyricIslandConfig currentConfig = config;
            // Resolve the configured slots before deciding whether anything can be published.
            // MUSIC_INFO is a valid standalone layout and must not be rejected by the old
            // lyric-only gate (or by a missing lyric line/placeholder).
            String primary;
            String secondary;
            if (currentConfig.getLyricMode() == 1) {
                primary = LyricPayloadBuilder.INSTANCE.contentFor(
                        snapshot, currentConfig, io.github.superisland.source.lyric.IslandContentMode.LYRIC, true);
                secondary = LyricPayloadBuilder.INSTANCE.contentFor(
                        snapshot, currentConfig, io.github.superisland.source.lyric.IslandContentMode.LYRIC, false);
            } else {
                primary = LyricPayloadBuilder.INSTANCE.contentFor(
                        snapshot, currentConfig, currentConfig.getContentLeft(), true);
                secondary = LyricPayloadBuilder.INSTANCE.contentFor(
                        snapshot, currentConfig, currentConfig.getContentRight(), false);
            }
            if (primary.isBlank() && secondary.isBlank()) {
                cancelNotification();
                LyricIslandNativeRenderer.clearAll();
                return;
            }
            Integer progress = LyricProgress.INSTANCE.lineProgress(line, position);
            boolean showProgress = currentConfig.getShowProgress() && progress != null;
            int progressValue = progress == null ? 0 : progress;
            String leftText = currentConfig.lyricSlot() == LyricSlot.RIGHT ? secondary : primary;
            String rightText = currentConfig.lyricSlot() == LyricSlot.RIGHT ? primary : secondary;
            FocusNotificationPublisher publisher =
                    new FocusNotificationPublisher(context, CHANNEL_NAME);
            boolean nativeAttached = LyricIslandNativeRenderer.hasAttachedSlot();
            if (!nativeAttached) {
                // FocusNotificationRequest intentionally requires both text fields to be
                // non-blank. Keep this compatibility fill local to the fallback request: the
                // native renderer still receives the original slot values, so an explicit NONE
                // slot is never turned into a duplicate lyric on the player's own island.
                String fallbackLeft = leftText;
                String fallbackRight = rightText;
                if (fallbackLeft.isBlank()) fallbackLeft = fallbackRight;
                if (fallbackRight.isBlank()) fallbackRight = fallbackLeft;
                if (fallbackLeft.isBlank() || fallbackRight.isBlank()) {
                    cancelNotification();
                    LyricIslandNativeRenderer.clearAll();
                    return;
                }
                FocusNotificationRequest request = new FocusNotificationRequest(
                        fallbackLeft,
                        fallbackRight,
                        progressValue,
                        !showProgress,
                        fallbackRight,
                        showProgress);
                Notification notification = publisher.buildSystemUiLyricNotification(
                        request,
                        android.R.drawable.ic_media_play,
                        null,
                        null,
                        true,
                        false,
                        null,
                        MODULE_PACKAGE);
                manager.notify(LyricIslandContract.LYRIC_NOTIFICATION_ID, notification);
            } else {
                // The player's own island is the primary surface while a native slot is attached.
                manager.cancel(LyricIslandContract.LYRIC_NOTIFICATION_ID);
            }
            lastPublishElapsed = android.os.SystemClock.elapsedRealtime();
            lastPublishedPosition = position;
            lastPublishedContentHash = contentHash;
            LyricIslandNativeRenderer.refreshAll();
        } catch (Throwable error) {
            Log.e(TAG, "Could not publish lyric island", error);
        }
    }

    /** True only while a module-owned lyric snapshot is eligible for native slot rendering. */
    public static boolean isNativeRendererActive() {
        LyricSnapshot snapshot = latestSnapshot;
        if (!enabled || snapshot == null || snapshot.getStopped() || !config.getEnabled()) return false;
        String left = LyricPayloadBuilder.INSTANCE.contentFor(
                snapshot, config,
                config.getLyricMode() == 1
                        ? io.github.superisland.source.lyric.IslandContentMode.LYRIC
                        : config.getContentLeft(), true);
        String right = LyricPayloadBuilder.INSTANCE.contentFor(
                snapshot, config,
                config.getLyricMode() == 1
                        ? io.github.superisland.source.lyric.IslandContentMode.LYRIC
                        : config.getContentRight(), false);
        return !left.isBlank() || !right.isBlank();
    }

    public static LyricSnapshot nativeSnapshot() {
        return latestSnapshot;
    }

    public static LyricIslandConfig nativeConfig() {
        return config;
    }

    public static long nativePosition() {
        return CLOCK.positionAt(android.os.SystemClock.elapsedRealtime());
    }

    public static float nativePlaybackSpeed() {
        LyricSnapshot snapshot = latestSnapshot;
        return snapshot == null || snapshot.getPlayback() == null
                ? 1f : snapshot.getPlayback().getSpeed();
    }

    public static String nativePrimaryText() {
        LyricSnapshot snapshot = latestSnapshot;
        return snapshot == null ? "" : LyricPayloadBuilder.INSTANCE.contentFor(
                snapshot, config,
                config.getLyricMode() == 1
                        ? io.github.superisland.source.lyric.IslandContentMode.LYRIC
                        : config.getContentLeft(), true);
    }

    public static String nativeSecondaryText() {
        LyricSnapshot snapshot = latestSnapshot;
        return snapshot == null ? "" : LyricPayloadBuilder.INSTANCE.contentFor(
                snapshot, config,
                config.getLyricMode() == 1
                        ? io.github.superisland.source.lyric.IslandContentMode.LYRIC
                        : config.getContentRight(), false);
    }

    public static String nativePublisher() {
        LyricSnapshot snapshot = latestSnapshot;
        return snapshot == null ? "" : snapshot.getPublisher();
    }

    public static java.util.List<Integer> nativeArtworkColors() {
        LyricSnapshot snapshot = latestSnapshot;
        return snapshot == null || snapshot.getArtworkColors() == null
                ? java.util.Collections.emptyList() : snapshot.getArtworkColors();
    }

    public static boolean nativeIsPlaying() {
        LyricSnapshot snapshot = latestSnapshot;
        return snapshot != null && snapshot.getPlayback() != null
                && snapshot.getPlayback().isPlaying();
    }

    public static boolean nativeSlotAttached() {
        return LyricIslandNativeRenderer.hasAttachedSlot();
    }

    /** Cancels only the optional Focus fallback; native island slots remain untouched. */
    public static void suppressFallbackNotification() {
        try {
            Context context = appContext;
            NotificationManager manager = context == null
                    ? null : context.getSystemService(NotificationManager.class);
            if (manager != null) manager.cancel(LyricIslandContract.LYRIC_NOTIFICATION_ID);
        } catch (Throwable error) {
            Log.w(TAG, "Could not suppress lyric fallback notification", error);
        }
    }

    /** Called by the bounded DynamicIslandContentView hook after OEM layout updates. */
    public static void renderNative(ViewGroup root) {
        LyricIslandNativeRenderer.render(root, null);
    }

    /** Called by the DynamicIslandContentView hook with the OEM island data object. */
    public static void renderNative(ViewGroup root, Object islandData) {
        LyricIslandNativeRenderer.render(root, islandData);
    }

    public static void clearNative(ViewGroup root) {
        LyricIslandNativeRenderer.clearRoot(root);
        if (!LyricIslandNativeRenderer.hasAttachedSlot()) {
            // A layout mismatch or player switch may happen without a new Binder line. Restore
            // the explicitly documented Focus fallback only after the native surface is gone.
            MAIN_HANDLER.post(() -> publishIfNeeded(false));
        }
    }

    /** Restores all OEM roots when the Dynamic Island plugin is unloaded. */
    public static void clearNativeAll() {
        LyricIslandNativeRenderer.clearAll();
    }

    public static void refreshNativeAll() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            LyricIslandNativeRenderer.rebindAll();
        } else {
            MAIN_HANDLER.post(LyricIslandNativeRenderer::rebindAll);
        }
    }

    public static void freezeNative(ViewGroup root) {
        LyricIslandNativeRenderer.freeze(root);
    }

    public static void renderAndFreezeNative(ViewGroup root, Object islandData) {
        LyricIslandNativeRenderer.render(root, islandData);
        LyricIslandNativeRenderer.freeze(root);
    }

    private static int contentHash(LyricSnapshot snapshot, LyricIslandConfig currentConfig) {
        int result = 17;
        result = 31 * result + (snapshot.getLine() == null ? 0 : snapshot.getLine().hashCode());
        result = 31 * result + (snapshot.getTranslation() == null ? 0 : snapshot.getTranslation().hashCode());
        result = 31 * result + (snapshot.getSecondary() == null ? 0 : snapshot.getSecondary().hashCode());
        result = 31 * result + java.util.Objects.hash(
                snapshot.getPublisher(), snapshot.getTitle(), snapshot.getArtist(), snapshot.getAlbum());
        result = 31 * result + currentConfig.hashCode();
        return result;
    }

    private static void cancelNotification() {
        try {
            Context context = appContext;
            NotificationManager manager = context == null
                    ? null : context.getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(LyricIslandContract.LYRIC_NOTIFICATION_ID);
            LyricIslandNativeRenderer.clearAll();
        } catch (Throwable error) {
            Log.w(TAG, "Could not cancel lyric island", error);
        }
    }
}
