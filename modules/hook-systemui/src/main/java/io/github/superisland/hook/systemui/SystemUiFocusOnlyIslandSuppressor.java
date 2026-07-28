package io.github.superisland.hook.systemui;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Suppresses only the Dynamic Island surface for mapper-owned Focus-only notifications. */
final class SystemUiFocusOnlyIslandSuppressor {
    private static final String TAG = "SuperIslandFocusOnly";
    private static final String EXTRA_LOCAL_ADAPTER =
            "io.github.superisland.focus.local_adapter_v1";
    private static final String LOCAL_ADAPTER_NAME = "hyperisland-local-sbn-v1";
    private static final String EXTRA_FOCUS_ONLY =
            "io.github.superisland.focus.focus_only_v1";
    private static final String EXTRA_ISLAND_UPDATE_NO_FLOAT = "miui.island.updateNoFloat";
    private static final AtomicInteger ACTIVE_INSTALLATIONS = new AtomicInteger();

    private final Field islandShowingMapField;
    private final Field windowViewCreatorField;
    private final Method providerGet;
    private final Method getWindowView;
    private final Method getWindowViewController;
    private final Method removeDynamicIslandView;
    private volatile boolean active;

    private SystemUiFocusOnlyIslandSuppressor(
            Field islandShowingMapField,
            Field windowViewCreatorField,
            Method providerGet,
            Method getWindowView,
            Method getWindowViewController,
            Method removeDynamicIslandView) {
        this.islandShowingMapField = islandShowingMapField;
        this.windowViewCreatorField = windowViewCreatorField;
        this.providerGet = providerGet;
        this.getWindowView = getWindowView;
        this.getWindowViewController = getWindowViewController;
        this.removeDynamicIslandView = removeDynamicIslandView;
    }

    static SystemUiFocusOnlyIslandSuppressor resolve(
            ClassLoader classLoader,
            Class<?> focusController) throws ReflectiveOperationException {
        Field islandShowingMap = focusController.getDeclaredField("islandShowingMap");
        Field windowViewCreator = focusController.getDeclaredField("windowViewCreator");
        islandShowingMap.setAccessible(true);
        windowViewCreator.setAccessible(true);

        Method providerGet = windowViewCreator.getType().getMethod("get");
        Class<?> creator = Class.forName(
                "miui.systemui.dynamicisland.window.DynamicIslandWindowViewCreator",
                false,
                classLoader);
        Class<?> window = Class.forName(
                "miui.systemui.dynamicisland.window.DynamicIslandWindowView",
                false,
                classLoader);
        Class<?> windowController = Class.forName(
                "miui.systemui.dynamicisland.window.DynamicIslandWindowViewController",
                false,
                classLoader);
        Method getWindowView = creator.getDeclaredMethod("getWindowView");
        Method getWindowViewController = window.getDeclaredMethod("getWindowViewController");
        Method removeDynamicIslandView =
                windowController.getDeclaredMethod(
                        "removeDynamicIslandView",
                        String.class,
                        boolean.class);
        providerGet.setAccessible(true);
        getWindowView.setAccessible(true);
        getWindowViewController.setAccessible(true);
        removeDynamicIslandView.setAccessible(true);
        return new SystemUiFocusOnlyIslandSuppressor(
                islandShowingMap,
                windowViewCreator,
                providerGet,
                getWindowView,
                getWindowViewController,
                removeDynamicIslandView);
    }

    static boolean isAvailable() {
        return ACTIVE_INSTALLATIONS.get() > 0;
    }

    synchronized void activate() {
        if (active) return;
        active = true;
        ACTIVE_INSTALLATIONS.incrementAndGet();
    }

    synchronized void deactivate() {
        if (!active) return;
        active = false;
        ACTIVE_INSTALLATIONS.decrementAndGet();
    }

    boolean suppress(Object focusController, StatusBarNotification sbn) {
        if (!active || focusController == null || !isMapperOwnedFocusOnly(sbn)) {
            return false;
        }
        String key = sbn.getKey();
        if (key == null || key.isBlank()) return false;
        try {
            removeExistingIsland(focusController, sbn, key);
            Log.i(TAG, "Suppressed Dynamic Island for source Focus key=" + key);
        } catch (Throwable error) {
            deactivate();
            Log.e(TAG, "Focus-only island suppression failed closed for key=" + key, error);
        }
        // Never enter OEM add/update after accepting this mapper-owned Focus-only marker. If
        // cleanup failed, the capability is disabled so subsequent source posts stay ordinary.
        return true;
    }

    @SuppressWarnings("unchecked")
    private void removeExistingIsland(
            Object focusController,
            StatusBarNotification sbn,
            String key) throws ReflectiveOperationException {
        Object rawMap = islandShowingMapField.get(focusController);
        if (!(rawMap instanceof Map<?, ?>)) {
            throw new ReflectiveOperationException("islandShowingMap is unavailable");
        }
        Map<String, Object> islands = (Map<String, Object>) rawMap;
        if (!islands.containsKey(key)) return;

        try {
            Object provider = windowViewCreatorField.get(focusController);
            Object creator = provider != null ? providerGet.invoke(provider) : null;
            Object window = creator != null ? getWindowView.invoke(creator) : null;
            Object controller = window != null ? getWindowViewController.invoke(window) : null;
            if (controller != null) {
                Bundle extras = sbn.getNotification() != null
                        ? sbn.getNotification().extras
                        : null;
                boolean noFloat = extras != null
                        && extras.getBoolean(EXTRA_ISLAND_UPDATE_NO_FLOAT, false);
                removeDynamicIslandView.invoke(controller, key, noFloat);
            }
        } finally {
            islands.remove(key);
        }
    }

    private static boolean isMapperOwnedFocusOnly(StatusBarNotification sbn) {
        if (sbn == null) return false;
        Notification notification = sbn.getNotification();
        Bundle extras = notification != null ? notification.extras : null;
        if (extras == null) return false;
        return LOCAL_ADAPTER_NAME.equals(extras.getString(EXTRA_LOCAL_ADAPTER))
                && extras.getBoolean(EXTRA_FOCUS_ONLY, false);
    }
}
