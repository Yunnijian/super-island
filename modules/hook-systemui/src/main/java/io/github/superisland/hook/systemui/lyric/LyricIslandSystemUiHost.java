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
import io.github.superisland.source.lyric.SuperLyricBridge;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

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
    // RichLyricLineView advances its visual animation from Choreographer frames. SystemUI only
    // needs a fresh playback anchor here; 100ms matches HyperLyric's progress sampling cadence
    // and leaves the shade gesture more frame budget than a 20Hz Binder/renderer tick.
    private static final long POSITION_TICK_MS = 100L;
    private static final long FALLBACK_TICK_MS = 250L;
    private static final long PLAYBACK_RESOLVE_INTERVAL_MS = 400L;
    private static final long ARTWORK_RESOLVE_INTERVAL_MS = 1_000L;

    private static boolean registered;
    private static Context appContext;
    private static SharedPreferences remotePreferences;
    private static volatile boolean enabled;
    private static volatile LyricIslandConfig config = new LyricIslandConfig();
    private static volatile LyricSnapshot latestSnapshot;
    private static volatile LyricSnapshot nativeActiveSnapshot;
    private static volatile LyricIslandConfig nativeActiveConfig;
    private static volatile boolean nativeActiveEnabled;
    private static volatile boolean nativeActiveResult;
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
    private static final ExecutorService MEDIA_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SuperIslandLyricMedia");
        thread.setDaemon(true);
        return thread;
    });
    private static final ExecutorService NOTIFICATION_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SuperIslandLyricNotification");
        thread.setDaemon(true);
        return thread;
    });
    private static final Object NOTIFICATION_QUEUE_LOCK = new Object();
    private static Runnable pendingNotificationOperation;
    private static boolean notificationWorkerRunning;
    /**
     * Tracks whether the optional Focus fallback may still be present. Native island callbacks
     * can arrive once per display frame; scheduling a Binder cancel for every callback made
     * notification-shade expansion compete with NotificationManager even after the fallback was
     * already gone.
     */
    private static boolean fallbackNotificationMayExist = true;
    private static final AtomicBoolean PLAYBACK_QUERY_RUNNING = new AtomicBoolean();
    private static final AtomicBoolean ARTWORK_QUERY_RUNNING = new AtomicBoolean();
    private static volatile String playbackCacheKey = "";
    private static volatile io.github.superisland.source.lyric.LyricPlayback playbackCache;
    private static volatile long playbackCacheElapsed;
    /** Last provider anchor applied to CLOCK; prevents placeholder Binder snapshots resetting it. */
    private static io.github.superisland.source.lyric.LyricPlayback lastAppliedProviderPlayback;
    private static volatile String artworkCacheKey = "";
    private static volatile java.util.List<Integer> artworkCache = Collections.emptyList();
    private static volatile long artworkCacheElapsed;
    private static final AtomicBoolean NATIVE_REFRESH_PENDING = new AtomicBoolean();
    private static final Map<ViewGroup, Boolean> NATIVE_FREEZE_RENDER_PENDING = new WeakHashMap<>();
    private static final Runnable NATIVE_REFRESH = () -> {
        NATIVE_REFRESH_PENDING.set(false);
        try {
            LyricIslandNativeRenderer.refreshAll();
        } catch (Throwable error) {
            Log.w(TAG, "Could not refresh native lyric slots", error);
        }
    };

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
            // LyricInfo is resolved by the fallback poller, but it still needs the same
            // position clock for progress, word highlighting and next-line transitions. The
            // previous gate stopped after the first fallback snapshot, leaving a frozen line.
            if (!enabled || (config.getSourceMode() != LyricSourceMode.SUPER_LYRIC
                    && config.getSourceMode() != LyricSourceMode.LYRICON
                    && config.getSourceMode() != LyricSourceMode.LYRIC_INFO
                    && config.getSourceMode() != LyricSourceMode.MEDIA_FALLBACK)) return;
            LyricSnapshot snapshot = latestSnapshot;
            if (snapshot != null && !snapshot.getStopped() && CLOCK.isActive()) {
                // Once the player's native island owns the lyric slot, publishing a Focus SBN on
                // every clock tick only cancels the same fallback notification and traverses the
                // OEM tree again. Content changes still arrive through onSnapshot; ticks only
                // advance the in-place rich renderer and keep shade scrolling responsive.
                boolean nativeAttached = LyricIslandNativeRenderer.hasAttachedSlot();
                if (!nativeAttached && config.getSourceMode() == LyricSourceMode.MEDIA_FALLBACK) {
                    publishIfNeeded(true);
                }
                if (nativeAttached) LyricIslandNativeRenderer.updatePositions();
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
                        if (!enabled || (config.getSourceMode() != LyricSourceMode.LYRIC_INFO
                                && config.getSourceMode() != LyricSourceMode.MEDIA_FALLBACK)) return;
                        if (resolvedSnapshot == null) {
                            latestSnapshot = null;
                            CLOCK.reset();
                            cancelNotification();
                            return;
                        }
                        latestSnapshot = resolvedSnapshot;
                        // LyricResolver already read the MediaSession on FALLBACK_EXECUTOR. Do
                        // not query the same controller again on the SystemUI main looper.
                        CLOCK.update(resolvedSnapshot.getPlayback(), android.os.SystemClock.elapsedRealtime());
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
        resetMediaCaches();
        cancelNotification();
        Log.i(TAG, "SuperLyric listener stopped");
    }

    private static void resetMediaCaches() {
        playbackCacheKey = "";
        playbackCache = null;
        playbackCacheElapsed = 0L;
        lastAppliedProviderPlayback = null;
        artworkCacheKey = "";
        artworkCache = Collections.emptyList();
        artworkCacheElapsed = 0L;
    }

    private static void onSnapshot(LyricSnapshot snapshot, LyricSourceMode sourceMode) {
        if (!enabled || config.getSourceMode() != sourceMode || snapshot == null) return;
        final LyricSnapshot incoming = snapshot;
        MAIN_HANDLER.post(() -> {
            // A source switch can happen while this callback is queued on the main looper. Do
            // not let a stale provider snapshot overwrite the newly selected source state.
            if (!enabled || config.getSourceMode() != sourceMode) return;
            LyricSnapshot effective = incoming;
            if (effective.getArtworkColors() == null || effective.getArtworkColors().isEmpty()) {
                // Use a previously resolved palette immediately, then refresh it off the main
                // looper when this track has not been seen before.
                java.util.List<Integer> cachedArtwork = cachedArtworkFor(mediaKey(effective));
                if (cachedArtwork != null && !cachedArtwork.isEmpty()) {
                    effective = effective.copy(
                            effective.getPublisher(),
                            effective.getLine(),
                            effective.getSecondary(),
                            effective.getTranslation(),
                            effective.getTitle(),
                            effective.getArtist(),
                            effective.getAlbum(),
                            cachedArtwork,
                            effective.getPlayback(),
                            effective.getStopped());
                }
            }
            latestSnapshot = effective;
            if (effective.getStopped()) {
                CLOCK.reset();
                cancelNotification();
                LyricIslandNativeRenderer.clearAll();
                return;
            }
            scheduleArtworkResolve(effective, sourceMode);
            applySnapshotPlayback(effective, sourceMode, false);
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
            if (positionTick) {
                // The clock already interpolates between source/resolver anchors. Re-applying a
                // cached MediaSession position on every 50 ms tick would reset that anchor and
                // make lyric progress appear frozen until the next Binder refresh.
                resolvePlaybackForSource(snapshot);
            } else {
                applySnapshotPlayback(snapshot, config.getSourceMode(), false);
            }
        }
        LyricLine line = snapshot.getLine();
        long position = CLOCK.positionAt(now);
        int contentHash = contentHash(snapshot, config);
        boolean contentChanged = contentHash != lastPublishedContentHash;
        if (!contentChanged && position == lastPublishedPosition) return;
        // A bound native player slot receives position updates directly from POSITION_TICK. Do
        // not rebuild/cancel the fallback notification every 120ms while the shade animation is
        // running; that Binder work is only needed when content/config changes.
        if (positionTick && !contentChanged && LyricIslandNativeRenderer.hasAttachedSlot()) return;
        if (positionTick && !contentChanged && now - lastPublishElapsed < PUBLISH_INTERVAL_MS) return;
        if (!positionTick && !contentChanged && now - lastPublishElapsed < 16L) return;
        publish(context, snapshot, line, position, contentHash);
    }

    /**
     * Returns the latest cached MediaSession state and schedules a refresh when it is stale.
     * MediaSessionManager.getActiveSessions() is a Binder call and must never run from the
     * high-frequency SystemUI animation tick
     * SystemUI animation tick.
     */
    private static io.github.superisland.source.lyric.LyricPlayback resolvePlaybackForSource(
            LyricSnapshot snapshot) {
        if (snapshot == null || snapshot.getPlayback() == null) {
            return new io.github.superisland.source.lyric.LyricPlayback();
        }
        if (config.getSourceMode() == LyricSourceMode.LYRICON
                || config.getSourceMode() == LyricSourceMode.LYRIC_INFO
                || config.getSourceMode() == LyricSourceMode.MEDIA_FALLBACK) {
            return snapshot.getPlayback();
        }
        schedulePlaybackResolve(snapshot);
        // Keep the provider's latest anchor for this frame. A cached MediaSession result may be
        // older than a seek/pause event just delivered by SuperLyric; the async resolver callback
        // will replace this anchor when its Binder read completes.
        return snapshot.getPlayback();
    }

    /** Applies only authoritative/new provider anchors; high-frequency placeholders must not
     * rewind the interpolated clock between asynchronous MediaSession reads. */
    private static void applySnapshotPlayback(
            LyricSnapshot snapshot,
            LyricSourceMode sourceMode,
            boolean force) {
        if (snapshot == null || snapshot.getPlayback() == null) return;
        io.github.superisland.source.lyric.LyricPlayback playback = snapshot.getPlayback();
        if (sourceMode == LyricSourceMode.SUPER_LYRIC) {
            schedulePlaybackResolve(snapshot);
            if (!force && !hasPlaybackSignal(playback)) return;
            if (!force && playback.equals(lastAppliedProviderPlayback)) return;
            lastAppliedProviderPlayback = playback;
        }
        CLOCK.update(playback, android.os.SystemClock.elapsedRealtime());
    }

    private static boolean hasPlaybackSignal(
            io.github.superisland.source.lyric.LyricPlayback playback) {
        return playback != null && (playback.isPlaying()
                || playback.getPositionMs() > 0L
                || playback.getDurationMs() > 0L
                || playback.getSpeed() != 1f);
    }

    private static String mediaKey(LyricSnapshot snapshot) {
        if (snapshot == null) return "";
        return String.valueOf(snapshot.getPublisher()) + '\u0000'
                + String.valueOf(snapshot.getTitle()) + '\u0000'
                + String.valueOf(snapshot.getArtist()) + '\u0000'
                + String.valueOf(snapshot.getAlbum());
    }

    private static java.util.List<Integer> cachedArtworkFor(String key) {
        java.util.List<Integer> cached = artworkCache;
        return key.equals(artworkCacheKey) ? cached : Collections.emptyList();
    }

    private static void schedulePlaybackResolve(LyricSnapshot snapshot) {
        Context context = appContext;
        if (context == null || snapshot == null || snapshot.getPublisher() == null
                || snapshot.getPublisher().isBlank()) return;
        final String key = mediaKey(snapshot);
        final long now = android.os.SystemClock.elapsedRealtime();
        if (key.equals(playbackCacheKey)
                && now - playbackCacheElapsed < PLAYBACK_RESOLVE_INTERVAL_MS) return;
        if (!PLAYBACK_QUERY_RUNNING.compareAndSet(false, true)) return;
        final String publisher = snapshot.getPublisher();
        final io.github.superisland.source.lyric.LyricPlayback fallback = snapshot.getPlayback();
        MEDIA_EXECUTOR.execute(() -> {
            io.github.superisland.source.lyric.LyricPlayback resolved;
            io.github.superisland.source.lyric.LyricPlaybackResolution mediaState;
            try {
                mediaState = LyricPlaybackResolver.INSTANCE.resolveWithMetadata(
                        context, publisher, fallback);
                resolved = mediaState.getPlayback();
            } catch (Throwable error) {
                resolved = fallback;
                mediaState = new io.github.superisland.source.lyric.LyricPlaybackResolution(
                        fallback, null, null, null);
            }
            final io.github.superisland.source.lyric.LyricPlaybackResolution resolvedMediaState = mediaState;
            final io.github.superisland.source.lyric.LyricPlayback result = resolved;
            MAIN_HANDLER.post(() -> {
                PLAYBACK_QUERY_RUNNING.set(false);
                final LyricSnapshot current = latestSnapshot;
                if (!enabled || config.getSourceMode() != LyricSourceMode.SUPER_LYRIC
                        || current == null || current.getStopped() || !key.equals(mediaKey(current))) {
                    if (enabled && current != null && !current.getStopped()
                            && config.getSourceMode() == LyricSourceMode.SUPER_LYRIC
                            && !key.equals(mediaKey(current))) {
                        schedulePlaybackResolve(current);
                    }
                    return;
                }
                playbackCacheKey = key;
                playbackCache = result;
                playbackCacheElapsed = android.os.SystemClock.elapsedRealtime();
                String title = current.getTitle();
                String artist = current.getArtist();
                String album = current.getAlbum();
                // SuperLyric publishers do not always include title/artist/album in the Binder
                // callback. HyperLyric reads the same public MediaSession metadata for its info
                // row, so fill only missing fields and preserve an explicit provider value.
                if (isBlank(title)) title = resolvedMediaState.getTitle();
                if (isBlank(artist)) artist = resolvedMediaState.getArtist();
                if (isBlank(album)) album = resolvedMediaState.getAlbum();
                latestSnapshot = current.copy(
                        current.getPublisher(),
                        current.getLine(),
                        current.getSecondary(),
                        current.getTranslation(),
                        title,
                        artist,
                        album,
                        current.getArtworkColors(),
                        result,
                        current.getStopped());
                CLOCK.update(result, android.os.SystemClock.elapsedRealtime());
                lastAppliedProviderPlayback = result;
                publishIfNeeded(false);
                if (CLOCK.isActive()) {
                    MAIN_HANDLER.removeCallbacks(POSITION_TICK);
                    MAIN_HANDLER.postDelayed(POSITION_TICK, POSITION_TICK_MS);
                }
            });
        });
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static void scheduleArtworkResolve(
            LyricSnapshot snapshot,
            LyricSourceMode sourceMode) {
        if (snapshot == null || (snapshot.getArtworkColors() != null
                && !snapshot.getArtworkColors().isEmpty())) return;
        Context context = appContext;
        if (context == null || snapshot.getPublisher() == null || snapshot.getPublisher().isBlank()) return;
        final String key = mediaKey(snapshot);
        final long now = android.os.SystemClock.elapsedRealtime();
        if (key.equals(artworkCacheKey)
                && now - artworkCacheElapsed < ARTWORK_RESOLVE_INTERVAL_MS) return;
        if (!ARTWORK_QUERY_RUNNING.compareAndSet(false, true)) return;
        final String publisher = snapshot.getPublisher();
        MEDIA_EXECUTOR.execute(() -> {
            java.util.List<Integer> resolved;
            try {
                resolved = LyricPlaybackResolver.INSTANCE.artworkColors(context, publisher);
            } catch (Throwable error) {
                resolved = Collections.emptyList();
            }
            if (resolved == null) resolved = Collections.emptyList();
            final java.util.List<Integer> result = Collections.unmodifiableList(
                    new java.util.ArrayList<>(resolved));
            MAIN_HANDLER.post(() -> {
                ARTWORK_QUERY_RUNNING.set(false);
                artworkCacheKey = key;
                artworkCache = result;
                artworkCacheElapsed = android.os.SystemClock.elapsedRealtime();
                final LyricSnapshot current = latestSnapshot;
                if (!enabled || current == null || current.getStopped()) return;
                if (config.getSourceMode() != sourceMode || !key.equals(mediaKey(current))) {
                    if (current.getArtworkColors() == null || current.getArtworkColors().isEmpty()) {
                        scheduleArtworkResolve(current, config.getSourceMode());
                    }
                    return;
                }
                if (!result.isEmpty() && (current.getArtworkColors() == null
                        || current.getArtworkColors().isEmpty())) {
                    latestSnapshot = current.copy(
                            current.getPublisher(),
                            current.getLine(),
                            current.getSecondary(),
                            current.getTranslation(),
                            current.getTitle(),
                            current.getArtist(),
                            current.getAlbum(),
                            result,
                            current.getPlayback(),
                            current.getStopped());
                    publishIfNeeded(false);
                }
            });
        });
    }

    private static void publish(
            Context context,
            LyricSnapshot snapshot,
            LyricLine line,
            long position,
            int contentHash) {
        try {
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
            boolean nativeAttached = LyricIslandNativeRenderer.hasAttachedSlot();
            boolean fallbackMode = currentConfig.getSourceMode() == LyricSourceMode.MEDIA_FALLBACK;
            if (fallbackMode) {
                // Keep the fallback's two lines identical to the resolved native slot contract.
                // A configured MUSIC_INFO slot must never be filled with lyric text.
                String fallbackLeft = primary;
                String fallbackRight = secondary;
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
                // FocusNotificationPublisher.ensureChannel() and notify() both cross the
                // NotificationManager Binder. Keep the whole build/post operation off the
                // SystemUI main looper so shade expansion never waits for notification service.
                markFallbackNotificationMayExist();
                enqueueNotificationOperation(() -> {
                    try {
                        NotificationManager manager = context.getSystemService(NotificationManager.class);
                        if (manager == null) return;
                        FocusNotificationPublisher publisher =
                                new FocusNotificationPublisher(context, CHANNEL_NAME);
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
                    } catch (Throwable error) {
                        Log.e(TAG, "Could not publish lyric island", error);
                    }
                });
            } else if (nativeAttached) {
                // HyperLyric sources are rendered in the player's own island. The Focus payload
                // is not a default fallback when the native resource binding is absent.
                enqueueNotificationCancel(context);
            } else {
                cancelNotification();
                LyricIslandNativeRenderer.clearAll();
                return;
            }
            lastPublishElapsed = android.os.SystemClock.elapsedRealtime();
            lastPublishedPosition = position;
            lastPublishedContentHash = contentHash;
            // Word-level providers can emit several snapshots within one frame. Match
            // HyperLyric's debounced content coordinator so each frame performs at most one
            // native-tree reconciliation while the latest snapshot remains authoritative.
            if (nativeAttached) scheduleNativeRefresh();
        } catch (Throwable error) {
            Log.e(TAG, "Could not publish lyric island", error);
        }
    }

    private static void scheduleNativeRefresh() {
        if (!NATIVE_REFRESH_PENDING.compareAndSet(false, true)) return;
        MAIN_HANDLER.postDelayed(NATIVE_REFRESH, 16L);
    }

    /** True only while a module-owned lyric snapshot is eligible for native slot rendering. */
    public static boolean isNativeRendererActive() {
        LyricSnapshot snapshot = latestSnapshot;
        LyricIslandConfig currentConfig = config;
        if (snapshot == nativeActiveSnapshot && currentConfig == nativeActiveConfig
                && nativeActiveEnabled == enabled) {
            return nativeActiveResult;
        }
        boolean active = enabled && currentConfig.getSourceMode() != LyricSourceMode.MEDIA_FALLBACK
                && snapshot != null && !snapshot.getStopped()
                && currentConfig.getEnabled();
        if (active) {
            String left;
            String right;
            boolean duplicateLyricSlots = currentConfig.getLyricMode() == 0
                    && currentConfig.getContentLeft() == io.github.superisland.source.lyric.IslandContentMode.LYRIC
                    && currentConfig.getContentRight() == io.github.superisland.source.lyric.IslandContentMode.LYRIC;
            if (currentConfig.getLyricMode() == 1) {
                left = LyricPayloadBuilder.INSTANCE.contentFor(
                        snapshot, currentConfig,
                        io.github.superisland.source.lyric.IslandContentMode.LYRIC, true);
                right = LyricPayloadBuilder.INSTANCE.contentFor(
                        snapshot, currentConfig,
                        io.github.superisland.source.lyric.IslandContentMode.LYRIC, false);
            } else {
                left = duplicateLyricSlots && currentConfig.getSlot() == io.github.superisland.source.lyric.LyricSlot.RIGHT
                        ? "" : LyricPayloadBuilder.INSTANCE.contentFor(snapshot, currentConfig, currentConfig.getContentLeft(), true);
                right = duplicateLyricSlots && currentConfig.getSlot() != io.github.superisland.source.lyric.LyricSlot.RIGHT
                        ? "" : LyricPayloadBuilder.INSTANCE.contentFor(snapshot, currentConfig, currentConfig.getContentRight(), false);
            }
            active = !left.isBlank() || !right.isBlank();
        }
        nativeActiveSnapshot = snapshot;
        nativeActiveConfig = currentConfig;
        nativeActiveEnabled = enabled;
        nativeActiveResult = active;
        return active;
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
        enqueueNotificationCancel(appContext);
    }

    /**
     * Keeps at most one notification operation queued. Position ticks can outpace the OEM
     * notification Binder; retaining stale payloads would otherwise create an unbounded queue
     * while the user is dragging the shade.
     */
    private static void enqueueNotificationOperation(Runnable operation) {
        if (operation == null) return;
        synchronized (NOTIFICATION_QUEUE_LOCK) {
            pendingNotificationOperation = operation;
            if (notificationWorkerRunning) return;
            notificationWorkerRunning = true;
        }
        try {
            NOTIFICATION_EXECUTOR.execute(() -> {
                while (true) {
                    Runnable next;
                    synchronized (NOTIFICATION_QUEUE_LOCK) {
                        next = pendingNotificationOperation;
                        pendingNotificationOperation = null;
                        if (next == null) {
                            notificationWorkerRunning = false;
                            return;
                        }
                    }
                    try {
                        next.run();
                    } catch (Throwable error) {
                        Log.w(TAG, "Notification operation failed", error);
                    }
                }
            });
        } catch (Throwable error) {
            synchronized (NOTIFICATION_QUEUE_LOCK) {
                notificationWorkerRunning = false;
            }
            Log.w(TAG, "Could not schedule notification operation", error);
        }
    }

    private static void enqueueNotificationCancel(Context context) {
        if (context == null) return;
        if (!takeFallbackNotificationMayExist()) return;
        enqueueNotificationOperation(() -> {
            try {
                NotificationManager manager = context.getSystemService(NotificationManager.class);
                if (manager != null) manager.cancel(LyricIslandContract.LYRIC_NOTIFICATION_ID);
            } catch (Throwable error) {
                Log.w(TAG, "Could not cancel lyric fallback notification", error);
            }
        });
    }

    private static void markFallbackNotificationMayExist() {
        synchronized (NOTIFICATION_QUEUE_LOCK) {
            fallbackNotificationMayExist = true;
        }
    }

    /** Returns true once for each fallback publish, so redundant native-frame cancels are free. */
    private static boolean takeFallbackNotificationMayExist() {
        synchronized (NOTIFICATION_QUEUE_LOCK) {
            if (!fallbackNotificationMayExist) return false;
            fallbackNotificationMayExist = false;
            return true;
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
        boolean cleared = LyricIslandNativeRenderer.clearRoot(root);
        if (cleared && !LyricIslandNativeRenderer.hasAttachedSlot()) {
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

    /** Coalesces fake-transition callbacks before touching the SystemUI view tree. */
    public static void scheduleRenderAndFreezeNative(ViewGroup root, Object islandData) {
        if (root == null) return;
        synchronized (NATIVE_FREEZE_RENDER_PENDING) {
            if (NATIVE_FREEZE_RENDER_PENDING.put(root, Boolean.TRUE) != null) return;
        }
        MAIN_HANDLER.post(() -> {
            synchronized (NATIVE_FREEZE_RENDER_PENDING) {
                NATIVE_FREEZE_RENDER_PENDING.remove(root);
            }
            try {
                renderAndFreezeNative(root, islandData);
            } catch (Throwable error) {
                clearNative(root);
                Log.w(TAG, "Could not render frozen native lyric island", error);
            }
        });
    }

    private static int contentHash(LyricSnapshot snapshot, LyricIslandConfig currentConfig) {
        int result = 17;
        result = 31 * result + (snapshot.getLine() == null ? 0 : snapshot.getLine().hashCode());
        result = 31 * result + (snapshot.getTranslation() == null ? 0 : snapshot.getTranslation().hashCode());
        result = 31 * result + (snapshot.getSecondary() == null ? 0 : snapshot.getSecondary().hashCode());
        result = 31 * result + (snapshot.getArtworkColors() == null
                ? 0 : snapshot.getArtworkColors().hashCode());
        result = 31 * result + java.util.Objects.hash(
                snapshot.getPublisher(), snapshot.getTitle(), snapshot.getArtist(), snapshot.getAlbum());
        result = 31 * result + currentConfig.hashCode();
        return result;
    }

    private static void cancelNotification() {
        enqueueNotificationCancel(appContext);
        LyricIslandNativeRenderer.clearAll();
    }
}
