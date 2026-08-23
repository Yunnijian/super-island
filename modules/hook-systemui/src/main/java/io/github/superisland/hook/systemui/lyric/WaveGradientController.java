package io.github.superisland.hook.systemui.lyric;

import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Applies HyperLyric's audited gradientTopColor/gradientBottomColor fields when the OEM holder
 * exposes them. Every reflection failure is fail-closed and leaves the OEM animation untouched.
 */
final class WaveGradientController {
    private static final String HOLDER = "miui.systemui.dynamicisland.module.IslandIconViewHolder";
    private final Map<Object, Original> originals = new WeakHashMap<>();

    void reconcile(
            Map<ClassLoader, WaveGradientSpec> desired,
            Map<ClassLoader, List<ViewGroup>> rootsByLoader) {
        for (Map.Entry<ClassLoader, List<ViewGroup>> entry : rootsByLoader.entrySet()) {
            WaveGradientSpec spec = desired.get(entry.getKey());
            for (ViewGroup root : entry.getValue()) {
                if (root != null) applyTree(root, spec);
            }
        }
    }

    void restoreAll(List<?> ignored) {
        for (Map.Entry<Object, Original> entry : new IdentityHashMap<>(originals).entrySet()) {
            restore(entry.getKey(), entry.getValue());
        }
        originals.clear();
    }

    private void applyTree(View view, WaveGradientSpec spec) {
        if (view == null) return;
        if (HOLDER.equals(view.getClass().getName())) applyHolder(view, spec);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) applyTree(group.getChildAt(i), spec);
        }
    }

    private void applyHolder(Object holder, WaveGradientSpec spec) {
        try {
            Field top = holder.getClass().getDeclaredField("gradientTopColor");
            Field bottom = holder.getClass().getDeclaredField("gradientBottomColor");
            top.setAccessible(true);
            bottom.setAccessible(true);
            Original original = originals.get(holder);
            if (original == null) {
                original = new Original(top.getInt(holder), bottom.getInt(holder));
                originals.put(holder, original);
            }
            if (spec == null) {
                restore(holder, original);
            } else {
                top.setInt(holder, spec.top);
                bottom.setInt(holder, spec.bottom);
                invalidate(holder);
            }
        } catch (Throwable ignored) {
            // OEM field names are version-specific; retain stock behavior when unavailable.
        }
    }

    private void restore(Object holder, Original original) {
        try {
            Field top = holder.getClass().getDeclaredField("gradientTopColor");
            Field bottom = holder.getClass().getDeclaredField("gradientBottomColor");
            top.setAccessible(true);
            bottom.setAccessible(true);
            top.setInt(holder, original.top);
            bottom.setInt(holder, original.bottom);
            invalidate(holder);
        } catch (Throwable ignored) {
        }
    }

    private void invalidate(Object holder) {
        if (holder instanceof View) ((View) holder).invalidate();
    }

    private static final class Original {
        final int top;
        final int bottom;

        Original(int top, int bottom) {
            this.top = top;
            this.bottom = bottom;
        }
    }
}
