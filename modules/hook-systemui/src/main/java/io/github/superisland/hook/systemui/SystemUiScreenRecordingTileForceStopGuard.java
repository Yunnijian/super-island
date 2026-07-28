package io.github.superisland.hook.systemui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.os.UserHandle;
import io.github.superisland.model.ScreenRecordingRootControlContract;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps the fixed recording tile from reviving a force-stopped module while warsaw SystemUI
 * recreates its custom-tile managers.
 */
final class SystemUiScreenRecordingTileForceStopGuard {
    static final String MODULE_PACKAGE = "io.github.superisland";
    static final String TILE_SERVICE_CLASS =
            "io.github.superisland.source.screenrecord.ScreenRecordingTileService";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String TILE_SERVICE_MANAGER_CLASS =
            "com.android.systemui.qs.external.TileServiceManager";
    private static final String TILE_LIFECYCLE_MANAGER_CLASS =
            "com.android.systemui.qs.external.TileLifecycleManager";
    private static final String TILE_SERVICES_CLASS =
            "com.android.systemui.qs.external.TileServices";
    private static final String CUSTOM_TILE_INTERFACE_CLASS =
            "com.android.systemui.qs.external.CustomTileInterface";
    private static final Integer QUEUED_CLICK_MESSAGE = Integer.valueOf(2);
    private static final long WARSAW_SYSTEM_UI_VERSION_CODE = 202_501_210L;

    private final LauncherApps launcherApps;
    private final Class<?> tileServiceManagerClass;
    private final Class<?> tileLifecycleManagerClass;
    private final Field stateManagerField;
    private final Field tileServiceManagerBoundField;
    private final Field intentField;
    private final Field userField;
    private final Field unbindImmediateField;
    private final Field queuedMessagesField;
    private final Field clickBinderField;
    private final Method getTileWrapperMethod;
    private final Method setBindRequestedMethod;
    private final Method tileServiceManagerUnbindMethod;
    private final Method setBindServiceMethod;
    private final Method onClickMethod;
    private final Map<Object, WeakReference<Object>> lifecycleOwners =
            Collections.synchronizedMap(new WeakHashMap<>());

    private SystemUiScreenRecordingTileForceStopGuard(
            LauncherApps launcherApps,
            Class<?> tileServiceManagerClass,
            Class<?> tileLifecycleManagerClass,
            Field stateManagerField,
            Field tileServiceManagerBoundField,
            Field intentField,
            Field userField,
            Field unbindImmediateField,
            Field queuedMessagesField,
            Field clickBinderField,
            Method getTileWrapperMethod,
            Method setBindRequestedMethod,
            Method tileServiceManagerUnbindMethod,
            Method setBindServiceMethod,
            Method onClickMethod) {
        this.launcherApps = launcherApps;
        this.tileServiceManagerClass = tileServiceManagerClass;
        this.tileLifecycleManagerClass = tileLifecycleManagerClass;
        this.stateManagerField = stateManagerField;
        this.tileServiceManagerBoundField = tileServiceManagerBoundField;
        this.intentField = intentField;
        this.userField = userField;
        this.unbindImmediateField = unbindImmediateField;
        this.queuedMessagesField = queuedMessagesField;
        this.clickBinderField = clickBinderField;
        this.getTileWrapperMethod = getTileWrapperMethod;
        this.setBindRequestedMethod = setBindRequestedMethod;
        this.tileServiceManagerUnbindMethod = tileServiceManagerUnbindMethod;
        this.setBindServiceMethod = setBindServiceMethod;
        this.onClickMethod = onClickMethod;
    }

