package io.github.superisland.hook.systemui;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Icon;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import io.github.libxposed.api.XposedModule;
import io.github.superisland.model.FocusNotificationRequest;
import io.github.superisland.model.ResidentExpandedAction;
import io.github.superisland.model.ResidentExpandedActionCodec;
import io.github.superisland.model.ResidentExpandedContentTemplate;
import io.github.superisland.model.ResidentExpandedTemplateRenderResult;
import io.github.superisland.model.ResidentExpandedTemplateValidation;
import io.github.superisland.model.ResidentMetricFormatter;
import io.github.superisland.publisher.focus.FocusCustomRemoteViews;
import io.github.superisland.publisher.focus.FocusNotificationPublisher;
import io.github.superisland.publisher.focus.SystemUiResidentIslandContract;
import java.lang.reflect.Method;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SystemUI-owned implementation of the resident Super Island.
 *
 * <p>This is intentionally a data owner, not merely a notification relay. It keeps a private
 * periodic battery reader and a hidden ongoing focus notification in SystemUI, so clearing or
 * force-stopping the module app leaves a live, updating island instead of a last-frame snapshot.
 * The app only writes user settings to libxposed RemotePreferences and exposes their UI.
 */
final class SystemUiResidentIslandHost {
    private static final String TAG = "SuperIslandResidentHost";
    private static final String MODULE_PACKAGE = "io.github.superisland";
    private static final String SETTINGS_PACKAGE = "com.android.settings";
    private static final String ACTION_REFRESH_NOW =
            "io.github.superisland.action.SYSTEMUI_RESIDENT_REFRESH_NOW";
    private static final String CHANNEL_NAME = "常驻超级岛";
    private static final int MAX_TEXT_LENGTH = 256;
    private static final int PENDING_INTENT_REQUEST_CODE_PREFIX = 0x53000000;
    private static final int PENDING_INTENT_REQUEST_CODE_MASK = 0x00ffffff;
    private static final int PENDING_INTENT_FLAGS =
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
    private static final long ACTIVITY_RESOLVE_FLAGS =
            PackageManager.MATCH_DIRECT_BOOT_AWARE | PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
    private static final long MIN_INTERVAL_MILLIS = 1_000L;
    private static final long MAX_INTERVAL_MILLIS = 60_000L;
    private static final long DEFAULT_INTERVAL_MILLIS = 10_000L;
    private static final long MIN_FAN_READ_INTERVAL_MILLIS = 1_000L;
    private static final long SETTINGS_APPLY_DEBOUNCE_MILLIS = 100L;

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final ExecutorService FAN_READER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SuperIslandFanReader");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    private static boolean registered;
    private static boolean batteryReceiverRegistered;
    private static boolean pollingEnabled;
    private static Context appContext;
    private static Context moduleResourceContext;
    private static SharedPreferences remotePreferences;
    private static Object miChargeInstance;
    private static Method miChargeReadMethod;
    private static volatile Boolean miuiFanSupported;
    private static boolean miuiFanReadFailureLogged;
    private static volatile Integer latestFanRpm;
    private static boolean fanReadInFlight;
    private static long lastFanReadStartedAt;
    private static String lastAppliedSettingsFingerprint;
    private static boolean customFocusRemoteViewsUnavailable;
    private static boolean customFocusRemoteViewsFailureLogged;

    private SystemUiResidentIslandHost() {}

    static synchronized void register(Context context, XposedModule module) {
        Context normalized = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        appContext = normalized;
        if (!registered) {
            registerHostReceiver(normalized);
            registered = true;
            Log.i(TAG, "Resident island host registered in SystemUI");
        }
        attachRemotePreferences(module);
        // SystemUI process restarts after a hard pkill can leave HyperOS Dynamic Island holding a
        // stale Focus surface for the same key. Drop any residual host notification before the
        // first settings apply so enabled configs re-post a clean updatable island.
        cancelHostNotification(normalized);
        lastAppliedSettingsFingerprint = null;
        // Application.attach(Context) has returned to its caller, but its ContextImpl can still
        // be completing application-context wiring on this stack. Defer the first post to the
        // main queue so FocusNotificationPublisher always receives a fully usable Context.
        // Preference changes already take this same path through PREFERENCES_LISTENER.
        requestPersistedSettingsApply();
    }

