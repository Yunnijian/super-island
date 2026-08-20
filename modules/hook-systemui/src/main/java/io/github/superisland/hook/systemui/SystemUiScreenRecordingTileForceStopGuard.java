package io.github.superisland.hook.systemui;

import android.content.ComponentName;
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
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps SystemUI from automatically reviving the stopped recording tile while allowing an
 * explicit QS click to recover the exact package and continue through the OEM tile lifecycle.
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
    private static final String PACKAGE_MANAGER_ADAPTER_CLASS =
            "com.android.systemui.qs.external.PackageManagerAdapter";
    private static final String I_PACKAGE_MANAGER_CLASS =
            "android.content.pm.IPackageManager";
    private static final String TILE_SERVICES_CLASS =
            "com.android.systemui.qs.external.TileServices";
    private static final String CUSTOM_TILE_CLASS =
            "com.android.systemui.qs.external.CustomTile";
    private static final String CUSTOM_TILE_INTERFACE_CLASS =
            "com.android.systemui.qs.external.CustomTileInterface";
    private static final String EXPANDABLE_CLASS =
            "com.android.systemui.animation.Expandable";
    private static final String DELAYABLE_EXECUTOR_CLASS =
            "com.android.systemui.util.concurrency.DelayableExecutor";
    private static final Integer QUEUED_CLICK_MESSAGE = Integer.valueOf(2);
    private static final long WARSAW_SYSTEM_UI_VERSION_CODE = 202_501_210L;
    private static final long CLICK_RECOVERY_CONNECTION_TIMEOUT_MS = 5_000L;

    private final LauncherApps launcherApps;
    private final Class<?> tileServiceManagerClass;
    private final Class<?> tileLifecycleManagerClass;
    private final Class<?> customTileClass;
    private final Field stateManagerField;
    private final Field tileServiceManagerBindRequestedField;
    private final Field tileServiceManagerBoundField;
    private final Field intentField;
    private final Field userField;
    private final Field packageManagerAdapterField;
    private final Field iPackageManagerField;
    private final Field unbindImmediateField;
    private final Field lifecycleExecutorField;
    private final Field queuedMessagesField;
    private final Field clickBinderField;
    private final Field customTileServiceField;
    private final Field customTileTokenField;
    private final Method getTileWrapperMethod;
    private final Method setBindRequestedMethod;
    private final Method tileServiceManagerUnbindMethod;
    private final Method setBindServiceMethod;
    private final Method onClickMethod;
    private final Method onServiceConnectedMethod;
    private final Method onNullBindingMethod;
    private final Method onBindingDiedMethod;
    private final Method onServiceDisconnectedMethod;
    private final Method executeDelayedMethod;
    private final Method customTileHandleClickMethod;
    private final Method setPackageStoppedStateMethod;
    private final Method userHandleGetIdentifierMethod;
    private final ExplicitClickProvenance explicitClickProvenance =
            new ExplicitClickProvenance();
    private final Map<Object, WeakReference<Object>> lifecycleOwners =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final RecoveryAttemptRegistry recoveryAttempts = new RecoveryAttemptRegistry();

    private SystemUiScreenRecordingTileForceStopGuard(
            LauncherApps launcherApps,
            Class<?> tileServiceManagerClass,
            Class<?> tileLifecycleManagerClass,
            Class<?> customTileClass,
            Field stateManagerField,
            Field tileServiceManagerBindRequestedField,
            Field tileServiceManagerBoundField,
            Field intentField,
            Field userField,
            Field packageManagerAdapterField,
            Field iPackageManagerField,
            Field unbindImmediateField,
            Field lifecycleExecutorField,
            Field queuedMessagesField,
            Field clickBinderField,
            Field customTileServiceField,
            Field customTileTokenField,
            Method getTileWrapperMethod,
            Method setBindRequestedMethod,
            Method tileServiceManagerUnbindMethod,
            Method setBindServiceMethod,
            Method onClickMethod,
            Method onServiceConnectedMethod,
            Method onNullBindingMethod,
            Method onBindingDiedMethod,
            Method onServiceDisconnectedMethod,
            Method executeDelayedMethod,
            Method customTileHandleClickMethod,
            Method setPackageStoppedStateMethod,
            Method userHandleGetIdentifierMethod) {
        this.launcherApps = launcherApps;
        this.tileServiceManagerClass = tileServiceManagerClass;
        this.tileLifecycleManagerClass = tileLifecycleManagerClass;
        this.customTileClass = customTileClass;
        this.stateManagerField = stateManagerField;
        this.tileServiceManagerBindRequestedField = tileServiceManagerBindRequestedField;
        this.tileServiceManagerBoundField = tileServiceManagerBoundField;
        this.intentField = intentField;
        this.userField = userField;
        this.packageManagerAdapterField = packageManagerAdapterField;
        this.iPackageManagerField = iPackageManagerField;
        this.unbindImmediateField = unbindImmediateField;
        this.lifecycleExecutorField = lifecycleExecutorField;
        this.queuedMessagesField = queuedMessagesField;
        this.clickBinderField = clickBinderField;
        this.customTileServiceField = customTileServiceField;
        this.customTileTokenField = customTileTokenField;
        this.getTileWrapperMethod = getTileWrapperMethod;
        this.setBindRequestedMethod = setBindRequestedMethod;
        this.tileServiceManagerUnbindMethod = tileServiceManagerUnbindMethod;
        this.setBindServiceMethod = setBindServiceMethod;
        this.onClickMethod = onClickMethod;
        this.onServiceConnectedMethod = onServiceConnectedMethod;
        this.onNullBindingMethod = onNullBindingMethod;
        this.onBindingDiedMethod = onBindingDiedMethod;
        this.onServiceDisconnectedMethod = onServiceDisconnectedMethod;
        this.executeDelayedMethod = executeDelayedMethod;
        this.customTileHandleClickMethod = customTileHandleClickMethod;
        this.setPackageStoppedStateMethod = setPackageStoppedStateMethod;
        this.userHandleGetIdentifierMethod = userHandleGetIdentifierMethod;
    }

    static SystemUiScreenRecordingTileForceStopGuard resolve(Context context, ClassLoader classLoader)
            throws ReflectiveOperationException {
        Context appContext = context.getApplicationContext() != null
                ? context.getApplicationContext()
                : context;
        if (!SYSTEM_UI_PACKAGE.equals(appContext.getPackageName())) return null;

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

        Field tileServiceManagerBindRequested = null;
        Field packageManagerAdapterField = null;
        Field iPackageManagerField = null;
        Class<?> customTile = null;
        Field customTileService = null;
        Field customTileToken = null;
        Field lifecycleExecutor = null;
        Method customTileHandleClick = null;
        Method onServiceConnected = null;
        Method onNullBinding = null;
        Method onBindingDied = null;
        Method onServiceDisconnected = null;
        Method executeDelayed = null;
        Method setPackageStoppedState = null;
        Method userHandleGetIdentifier = null;
        try {
            Class<?> packageManagerAdapter = Class.forName(
                    PACKAGE_MANAGER_ADAPTER_CLASS,
                    false,
                    classLoader);
            Class<?> iPackageManager = Class.forName(
                    I_PACKAGE_MANAGER_CLASS,
                    false,
                    classLoader);
            customTile = Class.forName(CUSTOM_TILE_CLASS, false, classLoader);
            Class<?> expandable = Class.forName(EXPANDABLE_CLASS, false, classLoader);
            Class<?> delayableExecutor = Class.forName(
                    DELAYABLE_EXECUTOR_CLASS,
                    false,
                    classLoader);
            tileServiceManagerBindRequested = exactField(
                    tileServiceManager,
                    "mBindRequested",
                    Boolean.TYPE);
            packageManagerAdapterField = exactField(
                    tileLifecycleManager,
                    "mPackageManagerAdapter",
                    packageManagerAdapter);
            iPackageManagerField = exactField(
                    packageManagerAdapter,
                    "mIPackageManager",
                    iPackageManager);
            customTileService = exactField(customTile, "mService", tileLifecycleManager);
            customTileToken = exactField(customTile, "mToken", IBinder.class);
            lifecycleExecutor = exactField(
                    tileLifecycleManager,
                    "mExecutor",
                    delayableExecutor);
            customTileHandleClick = exactMethod(
                    customTile,
                    "handleClick",
                    Void.TYPE,
                    expandable);
            onServiceConnected = exactMethod(
                    tileLifecycleManager,
                    "onServiceConnected",
                    Void.TYPE,
                    ComponentName.class,
                    IBinder.class);
            onNullBinding = exactMethod(
                    tileLifecycleManager,
                    "onNullBinding",
                    Void.TYPE,
                    ComponentName.class);
            onBindingDied = exactMethod(
                    tileLifecycleManager,
                    "onBindingDied",
                    Void.TYPE,
                    ComponentName.class);
            onServiceDisconnected = exactMethod(
                    tileLifecycleManager,
                    "onServiceDisconnected",
                    Void.TYPE,
                    ComponentName.class);
            executeDelayed = exactRunnableTokenMethod(
                    delayableExecutor,
                    "executeDelayed",
                    Runnable.class,
                    Long.TYPE);
            setPackageStoppedState = exactMethod(
                    iPackageManager,
                    "setPackageStoppedState",
                    Void.TYPE,
                    String.class,
                    Boolean.TYPE,
                    Integer.TYPE);
            userHandleGetIdentifier = exactMethod(
                    UserHandle.class,
                    "getIdentifier",
                    Integer.TYPE);
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            // The base stopped-package guard remains installed when click recovery drifts.
        }
        return new SystemUiScreenRecordingTileForceStopGuard(
                launcherApps,
                tileServiceManager,
                tileLifecycleManager,
                customTile,
                stateManager,
                tileServiceManagerBindRequested,
                tileServiceManagerBound,
                intent,
                user,
                packageManagerAdapterField,
                iPackageManagerField,
                unbindImmediate,
                lifecycleExecutor,
                queuedMessages,
                clickBinder,
                customTileService,
                customTileToken,
                getTileWrapper,
                setBindRequested,
                tileServiceManagerUnbind,
                setBindService,
                onClick,
                onServiceConnected,
                onNullBinding,
                onBindingDied,
                onServiceDisconnected,
                executeDelayed,
                customTileHandleClick,
                setPackageStoppedState,
                userHandleGetIdentifier);
    }

    static boolean isSupportedBuild(String device, String fingerprint, long systemUiVersionCode) {
        return true;
    }

    static boolean shouldBlock(String packageName, String className, int applicationFlags) {
        return MODULE_PACKAGE.equals(packageName)
                && TILE_SERVICE_CLASS.equals(className)
                && (applicationFlags & ApplicationInfo.FLAG_STOPPED) != 0;
    }

    static boolean canRecoverExplicitClick(
            String componentPackage,
            String componentClass,
            String applicationPackage,
            int applicationFlags,
            boolean applicationEnabled,
            boolean userMatches,
            boolean ownerAvailable,
            boolean explicitClickAuthorized) {
        return shouldBlock(componentPackage, componentClass, applicationFlags)
                && MODULE_PACKAGE.equals(applicationPackage)
                && applicationEnabled
                && (applicationFlags & ApplicationInfo.FLAG_SUSPENDED) == 0
                && userMatches
                && ownerAvailable
                && explicitClickAuthorized;
    }

    static boolean isRecoveredApplication(
            String applicationPackage,
            int applicationFlags,
            boolean applicationEnabled,
            boolean userMatches) {
        return MODULE_PACKAGE.equals(applicationPackage)
                && applicationEnabled
                && (applicationFlags
                        & (ApplicationInfo.FLAG_STOPPED | ApplicationInfo.FLAG_SUSPENDED)) == 0
                && userMatches;
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

    Method onServiceConnectedMethod() {
        return onServiceConnectedMethod;
    }

    Method onNullBindingMethod() {
        return onNullBindingMethod;
    }

    Method onBindingDiedMethod() {
        return onBindingDiedMethod;
    }

    Method onServiceDisconnectedMethod() {
        return onServiceDisconnectedMethod;
    }

    Method customTileHandleClickMethod() {
        return customTileHandleClickMethod;
    }

    /** Opens a one-shot permit only around the exact CustomTile user-click call stack. */
    ExplicitClickPermit beginExplicitClick(Object customTile) {
        if (!recoveryBindingsAvailable() || !customTileClass.isInstance(customTile)) return null;
        try {
            Object lifecycleManager = customTileServiceField.get(customTile);
            Object clickBinder = customTileTokenField.get(customTile);
            if (!tileLifecycleManagerClass.isInstance(lifecycleManager)
                    || !(clickBinder instanceof IBinder)) {
                return null;
            }
            Intent intent = (Intent) intentField.get(lifecycleManager);
            if (!isScreenRecordingTile(intent)) return null;
            return explicitClickProvenance.begin(lifecycleManager, clickBinder);
        } catch (ReflectiveOperationException | RuntimeException error) {
            return null;
        }
    }

    void endExplicitClick(ExplicitClickPermit permit) {
        explicitClickProvenance.end(permit);
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

    /** Prepares only a real click to recover a stopped module through the audited SystemUI API. */
    ClickRecoveryDecision prepareExplicitClickRecovery(
            Object lifecycleManager,
            IBinder clickBinder) {
        boolean screenRecordingTile = false;
        try {
            if (!tileLifecycleManagerClass.isInstance(lifecycleManager)) {
                return ClickRecoveryDecision.PROCEED;
            }
            Intent intent = (Intent) intentField.get(lifecycleManager);
            if (!isScreenRecordingTile(intent)) return ClickRecoveryDecision.PROCEED;
            screenRecordingTile = true;
            boolean explicitClickAuthorized =
                    explicitClickProvenance.consume(lifecycleManager, clickBinder);

            UserHandle user = (UserHandle) userField.get(lifecycleManager);
            if (user == null) return blockClickRecovery(lifecycleManager);
            ApplicationInfo appInfo = launcherApps.getApplicationInfo(MODULE_PACKAGE, 0, user);
            if (!shouldBlock(
                    intent.getComponent().getPackageName(),
                    intent.getComponent().getClassName(),
                    appInfo.flags)) {
                return ClickRecoveryDecision.PROCEED;
            }
            if (!recoveryBindingsAvailable()) {
                return blockClickRecovery(lifecycleManager);
            }

            Object owner = ownerFor(lifecycleManager);
            boolean userMatches = belongsToUser(appInfo, user);
            if (!canRecoverExplicitClick(
                    intent.getComponent().getPackageName(),
                    intent.getComponent().getClassName(),
                    appInfo.packageName,
                    appInfo.flags,
                    appInfo.enabled,
                    userMatches,
                    owner != null,
                    explicitClickAuthorized)) {
                return blockClickRecovery(lifecycleManager);
            }
            if (!resetManagerForClickRecovery(owner, lifecycleManager)) {
                return blockClickRecovery(lifecycleManager);
            }
            int userId = userIdentifier(user);
            if (userId < 0) return blockClickRecovery(lifecycleManager);

            Object packageManagerAdapter = packageManagerAdapterField.get(lifecycleManager);
            Object iPackageManager = iPackageManagerField.get(packageManagerAdapter);
            if (iPackageManager == null) return blockClickRecovery(lifecycleManager);
            setPackageStoppedStateMethod.invoke(
                    iPackageManager,
                    MODULE_PACKAGE,
                    false,
                    userId);

            ApplicationInfo recovered = launcherApps.getApplicationInfo(MODULE_PACKAGE, 0, user);
            if (!isRecoveredApplication(
                    recovered.packageName,
                    recovered.flags,
                    recovered.enabled,
                    belongsToUser(recovered, user))) {
                return blockClickRecovery(lifecycleManager);
            }
            return ClickRecoveryDecision.RECOVERED;
        } catch (PackageManager.NameNotFoundException | ReflectiveOperationException | RuntimeException error) {
            return screenRecordingTile
                    ? blockClickRecovery(lifecycleManager)
                    : ClickRecoveryDecision.PROCEED;
        }
    }

    /** Requests the exact owner bind only after OEM onClick has queued the current click binder. */
    boolean requestBindingAfterRecoveredClick(
            Object lifecycleManager,
            IBinder clickBinder,
            Runnable onBindingEstablished,
            Runnable onBindingFailed) {
        if (!recoveryBindingsAvailable()) return false;
        RecoveryAttempt attempt = null;
        try {
            Object owner = ownerFor(lifecycleManager);
            if (owner == null) return false;
            Object executor = lifecycleExecutorField.get(lifecycleManager);
            if (!(executor instanceof Executor)) return false;
            if (clickBinder == null
                    || clickBinderField.get(lifecycleManager) != clickBinder
                    || !hasPendingClick(lifecycleManager)) {
                return false;
            }
            attempt = recoveryAttempts.begin(
                    lifecycleManager,
                    clickBinder,
                    onBindingEstablished,
                    onBindingFailed);
            if (attempt == null) return false;
            setBindRequestedMethod.invoke(owner, true);
            boolean scheduled = tileServiceManagerBindRequestedField.getBoolean(owner)
                    && tileServiceManagerBoundField.getBoolean(owner);
            if (!scheduled) {
                recoveryAttempts.take(lifecycleManager, attempt);
                return false;
            }
            RecoveryAttempt expectedAttempt = attempt;
            executeDelayedMethod.invoke(
                    executor,
                    (Runnable) () -> failRecoveredBinding(lifecycleManager, expectedAttempt),
                    CLICK_RECOVERY_CONNECTION_TIMEOUT_MS);
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            if (attempt != null) {
                recoveryAttempts.take(lifecycleManager, attempt);
            }
            return false;
        }
    }

    RecoveryConnection beginRecoveredServiceConnection(
            Object lifecycleManager,
            ComponentName componentName) {
        try {
            if (!tileLifecycleManagerClass.isInstance(lifecycleManager)) return null;
            Intent intent = (Intent) intentField.get(lifecycleManager);
            boolean componentMatches = isScreenRecordingTile(intent)
                    && intent.getComponent().equals(componentName);
            return recoveryAttempts.beginConnection(lifecycleManager, componentMatches);
        } catch (ReflectiveOperationException | RuntimeException error) {
            return recoveryAttempts.beginConnection(lifecycleManager, false);
        }
    }

    void noteRecoveredClickDispatch(Object lifecycleManager, IBinder clickBinder) {
        try {
            recoveryAttempts.noteClickDispatch(
                    lifecycleManager,
                    clickBinder,
                    hasPendingClick(lifecycleManager));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Completion remains false and the connection callback fails closed.
        }
    }

    void finishRecoveredServiceConnection(
            RecoveryConnection connection,
            boolean callbackCompleted) {
        if (connection == null) return;
        boolean clickStillQueued = true;
        try {
            clickStillQueued = hasPendingClick(connection.attempt.lifecycleManager);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // A queue read failure cannot prove that this click was consumed.
        }
        RecoveryAttemptResult result = recoveryAttempts.finishConnection(
                connection,
                callbackCompleted,
                clickStillQueued);
        if (result == null) return;
        if (result.successful && clearDeliveredClickBinder(result.attempt)) {
            runBestEffort(result.attempt.onBindingEstablished);
            return;
        }
        clearForceStoppedLifecycle(result.attempt.lifecycleManager);
        runBestEffort(result.attempt.onBindingFailed);
    }

    void failRecoveredBinding(Object lifecycleManager) {
        failRecoveredBinding(lifecycleManager, null);
    }

    void abortRecoveredClick(Object lifecycleManager) {
        recoveryAttempts.take(lifecycleManager, null);
        clearForceStoppedLifecycle(lifecycleManager);
    }

    private void failRecoveredBinding(
            Object lifecycleManager,
            RecoveryAttempt expectedAttempt) {
        RecoveryAttempt attempt = recoveryAttempts.take(lifecycleManager, expectedAttempt);
        if (attempt == null) return;
        clearForceStoppedLifecycle(lifecycleManager);
        runBestEffort(attempt.onBindingFailed);
    }

    private boolean clearDeliveredClickBinder(RecoveryAttempt attempt) {
        try {
            Object lifecycleManager = attempt.lifecycleManager;
            if (hasPendingClick(lifecycleManager)
                    || clickBinderField.get(lifecycleManager) != attempt.clickBinder) {
                return false;
            }
            clickBinderField.set(lifecycleManager, null);
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            return false;
        }
    }

    private static void runBestEffort(Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException ignored) {
            // Logging callbacks must not affect SystemUI lifecycle cleanup.
        }
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

    private boolean resetManagerForClickRecovery(
            Object tileServiceManager,
            Object lifecycleManager) {
        try {
            ((AtomicBoolean) unbindImmediateField.get(lifecycleManager)).set(false);
            discardPendingClick(lifecycleManager);
            setBindRequestedMethod.invoke(tileServiceManager, false);
            if (tileServiceManagerBoundField.getBoolean(tileServiceManager)) {
                tileServiceManagerUnbindMethod.invoke(tileServiceManager);
            }
            return !tileServiceManagerBindRequestedField.getBoolean(tileServiceManager)
                    && !tileServiceManagerBoundField.getBoolean(tileServiceManager)
                    && !hasPendingClick(lifecycleManager)
                    && clickBinderField.get(lifecycleManager) == null;
        } catch (ReflectiveOperationException | RuntimeException error) {
            clearForceStoppedManager(tileServiceManager, lifecycleManager);
            return false;
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

    private static boolean belongsToUser(ApplicationInfo appInfo, UserHandle user) {
        return appInfo != null
                && user != null
                && UserHandle.getUserHandleForUid(appInfo.uid).equals(user);
    }

    private int userIdentifier(UserHandle user) throws ReflectiveOperationException {
        return ((Integer) userHandleGetIdentifierMethod.invoke(user)).intValue();
    }

    private boolean recoveryBindingsAvailable() {
        return customTileClass != null
                && customTileServiceField != null
                && customTileTokenField != null
                && customTileHandleClickMethod != null
                && tileServiceManagerBindRequestedField != null
                && lifecycleExecutorField != null
                && onServiceConnectedMethod != null
                && onNullBindingMethod != null
                && onBindingDiedMethod != null
                && onServiceDisconnectedMethod != null
                && executeDelayedMethod != null
                && packageManagerAdapterField != null
                && iPackageManagerField != null
                && setPackageStoppedStateMethod != null
                && userHandleGetIdentifierMethod != null;
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

    private boolean hasPendingClick(Object lifecycleManager) throws ReflectiveOperationException {
        Set<?> queuedMessages = (Set<?>) queuedMessagesField.get(lifecycleManager);
        if (queuedMessages == null) {
            throw new IllegalStateException("TileLifecycleManager.mQueuedMessages is null");
        }
        synchronized (queuedMessages) {
            return queuedMessages.contains(QUEUED_CLICK_MESSAGE);
        }
    }

    static void discardQueuedClickMessage(Set<?> queuedMessages) {
        queuedMessages.remove(QUEUED_CLICK_MESSAGE);
    }

    static final class RecoveryAttemptRegistry {
        private final Map<Object, RecoveryAttempt> attempts = new IdentityHashMap<>();

        synchronized RecoveryAttempt begin(
                Object lifecycleManager,
                Object clickBinder,
                Runnable onBindingEstablished,
                Runnable onBindingFailed) {
            if (lifecycleManager == null
                    || clickBinder == null
                    || onBindingEstablished == null
                    || onBindingFailed == null
                    || attempts.containsKey(lifecycleManager)) {
                return null;
            }
            RecoveryAttempt attempt = new RecoveryAttempt(
                    lifecycleManager,
                    clickBinder,
                    onBindingEstablished,
                    onBindingFailed);
            attempts.put(lifecycleManager, attempt);
            return attempt;
        }

        synchronized RecoveryConnection beginConnection(
                Object lifecycleManager,
                boolean componentMatches) {
            RecoveryAttempt attempt = attempts.get(lifecycleManager);
            if (attempt == null) return null;
            boolean uniqueMatchingConnection = componentMatches && !attempt.connectionInProgress;
            attempt.connectionInProgress = true;
            attempt.clickDelivered = false;
            return new RecoveryConnection(attempt, uniqueMatchingConnection);
        }

        synchronized void noteClickDispatch(
                Object lifecycleManager,
                Object clickBinder,
                boolean clickStillQueued) {
            RecoveryAttempt attempt = attempts.get(lifecycleManager);
            if (attempt != null
                    && attempt.connectionInProgress
                    && attempt.clickBinder == clickBinder
                    && !clickStillQueued) {
                attempt.clickDelivered = true;
            }
        }

        synchronized RecoveryAttemptResult finishConnection(
                RecoveryConnection connection,
                boolean callbackCompleted,
                boolean clickStillQueued) {
            if (connection == null) return null;
            RecoveryAttempt attempt = attempts.get(connection.attempt.lifecycleManager);
            if (attempt != connection.attempt) return null;
            attempts.remove(attempt.lifecycleManager);
            boolean successful = connection.componentMatches
                    && attempt.connectionInProgress
                    && callbackCompleted
                    && attempt.clickDelivered
                    && !clickStillQueued;
            attempt.connectionInProgress = false;
            return new RecoveryAttemptResult(attempt, successful);
        }

        synchronized RecoveryAttempt take(
                Object lifecycleManager,
                RecoveryAttempt expectedAttempt) {
            RecoveryAttempt attempt = attempts.get(lifecycleManager);
            if (attempt == null || (expectedAttempt != null && attempt != expectedAttempt)) {
                return null;
            }
            attempts.remove(lifecycleManager);
            attempt.connectionInProgress = false;
            return attempt;
        }
    }

    static final class RecoveryAttempt {
        final Object lifecycleManager;
        final Object clickBinder;
        final Runnable onBindingEstablished;
        final Runnable onBindingFailed;
        boolean connectionInProgress;
        boolean clickDelivered;

        private RecoveryAttempt(
                Object lifecycleManager,
                Object clickBinder,
                Runnable onBindingEstablished,
                Runnable onBindingFailed) {
            this.lifecycleManager = lifecycleManager;
            this.clickBinder = clickBinder;
            this.onBindingEstablished = onBindingEstablished;
            this.onBindingFailed = onBindingFailed;
        }
    }

    static final class RecoveryConnection {
        final RecoveryAttempt attempt;
        final boolean componentMatches;

        private RecoveryConnection(
                RecoveryAttempt attempt,
                boolean componentMatches) {
            this.attempt = attempt;
            this.componentMatches = componentMatches;
        }
    }

    static final class RecoveryAttemptResult {
        final RecoveryAttempt attempt;
        final boolean successful;

        private RecoveryAttemptResult(
                RecoveryAttempt attempt,
                boolean successful) {
            this.attempt = attempt;
            this.successful = successful;
        }
    }

    enum ClickRecoveryDecision {
        PROCEED,
        RECOVERED,
        BLOCKED
    }

    static final class ExplicitClickPermit {
        private final Object lifecycleManager;
        private final Object clickBinder;

        private ExplicitClickPermit(Object lifecycleManager, Object clickBinder) {
            this.lifecycleManager = lifecycleManager;
            this.clickBinder = clickBinder;
        }
    }

    static final class ExplicitClickProvenance {
        private final ThreadLocal<ExplicitClickPermit> current = new ThreadLocal<>();

        ExplicitClickPermit begin(Object lifecycleManager, Object clickBinder) {
            if (lifecycleManager == null || clickBinder == null || current.get() != null) {
                return null;
            }
            ExplicitClickPermit permit = new ExplicitClickPermit(lifecycleManager, clickBinder);
            current.set(permit);
            return permit;
        }

        boolean consume(Object lifecycleManager, Object clickBinder) {
            ExplicitClickPermit permit = current.get();
            if (permit == null) return false;
            current.remove();
            return permit.lifecycleManager == lifecycleManager
                    && permit.clickBinder == clickBinder;
        }

        void end(ExplicitClickPermit permit) {
            if (permit != null && current.get() == permit) {
                current.remove();
            }
        }
    }

    private ClickRecoveryDecision blockClickRecovery(Object lifecycleManager) {
        clearForceStoppedLifecycle(lifecycleManager);
        return ClickRecoveryDecision.BLOCKED;
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

    private static Method exactRunnableTokenMethod(
            Class<?> owner,
            String name,
            Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name, parameterTypes);
        if (!Runnable.class.isAssignableFrom(method.getReturnType())) {
            throw new NoSuchMethodException(owner.getName() + "." + name + " return type changed");
        }
        method.setAccessible(true);
        return method;
    }
}
