package io.github.superisland.hook.systemui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import io.github.libxposed.api.XposedModule;
import io.github.superisland.model.SystemUiSmartCapsuleContract;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Process-level config transport that does not depend on the optional Focus plugin lifecycle. */
final class SystemUiSmartCapsuleConfigBridge {
    private static final String TAG = "SuperIslandConfigBridge";
    private static final Uri REPORT_URI = Uri.parse(
            "content://" + SystemUiSmartCapsuleContract.REPORT_PROVIDER_AUTHORITY);
    private static final Object LOCK = new Object();
    private static final ThreadPoolExecutor RELOAD_EXECUTOR = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1),
            runnable -> {
                Thread thread = new Thread(runnable, "SuperIslandSystemUiConfig");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.DiscardOldestPolicy());

    private static Context runtimeContext;
    private static XposedModule module;
    private static BroadcastReceiver reloadReceiver;
    private static final Set<Long> activePluginEpochs = new HashSet<>();
    private static long latestPluginEpoch;
    private static String pluginInactiveFailure = "focus_plugin_not_active";

    private SystemUiSmartCapsuleConfigBridge() {}

    static void register(Context context, XposedModule xposedModule) {
        synchronized (LOCK) {
            if (reloadReceiver != null) return;
            Context applicationContext = Objects.requireNonNull(context).getApplicationContext();
            Context resolvedContext = applicationContext != null ? applicationContext : context;
            XposedModule resolvedModule = Objects.requireNonNull(xposedModule);
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ignored, Intent intent) {
                    if (SystemUiSmartCapsuleContract.ACTION_RELOAD_CONFIG.equals(intent.getAction())) {
                        reloadAndReport();
                    }
                }
            };
            resolvedContext.registerReceiver(
                    receiver,
                    new IntentFilter(SystemUiSmartCapsuleContract.ACTION_RELOAD_CONFIG),
                    SystemUiSmartCapsuleContract.RELOAD_SENDER_PERMISSION,
                    null,
                    Context.RECEIVER_EXPORTED);
            runtimeContext = resolvedContext;
            module = resolvedModule;
            reloadReceiver = receiver;
        }
        reloadAndReport();
    }

    static void reloadAndReport() {
        RELOAD_EXECUTOR.execute(() -> {
            LocalNotificationRuleAcceptance acceptance =
                    HyperIslandLocalNotificationAdapter.reloadRules();
            Context context;
            XposedModule logger;
            synchronized (LOCK) {
                context = runtimeContext;
                logger = module;
            }
            if (context == null) return;
            if (acceptance != null) {
                Bundle acceptanceReport = new Bundle();
                acceptanceReport.putLong(
                        SystemUiSmartCapsuleContract.EXTRA_CONFIG_REVISION,
                        acceptance.getRevision());
                acceptanceReport.putString(
                        SystemUiSmartCapsuleContract.EXTRA_CONFIG_DIGEST,
                        acceptance.getDigest());
                callReportProvider(
                        context,
                        logger,
                        SystemUiSmartCapsuleContract.METHOD_REPORT_CONFIG_ACCEPTANCE,
                        acceptanceReport,
                        "SystemUI config acceptance");
            }
            reportCurrentRuntime(context, logger);
        });
    }

    static void focusPluginActivated(long pluginEpoch) {
        if (pluginEpoch <= 0L) throw new IllegalArgumentException("pluginEpoch must be positive");
        synchronized (LOCK) {
            activePluginEpochs.add(pluginEpoch);
            latestPluginEpoch = Math.max(latestPluginEpoch, pluginEpoch);
            pluginInactiveFailure = "";
        }
        reloadAndReport();
    }

    static void focusPluginDeactivated(long pluginEpoch, String reason) {
        synchronized (LOCK) {
            activePluginEpochs.remove(pluginEpoch);
            latestPluginEpoch = Math.max(latestPluginEpoch, pluginEpoch);
            if (activePluginEpochs.isEmpty()) {
                pluginInactiveFailure = boundedFailure(reason);
            }
        }
        reportRuntimeAsync();
    }

    private static void reportRuntimeAsync() {
        RELOAD_EXECUTOR.execute(() -> {
            Context context;
            XposedModule logger;
            synchronized (LOCK) {
                context = runtimeContext;
                logger = module;
            }
            if (context != null) reportCurrentRuntime(context, logger);
        });
    }

    private static void reportCurrentRuntime(Context context, XposedModule logger) {
        boolean pluginActive;
        long pluginEpoch;
        String inactiveFailure;
        synchronized (LOCK) {
            pluginActive = !activePluginEpochs.isEmpty();
            pluginEpoch = latestPluginEpoch;
            inactiveFailure = pluginInactiveFailure;
        }
        boolean mapperInstalled = HyperIslandLocalNotificationAdapter.isInstalled();
        boolean capability = pluginActive && mapperInstalled;
        String failure = capability
                ? ""
                : pluginActive
                        ? boundedFailure(HyperIslandLocalNotificationAdapter.getLastFailure())
                        : inactiveFailure;

        Bundle runtimeReport = new Bundle();
        runtimeReport.putBoolean(
                SystemUiSmartCapsuleContract.EXTRA_RUNTIME_CAPABILITY,
                capability);
        runtimeReport.putString(
                SystemUiSmartCapsuleContract.EXTRA_RUNTIME_ADAPTER,
                HyperIslandLocalNotificationAdapter.ADAPTER_NAME);
        runtimeReport.putString(
                SystemUiSmartCapsuleContract.EXTRA_RUNTIME_LAST_FAILURE,
                failure);
        runtimeReport.putInt(
                SystemUiSmartCapsuleContract.EXTRA_RUNTIME_ACTIVE_SESSION_COUNT,
                capability ? HyperIslandLocalNotificationAdapter.getActiveKeyCount() : 0);
        runtimeReport.putLong(
                SystemUiSmartCapsuleContract.EXTRA_RUNTIME_PLUGIN_EPOCH,
                pluginEpoch);
        callReportProvider(
                context,
                logger,
                SystemUiSmartCapsuleContract.METHOD_REPORT_RUNTIME,
                runtimeReport,
                "SystemUI mapper runtime");
    }

    private static void callReportProvider(
            Context context,
            XposedModule logger,
            String method,
            Bundle report,
            String label) {
        if (!ModuleReportDeliveryPolicy.canDeliver(context)) return;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                Bundle result = context.getContentResolver().call(
                        REPORT_URI,
                        method,
                        null,
                        report);
                if (result != null
                        && result.getBoolean(
                                SystemUiSmartCapsuleContract.EXTRA_REPORT_ACCEPTED,
                                false)) {
                    return;
                }
            } catch (Throwable error) {
                if (attempt == 1 && logger != null) {
                    logger.log(Log.WARN, TAG, "Could not report " + label, error);
                }
            }
        }
    }

    private static String boundedFailure(String value) {
        if (value == null || value.isBlank()) return "focus_plugin_not_active";
        String normalized = value.replaceAll("[^a-z0-9_]", "_");
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }
}
