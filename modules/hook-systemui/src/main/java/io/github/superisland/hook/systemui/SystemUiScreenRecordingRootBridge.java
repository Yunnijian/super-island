package io.github.superisland.hook.systemui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import io.github.superisland.model.ScreenRecordingRootControlContract;
import io.github.superisland.model.SystemUiSmartCapsuleContract;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fixed SystemUI-side settings bridge for the two Root-only recording controls.
 *
 * <p>This contains no shell, app_process, Shizuku, arbitrary setting name, or arbitrary Binder
 * dispatch. Requests come only from the module's signed app, are exact-fingerprint gated, and
 * report a bounded result through the existing STATUS_BAR_SERVICE-protected provider.
 */
final class SystemUiScreenRecordingRootBridge {
    private static final String TAG = "SuperIslandScreenRecordRoot";
    private static final String SHOW_TOUCHES = "show_touches";
    private static final String SCREEN_SHARE_PROTECTION = "screen_share_protection";
    private static final String SCREEN_SHARE_PROTECTION_ON = "screen_share_protection_on";
    private static final String OPSTR_PROJECT_MEDIA = "android:project_media";
    /** Historical AppOps code for PROJECT_MEDIA; used only when strOpToOp is unavailable. */
    private static final int OP_PROJECT_MEDIA = 46;
    private static final int APP_OPS_MODE_ALLOWED = 0;
    private static final int APP_OPS_MODE_IGNORED = 1;
    private static final int INVALID_SETTING = -1;
    private static final List<String> SCREEN_SHARE_BLACKLIST = List.of(
            "NotificationShade",
            "StatusBar",
            "InputMethod",
            "com.miui.securitycenter/com.miui.permcenter.capsule.ScreenShareProtectionActivity"
    );
    private static final ExecutorService CONTROL_EXECUTOR = Executors.newSingleThreadExecutor(
            runnable -> {
                Thread thread = new Thread(runnable, "SuperIslandScreenRecordingRoot");
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                return thread;
            }
    );

    private static boolean registered;

    private SystemUiScreenRecordingRootBridge() {}

    static synchronized void register(Context context) {
        if (registered) return;
        Context normalized = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        IntentFilter filter = new IntentFilter(ScreenRecordingRootControlContract.ACTION_REQUEST);
        normalized.registerReceiver(
                REQUEST_RECEIVER,
                filter,
                ScreenRecordingRootControlContract.SENDER_PERMISSION,
                null,
                Context.RECEIVER_EXPORTED
        );
        registered = true;
    }