    private static void cancelHostNotification(Context context) {
        try {
            NotificationManager notificationManager =
                    context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.cancel(SystemUiResidentIslandContract.HOST_NOTIFICATION_ID);
            }
        } catch (Throwable error) {
            Log.w(TAG, "Could not clear residual resident-island notification", error);
        }
    }

    private static void attachRemotePreferences(XposedModule module) {
        if (remotePreferences != null) return;
        try {
            SharedPreferences preferences = module.getRemotePreferences(
                    SystemUiResidentIslandContract.REMOTE_PREFERENCES
            );
            preferences.registerOnSharedPreferenceChangeListener(PREFERENCES_LISTENER);
            remotePreferences = preferences;
            Log.i(TAG, "Loaded resident-island RemotePreferences");
        } catch (Throwable error) {
            // Do not fall back to app-private files or a global broadcast. The previous host state
            // remains intact and a future app/LSPosed reconnect will explicitly reload settings.
            Log.e(TAG, "Could not load resident-island RemotePreferences", error);
        }
    }

    private static final SharedPreferences.OnSharedPreferenceChangeListener PREFERENCES_LISTENER =
            (preferences, key) -> requestPersistedSettingsApply();

    private static final Runnable APPLY_SETTINGS_RUNNABLE =
            SystemUiResidentIslandHost::applyPersistedSettings;

    private static final BroadcastReceiver HOST_RECEIVER = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (SystemUiResidentIslandContract.ACTION_RELOAD_SETTINGS.equals(action)) {
                requestPersistedSettingsApply();
            } else if (SystemUiResidentIslandContract.ACTION_CANCEL.equals(action)) {
                MAIN_HANDLER.removeCallbacks(APPLY_SETTINGS_RUNNABLE);
                lastAppliedSettingsFingerprint = null;
                stopPolling(context, true);
            } else if (SystemUiResidentIslandContract.ACTION_POST.equals(action)) {
                // Compatibility path for a running pre-host service during an in-place update.
                postLegacySnapshot(context, intent);
            }
        }
    };

    private static final BroadcastReceiver BATTERY_RECEIVER = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                publishCurrentSnapshot(context);
            }
        }
    };

    private static final BroadcastReceiver REFRESH_RECEIVER = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ACTION_REFRESH_NOW.equals(intent.getAction()) || !pollingEnabled) return;
            MAIN_HANDLER.removeCallbacks(REFRESH_RUNNABLE);
            publishCurrentSnapshot(context);
        }
    };

    private static final Runnable REFRESH_RUNNABLE = new Runnable() {
        @Override
        public void run() {
            Context context = appContext;
            if (context != null && pollingEnabled) {
                publishCurrentSnapshot(context);
            }
        }
    };

    private static void registerHostReceiver(Context context) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(SystemUiResidentIslandContract.ACTION_POST);
        filter.addAction(SystemUiResidentIslandContract.ACTION_CANCEL);
        filter.addAction(SystemUiResidentIslandContract.ACTION_RELOAD_SETTINGS);
        context.registerReceiver(
                HOST_RECEIVER,
                filter,
                SystemUiResidentIslandContract.PERMISSION,
                null,
                Context.RECEIVER_EXPORTED
        );
        context.registerReceiver(
                REFRESH_RECEIVER,
                new IntentFilter(ACTION_REFRESH_NOW),
                Context.RECEIVER_NOT_EXPORTED
        );
    }

    private static void applyPersistedSettings() {
        Context context = appContext;
        SharedPreferences preferences = remotePreferences;
        if (context == null || preferences == null) return;
        try {
            String fingerprint = persistedSettingsFingerprint(preferences);
            if (fingerprint.equals(lastAppliedSettingsFingerprint)) return;
            if (preferences.getBoolean(SystemUiResidentIslandContract.KEY_ENABLED, false)) {
                startPolling(context);
            } else {
                stopPolling(context, true);
            }
            lastAppliedSettingsFingerprint = fingerprint;
        } catch (Throwable error) {
            // Do not poison equality deduplication with a configuration that never applied.
            // Preserve an already-live island; a transient preference failure must not turn it
            // into a dead last frame. A later callback may retry the same configuration.
            lastAppliedSettingsFingerprint = null;
            if (pollingEnabled) scheduleNextRefresh();
            Log.e(TAG, "Could not apply resident-island settings", error);
        }
    }

    /**
     * RemotePreferences reports every changed key and the app also sends one explicit reload.
     * Collapse both channels into a single main-thread apply so one atomic config save can never
     * fan out into a queue of immediate notification publications.
     */
    private static void requestPersistedSettingsApply() {
        MAIN_HANDLER.removeCallbacks(APPLY_SETTINGS_RUNNABLE);
        MAIN_HANDLER.postDelayed(APPLY_SETTINGS_RUNNABLE, SETTINGS_APPLY_DEBOUNCE_MILLIS);
    }

    private static String persistedSettingsFingerprint(SharedPreferences preferences) {
        return preferences.getBoolean(SystemUiResidentIslandContract.KEY_ENABLED, false)
                + "\u0000" + refreshIntervalMillis()
                + "\u0000" + leftTitleMetricSetting()
                + "\u0000" + rightTitleMetricSetting()
                + "\u0000" + leftIslandIconEnabled()
                + "\u0000" + rightIslandIconEnabled()
                + "\u0000" + expandedContentModeSetting()
                + "\u0000" + expandedContentTemplateSetting()
                + "\u0000" + expandedActionsSetting()
                + "\u0000" + settingString(
                        SystemUiResidentIslandContract.KEY_EXPANDED_METRICS,
                        "BATTERY_PERCENT,CURRENT,POWER,BATTERY_TEMPERATURE"
                );
    }

    private static void startPolling(Context context) {
        pollingEnabled = true;
        registerBatteryReceiver(context);
        publishCurrentSnapshot(context);
    }

    private static void stopPolling(Context context, boolean cancelNotification) {
        pollingEnabled = false;
        MAIN_HANDLER.removeCallbacks(REFRESH_RUNNABLE);
        if (batteryReceiverRegistered) {
            try {
                context.unregisterReceiver(BATTERY_RECEIVER);
            } catch (IllegalArgumentException ignored) {
                // The process may already be in SystemUI teardown.
            }
            batteryReceiverRegistered = false;
        }
        if (cancelNotification) {
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.cancel(SystemUiResidentIslandContract.HOST_NOTIFICATION_ID);
            }
        }
    }

    private static void registerBatteryReceiver(Context context) {
        if (batteryReceiverRegistered) return;
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        context.registerReceiver(BATTERY_RECEIVER, filter, Context.RECEIVER_NOT_EXPORTED);
        batteryReceiverRegistered = true;
    }

    private static void publishCurrentSnapshot(Context context) {
        publishCurrentSnapshot(context, true);
    }

    private static void publishCurrentSnapshot(Context context, boolean refreshFanMetric) {
        if (!pollingEnabled) return;
        if (refreshFanMetric && fanMetricRequested()) {
            requestFanRpmRefresh();
        }
        BatterySnapshot snapshot = readBatterySnapshot(context);
        try {
            String leftTitleMetric = effectiveTitleMetric(leftTitleMetricSetting(), snapshot);
            String rightTitleMetric = effectiveTitleMetric(rightTitleMetricSetting(), snapshot);
            FocusNotificationRequest request = new FocusNotificationRequest(
                    titleFor(snapshot, leftTitleMetric),
                    contentFor(snapshot),
                    snapshot.levelPercent >= 0 ? snapshot.levelPercent : 0,
                    false,
                    shortStatusFor(snapshot, rightTitleMetric)
            );
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager == null) return;
            FocusNotificationPublisher publisher = new FocusNotificationPublisher(context, CHANNEL_NAME);
            boolean showLeftIslandIcon = leftIslandIconEnabled();
            boolean showRightIslandIcon = rightIslandIconEnabled();
            FocusCustomRemoteViews customRemoteViews = residentFocusRemoteViews(
                    request.getText(),
                    resolveExpandedActions(context)
            );
            // OS4 HyperOS 2: also try DynamicIsland path in addition to miui.focus.
            if (Build.VERSION.SDK_INT >= 35) {
                try {
                    notifyOS4DynamicIsland(context, request);
                } catch (Throwable os4Error) {
                    Log.w(TAG, "OS4 DynamicIsland notify failed, keeping miui.focus only", os4Error);
                }
            }
            notificationManager.notify(
                    SystemUiResidentIslandContract.HOST_NOTIFICATION_ID,
                    publisher.buildSystemUiResidentNotification(
                            request,
                            android.R.drawable.ic_dialog_info,
                            moduleMetricIcon(leftTitleMetric),
                            moduleMetricIcon(rightTitleMetric),
                            showLeftIslandIcon,
                            showRightIslandIcon,
                            customRemoteViews,
                            MODULE_PACKAGE
                    )
            );
        } catch (Throwable error) {
            Log.e(TAG, "Could not publish SystemUI-owned resident island", error);
        } finally {
            scheduleNextRefresh();
        }
    }

    /** Compatibility only; normal resident operation always uses [publishCurrentSnapshot]. */
    private static void postLegacySnapshot(Context context, Intent intent) {
        String title = bounded(intent.getStringExtra(SystemUiResidentIslandContract.EXTRA_TITLE));
        String text = bounded(intent.getStringExtra(SystemUiResidentIslandContract.EXTRA_TEXT));
        String shortStatus = bounded(
                intent.getStringExtra(SystemUiResidentIslandContract.EXTRA_SHORT_STATUS));
        if (title.isEmpty() || text.isEmpty()) return;
        if (shortStatus.isEmpty()) shortStatus = "系统状态";
        try {
            FocusNotificationRequest request = new FocusNotificationRequest(title, text, 0, false, shortStatus);
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager == null) return;
            FocusNotificationPublisher publisher = new FocusNotificationPublisher(context, CHANNEL_NAME);
            Icon legacyIcon = sourceModuleIcon(intent);
            FocusCustomRemoteViews customRemoteViews = residentFocusRemoteViews(
                    request.getText(),
                    resolveExpandedActions(context)
            );
            notificationManager.notify(
                    SystemUiResidentIslandContract.HOST_NOTIFICATION_ID,
                    publisher.buildSystemUiResidentNotification(
                            request,
                            android.R.drawable.ic_dialog_info,
                            legacyIcon,
                            legacyIcon,
                            true,
                            false,
                            customRemoteViews,
                            MODULE_PACKAGE
                    )
            );
        } catch (Throwable error) {
            Log.e(TAG, "Could not post legacy resident-island update", error);
        }
    }

    private static BatterySnapshot readBatterySnapshot(Context context) {
        Intent batteryIntent = context.registerReceiver(
                null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                Context.RECEIVER_NOT_EXPORTED
        );
        int level = batteryIntent != null ? batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
        int scale = batteryIntent != null ? batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1) : -1;
        int levelPercent = level >= 0 && scale > 0
                ? Math.max(0, Math.min(100, Math.round(level * 100f / scale)))
                : -1;
        int status = batteryIntent != null
                ? batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
                : BatteryManager.BATTERY_STATUS_UNKNOWN;
        int voltage = batteryIntent != null ? batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) : -1;
        int temperature = batteryIntent != null
                ? batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE)
                : Integer.MIN_VALUE;
        BatteryManager batteryManager = context.getSystemService(BatteryManager.class);
        int rawCurrent = batteryManager != null
                ? batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                : Integer.MIN_VALUE;
        Integer current = rawCurrent == Integer.MIN_VALUE ? null : normalizeCurrent(rawCurrent, status);
        Integer fanRpm = fanMetricRequested() ? latestFanRpm : null;
        return new BatterySnapshot(
                levelPercent,
                status,
                current,
                voltage >= 0 ? voltage : null,
                temperature == Integer.MIN_VALUE ? null : temperature,
                fanRpm
        );
    }

    private static int normalizeCurrent(int current, int status) {
        if ((status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
                && current < 0) return -current;
        if (status == BatteryManager.BATTERY_STATUS_DISCHARGING && current > 0) return -current;
        return current;
    }

    private static String titleFor(BatterySnapshot snapshot, String metric) {
        String value = metricValue(metric, snapshot);
        return value == null || value.isEmpty() ? "设备状态" : value;
    }

    private static String contentFor(BatterySnapshot snapshot) {
        if ("CUSTOM".equals(expandedContentModeSetting())) {
            ResidentExpandedTemplateRenderResult rendered =
                    ResidentExpandedContentTemplate.render(
                            expandedContentTemplateSetting(),
                            expandedTemplateValues(snapshot)
                    );
            String text = rendered.getText();
            return text == null || text.trim().isEmpty()
                    ? "设备状态正在更新"
                    : bounded(text);
        }
        String csv = settingString(
                SystemUiResidentIslandContract.KEY_EXPANDED_METRICS,
                "BATTERY_PERCENT,CURRENT,POWER,BATTERY_TEMPERATURE"
        );
        StringBuilder content = new StringBuilder();
        for (String rawMetric : csv.split(",")) {
            String metric = rawMetric.trim();
            String value = metricValue(metric, snapshot);
            if (value == null || value.isEmpty()) continue;
            if (content.length() > 0) content.append(" · ");
            content.append(metricLabel(metric)).append(": ").append(value);
        }
        return content.length() == 0 ? "设备状态正在更新" : bounded(content.toString());
    }

    private static Map<String, String> expandedTemplateValues(BatterySnapshot snapshot) {
        Locale locale = Locale.getDefault();
        ZonedDateTime now = ZonedDateTime.now();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("time", now.format(DateTimeFormatter.ofPattern("HH:mm:ss", locale)));
        values.put(
                "date",
                now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        );
        values.put("weekday", now.format(DateTimeFormatter.ofPattern("EEEE", locale)));
        values.put("battery", metricValue("BATTERY_PERCENT", snapshot));
        values.put("charge_state", metricValue("CHARGE_STATE", snapshot));
        values.put("current", metricValue("CURRENT", snapshot));
        values.put("voltage", metricValue("VOLTAGE", snapshot));
        values.put("power", metricValue("POWER", snapshot));
        values.put("battery_temp", metricValue("BATTERY_TEMPERATURE", snapshot));
        values.put("fan_rpm", metricValue("FAN_RPM", snapshot));
        return values;
    }

    private static String expandedContentModeSetting() {
        String mode = settingString(
                SystemUiResidentIslandContract.KEY_EXPANDED_CONTENT_MODE,
                "PRESET"
        );
        return "CUSTOM".equals(mode) ? "CUSTOM" : "PRESET";
    }

    private static String expandedContentTemplateSetting() {
        return settingString(
                SystemUiResidentIslandContract.KEY_EXPANDED_CONTENT_TEMPLATE,
                ResidentExpandedContentTemplate.DEFAULT_TEMPLATE
        );
    }

    private static String shortStatusFor(BatterySnapshot snapshot, String metric) {
        String value = metricValue(metric, snapshot);
        return value == null || value.isEmpty() ? "系统状态" : value;
    }

    private static String effectiveTitleMetric(String configuredMetric, BatterySnapshot snapshot) {
        String normalizedMetric = normalizedTitleMetric(configuredMetric);
        String value = metricValue(normalizedMetric, snapshot);
        return value == null || value.isEmpty() ? "BATTERY_PERCENT" : normalizedMetric;
    }

    private static String leftTitleMetricSetting() {
        return normalizedTitleMetric(settingStringWithLegacy(
                SystemUiResidentIslandContract.KEY_LEFT_TITLE_METRIC,
                SystemUiResidentIslandContract.KEY_TITLE_METRIC,
                "BATTERY_PERCENT"
        ));
    }

    private static String rightTitleMetricSetting() {
        SharedPreferences preferences = remotePreferences;
        String metric;
        if (preferences != null
                && preferences.contains(SystemUiResidentIslandContract.KEY_RIGHT_TITLE_METRIC)) {
            metric = settingString(SystemUiResidentIslandContract.KEY_RIGHT_TITLE_METRIC, "BATTERY_PERCENT");
        } else if ("METRIC_SHORT".equals(settingString(
                SystemUiResidentIslandContract.KEY_TRAILING_KIND,
                "APP_ICON"
        ))) {
            metric = settingString(SystemUiResidentIslandContract.KEY_TRAILING_METRIC, "BATTERY_PERCENT");
        } else {
            // Version 1 rendered every other trailing choice as battery percentage. Preserve that
            // one-time migration result without keeping a permanent fallback in the new model.
            metric = "BATTERY_PERCENT";
        }
        return normalizedTitleMetric(metric);
    }

    private static String normalizedTitleMetric(String metric) {
        switch (metric) {
            case "BATTERY_PERCENT":
            case "CHARGE_STATE":
            case "CURRENT":
            case "POWER":
            case "BATTERY_TEMPERATURE":
            case "FAN_RPM":
                return metric;
            default:
                return "BATTERY_PERCENT";
        }
    }

    private static boolean fanMetricRequested() {
        if ("FAN_RPM".equals(leftTitleMetricSetting()) || "FAN_RPM".equals(rightTitleMetricSetting())) {
            return true;
        }
        if ("CUSTOM".equals(expandedContentModeSetting())) {
            ResidentExpandedTemplateValidation validation = ResidentExpandedContentTemplate
                    .validate(expandedContentTemplateSetting());
            return validation.isValid() && validation.getReferencedTokens().contains("fan_rpm");
        }
        String expanded = settingString(SystemUiResidentIslandContract.KEY_EXPANDED_METRICS, "");
        for (String rawMetric : expanded.split(",")) {
            if ("FAN_RPM".equals(rawMetric.trim())) return true;
        }
        return false;
    }

    private static void requestFanRpmRefresh() {
        long now = SystemClock.elapsedRealtime();
        synchronized (SystemUiResidentIslandHost.class) {
            if (Boolean.FALSE.equals(miuiFanSupported)) return;
            if (fanReadInFlight) return;
            if (lastFanReadStartedAt != 0L
                    && now - lastFanReadStartedAt < MIN_FAN_READ_INTERVAL_MILLIS) return;
            fanReadInFlight = true;
            lastFanReadStartedAt = now;
        }
        FAN_READER.execute(() -> {
            try {
                latestFanRpm = readFanRpmBlocking();
            } catch (Throwable error) {
                Log.w(TAG, "Could not refresh MIUI fan telemetry", error);
            } finally {
                synchronized (SystemUiResidentIslandHost.class) {
                    fanReadInFlight = false;
                    lastFanReadStartedAt = SystemClock.elapsedRealtime();
                }
                MAIN_HANDLER.post(() -> {
                    Context context = appContext;
                    if (context != null && pollingEnabled && fanMetricRequested()) {
                        // A completed fan read owns this supplemental publication. Do not route it
                        // through the acquisition gate again; the periodic base snapshot continues
                        // independently even while the Xiaomi API is slow or unavailable.
                        publishCurrentSnapshot(context, false);
                    }
                });
            }
        });
    }

    private static Integer readFanRpmBlocking() {
        if (!isMiuiFanSupported()) return null;
        String raw = readMiChargeValue("fan_real_speed");
        if (raw == null) return null;
        try {
            int rpm = Integer.parseInt(raw.trim());
            return ResidentMetricFormatter.fanRpm(rpm) != null ? rpm : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean isMiuiFanSupported() {
        Boolean cached = miuiFanSupported;
        if (cached != null) return cached;
        String support = readMiChargeValue("fan_support");
        if (support == null || support.trim().isEmpty()) return false;
        boolean supported = "1".equals(support.trim());
        miuiFanSupported = supported;
        return supported;
    }

    private static String readMiChargeValue(String key) {
        if (!"fan_support".equals(key) && !"fan_real_speed".equals(key)) return null;
        try {
            Method readMethod;
            Object instance;
            synchronized (SystemUiResidentIslandHost.class) {
                if (miChargeReadMethod == null || miChargeInstance == null) {
                    Class<?> chargeClass = Class.forName("miui.util.IMiCharge");
                    Method instanceMethod = chargeClass.getDeclaredMethod("getInstance");
                    instanceMethod.setAccessible(true);
                    miChargeInstance = instanceMethod.invoke(null);
                    miChargeReadMethod = miChargeInstance.getClass()
                            .getDeclaredMethod("getMiChargePath", String.class);
                    miChargeReadMethod.setAccessible(true);
                }
                readMethod = miChargeReadMethod;
                instance = miChargeInstance;
            }
            Object value = readMethod.invoke(instance, key);
            miuiFanReadFailureLogged = false;
            return value == null ? null : value.toString();
        } catch (Throwable error) {
            if (!miuiFanReadFailureLogged) {
                miuiFanReadFailureLogged = true;
                Log.w(TAG, "MIUI fan telemetry is unavailable", error);
            }
            return null;
        }
    }

    private static boolean leftIslandIconEnabled() {
        SharedPreferences preferences = remotePreferences;
        if (preferences != null && preferences.contains(SystemUiResidentIslandContract.KEY_LEFT_ICON)) {
            return !"NONE".equals(settingString(
                    SystemUiResidentIslandContract.KEY_LEFT_ICON,
                    "FOLLOW_TITLE"
            ));
        }
        return !"NONE".equals(settingString(
                SystemUiResidentIslandContract.KEY_LEADING_KIND,
                "APP_ICON"
        ));
    }

    private static boolean rightIslandIconEnabled() {
        SharedPreferences preferences = remotePreferences;
        return preferences != null
                && preferences.contains(SystemUiResidentIslandContract.KEY_RIGHT_ICON)
                && !"NONE".equals(settingString(
                        SystemUiResidentIslandContract.KEY_RIGHT_ICON,
                        "NONE"
                ));
    }

    private static String metricValue(String metric, BatterySnapshot snapshot) {
        switch (metric) {
            case "BATTERY_PERCENT":
                return snapshot.levelPercent >= 0 ? snapshot.levelPercent + "%" : null;
            case "CHARGE_STATE":
                return chargeStateText(snapshot.status);
            case "CURRENT":
                return ResidentMetricFormatter.currentMicroAmps(snapshot.currentMicroAmps);
            case "VOLTAGE":
                return snapshot.voltageMillivolts != null ? snapshot.voltageMillivolts + "mV" : null;
            case "POWER":
                return ResidentMetricFormatter.powerWatts(
                        snapshot.currentMicroAmps,
                        snapshot.voltageMillivolts
                );
            case "BATTERY_TEMPERATURE":
                return ResidentMetricFormatter.temperatureTenthsCelsius(
                        snapshot.temperatureTenthsCelsius
                );
            case "FAN_RPM":
                return ResidentMetricFormatter.fanRpm(snapshot.fanRpm);
            default:
                // Root-only and future metrics stay unavailable rather than pretending that
                // SystemUI can read them after the module app has been stopped.
                return null;
        }
    }

    private static String metricLabel(String metric) {
        switch (metric) {
            case "BATTERY_PERCENT": return "电量";
            case "CHARGE_STATE": return "充电状态";
            case "CURRENT": return "电流";
            case "VOLTAGE": return "电压";
            case "POWER": return "功耗";
            case "BATTERY_TEMPERATURE": return "电池温度";
            case "FAN_RPM": return "风扇转速";
            default: return "设备状态";
        }
    }

    private static String chargeStateText(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "充电中";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "放电中";
            case BatteryManager.BATTERY_STATUS_FULL: return "已充满";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "未充电";
            default: return "状态未知";
        }
    }

    private static void scheduleNextRefresh() {
        if (!pollingEnabled) return;
        MAIN_HANDLER.removeCallbacks(REFRESH_RUNNABLE);
        MAIN_HANDLER.postDelayed(REFRESH_RUNNABLE, refreshIntervalMillis());
    }

    private static long refreshIntervalMillis() {
        SharedPreferences preferences = remotePreferences;
        long stored = preferences == null
                ? DEFAULT_INTERVAL_MILLIS
                : preferences.getLong(
                        SystemUiResidentIslandContract.KEY_REFRESH_INTERVAL_MILLIS,
                        DEFAULT_INTERVAL_MILLIS
                );
        return Math.max(MIN_INTERVAL_MILLIS, Math.min(MAX_INTERVAL_MILLIS, stored));
    }

    private static String settingString(String key, String defaultValue) {
        SharedPreferences preferences = remotePreferences;
        if (preferences == null) return defaultValue;
        String value = preferences.getString(key, defaultValue);
        return value == null ? defaultValue : value;
    }

    private static String settingStringWithLegacy(
            String key,
            String legacyKey,
            String defaultValue
    ) {
        SharedPreferences preferences = remotePreferences;
        if (preferences != null && preferences.contains(key)) {
            return settingString(key, defaultValue);
        }
        return settingString(legacyKey, defaultValue);
    }

    private static Icon moduleMetricIcon(String metric) {
        try {
            Context moduleContext = getModuleResourceContext();
            int resourceId = moduleContext.getResources().getIdentifier(
                    metricIconResourceName(metric),
                    "drawable",
                    MODULE_PACKAGE
            );
            if (resourceId == 0) return null;
            return Icon.createWithResource(MODULE_PACKAGE, resourceId);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<ResolvedResidentAction> resolveExpandedActions(Context context) {
        List<ResidentExpandedAction> configured = ResidentExpandedActionCodec.decodeOrEmpty(
                expandedActionsSetting()
        );
        List<ResolvedResidentAction> resolved = new ArrayList<>(ResidentExpandedAction.MAX_ACTIONS);
        for (ResidentExpandedAction action : configured) {
            if (resolved.size() >= ResidentExpandedAction.MAX_ACTIONS) break;
            ResolvedResidentAction candidate = resolveExpandedAction(context, action);
            if (candidate != null) resolved.add(candidate);
        }
        return resolved;
    }

    private static String expandedActionsSetting() {
        try {
            return settingString(SystemUiResidentIslandContract.KEY_EXPANDED_ACTIONS, "");
        } catch (ClassCastException invalidPreferenceType) {
            return "";
        }
    }

    private static ResolvedResidentAction resolveExpandedAction(
            Context context,
            ResidentExpandedAction action
    ) {
        try {
            PendingIntent pendingIntent;
            String iconPackage;
            switch (action.getType()) {
                case REFRESH_NOW:
                    pendingIntent = PendingIntent.getBroadcast(
                            context,
                            stableActionRequestCode(action.getId()),
                            new Intent(ACTION_REFRESH_NOW).setPackage(context.getPackageName()),
                            PENDING_INTENT_FLAGS
                    );
                    iconPackage = MODULE_PACKAGE;
                    break;
                case LAUNCH_APP:
                    Intent launcherIntent = explicitLauncherIntent(context, action.getTargetPackage());
                    if (launcherIntent == null) return null;
                    pendingIntent = PendingIntent.getActivity(
                            context,
                            stableActionRequestCode(action.getId()),
                            launcherIntent,
                            PENDING_INTENT_FLAGS
                    );
                    iconPackage = explicitTargetPackage(launcherIntent);
                    break;
                case OPEN_BATTERY_SETTINGS:
                    Intent batterySettingsIntent = explicitResolvedActivityIntent(
                            context,
                            new Intent("android.intent.action.POWER_USAGE_SUMMARY")
                    );
                    if (batterySettingsIntent == null) return null;
                    pendingIntent = PendingIntent.getActivity(
                            context,
                            stableActionRequestCode(action.getId()),
                            batterySettingsIntent,
                            PENDING_INTENT_FLAGS
                    );
                    iconPackage = SETTINGS_PACKAGE;
                    break;
                case OPEN_NOTIFICATION_SETTINGS:
                    Intent notificationSettingsIntent = explicitResolvedActivityIntent(
                            context,
                            new Intent("android.settings.NOTIFICATION_SETTINGS")
                    );
                    if (notificationSettingsIntent == null) return null;
                    pendingIntent = PendingIntent.getActivity(
                            context,
                            stableActionRequestCode(action.getId()),
                            notificationSettingsIntent,
                            PENDING_INTENT_FLAGS
                    );
                    iconPackage = SETTINGS_PACKAGE;
                    break;
                case OPEN_KNOWN_SHORTCUT:
                    Intent shortcutIntent = ResidentKnownShortcutResolver.resolveExplicit(
                            context,
                            action.getTargetPackage()
                    );
                    if (shortcutIntent == null) return null;
                    pendingIntent = PendingIntent.getActivity(
                            context,
                            stableActionRequestCode(action.getId()),
                            shortcutIntent,
                            PENDING_INTENT_FLAGS
                    );
                    iconPackage = explicitTargetPackage(shortcutIntent);
                    break;
                default:
                    return null;
            }
            Icon appIcon = applicationIcon(context, iconPackage);
            if (appIcon == null) return null;
            return new ResolvedResidentAction(action.getLabel(), appIcon, pendingIntent);
        } catch (Throwable error) {
            Log.w(TAG, "Could not resolve resident expanded action " + action.getType(), error);
            return null;
        }
    }

    private static String explicitTargetPackage(Intent intent) {
        ComponentName component = intent == null ? null : intent.getComponent();
        return component == null ? null : component.getPackageName();
    }

    private static Icon applicationIcon(Context context, String packageName) throws Exception {
        if (packageName == null || packageName.trim().isEmpty()) return null;
        String normalizedPackage = packageName.trim();
        ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(
                normalizedPackage,
                PackageManager.ApplicationInfoFlags.of(ACTIVITY_RESOLVE_FLAGS)
        );
        if (!applicationInfo.enabled
                || !normalizedPackage.equals(applicationInfo.packageName)
                || applicationInfo.icon == 0) {
            return null;
        }
        return Icon.createWithResource(applicationInfo.packageName, applicationInfo.icon);
    }

    private static Intent explicitLauncherIntent(Context context, String targetPackage) {
        if (targetPackage == null || targetPackage.trim().isEmpty()) return null;
        String normalizedPackage = targetPackage.trim();
        Intent launcherQuery = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(normalizedPackage);
        List<ResolveInfo> candidates = context.getPackageManager().queryIntentActivities(
                launcherQuery,
                PackageManager.ResolveInfoFlags.of(ACTIVITY_RESOLVE_FLAGS)
        );
        for (ResolveInfo candidate : candidates) {
            ActivityInfo activityInfo = candidate.activityInfo;
            if (!isSafeActivity(activityInfo)
                    || !normalizedPackage.equals(activityInfo.packageName)) continue;
            Intent explicit = explicitActivityIntent(launcherQuery, activityInfo);
            if (isSameResolvedActivity(context, explicit, activityInfo)) return explicit;
        }
        return null;
    }

    private static Intent explicitResolvedActivityIntent(Context context, Intent implicitIntent) {
        ResolveInfo resolved = context.getPackageManager().resolveActivity(
                implicitIntent,
                PackageManager.ResolveInfoFlags.of(
                        ACTIVITY_RESOLVE_FLAGS | PackageManager.MATCH_DEFAULT_ONLY
                )
        );
        ActivityInfo activityInfo = resolved == null ? null : resolved.activityInfo;
        if (!isSafeActivity(activityInfo)) return null;
        Intent explicit = explicitActivityIntent(implicitIntent, activityInfo);
        return isSameResolvedActivity(context, explicit, activityInfo) ? explicit : null;
    }

    private static Intent explicitActivityIntent(Intent source, ActivityInfo activityInfo) {
        return new Intent(source)
                .setComponent(new ComponentName(activityInfo.packageName, activityInfo.name))
                .setPackage(activityInfo.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    private static boolean isSafeActivity(ActivityInfo activityInfo) {
        return activityInfo != null
                && activityInfo.exported
                && activityInfo.enabled
                && activityInfo.applicationInfo != null
                && activityInfo.applicationInfo.enabled;
    }

    private static boolean isSameResolvedActivity(
            Context context,
            Intent explicitIntent,
            ActivityInfo expected
    ) {
        ResolveInfo verified = context.getPackageManager().resolveActivity(
                explicitIntent,
                PackageManager.ResolveInfoFlags.of(ACTIVITY_RESOLVE_FLAGS)
        );
        ActivityInfo actual = verified == null ? null : verified.activityInfo;
        return isSafeActivity(actual)
                && expected.packageName.equals(actual.packageName)
                && expected.name.equals(actual.name);
    }

    private static int stableActionRequestCode(String actionId) {
        return PENDING_INTENT_REQUEST_CODE_PREFIX
                | (actionId.hashCode() & PENDING_INTENT_REQUEST_CODE_MASK);
    }

    private static FocusCustomRemoteViews residentFocusRemoteViews(
            String content,
            List<ResolvedResidentAction> actions
    ) {
        if (customFocusRemoteViewsUnavailable) return null;
        try {
            Context moduleContext = getModuleResourceContext();
            boolean hasActions = !actions.isEmpty();
            int layoutId = moduleContext.getResources().getIdentifier(
                    hasActions ? "focus_resident_expanded_with_actions" : "focus_resident_expanded",
                    "layout",
                    MODULE_PACKAGE
            );
            int contentId = moduleContext.getResources().getIdentifier(
                    "focus_resident_expanded_content",
                    "id",
                    MODULE_PACKAGE
            );
            if (layoutId == 0 || contentId == 0) {
                throw new IllegalStateException("Resident focus RemoteViews resources are missing");
            }
            return new FocusCustomRemoteViews(
                    residentFocusRemoteView(moduleContext, layoutId, contentId, content, actions, 0xde000000),
                    residentFocusRemoteView(moduleContext, layoutId, contentId, content, actions, 0xdeffffff),
                    residentFocusRemoteView(moduleContext, layoutId, contentId, content, actions, 0xffffffff)
            );
        } catch (Throwable error) {
            customFocusRemoteViewsUnavailable = true;
            if (!customFocusRemoteViewsFailureLogged) {
                customFocusRemoteViewsFailureLogged = true;
                Log.e(TAG, "Custom resident focus view unavailable; using the native focus template", error);
            }
            return null;
        }
    }

    private static RemoteViews residentFocusRemoteView(
            Context moduleContext,
            int layoutId,
            int contentId,
            String content,
            List<ResolvedResidentAction> actions,
            int textColor
    ) {
        RemoteViews views = new RemoteViews(MODULE_PACKAGE, layoutId);
        views.setTextViewText(contentId, content);
        views.setTextColor(contentId, textColor);
        if (actions.isEmpty()) return views;
        for (int index = 0; index < ResidentExpandedAction.MAX_ACTIONS; index++) {
            int buttonId = moduleContext.getResources().getIdentifier(
                    "focus_resident_action_" + (index + 1),
                    "id",
                    MODULE_PACKAGE
            );
            int iconId = moduleContext.getResources().getIdentifier(
                    "focus_resident_action_icon_" + (index + 1),
                    "id",
                    MODULE_PACKAGE
            );
            int labelId = moduleContext.getResources().getIdentifier(
                    "focus_resident_action_label_" + (index + 1),
                    "id",
                    MODULE_PACKAGE
            );
            if (buttonId == 0 || iconId == 0 || labelId == 0) {
                throw new IllegalStateException("Resident focus action resources are missing");
            }
            if (index < actions.size()) {
                ResolvedResidentAction action = actions.get(index);
                views.setImageViewIcon(iconId, action.appIcon);
                views.setTextViewText(labelId, action.label);
                views.setTextColor(labelId, textColor);
                views.setContentDescription(buttonId, action.label);
                views.setOnClickPendingIntent(buttonId, action.pendingIntent);
                views.setViewVisibility(buttonId, View.VISIBLE);
            } else {
                views.setViewVisibility(buttonId, View.GONE);
            }
        }
        return views;
    }

    private static Context getModuleResourceContext() throws Exception {
        Context cached = moduleResourceContext;
        if (cached != null) return cached;
        Context context = appContext;
        if (context == null) throw new IllegalStateException("SystemUI context is unavailable");
        Context moduleContext = context.createPackageContext(
                MODULE_PACKAGE,
                Context.CONTEXT_IGNORE_SECURITY
        );
        moduleResourceContext = moduleContext;
        return moduleContext;
    }

    private static String metricIconResourceName(String metric) {
        switch (normalizedTitleMetric(metric)) {
            case "CHARGE_STATE": return "ic_resident_charging";
            case "CURRENT": return "ic_resident_current";
            case "POWER": return "ic_resident_power";
            case "BATTERY_TEMPERATURE": return "ic_resident_temperature";
            case "FAN_RPM": return "ic_resident_fan";
            case "BATTERY_PERCENT":
            default:
                return "ic_resident_battery";
        }
    }

    private static Icon sourceModuleIcon(Intent intent) {
        Icon icon;
        try {
            icon = intent.getParcelableExtra(
                    SystemUiResidentIslandContract.EXTRA_APP_ICON,
                    Icon.class
            );
        } catch (Throwable ignored) {
            return null;
        }
        if (icon == null
                || icon.getType() != Icon.TYPE_RESOURCE
                || !MODULE_PACKAGE.equals(icon.getResPackage())) {
            return null;
        }
        return icon;
    }

    private static void notifyOS4DynamicIsland(Context context, FocusNotificationRequest request) throws Exception {
        // HyperOS 2 (OS4, SDK 37) uses DynamicIslandWindowAnimHelper.notifyIslandInfoAdd via
        // miui.dynamicisland.DynamicIslandManager. Use the helper that wraps the manager's
        // DynamicIslandData construction, as the manager's direct API is obfuscated per build.
        try {
            Class<?> helperClass = Class.forName("com.android.systemui.statusbar.notification.utils.DynamicIslandWindowAnimHelper");
            // Helper's overload is (String pkg, int uid, String pkg2, ElementSurfaceTransition)
            // Use 4-arg if available, otherwise 3-arg (pkg, uid, transition)
            try {
                helperClass.getMethod("notifyIslandInfoAdd", String.class, int.class, String.class, Class.forName("android.app.ElementSurfaceTransition"))
                        .invoke(null, MODULE_PACKAGE, context.getApplicationInfo().uid, MODULE_PACKAGE, null);
            } catch (NoSuchMethodException e1) {
                try {
                    helperClass.getMethod("notifyIslandInfoAdd", String.class, int.class, Class.forName("android.app.ElementSurfaceTransition"))
                            .invoke(null, MODULE_PACKAGE, context.getApplicationInfo().uid, null);
                } catch (NoSuchMethodException e2) {
                    // Fallback to manager's single-arg DynamicIslandData path
                    Class<?> managerClass = Class.forName("miui.dynamicisland.DynamicIslandManager");
                    Object manager = managerClass.getMethod("getInstance").invoke(null);
                    Class<?> dataClass = Class.forName("miui.dynamicisland.DynamicIslandData");
                    Object data = dataClass.getConstructor(String.class, Integer.TYPE, Class.forName("android.app.ElementSurfaceTransition"))
                            .newInstance(MODULE_PACKAGE, context.getApplicationInfo().uid, null);
                    managerClass.getMethod("notifyIslandInfoAdd", dataClass).invoke(manager, data);
                }
            }
            Log.i(TAG, "Notified OS4 DynamicIsland via helper for " + request.getTitle());
            return;
        } catch (Throwable helperError) {
            Log.w(TAG, "DynamicIslandWindowAnimHelper path failed, trying manager", helperError);
        }
        // Final fallback: manager single-arg
        Class<?> managerClass = Class.forName("miui.dynamicisland.DynamicIslandManager");
        Object manager = managerClass.getMethod("getInstance").invoke(null);
        Class<?> dataClass = Class.forName("miui.dynamicisland.DynamicIslandData");
        Object data = dataClass.getConstructor(String.class, Integer.TYPE, Class.forName("android.app.ElementSurfaceTransition"))
                .newInstance(MODULE_PACKAGE, context.getApplicationInfo().uid, null);
        managerClass.getMethod("notifyIslandInfoAdd", dataClass).invoke(manager, data);
        Log.i(TAG, "Notified OS4 DynamicIsland via manager fallback for " + request.getTitle());
    }

    private static String bounded(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= MAX_TEXT_LENGTH ? trimmed : trimmed.substring(0, MAX_TEXT_LENGTH);
    }

    private static final class ResolvedResidentAction {
        final String label;
        final Icon appIcon;
        final PendingIntent pendingIntent;

        ResolvedResidentAction(String label, Icon appIcon, PendingIntent pendingIntent) {
            this.label = label;
            this.appIcon = appIcon;
            this.pendingIntent = pendingIntent;
        }
    }

    private static final class BatterySnapshot {
        final int levelPercent;
        final int status;
        final Integer currentMicroAmps;
        final Integer voltageMillivolts;
        final Integer temperatureTenthsCelsius;
        final Integer fanRpm;

        BatterySnapshot(
                int levelPercent,
                int status,
                Integer currentMicroAmps,
                Integer voltageMillivolts,
                Integer temperatureTenthsCelsius,
                Integer fanRpm
        ) {
            this.levelPercent = levelPercent;
            this.status = status;
            this.currentMicroAmps = currentMicroAmps;
            this.voltageMillivolts = voltageMillivolts;
            this.temperatureTenthsCelsius = temperatureTenthsCelsius;
            this.fanRpm = fanRpm;
        }
    }
}
