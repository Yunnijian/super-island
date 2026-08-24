package io.github.superisland.hook.systemui.lyric;

import android.graphics.Bitmap;
import android.view.View;
import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.Hooker;
import io.github.libxposed.api.XposedModule;
import io.github.superisland.source.lyric.LyricIslandConfig;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * HyperLyric-compatible hook for the OEM music-wave holder. The tree scanner remains a fallback
 * for ROMs without these methods; on supported ROMs this lifecycle hook is what survives Lottie
 * holder recreation and prevents the OEM callback from immediately overwriting our colors.
 */
public final class LyricMusicWaveColorHook {
    private static final String HOLDER =
            "miui.systemui.dynamicisland.module.IslandIconViewHolder";
    private static final Map<ClassLoader, Boolean> INSTALLED = Collections.synchronizedMap(
            new WeakHashMap<>());
    private static final Map<Object, int[]> ORIGINALS = Collections.synchronizedMap(
            new WeakHashMap<>());
    private static final Map<Object, HolderState> HOLDERS = Collections.synchronizedMap(
            new WeakHashMap<>());

    private LyricMusicWaveColorHook() {}

    public static void install(XposedModule module, ClassLoader loader) throws ReflectiveOperationException {
        if (module == null || loader == null || INSTALLED.put(loader, Boolean.TRUE) != null) return;
        try {
            Class<?> holderClass = Class.forName(HOLDER, false, loader);
            Field top = holderClass.getDeclaredField("gradientTopColor");
            Field bottom = holderClass.getDeclaredField("gradientBottomColor");
            top.setAccessible(true);
            bottom.setAccessible(true);
            Field data = findField(holderClass, "data");
            Field lottie = findField(holderClass, "lottieView");
            Field picInfo = findField(holderClass, "picInfo");

            Method setColor = findMethod(holderClass, "setLottieColor", Bitmap.class);
            if (setColor != null) {
                module.deoptimize(setColor);
                module.hook(setColor)
                        .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(new SetColorHook(top, bottom, data, lottie));
            }
            Method register = findMethod(holderClass, "registerLottieCallback");
            if (register != null) {
                module.deoptimize(register);
                module.hook(register)
                        .setExceptionMode(io.github.libxposed.api.XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(
                        new RegisterHook(top, bottom, data, lottie, picInfo));
            }
        } catch (Throwable error) {
            INSTALLED.remove(loader);
            if (error instanceof ReflectiveOperationException) throw (ReflectiveOperationException) error;
            throw new ReflectiveOperationException("music-wave color hook unavailable", error);
        }
    }

    /** Replays the latest HyperLyric wave colors on every holder after a media/config change. */
    public static void refresh() {
        synchronized (HOLDERS) {
            for (Map.Entry<Object, HolderState> entry : HOLDERS.entrySet()) {
                HolderState state = entry.getValue();
                if (state == null) continue;
                apply(entry.getKey(), state.top, state.bottom, state.data, state.lottie, false);
            }
        }
    }

    private static Field findField(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... args) {
        for (Method method : type.getDeclaredMethods()) {
            if (!name.equals(method.getName()) || method.getParameterTypes().length != args.length) continue;
            boolean matches = true;
            for (int i = 0; i < args.length; i++) {
                if (args[i] != method.getParameterTypes()[i]) {
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

    private static final class SetColorHook implements Hooker {
        private final Field top;
        private final Field bottom;
        private final Field data;
        private final Field lottie;

        SetColorHook(Field top, Field bottom, Field data, Field lottie) {
            this.top = top;
            this.bottom = bottom;
            this.data = data;
            this.lottie = lottie;
        }

        @Override public Object intercept(Chain chain) throws Throwable {
            Object result = chain.proceed();
            apply(chain.getThisObject(), top, bottom, data, lottie, false);
            return result;
        }
    }

    private static final class RegisterHook implements Hooker {
        private final Field top;
        private final Field bottom;
        private final Field data;
        private final Field lottie;
        private final Field picInfo;

        RegisterHook(Field top, Field bottom, Field data, Field lottie, Field picInfo) {
            this.top = top;
            this.bottom = bottom;
            this.data = data;
            this.lottie = lottie;
            this.picInfo = picInfo;
        }

        @Override public Object intercept(Chain chain) throws Throwable {
            Object result = chain.proceed();
            Object holder = chain.getThisObject();
            if (isMusicWave(picInfo, holder)) apply(holder, top, bottom, data, lottie, true);
            return result;
        }
    }

    private static void apply(
            Object holder, Field top, Field bottom, Field data, Field lottie, boolean callback) {
        if (holder == null || !isCurrentMedia(holder, data)) return;
        Field picInfo = null;
        try {
            picInfo = holder.getClass().getDeclaredField("picInfo");
            picInfo.setAccessible(true);
        } catch (Throwable ignored) { }
        if (picInfo != null && !isMusicWave(picInfo, holder)) return;
        HOLDERS.put(holder, new HolderState(top, bottom, data, lottie));
        LyricIslandConfig config = LyricIslandSystemUiHost.nativeConfig();
        if (config == null || !config.getEnabled()) {
            restore(holder, top, bottom);
            invalidate(lottie, holder);
            return;
        }
        int style = config.getMusicWaveStyle();
        if (style != 1 && style != 2) {
            restore(holder, top, bottom);
            invalidate(lottie, holder);
            return;
        }
        java.util.List<Integer> colors = LyricIslandSystemUiHost.nativeArtworkColors();
        if (colors == null || colors.isEmpty()) return;
        remember(holder, top, bottom);
        int first = withAlpha(colors.get(0));
        int second = withAlpha(style == 1 || colors.size() < 2 ? colors.get(0) : colors.get(1));
        write(top, holder, first);
        write(bottom, holder, second);
        invalidate(lottie, holder);
    }

    private static boolean isCurrentMedia(Object holder, Field data) {
        if (data == null) return true;
        try {
            Object value = data.get(holder);
            String packageName = findPackage(value, 0, Collections.newSetFromMap(new WeakHashMap<>()));
            String publisher = LyricIslandSystemUiHost.nativePublisher();
            if (packageName == null || publisher == null) return true;
            int slash = publisher.indexOf('/');
            int colon = publisher.indexOf(':');
            int end = publisher.length();
            if (slash >= 0) end = Math.min(end, slash);
            if (colon >= 0) end = Math.min(end, colon);
            return packageName.equals(publisher.substring(0, end));
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static String findPackage(Object value, int depth, java.util.Set<Object> seen) {
        if (value == null || depth > 2 || seen.contains(value)) return null;
        seen.add(value);
        if (value instanceof String && ((String) value).contains(".")) return (String) value;
        for (String name : new String[] {"getPkgName", "getPackageName", "getPackage", "getPkg"}) {
            try {
                Method method = value.getClass().getMethod(name);
                Object child = method.invoke(value);
                if (child instanceof String) return (String) child;
            } catch (Throwable ignored) { }
        }
        return null;
    }

    private static boolean isMusicWave(Field picInfo, Object holder) {
        if (picInfo == null) return true;
        try {
            Object info = picInfo.get(holder);
            Method getter = info.getClass().getMethod("getPic");
            Object pic = getter.invoke(info);
            return "musicWave".equals(pic) || "musicPause".equals(pic);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void remember(Object holder, Field top, Field bottom) {
        if (ORIGINALS.containsKey(holder)) return;
        ORIGINALS.put(holder, new int[] {read(top, holder), read(bottom, holder)});
    }

    private static void restore(Object holder, Field top, Field bottom) {
        int[] original = ORIGINALS.get(holder);
        if (original == null) return;
        write(top, holder, original[0]);
        write(bottom, holder, original[1]);
    }

    private static int read(Field field, Object holder) {
        try { return field.getInt(Modifier.isStatic(field.getModifiers()) ? null : holder); }
        catch (Throwable ignored) { return 0; }
    }

    private static void write(Field field, Object holder, int value) {
        try { field.setInt(Modifier.isStatic(field.getModifiers()) ? null : holder, value); }
        catch (Throwable ignored) { }
    }

    private static void invalidate(Field field, Object holder) {
        if (field == null) return;
        try {
            Object view = field.get(holder);
            if (view instanceof View) ((View) view).invalidate();
        } catch (Throwable ignored) { }
    }

    private static int withAlpha(int color) { return (color & 0x00ffffff) | (230 << 24); }

    private static final class HolderState {
        final Field top;
        final Field bottom;
        final Field data;
        final Field lottie;

        HolderState(Field top, Field bottom, Field data, Field lottie) {
            this.top = top;
            this.bottom = bottom;
            this.data = data;
            this.lottie = lottie;
        }
    }
}
