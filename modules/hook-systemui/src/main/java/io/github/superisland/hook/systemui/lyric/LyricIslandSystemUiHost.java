package io.github.superisland.hook.systemui.lyric;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import io.github.superisland.model.FocusNotificationRequest;
import io.github.superisland.model.IslandPriority;
import io.github.superisland.publisher.focus.FocusNotificationPublisher;
import io.github.superisland.publisher.focus.LyricIslandContract;
import io.github.superisland.source.lyric.LyricLine;
import io.github.superisland.source.lyric.SuperLyricBridge;

/**
 * SystemUI-owned lyric island host.
 *
 * Mirrors {@link SystemUiResidentIslandHost}'s lifecycle: register on Application.attach, read
 * the module's RemotePreferences, hot-reload on preference change. When enabled it registers the
 * SuperLyric Binder receiver and republishes each pushed line as a Focus notification carrying
 * param_island (OS3 and OS4 share this single transport per commit 223ab0e evidence).
 */
public final class LyricIslandSystemUiHost {
    private static final String TAG = "SuperIslandLyricHost";
    private static final String CHANNEL_NAME = "超级岛歌词";
    private static final String MODULE_PACKAGE = "io.github.superisland";

    private static boolean registered;
    private static Context appContext;
    private static SharedPreferences remotePreferences;
    private static RemotePreferenceReader remotePreferenceReader;
    private static volatile boolean enabled;
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private LyricIslandSystemUiHost() {}

    /** Reads a libxposed RemotePreferences by name; implemented by the Xposed module. */
    public interface RemotePreferenceReader {
        SharedPreferences getRemotePreferences(String name);
    }

    public static synchronized void register(
            Context context, RemotePreferenceReader reader) {
        Context normalized = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        appContext = normalized;
        remotePreferenceReader = reader;
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
                    MAIN_HANDLER.postDelayed(
                            LyricIslandSystemUiHost::applyPersistedSettings, 80L));
            remotePreferences = preferences;
            Log.i(TAG, "Loaded lyric-island RemotePreferences");
        } catch (Throwable error) {
            Log.e(TAG, "Could not load lyric-island RemotePreferences", error);
        }
    }

    private static void applyPersistedSettings() {
        SharedPreferences preferences = remotePreferences;
        if (preferences == null) return;
        boolean nextEnabled = preferences.getBoolean(LyricIslandContract.KEY_ENABLED, false);
        if (nextEnabled == enabled) return;
        enabled = nextEnabled;
        if (enabled) {
            startListening();
        } else {
            stopListening();
        }
    }

    private static void startListening() {
        if (appContext == null) return;
        SuperLyricBridge.INSTANCE.addListener(line -> {
            onLyric(line);
            return kotlin.Unit.INSTANCE;
        });
        SuperLyricBridge.INSTANCE.start();
        Log.i(TAG, "SuperLyric listener started");
    }

    private static void stopListening() {
        SuperLyricBridge.INSTANCE.stop();
        cancelNotification();
        Log.i(TAG, "SuperLyric listener stopped");
    }

    private static void onLyric(io.github.superisland.source.lyric.LyricLine line) {
        if (!enabled || line == null) return;
        Context context = appContext;
        if (context == null) return;
        MAIN_HANDLER.post(() -> publish(context, line));
    }

    private static void publish(Context context, io.github.superisland.source.lyric.LyricLine line) {
        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) return;
            FocusNotificationPublisher publisher =
                    new FocusNotificationPublisher(context, CHANNEL_NAME);
            FocusNotificationRequest request =
                    new FocusNotificationRequest(
                            line.getText(),
                            line.getText(),
                            0,
                            false,
                            line.getText());
            Notification notification =
                    publisher.buildSystemUiResidentNotification(
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
            Log.w(TAG, "Could not publish lyric island", error);
        }
    }

    private static void cancelNotification() {
        try {
            NotificationManager manager =
                    appContext.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.cancel(LyricIslandContract.LYRIC_NOTIFICATION_ID);
            }
        } catch (Throwable error) {
            Log.w(TAG, "Could not cancel lyric island", error);
        }
    }
}
