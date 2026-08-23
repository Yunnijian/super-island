package io.github.superisland.hook.systemui;

import android.app.Application;
import android.app.BroadcastOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.IBinder;
import android.os.Looper;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.ViewGroup;
import io.github.libxposed.api.XposedInterface.HookHandle;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import io.github.superisland.model.MiShareFolderRedirectContract;
import io.github.superisland.model.SystemUiSmartCapsuleContract;
import io.github.superisland.model.SmartCapsuleRemoteSnapshot;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal libxposed API 102 entry for Super Island.
 *
 * <p>Third-party smart capsules are mapped in place before SystemUI snapshots the source SBN.
 * Module-owned and resident-host Focus notifications keep their separate exact identity checks.
 */
public final class SuperIslandXposedModule extends XposedModule {
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String XMSF_PACKAGE = "com.xiaomi.xmsf";
    private static final String MISHARE_PACKAGE = MiShareFolderRedirectContract.MISHARE_PACKAGE;
    private static final String MODULE_PACKAGE = "io.github.superisland";
    private static final String XMSF_AUTH_SERVICE_ACTION = "com.xiaomi.xms.auth.BIND_AUTH_SERVICE";
    private static final String XMSF_AUTH_PROCESS_METADATA = "auth_process_name";
    private static final String TAG = "SuperIslandXposed";
    private static final String PLUGIN_INSTANCE_CLASS = "com.android.systemui.shared.plugins.PluginInstance";
    private static final String FOCUS_UTILS_CLASS = "miui.systemui.notification.focus.FocusNotifUtils";
    private static final String FOCUS_CONTROLLER_CLASS =
            "miui.systemui.notification.focus.FocusNotificationController";
    private static final String FOCUS_CALLBACK_CLASS =
            "miui.systemui.notification.focus.InflateAndAuthCallBack";
    private static final String FOCUS_CONTENT_CLASS =
            "com.android.systemui.plugins.miui.notification.FocusNotificationContent";
    private static final String FOCUS_PLUGIN_CLASS =
            "miui.systemui.notification.FocusNotificationPluginImpl";
    private static final String DYNAMIC_ISLAND_CONTENT_VIEW_CLASS =
            "miui.systemui.dynamicisland.window.content.DynamicIslandContentView";
    private static final String DYNAMIC_ISLAND_CONTENT_FAKE_VIEW_CLASS =
            "miui.systemui.dynamicisland.window.content.DynamicIslandContentFakeView";
    private static final String ISLAND_TEMPLATE_BUILDER_CLASS =
            "miui.systemui.dynamicisland.template.IslandTemplateBuilder";
    private static final String ISLAND_MODULE_ADAPTER_CLASS =
            "miui.systemui.dynamicisland.module.IslandModuleViewHolderAdapter";
    /** One posted native render per root per main-loop turn. */
    private static final Object NATIVE_UPDATE_LOCK = new Object();
    private static final Map<ViewGroup, Runnable> PENDING_NATIVE_UPDATES = new WeakHashMap<>();
    private static final Map<ViewGroup, Object> PENDING_NATIVE_DATA = new WeakHashMap<>();

    /**
     * HyperOS loads the focus plugin with a separate ClassLoader. A hook installed only in the
     * SystemUI default loader silently misses the plugin's actual auth gate.
     */
    private final Map<ClassLoader, List<HookHandle>> installedFocusLoaderHooks =
            new WeakHashMap<>();
    private final Map<ClassLoader, SystemUiFocusSupportBridge> focusSupportBridges =
            new WeakHashMap<>();
    private final Map<Object, SystemUiFocusSupportBridge> focusPluginInstances =
            new WeakHashMap<>();
    /**
     * Dynamic Island may be loaded by the SystemUI loader or by the MIUI plugin loader. Keep one
     * bounded hook set per loader so a plugin reload cannot stack duplicate lyric render passes.
     */
    private final Map<ClassLoader, List<HookHandle>> installedLyricNativeHooks =
            new WeakHashMap<>();
    private final Map<Object, ClassLoader> lyricNativePluginLoaders =
            new WeakHashMap<>();
    private volatile ClassLoader systemUiLyricNativeLoader;
    private final AtomicLong pluginEpochs = new AtomicLong();
    private volatile HookHandle xmsfFocusAuthHook;
    private volatile HookHandle xmsfRuntimeAttachHook;
    private volatile HookHandle miShareRuntimeAttachHook;
    private volatile List<HookHandle> screenRecordingTileForceStopHooks;
    private volatile BroadcastReceiver xmsfReloadReceiver;
    private volatile Context xmsfRuntimeContext;
    private volatile XmsfSmartCapsuleSelection xmsfSelection;

    public SuperIslandXposedModule() {
        // Required public zero-argument constructor for API 102 entry instantiation.
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (MISHARE_PACKAGE.equals(param.getPackageName())) {
            try {
                installMiShareRuntimeAttachHook(param.getDefaultClassLoader());
            } catch (Throwable error) {
                // The extension is optional. Any ROM/MT drift leaves Mi Share's OEM behavior intact.
                log(Log.WARN, TAG, "Mi Share folder redirect unavailable", error);
            }
            return;
        }
        if (XMSF_PACKAGE.equals(param.getPackageName())) {
            try {
                installXmsfRuntimeAttachHook(param.getDefaultClassLoader());
            } catch (Throwable error) {
                // Fail closed: a changed or ambiguous XMSF contract keeps OEM auth intact.
                log(Log.ERROR, TAG, "XMSF Focus authorization adapter unavailable", error);
            }
            return;
        }
        if (!SYSTEM_UI_PACKAGE.equals(param.getPackageName())) {
            return;
        }
        try {
            ClassLoader defaultClassLoader = param.getDefaultClassLoader();
            SharedPreferences preferences =
                    getRemotePreferences(SystemUiSmartCapsuleContract.REMOTE_PREFERENCES);
            LocalNotificationAdapterInstallResult adapter =
                    HyperIslandLocalNotificationAdapter.install(
                            this,
                            defaultClassLoader,
                            preferences);
            if (!adapter.getInstalled()) {
                log(Log.ERROR, TAG, "SystemUI notification mapper unavailable: "
                        + adapter.getFailure());
            }
            installResidentIslandHost(defaultClassLoader);
            try {
                installLyricNativeRendererHook(defaultClassLoader, "SystemUI default loader");
                systemUiLyricNativeLoader = defaultClassLoader;
            } catch (Throwable error) {
                // Dynamic Island is an optional ROM surface. A missing/drifted class leaves the
                // audited Focus fallback untouched and must not disable the other SystemUI hooks.
                log(Log.WARN, TAG, "Native lyric Dynamic Island hook unavailable", error);
            }
            installPluginLoadHook(defaultClassLoader);
            installFocusHooksIfPresent(defaultClassLoader, "SystemUI default loader");
            log(Log.INFO, TAG, "Installed scoped HyperOS focus-notification bridge");
        } catch (Throwable error) {
            // Fail closed: no focus payload is authorized when the exact SystemUI contract drifts.
            log(Log.ERROR, TAG, "Focus-notification bridge unavailable; leaving SystemUI unchanged", error);
        }
    }

