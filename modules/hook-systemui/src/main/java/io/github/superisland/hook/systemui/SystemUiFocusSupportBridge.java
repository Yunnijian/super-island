package io.github.superisland.hook.systemui;

import android.app.Notification;
import android.app.NotificationChannel;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherApps;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import io.github.libxposed.api.XposedInterface.HookHandle;
import io.github.superisland.model.AppRule;
import io.github.superisland.model.ChannelSelection;
import io.github.superisland.model.SystemUiSmartCapsuleContract;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Product-owned support around Xiaomi's Focus plugin.
 *
 * <p>Third-party smart capsules do not pass through this class. They keep their source identity and
 * use the SystemUI notification mapper plus the scoped XMSF adapter. This bridge only preserves the
 * exact authorization required by module-owned/resident Focus notifications and the optional
 * notification-Channel catalog used by the editor.
 */
final class SystemUiFocusSupportBridge {
    private static final String TAG = "SuperIslandFocusSupport";
    private static final String MODULE_PACKAGE = SystemUiSmartCapsuleContract.MODULE_PACKAGE;
    private static final String SYSTEM_UI_PACKAGE = SystemUiSmartCapsuleContract.SYSTEM_UI_PACKAGE;
    private static final Uri REPORT_URI =
            Uri.parse("content://" + SystemUiSmartCapsuleContract.REPORT_PROVIDER_AUTHORITY);
    private static final int MAX_CHANNELS = 128;
    private static final int MAX_CHANNEL_NAME_CODE_UNITS = 160;
    private static final int ANDROID_UIDS_PER_USER = 100_000;

    private static final ThreadPoolExecutor REPORT_EXECUTOR = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(32),
            runnable -> {
                Thread thread = new Thread(runnable, "SuperIslandFocusReports");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.DiscardOldestPolicy());
    private static final ThreadPoolExecutor CHANNEL_EXECUTOR = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(8),
            runnable -> {
                Thread thread = new Thread(runnable, "SuperIslandChannelQuery");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.DiscardOldestPolicy());

    private final Context runtimeContext;
    private final long pluginEpoch;
    private final Class<?> callbackInterface;
    private final Method callbackOnAuthSuccess;
    private final Method notificationManagerGetService;
    private final Method getNotificationChannelsForPackage;
    private final Method parceledListGetList;
    private final List<HookHandle> hookHandles = new ArrayList<>();
    private final AtomicLong channelRequestIds = new AtomicLong();
    private final ConcurrentHashMap<ChannelRequestKey, Long> channelRequests =
            new ConcurrentHashMap<>();
    private SystemUiFocusOnlyIslandSuppressor focusOnlyIslandSuppressor;
    private volatile boolean active;
    private volatile boolean receiverRegistered;