    static SystemUiScreenRecordingTileForceStopGuard resolve(Context context, ClassLoader classLoader)
            throws ReflectiveOperationException {
        Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        if (!SYSTEM_UI_PACKAGE.equals(appContext.getPackageName())) return null;
        long versionCode;
        try {
            versionCode = appContext.getPackageManager()
                    .getPackageInfo(SYSTEM_UI_PACKAGE, 0)
                    .getLongVersionCode();
        } catch (PackageManager.NameNotFoundException missing) {
            return null;
        }
        if (!isSupportedBuild(Build.DEVICE, Build.FINGERPRINT, versionCode)) return null;

        LauncherApps launcherApps = appContext.getSystemService(LauncherApps.class);
        if (launcherApps == null) {
            throw new IllegalStateException("SystemUI LauncherApps is unavailable");
        }
        Class<?> tileServiceManager = Class.forName(
                TILE_SERVICE_MANAGER_CLASS,
                false,
                classLoader);
        Class<?> tileLifecycleManager = Class.forName(
                TILE_LIFECYCLE_MANAGER_CLASS,
                false,
                classLoader);
        Class<?> tileServices = Class.forName(TILE_SERVICES_CLASS, false, classLoader);
        Class<?> customTileInterface = Class.forName(
                CUSTOM_TILE_INTERFACE_CLASS,
                false,
                classLoader);
        Field stateManager = exactField(
                tileServiceManager,
                "mStateManager",
                tileLifecycleManager);
        Field tileServiceManagerBound = exactField(tileServiceManager, "mBound", Boolean.TYPE);
        Field intent = exactField(tileLifecycleManager, "mIntent", Intent.class);
        Field user = exactField(tileLifecycleManager, "mUser", UserHandle.class);
        Field unbindImmediate = exactField(
                tileLifecycleManager,
                "mUnbindImmediate",
                AtomicBoolean.class);
        Field queuedMessages = exactField(tileLifecycleManager, "mQueuedMessages", Set.class);
        Field clickBinder = exactField(tileLifecycleManager, "mClickBinder", IBinder.class);
        Method getTileWrapper = exactMethod(
                tileServices,
                "getTileWrapper",
                tileServiceManager,
                customTileInterface);
        Method setBindRequested = exactMethod(
                tileServiceManager,
                "setBindRequested",
                Void.TYPE,
                Boolean.TYPE);
        Method tileServiceManagerUnbind = exactMethod(
                tileServiceManager,
                "unbindService",
                Void.TYPE);
        Method setBindService = exactMethod(
                tileLifecycleManager,
                "setBindService",
                Void.TYPE,
                Boolean.TYPE);
        Method onClick = exactMethod(tileLifecycleManager, "onClick", Void.TYPE, IBinder.class);
        return new SystemUiScreenRecordingTileForceStopGuard(
                launcherApps,
                tileServiceManager,
                tileLifecycleManager,
                stateManager,
                tileServiceManagerBound,
                intent,
                user,
                unbindImmediate,
                queuedMessages,
                clickBinder,
                getTileWrapper,
                setBindRequested,
                tileServiceManagerUnbind,
                setBindService,
                onClick);
    }

    static boolean isSupportedBuild(String device, String fingerprint, long systemUiVersionCode) {
        return ScreenRecordingRootControlContract.INSTANCE.isVerifiedDevice(device, fingerprint)
                && systemUiVersionCode == WARSAW_SYSTEM_UI_VERSION_CODE;
    }

    static boolean shouldBlock(String packageName, String className, int applicationFlags) {
        return MODULE_PACKAGE.equals(packageName)
                && TILE_SERVICE_CLASS.equals(className)
                && (applicationFlags & ApplicationInfo.FLAG_STOPPED) != 0;
    }

    Method setBindRequestedMethod() {
        return setBindRequestedMethod;
    }

    Method getTileWrapperMethod() {
        return getTileWrapperMethod;
    }

    Method setBindServiceMethod() {
        return setBindServiceMethod;
    }

    Method onClickMethod() {
        return onClickMethod;
    }

