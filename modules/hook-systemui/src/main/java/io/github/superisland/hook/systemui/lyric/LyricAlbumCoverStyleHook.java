package io.github.superisland.hook.systemui.lyric;

import android.animation.ObjectAnimator;
import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.Hooker;
import io.github.libxposed.api.XposedModule;
import io.github.superisland.source.lyric.LyricAlbumCoverStyle;
import io.github.superisland.source.lyric.LyricIslandConfig;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import android.os.Handler;
import android.os.Looper;

/** Direct HyperLyric-compatible IslandIconViewHolder cover-style lifecycle hook. */
public final class LyricAlbumCoverStyleHook {
    private static final String HOLDER_CLASS =
            "miui.systemui.dynamicisland.module.IslandIconViewHolder";
    private static final String MEDIA_ALBUM_ICON = "miui_media_album_icon";
    private static final Map<ClassLoader, Boolean> INSTALLED = Collections.synchronizedMap(
            new WeakHashMap<>());
    private static final Map<Object, HolderState> STATES = Collections.synchronizedMap(
            new WeakHashMap<>());
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final ViewOutlineProvider CIRCLE_OUTLINE = new ViewOutlineProvider() {
        @Override public void getOutline(View view, Outline outline) {
            outline.setOval(0, 0, view.getWidth(), view.getHeight());
        }
    };
    private static final View.OnAttachStateChangeListener ROTATION_ATTACH_LISTENER =
            new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) {
                    if (!(view instanceof ImageView)) return;
                    HolderState state = findState((ImageView) view);
                    if (state != null && LyricIslandSystemUiHost.nativeIsPlaying()) {
                        startRotation(state);
                    }
                }

                @Override public void onViewDetachedFromWindow(View view) {
                    if (view instanceof ImageView) {
                        HolderState state = findState((ImageView) view);
                        if (state != null) stopRotation(state);
                    }
                }
            };

    private LyricAlbumCoverStyleHook() {}

    public static void install(XposedModule module, ClassLoader loader)
            throws ReflectiveOperationException {
        if (module == null || loader == null || INSTALLED.put(loader, Boolean.TRUE) != null) return;
        try {
            Class<?> holder = Class.forName(HOLDER_CLASS, false, loader);
            Method setFixIcon = null;
            for (Method method : holder.getDeclaredMethods()) {
                if ("setFixIcon".equals(method.getName())
                        && method.getParameterTypes().length == 1) {
                    setFixIcon = method;
                    break;
                }
            }
            if (setFixIcon == null) throw new NoSuchMethodException("setFixIcon");
            setFixIcon.setAccessible(true);
            Accessor accessor = new Accessor(
                    setFixIcon,
                    findMethod(holder, "setAppIcon", setFixIcon.getParameterTypes()),
                    requiredField(holder, "picInfo"),
                    requiredField(holder, "fixIcon"),
                    requiredField(holder, "appIcon"),
                    requiredField(holder, "iconContainer"));
            module.deoptimize(setFixIcon);
            module.hook(setFixIcon)
                    .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(new SetFixIconHook(accessor));
        } catch (Throwable error) {
            INSTALLED.remove(loader);
            if (error instanceof ReflectiveOperationException) {
                throw (ReflectiveOperationException) error;
            }
            throw new ReflectiveOperationException("album cover hook unavailable", error);
        }
    }

    /** HyperLyric replays setFixIcon for every tracked holder when settings change. */
    public static void refresh() {
        Runnable task = () -> {
            synchronized (STATES) {
                for (Map.Entry<Object, HolderState> entry : STATES.entrySet()) {
                    Object holder = entry.getKey();
                    HolderState state = entry.getValue();
                    if (holder == null || state == null) continue;
                    try {
                        Accessor accessor = state.accessor;
                        Object data = state.data;
                        if (accessor != null && data != null) apply(accessor, holder, data);
                    } catch (Throwable ignored) {
                        // OEM holder drift must not affect the SystemUI main loop.
                    }
                }
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) task.run(); else MAIN_HANDLER.post(task);
    }

    /** Keeps rotating-cover playback state aligned without waiting for the next media item. */
    public static void onPlaybackStateChanged(boolean playing) {
        Runnable task = () -> {
            synchronized (STATES) {
                for (HolderState state : STATES.values()) {
                    if (state == null || state.rotation == null) continue;
                    if (playing) {
                        if (state.rotation.isPaused()) state.rotation.resume();
                        else if (!state.rotation.isStarted()) state.rotation.start();
                    } else if (state.rotation.isRunning()) {
                        state.rotation.pause();
                    }
                }
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) task.run(); else MAIN_HANDLER.post(task);
    }

    private static Field requiredField(Class<?> type, String name)
            throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method findMethod(Class<?> type, String name, Class<?>[] args) {
        for (Method method : type.getDeclaredMethods()) {
            if (!name.equals(method.getName())
                    || method.getParameterTypes().length != args.length) continue;
            boolean matches = true;
            Class<?>[] parameters = method.getParameterTypes();
            for (int i = 0; i < args.length; i++) {
                if (parameters[i] != args[i]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static final class SetFixIconHook implements Hooker {
        private final Accessor accessor;

        SetFixIconHook(Accessor accessor) { this.accessor = accessor; }

        @Override public Object intercept(Chain chain) throws Throwable {
            Object result = chain.proceed();
            try {
                Object holder = chain.getThisObject();
                Object data = chain.getArg(0);
                apply(accessor, holder, data);
            } catch (Throwable ignored) {
                // A changed OEM holder must never break its original icon update.
            }
            return result;
        }
    }

    private static void apply(Accessor accessor, Object holder, Object data) throws Exception {
        if (holder == null || !isMediaAlbum(accessor.picInfo, holder)) return;
        ImageView fixIcon = accessor.fixIcon.get(holder) instanceof ImageView
                ? (ImageView) accessor.fixIcon.get(holder) : null;
        if (fixIcon == null) return;
        HolderState state = STATES.get(holder);
        if (state == null) {
            state = new HolderState(fixIcon, accessor, data);
            STATES.put(holder, state);
        }
        state.accessor = accessor;
        state.data = data;
        LyricIslandConfig config = LyricIslandSystemUiHost.nativeConfig();
        int style = config == null || !config.getEnabled()
                ? LyricAlbumCoverStyle.DEFAULT : config.getAlbumCoverStyle();
        if (style == LyricAlbumCoverStyle.DEFAULT) {
            // setFixIcon() has already restored the current track's native drawable. HyperLyric
            // leaves the default branch untouched; restoring a drawable captured from the first
            // holder update would roll a live track back to stale artwork.
            stopRotation(state);
            return;
        }
        if (style == LyricAlbumCoverStyle.HIDDEN) {
            stopRotation(state);
            fixIcon.setVisibility(View.INVISIBLE);
            return;
        }
        fixIcon.setVisibility(View.VISIBLE);
        if (style == LyricAlbumCoverStyle.APP_ICON && accessor.setAppIcon != null) {
            accessor.setAppIcon.invoke(holder, data);
            ImageView appIcon = accessor.appIcon.get(holder) instanceof ImageView
                    ? (ImageView) accessor.appIcon.get(holder) : null;
            View container = accessor.iconContainer.get(holder) instanceof View
                    ? (View) accessor.iconContainer.get(holder) : null;
            if (appIcon == null || appIcon.getDrawable() == null
                    || appIcon.getVisibility() != View.VISIBLE
                    || container == null || container.getVisibility() != View.VISIBLE) {
                if (appIcon != null) appIcon.setVisibility(View.GONE);
                fixIcon.setVisibility(View.VISIBLE);
                if (container != null) container.setVisibility(View.VISIBLE);
            }
            stopRotation(state);
            return;
        }
        applyCircle(fixIcon);
        if (style == LyricAlbumCoverStyle.ROTATING_CIRCLE) {
            if (state.rotation == null) {
                state.rotation = ObjectAnimator.ofFloat(fixIcon, View.ROTATION, 0f, 360f);
                state.rotation.setDuration(20_000L);
                state.rotation.setRepeatCount(ObjectAnimator.INFINITE);
                state.rotation.setInterpolator(new android.view.animation.LinearInterpolator());
            }
            if (!state.listenerAttached) {
                fixIcon.addOnAttachStateChangeListener(ROTATION_ATTACH_LISTENER);
                state.listenerAttached = true;
            }
            startRotation(state);
        } else {
            stopRotation(state);
        }
    }

    private static void applyCircle(ImageView image) {
        image.setOutlineProvider(CIRCLE_OUTLINE);
        image.setClipToOutline(true);
        image.invalidateOutline();
    }

    private static void stopRotation(HolderState state) {
        if (state.rotation != null) {
            state.rotation.cancel();
            state.rotation = null;
        }
        if (state.fixIcon != null) state.fixIcon.setRotation(0f);
        if (state.fixIcon != null && state.listenerAttached) {
            state.fixIcon.removeOnAttachStateChangeListener(ROTATION_ATTACH_LISTENER);
            state.listenerAttached = false;
        }
    }

    private static void startRotation(HolderState state) {
        if (state == null || state.rotation == null || state.fixIcon == null
                || !state.fixIcon.isAttachedToWindow()
                || !LyricIslandSystemUiHost.nativeIsPlaying()) return;
        if (!state.rotation.isStarted()) state.rotation.start();
    }

    private static HolderState findState(ImageView image) {
        synchronized (STATES) {
            for (HolderState state : STATES.values()) {
                if (state != null && state.fixIcon == image) return state;
            }
        }
        return null;
    }

    private static boolean isMediaAlbum(Field picInfo, Object holder) {
        try {
            Object value = picInfo.get(holder);
            if (value == null) return false;
            for (Method method : value.getClass().getMethods()) {
                if ("getPic".equals(method.getName()) && method.getParameterTypes().length == 0) {
                    return MEDIA_ALBUM_ICON.equals(method.invoke(value));
                }
            }
        } catch (Throwable ignored) { }
        return false;
    }

    private static final class HolderState {
        Accessor accessor;
        Object data;
        final ImageView fixIcon;
        boolean listenerAttached;
        ObjectAnimator rotation;

        HolderState(ImageView image, Accessor accessor, Object data) {
            this.fixIcon = image;
            this.accessor = accessor;
            this.data = data;
        }
    }

    private static final class Accessor {
        final Method setFixIcon;
        final Method setAppIcon;
        final Field picInfo;
        final Field fixIcon;
        final Field appIcon;
        final Field iconContainer;

        Accessor(Method setFixIcon, Method setAppIcon, Field picInfo, Field fixIcon,
                Field appIcon, Field iconContainer) {
            this.setFixIcon = setFixIcon;
            this.setAppIcon = setAppIcon;
            this.picInfo = picInfo;
            this.fixIcon = fixIcon;
            this.appIcon = appIcon;
            this.iconContainer = iconContainer;
        }
    }
}