    private final BroadcastReceiver channelRequestReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!SystemUiSmartCapsuleContract.ACTION_REQUEST_CHANNELS.equals(intent.getAction())) {
                return;
            }
            String packageName =
                    intent.getStringExtra(SystemUiSmartCapsuleContract.EXTRA_CHANNEL_PACKAGE);
            int userId = intent.getIntExtra(
                    SystemUiSmartCapsuleContract.EXTRA_CHANNEL_USER_ID,
                    -1);
            if (!active
                    || packageName == null
                    || !packageName.equals(AppRule.normalizePackageName(packageName))
                    || userId != Process.myUid() / ANDROID_UIDS_PER_USER
                    || !channelCatalogAvailable()) {
                return;
            }
            ChannelRequestKey requestKey = new ChannelRequestKey(userId, packageName);
            long requestId = channelRequestIds.incrementAndGet();
            channelRequests.put(requestKey, requestId);
            CHANNEL_EXECUTOR.execute(
                    () -> queryAndReportChannels(requestKey, requestId));
        }
    };

    SystemUiFocusSupportBridge(
            Context runtimeContext,
            long pluginEpoch,
            Class<?> callbackInterface,
            Method callbackOnAuthSuccess,
            Method notificationManagerGetService,
            Method getNotificationChannelsForPackage,
            Method parceledListGetList) {
        Context applicationContext = Objects.requireNonNull(runtimeContext).getApplicationContext();
        this.runtimeContext = applicationContext != null ? applicationContext : runtimeContext;
        if (pluginEpoch <= 0L) throw new IllegalArgumentException("pluginEpoch must be positive");
        this.pluginEpoch = pluginEpoch;
        this.callbackInterface = Objects.requireNonNull(callbackInterface);
        this.callbackOnAuthSuccess = Objects.requireNonNull(callbackOnAuthSuccess);
        this.notificationManagerGetService = notificationManagerGetService;
        this.getNotificationChannelsForPackage = getNotificationChannelsForPackage;
        this.parceledListGetList = parceledListGetList;
    }

    void attachHookHandles(List<HookHandle> handles) {
        if (!hookHandles.isEmpty()) throw new IllegalStateException("Hook handles already attached");
        hookHandles.addAll(Objects.requireNonNull(handles));
    }

    void attachFocusOnlyIslandSuppressor(SystemUiFocusOnlyIslandSuppressor suppressor) {
        if (active || focusOnlyIslandSuppressor != null) {
            throw new IllegalStateException("Focus-only island suppressor already attached");
        }
        focusOnlyIslandSuppressor = Objects.requireNonNull(suppressor);
    }

    static boolean isFocusOnlyIslandSuppressionAvailable() {
        return SystemUiFocusOnlyIslandSuppressor.isAvailable();
    }

    void activate() {
        if (active) return;
        if (channelCatalogAvailable()) {
            IntentFilter controlFilter =
                    new IntentFilter(SystemUiSmartCapsuleContract.ACTION_REQUEST_CHANNELS);
            runtimeContext.registerReceiver(
                    channelRequestReceiver,
                    controlFilter,
                    SystemUiSmartCapsuleContract.RELOAD_SENDER_PERMISSION,
                    null,
                    Context.RECEIVER_EXPORTED);
            receiverRegistered = true;
        }
        active = true;
        if (focusOnlyIslandSuppressor != null) {
            focusOnlyIslandSuppressor.activate();
        }
        SystemUiSmartCapsuleConfigBridge.focusPluginActivated(pluginEpoch);
    }

    void deactivate(String reason) {
        if (!active && hookHandles.isEmpty()) return;
        active = false;
        if (focusOnlyIslandSuppressor != null) {
            focusOnlyIslandSuppressor.deactivate();
        }
        if (receiverRegistered) {
            try {
                runtimeContext.unregisterReceiver(channelRequestReceiver);
            } catch (IllegalArgumentException ignored) {
                // SystemUI may already be tearing down the plugin Context.
            }
            receiverRegistered = false;
        }
        for (HookHandle handle : hookHandles) {
            try {
                handle.unhook();
            } catch (Throwable error) {
                Log.w(TAG, "Could not unhook Focus support", error);
            }
        }
        hookHandles.clear();
        channelRequests.clear();
        SystemUiSmartCapsuleConfigBridge.focusPluginDeactivated(pluginEpoch, reason);
    }

    boolean suppressFocusOnlyIsland(
            Object focusController,
            StatusBarNotification sbn) {
        SystemUiFocusOnlyIslandSuppressor suppressor = focusOnlyIslandSuppressor;
        return suppressor != null && suppressor.suppress(focusController, sbn);
    }

    boolean authorizeModuleOrResident(
            StatusBarNotification sbn,
            String targetPackage,
            Object callback) {
        if (!active
                || sbn == null
                || callback == null
                || !callbackInterface.isInstance(callback)
                || sbn.getUserId() < 0
                || (!MODULE_PACKAGE.equals(sbn.getPackageName())
                        && !SYSTEM_UI_PACKAGE.equals(sbn.getPackageName()))) {
            return false;
        }
        try {
            Notification notification = sbn.getNotification();
            Bundle extras = notification != null ? notification.extras : null;
            String standard = extras != null ? extras.getString("miui.focus.param") : null;
            String custom = extras != null ? extras.getString("miui.focus.param.custom") : null;
            boolean hasFocusPayload = nonBlank(standard) || nonBlank(custom);
            String channelId = notification != null ? notification.getChannelId() : null;

            LauncherApps launcherApps = Objects.requireNonNull(
                    runtimeContext.getSystemService(LauncherApps.class),
                    "LauncherApps service");
            UserHandle user = userHandleForId(sbn.getUserId());
            int moduleUid = launcherApps.getApplicationInfo(MODULE_PACKAGE, 0, user).uid;
            int systemUiUid = launcherApps.getApplicationInfo(SYSTEM_UI_PACKAGE, 0, user).uid;
            if (!ModuleNotificationBoundary.matches(
                    sbn.getPackageName(),
                    targetPackage,
                    sbn.getOpPkg(),
                    sbn.getKey(),
                    channelId,
                    sbn.getId(),
                    sbn.getTag(),
                    sbn.getUid(),
                    sbn.getUserId(),
                    moduleUid,
                    systemUiUid,
                    sbn.getPostTime(),
                    hasFocusPayload)) {
                return false;
            }
            callbackOnAuthSuccess.invoke(callback, sbn.getKey(), targetPackage);
            return true;
        } catch (Throwable error) {
            Log.w(TAG, "Exact module/resident Focus authorization failed closed", error);
            return false;
        }
    }

    private boolean channelCatalogAvailable() {
        return notificationManagerGetService != null
                && getNotificationChannelsForPackage != null
                && parceledListGetList != null;
    }

    private void queryAndReportChannels(ChannelRequestKey request, long requestId) {
        if (!isCurrentChannelRequest(request, requestId)) return;
        String packageName = request.packageName();
        int userId = request.userId();
        try {
            LauncherApps launcherApps = Objects.requireNonNull(
                    runtimeContext.getSystemService(LauncherApps.class),
                    "LauncherApps service");
            int uid = launcherApps.getApplicationInfo(packageName, 0, userHandleForId(userId)).uid;
            Object notificationService = notificationManagerGetService.invoke(null);
            Object slice = getNotificationChannelsForPackage.invoke(
                    notificationService,
                    packageName,
                    uid,
                    false);
            Object rawList = slice != null ? parceledListGetList.invoke(slice) : null;
            List<NotificationChannel> channels = new ArrayList<>();
            if (rawList instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof NotificationChannel channel
                            && ChannelSelection.isValidChannelId(channel.getId())) {
                        channels.add(channel);
                    }
                }
            }
            channels.sort(Comparator.comparing(NotificationChannel::getId));
            if (channels.size() > MAX_CHANNELS) {
                channels = new ArrayList<>(channels.subList(0, MAX_CHANNELS));
            }

            ArrayList<String> ids = new ArrayList<>(channels.size());
            ArrayList<String> names = new ArrayList<>(channels.size());
            int[] importance = new int[channels.size()];
            for (int index = 0; index < channels.size(); index++) {
                NotificationChannel channel = channels.get(index);
                ids.add(channel.getId());
                CharSequence name = channel.getName();
                names.add(boundedChannelName(name != null ? name.toString() : channel.getId()));
                importance[index] = channel.getImportance();
            }

            Bundle report = new Bundle();
            report.putString(SystemUiSmartCapsuleContract.EXTRA_CHANNEL_PACKAGE, packageName);
            report.putInt(SystemUiSmartCapsuleContract.EXTRA_CHANNEL_USER_ID, userId);
            report.putStringArrayList(SystemUiSmartCapsuleContract.EXTRA_CHANNEL_IDS, ids);
            report.putStringArrayList(SystemUiSmartCapsuleContract.EXTRA_CHANNEL_NAMES, names);
            report.putIntArray(SystemUiSmartCapsuleContract.EXTRA_CHANNEL_IMPORTANCE, importance);
            if (isCurrentChannelRequest(request, requestId)) {
                callReportProvider(SystemUiSmartCapsuleContract.METHOD_REPORT_CHANNELS, report);
            }
        } catch (Throwable error) {
            Log.w(TAG, "Optional Channel catalog query failed", error);
        } finally {
            channelRequests.remove(request, requestId);
        }
    }

    private boolean isCurrentChannelRequest(ChannelRequestKey request, long requestId) {
        return active && Long.valueOf(requestId).equals(channelRequests.get(request));
    }

    private void callReportProvider(String method, Bundle extras) {
        if (!ModuleReportDeliveryPolicy.canDeliver(runtimeContext)) return;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                Bundle result = runtimeContext.getContentResolver().call(
                        REPORT_URI,
                        method,
                        null,
                        extras);
                if (result != null
                        && result.getBoolean(
                                SystemUiSmartCapsuleContract.EXTRA_REPORT_ACCEPTED,
                                false)) {
                    return;
                }
            } catch (Throwable error) {
                if (attempt == 1) Log.w(TAG, "Could not deliver " + method, error);
            }
        }
    }

    private static UserHandle userHandleForId(int userId) {
        if (userId < 0) throw new IllegalArgumentException("userId must be non-negative");
        return UserHandle.getUserHandleForUid(Math.multiplyExact(userId, ANDROID_UIDS_PER_USER));
    }

    private static String boundedChannelName(String value) {
        StringBuilder result = new StringBuilder(Math.min(value.length(), MAX_CHANNEL_NAME_CODE_UNITS));
        for (int offset = 0;
                offset < value.length() && result.length() < MAX_CHANNEL_NAME_CODE_UNITS;) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isISOControl(codePoint)) continue;
            int width = Character.charCount(codePoint);
            if (result.length() + width > MAX_CHANNEL_NAME_CODE_UNITS) break;
            result.appendCodePoint(codePoint);
        }
        String normalized = result.toString().trim();
        return normalized.isEmpty() ? "未命名 Channel" : normalized;
    }

    private static String boundedFailure(String value) {
        if (value == null) return "";
        String normalized = value.replaceAll("[^a-z0-9_]", "_");
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private record ChannelRequestKey(int userId, String packageName) {}
}