    /** Records the exact lifecycle owner before TileServices posts its initial active-tile bind. */
    void rememberTileServiceManager(Object tileServiceManager) {
        try {
            if (!tileServiceManagerClass.isInstance(tileServiceManager)) return;
            Object lifecycleManager = stateManagerField.get(tileServiceManager);
            if (!tileLifecycleManagerClass.isInstance(lifecycleManager)) return;
            Intent intent = (Intent) intentField.get(lifecycleManager);
            if (!isScreenRecordingTile(intent)) return;
            synchronized (lifecycleOwners) {
                lifecycleOwners.put(lifecycleManager, new WeakReference<>(tileServiceManager));
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // An incomplete reverse association must leave SystemUI's normal behavior unchanged.
        }
    }

    /** Blocks the request before TileServiceManager can record a stale pending bind. */
    boolean blockForceStoppedBindingRequest(Object tileServiceManager) {
        Object lifecycleManager;
        try {
            if (!tileServiceManagerClass.isInstance(tileServiceManager)) return false;
            lifecycleManager = stateManagerField.get(tileServiceManager);
            if (!isForceStoppedLifecycle(lifecycleManager)) return false;
        } catch (PackageManager.NameNotFoundException | ReflectiveOperationException | RuntimeException error) {
            return false;
        }
        // Clearing stale state is best effort, but a verified stopped package must never bind.
        clearForceStoppedManager(tileServiceManager, lifecycleManager);
        return true;
    }

    /** Prevents a stopped-package click from being queued and replayed after a later bind. */
    boolean blockForceStoppedClick(Object lifecycleManager) {
        try {
            if (!isForceStoppedLifecycle(lifecycleManager)) return false;
        } catch (PackageManager.NameNotFoundException | ReflectiveOperationException | RuntimeException error) {
            return false;
        }
        discardPendingClickBestEffort(lifecycleManager);
        return true;
    }

    /**
     * Blocks a direct OEM lifecycle rebind and prepares the manager for an OEM {@code false}
     * transition.
     *
     * <p>The death-rebind runnable invokes {@code setBindService(true)} directly, bypassing
     * {@code TileServiceManager#setBindRequested}. The owner association is only an optimization
     * for clearing manager state: losing it must not turn a confirmed stopped package into an
     * allowed bind.</p>
     */
    boolean blockForceStoppedLifecycleBind(Object lifecycleManager) {
        try {
            if (!isForceStoppedLifecycle(lifecycleManager)) return false;
        } catch (PackageManager.NameNotFoundException | ReflectiveOperationException | RuntimeException error) {
            return false;
        }
        clearForceStoppedLifecycle(lifecycleManager);
        return true;
    }

    private void clearForceStoppedLifecycle(Object lifecycleManager) {
        resetUnbindImmediateBestEffort(lifecycleManager);
        discardPendingClickBestEffort(lifecycleManager);
        try {
            Object tileServiceManager = ownerFor(lifecycleManager);
            if (tileServiceManager != null) {
                clearForceStoppedManager(tileServiceManager, lifecycleManager);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // The following original setBindService(false) still tears down lifecycle state.
        }
    }

    private void clearForceStoppedManager(Object tileServiceManager, Object lifecycleManager) {
        resetUnbindImmediateBestEffort(lifecycleManager);
        discardPendingClickBestEffort(lifecycleManager);
        try {
            // This call re-enters the hook with false and therefore executes the audited OEM
            // method, clearing mBindRequested before a future user-launched retry.
            setBindRequestedMethod.invoke(tileServiceManager, false);
            if (tileServiceManagerBoundField.getBoolean(tileServiceManager)) {
                tileServiceManagerUnbindMethod.invoke(tileServiceManager);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Manager cleanup is optional; the caller still blocks the stopped-package bind.
        }
    }

    private void resetUnbindImmediateBestEffort(Object lifecycleManager) {
        try {
            ((AtomicBoolean) unbindImmediateField.get(lifecycleManager)).set(false);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Exact signatures were checked at installation; retain the independent bind block.
        }
    }

    private void discardPendingClickBestEffort(Object lifecycleManager) {
        try {
            discardPendingClick(lifecycleManager);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // A queue cleanup failure must not allow a stopped-package click to be replayed.
        }
    }

    private boolean isForceStoppedLifecycle(Object lifecycleManager)
            throws ReflectiveOperationException, PackageManager.NameNotFoundException {
        if (!tileLifecycleManagerClass.isInstance(lifecycleManager)) return false;
        Intent intent = (Intent) intentField.get(lifecycleManager);
        UserHandle user = (UserHandle) userField.get(lifecycleManager);
        if (!isScreenRecordingTile(intent) || user == null) return false;
        ApplicationInfo appInfo = launcherApps.getApplicationInfo(MODULE_PACKAGE, 0, user);
        return shouldBlock(
                intent.getComponent().getPackageName(),
                intent.getComponent().getClassName(),
                appInfo.flags);
    }

    private Object ownerFor(Object lifecycleManager) throws ReflectiveOperationException {
        synchronized (lifecycleOwners) {
            WeakReference<Object> reference = lifecycleOwners.get(lifecycleManager);
            Object tileServiceManager = reference == null ? null : reference.get();
            if (tileServiceManager == null) {
                lifecycleOwners.remove(lifecycleManager);
                return null;
            }
            if (!tileServiceManagerClass.isInstance(tileServiceManager)
                    || stateManagerField.get(tileServiceManager) != lifecycleManager) {
                lifecycleOwners.remove(lifecycleManager);
                return null;
            }
            return tileServiceManager;
        }
    }

    private void discardPendingClick(Object lifecycleManager) throws ReflectiveOperationException {
        Set<?> queuedMessages = (Set<?>) queuedMessagesField.get(lifecycleManager);
        if (queuedMessages == null) {
            throw new IllegalStateException("TileLifecycleManager.mQueuedMessages is null");
        }
        synchronized (queuedMessages) {
            discardQueuedClickMessage(queuedMessages);
        }
        clickBinderField.set(lifecycleManager, null);
    }

    static void discardQueuedClickMessage(Set<?> queuedMessages) {
        queuedMessages.remove(QUEUED_CLICK_MESSAGE);
    }

    private static boolean isScreenRecordingTile(Intent intent) {
        return intent != null
                && intent.getComponent() != null
                && MODULE_PACKAGE.equals(intent.getComponent().getPackageName())
                && TILE_SERVICE_CLASS.equals(intent.getComponent().getClassName());
    }

    private static Field exactField(Class<?> owner, String name, Class<?> expectedType)
            throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        if (field.getType() != expectedType) {
            throw new NoSuchFieldException(owner.getName() + "." + name + " type changed");
        }
        field.setAccessible(true);
        return field;
    }

    private static Method exactMethod(
            Class<?> owner,
            String name,
            Class<?> returnType,
            Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name, parameterTypes);
        if (method.getReturnType() != returnType) {
            throw new NoSuchMethodException(owner.getName() + "." + name + " return type changed");
        }
        method.setAccessible(true);
        return method;
    }
}