    private static final BroadcastReceiver REQUEST_RECEIVER = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ScreenRecordingRootControlContract.ACTION_REQUEST.equals(intent.getAction())) return;
            // Framework already enforced SENDER_PERMISSION on this dynamically registered
            // receiver. HyperOS may leave getSentFromUid() at INVALID_UID for non-ordered
            // broadcasts; fall back to package identity, then permission-gated delivery.
            if (!isAuthorizedModuleSender(context, this)) {
                Log.w(TAG, "Rejected screen-recording control sender");
                return;
            }
            String requestId = intent.getStringExtra(ScreenRecordingRootControlContract.EXTRA_REQUEST_ID);
            String operation = intent.getStringExtra(ScreenRecordingRootControlContract.EXTRA_OPERATION);
            if (!ScreenRecordingRootControlContract.INSTANCE.isValidRequestId(requestId)
                    || !isKnownOperation(operation)) {
                Log.w(TAG, "Rejected malformed screen-recording control request");
                return;
            }
            Log.i(TAG, "Accepted screen-recording control op=" + operation);
            BroadcastReceiver.PendingResult pendingResult = goAsync();
            CONTROL_EXECUTOR.execute(() -> {
                try {
                    RootControlResult result = handleRequest(context, intent, operation);
                    reportResult(context, requestId, result);
                } catch (Throwable error) {
                    Log.w(TAG, "Screen-recording control failed", error);
                    reportResult(context, requestId,
                            RootControlResult.failure("SystemUI 设置桥接失败", RootState.empty()));
                } finally {
                    pendingResult.finish();
                }
            });
        }
    };

    /**
     * Authorizes the module app as the only sender.
     *
     * <p>Order: sent-from package (API 34+) → sent-from UID package list → accept when the
     * receiver was permission-gated with the module signature permission (framework already
     * dropped unprivileged senders).
     */
    private static boolean isAuthorizedModuleSender(Context context, BroadcastReceiver receiver) {
        try {
            String sentFromPackage = receiver.getSentFromPackage();
            if (sentFromPackage != null) {
                return ScreenRecordingRootControlContract.MODULE_PACKAGE.equals(sentFromPackage);
            }
        } catch (Throwable ignored) {
            // Older stubs or ROM drift: continue with UID / permission-gate fallbacks.
        }
        int senderUid = -1;
        try {
            senderUid = receiver.getSentFromUid();
        } catch (Throwable ignored) {
            senderUid = -1;
        }
        if (senderUid >= 0) {
            if (!UserHandle.getUserHandleForUid(senderUid).equals(Process.myUserHandle())) {
                return false;
            }
            String[] packages = context.getPackageManager().getPackagesForUid(senderUid);
            if (packages == null) return false;
            for (String packageName : packages) {
                if (ScreenRecordingRootControlContract.MODULE_PACKAGE.equals(packageName)) {
                    return true;
                }
            }
            return false;
        }
        Log.i(TAG, "Sender UID unavailable; accepting under SENDER_PERMISSION receiver gate");
        return true;
    }

    private static boolean isKnownOperation(String operation) {
        return ScreenRecordingRootControlContract.OPERATION_PREPARE.equals(operation)
                || ScreenRecordingRootControlContract.OPERATION_RESTORE.equals(operation)
                || ScreenRecordingRootControlContract.OPERATION_SET_PROJECT_MEDIA.equals(operation);
    }

    private static RootControlResult handleRequest(
            Context context,
            Intent intent,
            String operation
    ) {
        if (!ScreenRecordingRootControlContract.INSTANCE.isVerifiedDevice(
                Build.DEVICE, Build.FINGERPRINT)) {
            return RootControlResult.failure("当前 ROM 未通过录屏 Root 设置验证", RootState.empty());
        }
        if (ScreenRecordingRootControlContract.OPERATION_PREPARE.equals(operation)) {
            return prepare(
                    context,
                    intent.getBooleanExtra(ScreenRecordingRootControlContract.EXTRA_SHOW_TOUCHES, false),
                    intent.getBooleanExtra(
                            ScreenRecordingRootControlContract.EXTRA_BYPASS_PROTECTION,
                            false)
            );
        }
        if (ScreenRecordingRootControlContract.OPERATION_SET_PROJECT_MEDIA.equals(operation)) {
            return setProjectMedia(
                    context,
                    intent.getBooleanExtra(
                            ScreenRecordingRootControlContract.EXTRA_PROJECT_MEDIA_ALLOWED,
                            false)
            );
        }
        return restore(
                context,
                intent.getBooleanExtra(ScreenRecordingRootControlContract.EXTRA_SHOW_TOUCHES, false),
                intent.getBooleanExtra(
                        ScreenRecordingRootControlContract.EXTRA_BYPASS_PROTECTION,
                        false),
                originalStateFrom(intent)
        );
    }

    /**
     * Grants or revokes {@code android:project_media} for the module package only. Mirrors the
     * closed IslandRecorder Root path: fixed op, fixed package/UID, warsaw fingerprint gate.
     */
    private static RootControlResult setProjectMedia(Context context, boolean allowed) {
        try {
            android.content.pm.ApplicationInfo info =
                    context.getPackageManager()
                            .getApplicationInfo(
                                    ScreenRecordingRootControlContract.MODULE_PACKAGE, 0);
            require(
                    ScreenRecordingRootControlContract.MODULE_PACKAGE.equals(info.packageName),
                    "投影媒体权限仅允许模块自身");
            setProjectMediaMode(info.uid, ScreenRecordingRootControlContract.MODULE_PACKAGE, allowed);
            boolean actual = isProjectMediaModeAllowed(
                    info.uid, ScreenRecordingRootControlContract.MODULE_PACKAGE);
            require(actual == allowed, "投影媒体权限读回失败");
            return RootControlResult.success(RootState.empty());
        } catch (Throwable error) {
            Log.w(TAG, "setProjectMedia failed", error);
            return RootControlResult.failure("无法写入投影媒体权限", RootState.empty());
        }
    }

    private static void setProjectMediaMode(int uid, String packageName, boolean allowed)
            throws Exception {
        Object appOps = appOpsService();
        int op = projectMediaOpCode();
        int mode = allowed ? APP_OPS_MODE_ALLOWED : APP_OPS_MODE_IGNORED;
        Method setMode = appOps.getClass().getMethod(
                "setMode", int.class, int.class, String.class, int.class);
        setMode.invoke(appOps, op, uid, packageName, mode);
    }

    private static boolean isProjectMediaModeAllowed(int uid, String packageName) throws Exception {
        Object appOps = appOpsService();
        int op = projectMediaOpCode();
        Method checkOperation = appOps.getClass().getMethod(
                "checkOperation", int.class, int.class, String.class);
        Object result = checkOperation.invoke(appOps, op, uid, packageName);
        return result instanceof Integer && ((Integer) result) == APP_OPS_MODE_ALLOWED;
    }

    private static Object appOpsService() throws Exception {
        Class<?> serviceManager = Class.forName("android.os.ServiceManager");
        Method getService = serviceManager.getDeclaredMethod("getService", String.class);
        Object binder = getService.invoke(null, "appops");
        require(binder != null, "AppOps 服务不可用");
        Class<?> stub = Class.forName("com.android.internal.app.IAppOpsService$Stub");
        Method asInterface = stub.getDeclaredMethod("asInterface", android.os.IBinder.class);
        Object service = asInterface.invoke(null, binder);
        require(service != null, "AppOps 接口不可用");
        return service;
    }

    private static int projectMediaOpCode() throws Exception {
        Class<?> appOpsManager = Class.forName("android.app.AppOpsManager");
        try {
            Method strOpToOp = appOpsManager.getDeclaredMethod("strOpToOp", String.class);
            Object code = strOpToOp.invoke(null, OPSTR_PROJECT_MEDIA);
            if (code instanceof Integer) return (Integer) code;
        } catch (Throwable ignored) {
            // Fall through to the fixed warsaw op code.
        }
        return OP_PROJECT_MEDIA;
    }

    private static RootControlResult prepare(
            Context context,
            boolean showTouches,
            boolean bypassScreenShareProtection
    ) {
        RootState original = readState(context);
        try {
            if (showTouches && !original.showTouches) {
                putSystemBoolean(context, SHOW_TOUCHES, true);
                require(readState(context).showTouches, "点按反馈读回失败");
            }
            if (bypassScreenShareProtection) {
                require(original.protection == original.protectionOn,
                        "小米屏幕共享保护状态不一致");
                if (original.protection) {
                    setScreenShareProtection(context, false);
                }
            }
            return RootControlResult.success(original);
        } catch (Throwable error) {
            restoreBestEffort(context, original, showTouches, bypassScreenShareProtection);
            return RootControlResult.failure("无法应用录屏 Root 设置", original);
        }
    }

    private static RootControlResult restore(
            Context context,
            boolean restoreShowTouches,
            boolean restoreScreenShareProtection,
            RootState original
    ) {
        try {
            if (restoreShowTouches) {
                putSystemBoolean(context, SHOW_TOUCHES, original.showTouches);
                require(readState(context).showTouches == original.showTouches, "点按反馈恢复读回失败");
            }
            if (restoreScreenShareProtection) {
                require(original.protection == original.protectionOn,
                        "小米屏幕共享保护原始状态不一致");
                setScreenShareProtection(context, original.protection);
            }
            return RootControlResult.success(original);
        } catch (Throwable error) {
            return RootControlResult.failure("无法恢复录屏 Root 设置", original);
        }
    }

    private static void restoreBestEffort(
            Context context,
            RootState original,
            boolean restoreShowTouches,
            boolean restoreScreenShareProtection
    ) {
        try {
            if (restoreShowTouches) putSystemBoolean(context, SHOW_TOUCHES, original.showTouches);
            if (restoreScreenShareProtection && original.protection == original.protectionOn) {
                setScreenShareProtection(context, original.protection);
            }
        } catch (Throwable restoreError) {
            Log.e(TAG, "Could not restore recording settings after failed prepare", restoreError);
        }
    }

    private static RootState readState(Context context) {
        int showTouches = Settings.System.getInt(
                context.getContentResolver(), SHOW_TOUCHES, INVALID_SETTING);
        int protection = Settings.Secure.getInt(
                context.getContentResolver(), SCREEN_SHARE_PROTECTION, INVALID_SETTING);
        int protectionOn = Settings.Secure.getInt(
                context.getContentResolver(), SCREEN_SHARE_PROTECTION_ON, INVALID_SETTING);
        requireBinary(showTouches, SHOW_TOUCHES);
        requireBinary(protection, SCREEN_SHARE_PROTECTION);
        requireBinary(protectionOn, SCREEN_SHARE_PROTECTION_ON);
        return new RootState(showTouches == 1, protection == 1, protectionOn == 1);
    }

    private static RootState originalStateFrom(Intent intent) {
        String[] required = {
                ScreenRecordingRootControlContract.EXTRA_ORIGINAL_SHOW_TOUCHES,
                ScreenRecordingRootControlContract.EXTRA_ORIGINAL_PROTECTION,
                ScreenRecordingRootControlContract.EXTRA_ORIGINAL_PROTECTION_ON,
        };
        for (String key : required) {
            require(intent.hasExtra(key), "缺少 Root 设置恢复状态");
        }
        return new RootState(
                intent.getBooleanExtra(
                        ScreenRecordingRootControlContract.EXTRA_ORIGINAL_SHOW_TOUCHES, false),
                intent.getBooleanExtra(
                        ScreenRecordingRootControlContract.EXTRA_ORIGINAL_PROTECTION, false),
                intent.getBooleanExtra(
                        ScreenRecordingRootControlContract.EXTRA_ORIGINAL_PROTECTION_ON, false)
        );
    }

    private static void putSystemBoolean(Context context, String key, boolean value) {
        require(Settings.System.putInt(context.getContentResolver(), key, value ? 1 : 0),
                "System 设置写入失败");
    }

    private static void setScreenShareProtection(Context context, boolean enabled) {
        int target = enabled ? 1 : 0;
        require(Settings.Secure.putInt(
                context.getContentResolver(), SCREEN_SHARE_PROTECTION, target),
                "屏幕共享保护设置写入失败");
        require(Settings.Secure.putInt(
                context.getContentResolver(), SCREEN_SHARE_PROTECTION_ON, target),
                "屏幕共享保护运行时设置写入失败");
        require(setScreenShareProjectBlacklist(enabled), "屏幕共享保护 WindowManager 更新失败");
        RootState actual = readState(context);
        require(actual.protection == enabled && actual.protectionOn == enabled,
                "屏幕共享保护读回失败");
    }

    /** Exact ROM method name verified in the task audit; any drift fails closed. */
    private static boolean setScreenShareProjectBlacklist(boolean enabled) {
        try {
            Class<?> serviceManager = Class.forName("android.os.ServiceManager");
            Method getService = serviceManager.getDeclaredMethod("getService", String.class);
            Object windowBinder = getService.invoke(null, Context.WINDOW_SERVICE);
            if (windowBinder == null) return false;
            Class<?> windowManagerStub = Class.forName("android.view.IWindowManager$Stub");
            Method asInterface = windowManagerStub.getDeclaredMethod(
                    "asInterface", android.os.IBinder.class);
            Object windowManager = asInterface.invoke(null, windowBinder);
            if (windowManager == null) return false;
            Method setter = windowManager.getClass().getMethod(
                    "setScreenShareProjectBlackList", List.class);
            setter.setAccessible(true);
            setter.invoke(windowManager, enabled ? new ArrayList<>(SCREEN_SHARE_BLACKLIST) : null);
            return true;
        } catch (Throwable error) {
            Log.w(TAG, "Exact WindowManager screen-share method unavailable", error);
            return false;
        }
    }

    private static void reportResult(Context context, String requestId, RootControlResult result) {
        if (!ModuleReportDeliveryPolicy.canDeliver(context)) return;
        try {
            Bundle extras = new Bundle();
            extras.putString(ScreenRecordingRootControlContract.EXTRA_REQUEST_ID, requestId);
            extras.putBoolean(ScreenRecordingRootControlContract.EXTRA_RESULT_SUCCESS, result.success);
            extras.putString(ScreenRecordingRootControlContract.EXTRA_RESULT_REASON, result.reason);
            extras.putBoolean(
                    ScreenRecordingRootControlContract.EXTRA_ORIGINAL_SHOW_TOUCHES,
                    result.original.showTouches);
            extras.putBoolean(
                    ScreenRecordingRootControlContract.EXTRA_ORIGINAL_PROTECTION,
                    result.original.protection);
            extras.putBoolean(
                    ScreenRecordingRootControlContract.EXTRA_ORIGINAL_PROTECTION_ON,
                    result.original.protectionOn);
            context.getContentResolver().call(
                    Uri.parse("content://" + SystemUiSmartCapsuleContract.REPORT_PROVIDER_AUTHORITY),
                    ScreenRecordingRootControlContract.METHOD_REPORT_RESULT,
                    null,
                    extras
            );
        } catch (Throwable error) {
            Log.w(TAG, "Could not report screen-recording control result", error);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }

    private static void requireBinary(int value, String name) {
        require(value == 0 || value == 1, "无效系统设置：" + name);
    }

    private static final class RootState {
        final boolean showTouches;
        final boolean protection;
        final boolean protectionOn;

        RootState(boolean showTouches, boolean protection, boolean protectionOn) {
            this.showTouches = showTouches;
            this.protection = protection;
            this.protectionOn = protectionOn;
        }

        static RootState empty() {
            return new RootState(false, false, false);
        }
    }

    private static final class RootControlResult {
        final boolean success;
        final String reason;
        final RootState original;

        private RootControlResult(boolean success, String reason, RootState original) {
            this.success = success;
            this.reason = reason;
            this.original = original;
        }

        static RootControlResult success(RootState original) {
            return new RootControlResult(true, "", original);
        }

        static RootControlResult failure(String reason, RootState original) {
            return new RootControlResult(false, reason, original);
        }
    }
}