    /** Waits for a real process Context so the public PackageManager API can provide versionCode. */
    private synchronized void installMiShareRuntimeAttachHook(ClassLoader classLoader)
            throws ReflectiveOperationException {
        if (miShareRuntimeAttachHook != null) return;
        Class<?> application = Class.forName("android.app.Application", false, classLoader);
        Method attach = application.getDeclaredMethod("attach", Context.class);
        deoptimize(attach);
        miShareRuntimeAttachHook = hook(attach)
                .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object original = chain.proceed();
                    Context context = (Context) chain.getArg(0);
                    if (context == null || !MISHARE_PACKAGE.equals(context.getPackageName())) {
                        return original;
                    }
                    try {
                        long versionCode = context.getPackageManager()
                                .getPackageInfo(MISHARE_PACKAGE, 0)
                                .getLongVersionCode();
                        MiShareFolderRedirectInstallResult result =
                                MiShareFolderRedirectHook.install(
                                        this,
                                        classLoader,
                                        context.getApplicationInfo(),
                                        versionCode,
                                        getRemotePreferences(
                                                MiShareFolderRedirectContract.REMOTE_PREFERENCES));
                        if (!result.getInstalled()) {
                            log(Log.WARN, TAG,
                                    "Mi Share folder redirect unavailable: " + result.getFailure());
                        }
                    } catch (Throwable error) {
                        log(Log.WARN, TAG, "Mi Share folder redirect unavailable", error);
                    }
                    return original;
                });
    }

    /**
     * Adapts only the two Xiaomi Focus scopes at AuthSession's error dispatch boundary. Unlike
     * broad whitelist hooks, this leaves every other XMS authorization request untouched.
     */
    private synchronized void installXmsfFocusAuthorizationAdapter(ClassLoader classLoader)
            throws ReflectiveOperationException {
        if (xmsfFocusAuthHook != null) return;

        XmsfFocusAuthContract.Resolved contract = XmsfFocusAuthContract.resolve(classLoader);
        SharedPreferences preferences =
                getRemotePreferences(SystemUiSmartCapsuleContract.REMOTE_PREFERENCES);
        XmsfSmartCapsuleSelection selection = new XmsfSmartCapsuleSelection(preferences);
        try {
            Method errorMethod = contract.errorMethod();
            deoptimize(errorMethod);
            HookHandle handle = hook(errorMethod)
                    .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object error = chain.getArg(0);
                        if (error == null || !contract.isAuthError(error)) {
                            return chain.proceed();
                        }
                        boolean eligible;
                        try {
                            Object session = chain.getThisObject();
                            eligible = selection.matches(contract.focusRequestOrNull(session));
                        } catch (Throwable contractError) {
                            log(Log.WARN, TAG,
                                    "XMSF Focus request validation failed closed", contractError);
                            return chain.proceed();
                        }
                        if (!eligible) return chain.proceed();
                        try {
                            return contract.invokeSuccess(chain.getThisObject());
                        } catch (Throwable successError) {
                            log(Log.WARN, TAG,
                                    "XMSF Focus success dispatch failed closed", successError);
                            return chain.proceed();
                        }
                    });
            xmsfSelection = selection;
            xmsfFocusAuthHook = handle;
            selection.setAcceptanceReporter(this::reportXmsfAcceptance);
        } catch (Throwable error) {
            selection.close();
            if (error instanceof ReflectiveOperationException) {
                throw (ReflectiveOperationException) error;
            }
            throw new ReflectiveOperationException("Could not install XMSF Focus hook", error);
        }
        log(Log.INFO, TAG, "Installed scoped XMSF Focus authorization adapter");
    }

    /** Defers XMSF setup until its Context can identify the process that owns AuthService. */
    private synchronized void installXmsfRuntimeAttachHook(ClassLoader classLoader)
            throws ReflectiveOperationException {
        if (xmsfRuntimeAttachHook != null) return;
        Class<?> application = Class.forName("android.app.Application", false, classLoader);
        Method attach = application.getDeclaredMethod("attach", Context.class);
        deoptimize(attach);
        xmsfRuntimeAttachHook = hook(attach)
                .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Object context = chain.getArg(0);
                    if (context instanceof Context) {
                        Context runtimeContext = (Context) context;
                        try {
                            if (isXmsfAuthProcess(runtimeContext)) {
                                installXmsfFocusAuthorizationAdapter(classLoader);
                                registerXmsfConfigReloadReceiver(runtimeContext);
                            }
                        } catch (Throwable error) {
                            log(Log.ERROR, TAG,
                                    "XMSF auth-process adapter unavailable; leaving OEM auth unchanged",
                                    error);
                        }
                    }
                    return result;
                });
    }

    private boolean isXmsfAuthProcess(Context context) {
        String expectedProcess = resolveXmsfAuthProcessName(context);
        String currentProcess = Application.getProcessName();
        boolean matches = expectedProcess != null && expectedProcess.equals(currentProcess);
        if (!matches) {
            log(Log.INFO, TAG, "Skipping non-auth XMSF process=" + currentProcess
                    + " expected=" + expectedProcess);
        }
        return matches;
    }

    private String resolveXmsfAuthProcessName(Context context) {
        PackageManager packageManager = context.getPackageManager();
        try {
            ResolveInfo service = packageManager.resolveService(
                    new Intent(XMSF_AUTH_SERVICE_ACTION).setPackage(XMSF_PACKAGE),
                    PackageManager.MATCH_ALL);
            if (service != null && service.serviceInfo != null) {
                String processName = normalizeXmsfProcessName(service.serviceInfo.processName);
                if (processName != null) return processName;
            }
        } catch (RuntimeException ignored) {
            // Fall through to the split manifest metadata published by XMSF itself.
        }
        try {
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(
                    XMSF_PACKAGE,
                    PackageManager.GET_META_DATA);
            String configured = applicationInfo.metaData != null
                    ? applicationInfo.metaData.getString(XMSF_AUTH_PROCESS_METADATA)
                    : null;
            return normalizeXmsfProcessName(configured);
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        }
    }

    private String normalizeXmsfProcessName(String processName) {
        if (processName == null || processName.isBlank()) return null;
        if (processName.startsWith(":")) return XMSF_PACKAGE + processName;
        if (XMSF_PACKAGE.equals(processName) || processName.startsWith(XMSF_PACKAGE + ":")) {
            return processName;
        }
        return null;
    }

    private synchronized void registerXmsfConfigReloadReceiver(Context context) {
        if (xmsfReloadReceiver != null) return;
        Context applicationContext = context.getApplicationContext();
        Context runtimeContext = applicationContext != null ? applicationContext : context;
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ignored, Intent intent) {
                if (!SystemUiSmartCapsuleContract.ACTION_RELOAD_CONFIG.equals(intent.getAction())) {
                    return;
                }
                reloadXmsfSelectionAsync();
            }
        };
        runtimeContext.registerReceiver(
                receiver,
                new IntentFilter(SystemUiSmartCapsuleContract.ACTION_RELOAD_CONFIG),
                SystemUiSmartCapsuleContract.RELOAD_SENDER_PERMISSION,
                null,
                Context.RECEIVER_EXPORTED);
        xmsfRuntimeContext = runtimeContext;
        xmsfReloadReceiver = receiver;
        log(Log.INFO, TAG, "Registered XMSF smart-capsule config receiver");
        // Re-read after registration so an update during Application.attach cannot be lost.
        reloadXmsfSelectionAsync();
    }

    private void reloadXmsfSelectionAsync() {
        XmsfSmartCapsuleSelection selection = xmsfSelection;
        if (selection == null) return;
        selection.reloadAsync();
    }

    private void reportXmsfAcceptance(SmartCapsuleRemoteSnapshot snapshot) {
        if (snapshot == null || xmsfFocusAuthHook == null || xmsfSelection == null) return;
        log(Log.INFO, TAG, "Reloaded XMSF rules revision="
                + snapshot.getRevision()
                + " enabled=" + snapshot.getEnabled()
                + " applications=" + snapshot.getApplications().size());
        Context context = xmsfRuntimeContext;
        if (context == null) return;
        if (!ModuleReportDeliveryPolicy.canDeliver(context)) return;
        try {
            Intent report = new Intent(SystemUiSmartCapsuleContract.ACTION_REPORT_XMSF_ACCEPTANCE)
                    .setPackage(MODULE_PACKAGE)
                    .putExtra(
                            SystemUiSmartCapsuleContract.EXTRA_CONFIG_REVISION,
                            snapshot.getRevision())
                    .putExtra(
                            SystemUiSmartCapsuleContract.EXTRA_CONFIG_DIGEST,
                            snapshot.getDigest());
            BroadcastOptions options = BroadcastOptions.makeBasic()
                    .setShareIdentityEnabled(true);
            context.sendBroadcast(report, null, options.toBundle());
        } catch (RuntimeException error) {
            log(Log.WARN, TAG, "Could not report XMSF smart-capsule acceptance", error);
        }
    }

    /**
     * The persistent island must be owned by SystemUI rather than by the app's foreground service.
     * Register the protected receiver from Application.attach(Context), rather than an individual
     * SystemUI Application.onCreate override. HyperOS has changed that override path across builds,
     * but every Application reaches attach after its ContextImpl is available.
     */
    private void installResidentIslandHost(ClassLoader classLoader) throws ReflectiveOperationException {
        Class<?> application = Class.forName("android.app.Application", false, classLoader);
        Method attach = application.getDeclaredMethod("attach", android.content.Context.class);
        deoptimize(attach);
        hook(attach)
                .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Object context = chain.getArg(0);
                    if (chain.getThisObject() instanceof android.app.Application && context instanceof android.content.Context) {
                        try {
                            SystemUiSmartCapsuleConfigBridge.register(
                                    (android.content.Context) context,
                                    SuperIslandXposedModule.this
                            );
                            log(Log.INFO, TAG, "Registered SystemUI smart-capsule config bridge");
                        } catch (Throwable error) {
                            log(Log.ERROR, TAG, "Could not register SystemUI config bridge", error);
                        }
                        try {
                            SystemUiResidentIslandHost.register(
                                    (android.content.Context) context,
                                    SuperIslandXposedModule.this
                            );
                            log(Log.INFO, TAG, "Registered SystemUI resident-island host");
                        } catch (Throwable error) {
                            log(Log.ERROR, TAG, "Could not register resident-island host", error);
                        }
                        try {
                            io.github.superisland.hook.systemui.lyric.LyricIslandSystemUiHost.register(
                                    (android.content.Context) context,
                                    name -> getRemotePreferences(name)
                            );
                            log(Log.INFO, TAG, "Registered SystemUI lyric-island host");
                        } catch (Throwable error) {
                            log(Log.ERROR, TAG, "Could not register lyric-island host", error);
                        }
                        try {
                            SystemUiScreenRecordingRootBridge.register(
                                    (android.content.Context) context
                            );
                            log(Log.INFO, TAG, "Registered SystemUI screen-recording settings bridge");
                        } catch (Throwable error) {
                            // The screen recorder is optional. A capability mismatch must leave
                            // SystemUI's existing settings and notification behavior untouched.
                            log(Log.WARN, TAG,
                                    "Screen-recording Root settings bridge unavailable", error);
                        }
                        try {
                            installScreenRecordingTileForceStopGuard(
                                    (android.content.Context) context,
                                    classLoader
                            );
                        } catch (Throwable error) {
                            // Exact ROM signatures are required. Any drift leaves QS unchanged.
                            log(Log.WARN, TAG,
                                    "Screen-recording QS stopped-package guard unavailable", error);
                        }
                    }
                    return result;
                });
    }

    private synchronized void installScreenRecordingTileForceStopGuard(
            Context context,
            ClassLoader classLoader) throws ReflectiveOperationException {
        if (screenRecordingTileForceStopHooks != null) return;
        SystemUiScreenRecordingTileForceStopGuard guard =
                SystemUiScreenRecordingTileForceStopGuard.resolve(context, classLoader);
        if (guard == null) return;
        List<HookHandle> handles = new ArrayList<>();
        try {
            Method getTileWrapper = guard.getTileWrapperMethod();
            deoptimize(getTileWrapper);
            handles.add(hook(getTileWrapper)
                    .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object tileServiceManager = chain.proceed();
                        guard.rememberTileServiceManager(tileServiceManager);
                        return tileServiceManager;
                    }));
            Method setBindRequested = guard.setBindRequestedMethod();
            deoptimize(setBindRequested);
            handles.add(hook(setBindRequested)
                    .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object requested = chain.getArg(0);
                        if (requested instanceof Boolean
                                && (Boolean) requested
                                && guard.blockForceStoppedBindingRequest(chain.getThisObject())) {
                            return null;
                        }
                        return chain.proceed();
                    }));
            Method setBindService = guard.setBindServiceMethod();
            deoptimize(setBindService);
            handles.add(hook(setBindService)
                    .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object requested = chain.getArg(0);
                        if (requested instanceof Boolean
                                && (Boolean) requested
                                && guard.blockForceStoppedLifecycleBind(
                                        chain.getThisObject())) {
                            return chain.proceed(new Object[]{false});
                        }
                        return chain.proceed();
                    }));
            Method customTileHandleClick = guard.customTileHandleClickMethod();
            if (customTileHandleClick != null) {
                deoptimize(customTileHandleClick);
                handles.add(hook(customTileHandleClick)
                        .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            SystemUiScreenRecordingTileForceStopGuard.ExplicitClickPermit permit =
                                    guard.beginExplicitClick(chain.getThisObject());
                            try {
                                return chain.proceed();
                            } finally {
                                guard.endExplicitClick(permit);
                            }
                        }));
            }
            Method onServiceConnected = guard.onServiceConnectedMethod();
            if (onServiceConnected != null) {
                deoptimize(onServiceConnected);
                handles.add(hook(onServiceConnected)
                        .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object componentArg = chain.getArg(0);
                            ComponentName componentName = componentArg instanceof ComponentName
                                    ? (ComponentName) componentArg
                                    : null;
                            SystemUiScreenRecordingTileForceStopGuard.RecoveryConnection connection =
                                    guard.beginRecoveredServiceConnection(
                                            chain.getThisObject(),
                                            componentName);
                            boolean callbackCompleted = false;
                            try {
                                Object result = chain.proceed();
                                callbackCompleted = true;
                                return result;
                            } finally {
                                guard.finishRecoveredServiceConnection(
                                        connection,
                                        callbackCompleted);
                            }
                        }));
            }
            Method[] failedConnectionCallbacks = new Method[]{
                    guard.onNullBindingMethod(),
                    guard.onBindingDiedMethod(),
                    guard.onServiceDisconnectedMethod(),
            };
            for (Method failedConnectionCallback : failedConnectionCallbacks) {
                if (failedConnectionCallback == null) continue;
                deoptimize(failedConnectionCallback);
                handles.add(hook(failedConnectionCallback)
                        .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            try {
                                return chain.proceed();
                            } finally {
                                guard.failRecoveredBinding(chain.getThisObject());
                            }
                        }));
            }
            Method onClick = guard.onClickMethod();
            deoptimize(onClick);
            handles.add(hook(onClick)
                    .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object clickArg = chain.getArg(0);
                        IBinder clickBinder = clickArg instanceof IBinder ? (IBinder) clickArg : null;
                        SystemUiScreenRecordingTileForceStopGuard.ClickRecoveryDecision decision =
                                guard.prepareExplicitClickRecovery(
                                        chain.getThisObject(),
                                        clickBinder);
                        if (decision
                                == SystemUiScreenRecordingTileForceStopGuard.ClickRecoveryDecision.BLOCKED) {
                            log(Log.WARN, TAG,
                                    "Blocked stopped screen-recording tile click recovery");
                            return null;
                        }
                        Object result;
                        try {
                            // Queue the current binder before binding so a fast service connection
                            // cannot observe an empty OEM click queue.
                            result = chain.proceed();
                        } catch (Throwable error) {
                            if (decision
                                    == SystemUiScreenRecordingTileForceStopGuard.ClickRecoveryDecision.RECOVERED) {
                                guard.abortRecoveredClick(chain.getThisObject());
                            }
                            throw error;
                        }
                        guard.noteRecoveredClickDispatch(
                                chain.getThisObject(),
                                clickBinder);
                        if (decision
                                == SystemUiScreenRecordingTileForceStopGuard.ClickRecoveryDecision.RECOVERED) {
                            if (!guard.requestBindingAfterRecoveredClick(
                                    chain.getThisObject(),
                                    clickBinder,
                                    () -> log(Log.INFO, TAG,
                                            "Recovered stopped screen-recording tile after explicit QS click"),
                                    () -> log(Log.WARN, TAG,
                                            "Recovered screen-recording tile service bind failed"))) {
                                guard.abortRecoveredClick(chain.getThisObject());
                                log(Log.WARN, TAG,
                                        "Could not bind recovered screen-recording tile click");
                            }
                        }
                        return result;
                    }));
            screenRecordingTileForceStopHooks = List.copyOf(handles);
        } catch (Throwable error) {
            unhookAll(handles, "screen-recording QS stopped-package guard installation");
            throw error;
        }
        log(Log.INFO, TAG, "Installed screen-recording QS stopped-package guard");
    }

    /**
     * Installs the narrow Dynamic Island content hook used by HyperLyric's native-player path.
     *
     * <p>The renderer is deliberately called only after the OEM has rebuilt its island layout.
     * The first argument of {@code updateBigIslandView} is treated as opaque and inspected only
     * for the audited media package/pending-intent extras. Unknown signatures or layouts fail
     * closed, leaving the normal Focus notification path available.
     */
    private synchronized void installLyricNativeRendererHook(
            ClassLoader classLoader,
            String loaderLabel) throws ReflectiveOperationException {
        if (classLoader == null) {
            throw new ReflectiveOperationException("Dynamic Island class loader is null");
        }
        synchronized (installedLyricNativeHooks) {
            if (installedLyricNativeHooks.containsKey(classLoader)) return;
        }

        Class<?> contentViewClass = Class.forName(
                DYNAMIC_ISLAND_CONTENT_VIEW_CLASS,
                false,
                classLoader);
        List<Method> updateMethods = new ArrayList<>();
        List<Method> visibilityMethods = new ArrayList<>();
        List<Method> widthMethods = new ArrayList<>();
        for (Method method : contentViewClass.getMethods()) {
            String name = method.getName();
            if ("updateBigIslandView".equals(name)
                    && isSupportedDynamicIslandUpdateSignature(method)) {
                addUniqueMethod(updateMethods, method);
            } else if (("calculateBigIslandWidth".equals(name)
                    || "updateBigIslandViewWidth".equals(name))
                    && method.getParameterTypes().length == 0) {
                addUniqueMethod(widthMethods, method);
            } else if (("hideIslandLayout".equals(name) || "showIslandLayout".equals(name))
                    && method.getParameterTypes().length == 0) {
                addUniqueMethod(visibilityMethods, method);
            }
        }
        if (updateMethods.isEmpty()) {
            throw new ReflectiveOperationException(
                    "DynamicIslandContentView.updateBigIslandView is unavailable");
        }

        List<HookHandle> handles = new ArrayList<>();
        try {
            for (Method method : updateMethods) {
                deoptimize(method);
                handles.add(hook(method)
                        .setExceptionMode(
                                io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            try {
                                Object data = method.getParameterTypes().length == 0
                                        ? null
                                        : chain.getArg(0);
                                dispatchNativeIslandUpdate(chain.getThisObject(), data);
                            } catch (Throwable error) {
                                // Renderer failures must never alter the OEM update result.
                                log(Log.WARN, TAG,
                                        "Native lyric render after update failed", error);
                            }
                            return result;
                        }));
            }
            for (Method method : widthMethods) {
                deoptimize(method);
                handles.add(hook(method)
                        .setExceptionMode(
                                io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            // Width is calculated from the current children. Inject once before
                            // OEM measurement. The normal updateBigIslandView callback performs
                            // the post-rebuild reconciliation; dispatching both sides here made
                            // shade expansion enqueue two full SystemUI renders per width pass.
                            try {
                                ViewGroup root = chain.getThisObject() instanceof ViewGroup
                                        ? (ViewGroup) chain.getThisObject() : null;
                                Object data = currentIslandData(root);
                                if (data != null) dispatchNativeIslandUpdate(root, data);
                            } catch (Throwable error) {
                                log(Log.WARN, TAG,
                                        "Native lyric pre-width render failed", error);
                            }
                            Object result = chain.proceed();
                            return result;
                        }));
            }
            for (Method method : visibilityMethods) {
                deoptimize(method);
                boolean show = "showIslandLayout".equals(method.getName());
                handles.add(hook(method)
                        .setExceptionMode(
                                io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            try {
                                Object receiver = chain.getThisObject();
                                if (!(receiver instanceof ViewGroup)) return result;
                                ViewGroup root = (ViewGroup) receiver;
                                if (show) {
                                    Object data = currentIslandData(root);
                                    if (data != null) dispatchNativeIslandUpdate(root, data);
                                } else {
                                    io.github.superisland.hook.systemui.lyric
                                            .LyricIslandSystemUiHost.clearNative(root);
                                }
                            } catch (Throwable error) {
                                log(Log.WARN, TAG,
                                        "Native lyric render after visibility change failed", error);
                            }
                            return result;
                        }));
            }
            installLyricModuleRestoreHooks(classLoader, handles);
            installLyricFakeTransitionHooks(classLoader, handles);
        } catch (Throwable error) {
            unhookAll(handles, "native lyric hook installation");
            if (error instanceof ReflectiveOperationException) {
                throw (ReflectiveOperationException) error;
            }
            throw new ReflectiveOperationException(
                    "Could not install native lyric Dynamic Island hooks", error);
        }

        synchronized (installedLyricNativeHooks) {
            if (installedLyricNativeHooks.containsKey(classLoader)) {
                unhookAll(handles, "duplicate native lyric hook installation");
                return;
            }
            installedLyricNativeHooks.put(classLoader, List.copyOf(handles));
        }
        log(Log.INFO, TAG, "Installed native lyric Dynamic Island hooks: " + loaderLabel
                + " updateMethods=" + updateMethods.size()
                + " widthMethods=" + widthMethods.size()
                + " visibilityMethods=" + visibilityMethods.size());
    }

    /** Re-applies the renderer after OEM module holders are rebound during an island update. */
    private void installLyricModuleRestoreHooks(
            ClassLoader classLoader,
            List<HookHandle> destination) {
        List<HookHandle> optional = new ArrayList<>();
        try {
            Class<?> builder = Class.forName(ISLAND_TEMPLATE_BUILDER_CLASS, false, classLoader);
            List<Method> methods = new ArrayList<>();
            for (Method method : builder.getMethods()) {
                if ("updateModuleView".equals(method.getName())
                        && method.getParameterTypes().length == 3) {
                    addUniqueMethod(methods, method);
                }
            }
            for (Method method : methods) {
                method.setAccessible(true);
                deoptimize(method);
                optional.add(hook(method)
                        .setExceptionMode(
                                io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            dispatchModuleRestore(
                                    chain.getThisObject(),
                                    chain.getArg(0),
                                    chain.getArg(2),
                                    true);
                            return result;
                        }));
            }
            if (!methods.isEmpty()) {
                log(Log.INFO, TAG, "Installed native lyric template-builder hooks=" + methods.size());
            }
        } catch (ClassNotFoundException ignored) {
            // This ROM keeps module rebuilding in the adapter; absence is an expected variant.
        } catch (Throwable error) {
            unhookAll(optional, "optional lyric template-builder hook failure");
            log(Log.WARN, TAG, "Native lyric template-builder hook unavailable", error);
            optional.clear();
        }
        destination.addAll(optional);

        optional = new ArrayList<>();
        try {
            Class<?> adapter = Class.forName(ISLAND_MODULE_ADAPTER_CLASS, false, classLoader);
            List<Method> methods = new ArrayList<>();
            for (Method method : adapter.getMethods()) {
                if ("updateView".equals(method.getName())
                        && method.getParameterTypes().length == 3) {
                    addUniqueMethod(methods, method);
                }
            }
            for (Method method : methods) {
                method.setAccessible(true);
                deoptimize(method);
                optional.add(hook(method)
                        .setExceptionMode(
                                io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            dispatchModuleRestore(
                                    chain.getThisObject(),
                                    chain.getArg(0),
                                    chain.getArg(2),
                                    false);
                            return result;
                        }));
            }
            if (!methods.isEmpty()) {
                log(Log.INFO, TAG, "Installed native lyric module-adapter hooks=" + methods.size());
            }
        } catch (ClassNotFoundException ignored) {
            // Optional on older Dynamic Island builds.
        } catch (Throwable error) {
            unhookAll(optional, "optional lyric module-adapter hook failure");
            log(Log.WARN, TAG, "Native lyric module-adapter hook unavailable", error);
            optional.clear();
        }
        destination.addAll(optional);
    }

    /** Keeps the copied lyric canvas in sync with Dynamic Island's fake transition surface. */
    private void installLyricFakeTransitionHooks(
            ClassLoader classLoader,
            List<HookHandle> destination) {
        List<HookHandle> optional = new ArrayList<>();
        try {
            Class<?> fake = Class.forName(DYNAMIC_ISLAND_CONTENT_FAKE_VIEW_CLASS, false, classLoader);
            List<Method> lifecycle = new ArrayList<>();
            for (Method method : fake.getDeclaredMethods()) {
                if (("onTrackingFakeViewStart".equals(method.getName())
                        || "updateViewStateWhenOpenAnimStart".equals(method.getName()))
                        && method.getParameterTypes().length == 0) {
                    addUniqueMethod(lifecycle, method);
                }
            }
            for (Method method : lifecycle) {
                method.setAccessible(true);
                deoptimize(method);
                optional.add(hook(method)
                        .setExceptionMode(
                                io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            Object receiver = chain.getThisObject();
                            if (receiver instanceof ViewGroup) {
                                ViewGroup fakeRoot = (ViewGroup) receiver;
                                Object data = currentIslandData(fakeRoot);
                                io.github.superisland.hook.systemui.lyric.LyricIslandSystemUiHost
                                        .scheduleRenderAndFreezeNative(fakeRoot, data);
                            }
                            return result;
                        }));
            }
            for (Method method : fake.getDeclaredMethods()) {
                if (!"setVisibility".equals(method.getName())
                        || method.getParameterTypes().length != 1
                        || method.getParameterTypes()[0] != Integer.TYPE
                        || method.getDeclaringClass() != fake) {
                    continue;
                }
                method.setAccessible(true);
                deoptimize(method);
                optional.add(hook(method)
                        .setExceptionMode(
                                io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            int visibility = ((Number) chain.getArg(0)).intValue();
                            // Visibility is called repeatedly for every shade-animation frame on
                            // some HyperOS builds. Rendering here made each frame compete with the
                            // status-bar transition; the lifecycle hooks above already schedule
                            // the one freeze bind needed when the fake surface starts.
                            Object result = chain.proceed();
                            if (visibility == android.view.View.INVISIBLE
                                    && chain.getThisObject() instanceof ViewGroup) {
                                io.github.superisland.hook.systemui.lyric.LyricIslandSystemUiHost
                                        .clearNative((ViewGroup) chain.getThisObject());
                            }
                            return result;
                        }));
            }
            if (!optional.isEmpty()) {
                log(Log.INFO, TAG, "Installed native lyric fake-transition hooks=" + optional.size());
            }
        } catch (ClassNotFoundException ignored) {
            // Fake transition view is not present on compact/older ROM variants.
        } catch (Throwable error) {
            unhookAll(optional, "optional lyric fake-transition hook failure");
            log(Log.WARN, TAG, "Native lyric fake-transition hooks unavailable", error);
            optional.clear();
        }
        destination.addAll(optional);
    }

    private static void dispatchModuleRestore(
            Object owner,
            Object moduleTypeObject,
            Object islandData,
            boolean templateBuilder) {
        if (!(moduleTypeObject instanceof String)) return;
        String moduleType = (String) moduleTypeObject;
        if (!moduleType.endsWith("_1") && !moduleType.endsWith("_2")) return;
        Object adapter = templateBuilder ? readField(owner, "islandAdapter") : owner;
        Object holder = findHolder(adapter, moduleType);
        ViewGroup root = findHolderRoot(holder);
        if (root == null) {
            io.github.superisland.hook.systemui.lyric.LyricIslandSystemUiHost.refreshNativeAll();
            return;
        }
        dispatchNativeIslandUpdate(root, islandData);
    }

    private static Object findHolder(Object adapter, String moduleType) {
        if (adapter == null || moduleType == null) return null;
        Object holders = readField(adapter, "holders");
        if (holders instanceof Map<?, ?>) return ((Map<?, ?>) holders).get(moduleType);
        return null;
    }

    private static ViewGroup findHolderRoot(Object holder) {
        if (holder == null) return null;
        try {
            Method getter = holder.getClass().getMethod("getRootView");
            Object value = getter.invoke(holder);
            return value instanceof ViewGroup ? (ViewGroup) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object readField(Object receiver, String name) {
        if (receiver == null || name == null) return null;
        Class<?> current = receiver.getClass();
        while (current != null) {
            try {
                java.lang.reflect.Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(receiver);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static void addUniqueMethod(List<Method> methods, Method candidate) {
        for (Method existing : methods) {
            if (existing.getName().equals(candidate.getName())
                    && java.util.Arrays.equals(
                            existing.getParameterTypes(), candidate.getParameterTypes())) {
                return;
            }
        }
        methods.add(candidate);
    }

    /** Accepts only the audited Kotlin-suspend Dynamic Island update contract. */
    private static boolean isSupportedDynamicIslandUpdateSignature(Method method) {
        Class<?>[] parameters = method.getParameterTypes();
        if (parameters.length != 3 || parameters[1] != Boolean.TYPE) return false;
        Class<?> continuation = null;
        try {
            continuation = Class.forName(
                    "kotlin.coroutines.Continuation",
                    false,
                    method.getDeclaringClass().getClassLoader());
        } catch (Throwable ignored) {
            // Fall through to the stable obfuscated descriptor used by MIUI's plugin APK.
        }
        if (continuation != null && continuation.isAssignableFrom(parameters[2])) return true;
        String name = parameters[2].getName();
        return name.equals("kotlin.coroutines.Continuation")
                || name.equals("kotlin.coroutines.ContinuationImpl")
                || name.equals("K0.d");
    }

    private static void dispatchNativeIslandUpdate(Object receiver, Object data) {
        if (!(receiver instanceof ViewGroup)) return;
        ViewGroup root = (ViewGroup) receiver;
        final Runnable update;
        synchronized (NATIVE_UPDATE_LOCK) {
            PENDING_NATIVE_DATA.put(root, data);
            if (PENDING_NATIVE_UPDATES.containsKey(root)) return;
            update = () -> {
                Object latest;
                synchronized (NATIVE_UPDATE_LOCK) {
                    PENDING_NATIVE_UPDATES.remove(root);
                    latest = PENDING_NATIVE_DATA.remove(root);
                }
                try {
                    // LyricIslandNativeRenderer owns the complete media/pending-intent gate. The
                    // posted/coalesced dispatch keeps expensive OEM tree work off the hook call
                    // stack and lets one frame absorb bursty width/update callbacks.
                    io.github.superisland.hook.systemui.lyric.LyricIslandSystemUiHost
                            .renderNative(root, latest);
                } catch (Throwable error) {
                    // Renderer/runtime drift must restore OEM children and never escape a hook.
                    io.github.superisland.hook.systemui.lyric.LyricIslandSystemUiHost.clearNative(root);
                    Log.w(TAG, "Native lyric render failed closed", error);
                }
            };
            PENDING_NATIVE_UPDATES.put(root, update);
        }
        try {
            if (!root.post(update)) {
                synchronized (NATIVE_UPDATE_LOCK) {
                    PENDING_NATIVE_UPDATES.remove(root);
                    PENDING_NATIVE_DATA.remove(root);
                }
            }
        } catch (Throwable error) {
            synchronized (NATIVE_UPDATE_LOCK) {
                PENDING_NATIVE_UPDATES.remove(root);
                PENDING_NATIVE_DATA.remove(root);
            }
            Log.w(TAG, "Could not dispatch native lyric render", error);
        }
    }

    private static Object currentIslandData(ViewGroup root) {
        try {
            Method getter = root.getClass().getMethod("getCurrentIslandData");
            return getter.invoke(root);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void installPluginLoadHook(ClassLoader classLoader) throws ReflectiveOperationException {
        Class<?> pluginInstance = Class.forName(PLUGIN_INSTANCE_CLASS, false, classLoader);
        Method loadPlugin = pluginInstance.getDeclaredMethod("loadPlugin");
        Method unloadPlugin = pluginInstance.getDeclaredMethod("unloadPlugin");
        deoptimize(loadPlugin);
        deoptimize(unloadPlugin);
        hook(loadPlugin)
                .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    installFocusHooksFromPluginInstance(chain.getThisObject());
                    return result;
                });
        hook(unloadPlugin)
                .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    try {
                        // Let the OEM cancel the plugin coroutine scope before local hooks are
                        // released.
                        return chain.proceed();
                    } finally {
                        deactivateFocusPluginInstance(chain.getThisObject());
                    }
                });
    }

    private void installFocusHooksFromPluginInstance(Object pluginInstance) {
        if (pluginInstance == null) {
            return;
        }
        try {
            Object focusPlugin = getFocusNotificationPlugin(pluginInstance);
            if (focusPlugin == null) return;
            // HyperOS OS4 moved the plugin context off PluginInstance. The loaded Focus plugin
            // inherits the public PluginBase API, while older SystemUI builds exposed the
            // context directly on PluginInstance. Resolve the actual plugin first and retain the
            // instance path only as a compatibility fallback.
            Context pluginContext = getPluginContext(focusPlugin, pluginInstance);
            Context systemUiContext = getSystemUiContext(focusPlugin);
            ClassLoader pluginClassLoader = pluginContext.getClassLoader();
            boolean nativeHookInstalled = false;
            try {
                installLyricNativeRendererHook(pluginClassLoader, "MIUI SystemUI plugin loader");
                nativeHookInstalled = true;
            } catch (Throwable error) {
                // Focus auth hooks remain useful when this ROM does not expose Dynamic Island.
                log(Log.WARN, TAG, "Native lyric hook unavailable in plugin loader", error);
            }
            if (nativeHookInstalled) {
                synchronized (lyricNativePluginLoaders) {
                    lyricNativePluginLoaders.put(pluginInstance, pluginClassLoader);
                }
            }
            installFocusHooksIfPresent(pluginClassLoader, "MIUI SystemUI plugin loader");
            SystemUiFocusSupportBridge bridge =
                    installFocusSupportBridge(pluginClassLoader, systemUiContext);
            if (bridge != null) {
                synchronized (focusPluginInstances) {
                    focusPluginInstances.put(pluginInstance, bridge);
                }
            } else {
                // Do not leave a partial visibility bypass when the exact-SBN adapter failed.
                uninstallFocusNotificationGateHooks(pluginClassLoader);
            }
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Could not obtain the focus plugin ClassLoader", error);
        }
    }

    /** Resolves the context from the loaded plugin, with the pre-OS4 PluginInstance fallback. */
    private Context getPluginContext(Object focusPlugin, Object pluginInstance)
            throws ReflectiveOperationException {
        try {
            Method getter = focusPlugin.getClass().getMethod("getPluginContext");
            getter.setAccessible(true);
            Object value = getter.invoke(focusPlugin);
            if (value instanceof Context) return (Context) value;
        } catch (NoSuchMethodException ignored) {
            // Fall through to the historical PluginInstance API.
        }
        try {
            Method getter = pluginInstance.getClass().getMethod("getPluginContext");
            getter.setAccessible(true);
            Object value = getter.invoke(pluginInstance);
            if (value instanceof Context) return (Context) value;
        } catch (NoSuchMethodException ignored) {
            // Fall through to the historical field after the API probe.
        }
        java.lang.reflect.Field field = pluginInstance.getClass().getDeclaredField("mPluginContext");
        field.setAccessible(true);
        Object value = field.get(pluginInstance);
        if (value instanceof Context) return (Context) value;
        throw new ReflectiveOperationException("Focus plugin context is unavailable");
    }

    private Object getFocusNotificationPlugin(Object pluginInstance)
            throws ReflectiveOperationException {
        Method getter = pluginInstance.getClass().getMethod("getPlugin");
        Object plugin = getter.invoke(pluginInstance);
        return plugin != null && FOCUS_PLUGIN_CLASS.equals(plugin.getClass().getName())
                ? plugin
                : null;
    }

    /** The plugin's public host API supplies a correctly attributed SystemUI Context. */
    private Context getSystemUiContext(Object focusPlugin) throws ReflectiveOperationException {
        Method getter = focusPlugin.getClass().getMethod("getSysuiContext");
        Object value = getter.invoke(focusPlugin);
        if (!(value instanceof Context context)) {
            throw new ReflectiveOperationException("Focus plugin SystemUI context is unavailable");
        }
        if (!SYSTEM_UI_PACKAGE.equals(context.getPackageName())
                || context.getApplicationInfo().uid != android.os.Process.myUid()) {
            throw new ReflectiveOperationException("Focus plugin returned an invalid SystemUI context");
        }
        return context;
    }

    private void deactivateFocusPluginInstance(Object pluginInstance) {
        ClassLoader nativePluginLoader;
        synchronized (lyricNativePluginLoaders) {
            nativePluginLoader = lyricNativePluginLoaders.remove(pluginInstance);
        }
        SystemUiFocusSupportBridge bridge;
        synchronized (focusPluginInstances) {
            bridge = focusPluginInstances.remove(pluginInstance);
        }
        if (bridge != null) bridge.deactivate("plugin_unloaded");
        ClassLoader pluginLoader = null;
        if (bridge != null) {
            synchronized (focusSupportBridges) {
                Iterator<Map.Entry<ClassLoader, SystemUiFocusSupportBridge>> iterator =
                        focusSupportBridges.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<ClassLoader, SystemUiFocusSupportBridge> entry = iterator.next();
                    if (entry.getValue() == bridge) {
                        pluginLoader = entry.getKey();
                        iterator.remove();
                    }
                }
            }
        }
        if (pluginLoader != null) uninstallFocusNotificationGateHooks(pluginLoader);
        if (nativePluginLoader != null
                && nativePluginLoader != systemUiLyricNativeLoader
                && !hasOtherLyricNativePluginOwner(nativePluginLoader)) {
            uninstallLyricNativeRendererHooks(nativePluginLoader);
            io.github.superisland.hook.systemui.lyric.LyricIslandSystemUiHost.clearNativeAll();
        }
    }

    private boolean hasOtherLyricNativePluginOwner(ClassLoader classLoader) {
        synchronized (lyricNativePluginLoaders) {
            for (ClassLoader owner : lyricNativePluginLoaders.values()) {
                if (owner == classLoader) return true;
            }
        }
        return false;
    }

    private void installFocusHooksIfPresent(ClassLoader classLoader, String loaderLabel) {
        try {
            installFocusNotificationGateHooks(classLoader);
            log(Log.INFO, TAG, "Installed focus bridge in " + loaderLabel);
        } catch (ClassNotFoundException ignored) {
            // The default loader does not own focus classes on most HyperOS builds. The plugin
            // callback above will retry using the real plugin ClassLoader.
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Focus bridge unavailable in " + loaderLabel, error);
        }
    }

    private void installFocusNotificationGateHooks(ClassLoader classLoader) throws ReflectiveOperationException {
        if (classLoader == null) return;
        synchronized (installedFocusLoaderHooks) {
            if (installedFocusLoaderHooks.containsKey(classLoader)) return;
        }
        List<HookHandle> handles = new ArrayList<>();
        try {
            handles.add(installFocusVisibilityGate(classLoader));
            synchronized (installedFocusLoaderHooks) {
                if (installedFocusLoaderHooks.containsKey(classLoader)) {
                    unhookAll(handles, "duplicate focus gate installation");
                    return;
                }
                installedFocusLoaderHooks.put(classLoader, List.copyOf(handles));
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            unhookAll(handles, "failed focus gate installation");
            throw error;
        }
    }

    private void uninstallFocusNotificationGateHooks(ClassLoader classLoader) {
        List<HookHandle> handles;
        synchronized (installedFocusLoaderHooks) {
            handles = installedFocusLoaderHooks.remove(classLoader);
        }
        unhookAll(handles, "focus gate uninstall");
    }

    private void uninstallLyricNativeRendererHooks(ClassLoader classLoader) {
        if (classLoader == null) return;
        List<HookHandle> handles;
        synchronized (installedLyricNativeHooks) {
            handles = installedLyricNativeHooks.remove(classLoader);
        }
        unhookAll(handles, "native lyric hook uninstall");
    }

    private SystemUiFocusSupportBridge installFocusSupportBridge(
            ClassLoader classLoader,
            Context systemUiContext) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            log(Log.ERROR, TAG, "Refusing to install smart-capsule hooks off SystemUI main");
            return null;
        }
        synchronized (focusSupportBridges) {
            SystemUiFocusSupportBridge installed = focusSupportBridges.get(classLoader);
            if (installed != null) return installed;
        }

        long pluginEpoch = nextPluginEpoch();
        SystemUiFocusSupportBridge bridge = null;
        List<HookHandle> installedHandles = new ArrayList<>();
        boolean handlesAttached = false;
        try {
            Class<?> controller = Class.forName(FOCUS_CONTROLLER_CLASS, false, classLoader);
            Class<?> callback = Class.forName(FOCUS_CALLBACK_CLASS, false, classLoader);
            ChannelCatalogMethods channelCatalogMethods =
                    resolveChannelCatalogMethods(classLoader);

            Method fetchAuthResult = controller.getDeclaredMethod(
                    "fetchAuthResult",
                    Context.class,
                    StatusBarNotification.class,
                    String.class,
                    android.os.Bundle.class,
                    callback);
            Method onAuthSuccess = callback.getDeclaredMethod(
                    "onAuthSuccess",
                    String.class,
                    String.class);
            fetchAuthResult.setAccessible(true);
            onAuthSuccess.setAccessible(true);
            deoptimize(fetchAuthResult);

            bridge = new SystemUiFocusSupportBridge(
                    systemUiContext,
                    pluginEpoch,
                    callback,
                    onAuthSuccess,
                    channelCatalogMethods.notificationManagerGetService,
                    channelCatalogMethods.getNotificationChannelsForPackage,
                    channelCatalogMethods.parceledListGetList);
            SystemUiFocusSupportBridge installedBridge = bridge;

            installedHandles.add(hook(fetchAuthResult)
                    .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        StatusBarNotification sbn = (StatusBarNotification) chain.getArg(1);
                        String targetPackage = (String) chain.getArg(2);
                        Object callbackObject = chain.getArg(4);
                        if (installedBridge.authorizeModuleOrResident(
                                sbn,
                                targetPackage,
                                callbackObject)) {
                            return null;
                        }
                        return chain.proceed();
                    }));

            List<HookHandle> focusOnlyHandles = new ArrayList<>();
            try {
                Class<?> focusContent = Class.forName(FOCUS_CONTENT_CLASS, false, classLoader);
                Method addDynamicIslandView = controller.getDeclaredMethod(
                        "addDynamicIslandView",
                        focusContent,
                        StatusBarNotification.class);
                Method updateDynamicIslandView = controller.getDeclaredMethod(
                        "updateDynamicIslandView",
                        focusContent,
                        StatusBarNotification.class);
                addDynamicIslandView.setAccessible(true);
                updateDynamicIslandView.setAccessible(true);
                deoptimize(addDynamicIslandView);
                deoptimize(updateDynamicIslandView);

                SystemUiFocusOnlyIslandSuppressor suppressor =
                        SystemUiFocusOnlyIslandSuppressor.resolve(classLoader, controller);
                for (Method target : List.of(addDynamicIslandView, updateDynamicIslandView)) {
                    focusOnlyHandles.add(hook(target)
                            .setExceptionMode(
                                    io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                StatusBarNotification sbn =
                                        (StatusBarNotification) chain.getArg(1);
                                if (installedBridge.suppressFocusOnlyIsland(
                                        chain.getThisObject(),
                                        sbn)) {
                                    return null;
                                }
                                return chain.proceed();
                            }));
                }
                bridge.attachFocusOnlyIslandSuppressor(suppressor);
                installedHandles.addAll(focusOnlyHandles);
                focusOnlyHandles.clear();
            } catch (Throwable error) {
                unhookAll(focusOnlyHandles, "failed Focus-only island suppression installation");
                log(Log.WARN, TAG,
                        "Focus-only island suppression unavailable; selected posts stay ordinary",
                        error);
            }

            bridge.attachHookHandles(installedHandles);
            handlesAttached = true;
            bridge.activate();
            synchronized (focusSupportBridges) {
                focusSupportBridges.put(classLoader, bridge);
            }
            log(Log.INFO, TAG, "Installed module/resident Focus support epoch="
                    + pluginEpoch);
            return bridge;
        } catch (Throwable error) {
            if (bridge != null) {
                bridge.deactivate("rom_contract_mismatch");
            }
            // Handles may not yet have been attached to the bridge when construction failed.
            if (!handlesAttached) {
                unhookAll(installedHandles, "failed smart-capsule installation");
            }
            log(Log.ERROR, TAG, "Focus plugin support unavailable", error);
            return null;
        }
    }

    private long nextPluginEpoch() {
        while (true) {
            long current = pluginEpochs.get();
            if (current == Long.MAX_VALUE) {
                throw new IllegalStateException("Smart-capsule plugin epoch exhausted");
            }
            long next = current + 1L;
            if (pluginEpochs.compareAndSet(current, next)) return next;
        }
    }

    /**
     * Channel enumeration improves the editor but is not part of the Focus adapter contract.
     * Hidden notification-service signatures have changed across Android/HyperOS releases, so a
     * mismatch must disable only the catalog instead of preventing the source-SBN hooks from
     * installing.
     */
    private ChannelCatalogMethods resolveChannelCatalogMethods(ClassLoader classLoader) {
        try {
            Class<?> notificationManager =
                    Class.forName("android.app.NotificationManager", false, classLoader);
            Class<?> iNotificationManager =
                    Class.forName("android.app.INotificationManager", false, classLoader);
            Class<?> parceledListSlice =
                    Class.forName("android.content.pm.ParceledListSlice", false, classLoader);
            Method notificationManagerGetService =
                    notificationManager.getDeclaredMethod("getService");
            Method getNotificationChannelsForPackage = iNotificationManager.getDeclaredMethod(
                    "getNotificationChannelsForPackage",
                    String.class,
                    int.class,
                    boolean.class);
            Method parceledListGetList = parceledListSlice.getDeclaredMethod("getList");
            notificationManagerGetService.setAccessible(true);
            getNotificationChannelsForPackage.setAccessible(true);
            parceledListGetList.setAccessible(true);
            return new ChannelCatalogMethods(
                    notificationManagerGetService,
                    getNotificationChannelsForPackage,
                    parceledListGetList);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            log(Log.WARN, TAG, "Channel catalog unavailable; core Focus adapter remains enabled", error);
            return ChannelCatalogMethods.unavailable();
        }
    }

    private static final class ChannelCatalogMethods {
        final Method notificationManagerGetService;
        final Method getNotificationChannelsForPackage;
        final Method parceledListGetList;

        ChannelCatalogMethods(
                Method notificationManagerGetService,
                Method getNotificationChannelsForPackage,
                Method parceledListGetList) {
            this.notificationManagerGetService = notificationManagerGetService;
            this.getNotificationChannelsForPackage = getNotificationChannelsForPackage;
            this.parceledListGetList = parceledListGetList;
        }

        static ChannelCatalogMethods unavailable() {
            return new ChannelCatalogMethods(null, null, null);
        }
    }

    private HookHandle installFocusVisibilityGate(ClassLoader classLoader) throws ReflectiveOperationException {
        Class<?> focusUtils = Class.forName(
                FOCUS_UTILS_CLASS,
                false,
                classLoader
        );
        Method canShowFocus;
        try {
            // OS4 passes the complete source SBN so the Focus provider can resolve its user.
            canShowFocus = focusUtils.getDeclaredMethod(
                    "canShowFocus",
                    android.content.Context.class,
                    String.class,
                    StatusBarNotification.class
            );
        } catch (NoSuchMethodException os4SignatureMissing) {
            // Older HyperOS releases exposed only the numeric user id.
            canShowFocus = focusUtils.getDeclaredMethod(
                    "canShowFocus",
                    android.content.Context.class,
                    String.class,
                    int.class
            );
        }
        deoptimize(canShowFocus);
        return hook(canShowFocus)
                .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    String packageName = (String) chain.getArg(1);
                    if (MODULE_PACKAGE.equals(packageName)) {
                        return true;
                    }
                    return chain.proceed();
                });
    }

    private void unhookAll(List<HookHandle> handles, String reason) {
        if (handles == null) return;
        for (HookHandle handle : handles) {
            if (handle == null) continue;
            try {
                handle.unhook();
            } catch (Throwable error) {
                log(Log.WARN, TAG, "Could not unhook during " + reason, error);
            }
        }
    }
}
