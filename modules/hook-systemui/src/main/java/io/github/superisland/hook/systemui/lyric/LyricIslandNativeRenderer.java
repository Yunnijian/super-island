package io.github.superisland.hook.systemui.lyric;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.PendingIntent;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import io.github.superisland.source.lyric.LyricCanvasView;
import io.github.superisland.source.lyric.LyricIslandConfig;
import io.github.superisland.source.lyric.LyricSnapshot;
import io.github.superisland.source.lyric.IslandContentMode;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Reversible adapter for the player's own HyperOS Dynamic Island slots.
 *
 * <p>HyperLyric injects into resource-backed image/text module containers rather than creating a
 * second island. The OEM view tree is unstable across media updates, so this class keeps only weak
 * root references, hides (instead of destroys) OEM children, and restores every changed child when
 * the media package changes or the root is torn down.</p>
 */
final class LyricIslandNativeRenderer {
    private static final String LEFT_PARENT_NAME = "island_container_module_image_text_1";
    private static final String RIGHT_PARENT_NAME = "island_container_module_image_text_2";
    private static final String TEXT_CONTAINER_NAME = "island_container_module_text";
    private static final String[] RESOURCE_PACKAGES = {
            "miui.systemui.plugin", "com.android.systemui", "miui.systemui.dynamicisland",
            "miui.systemui"
    };
    private static final Object LOCK = new Object();
    private static final Map<ViewGroup, RootState> ROOTS = new WeakHashMap<>();

    private LyricIslandNativeRenderer() {}

    static void render(ViewGroup root, Object islandData) {
        if (root == null) return;
        if (!LyricIslandSystemUiHost.isNativeRendererActive()
                || !isEligibleMediaIsland(root, islandData)) {
            clear(root);
            return;
        }

        LyricSnapshot snapshot = LyricIslandSystemUiHost.nativeSnapshot();
        LyricIslandConfig config = LyricIslandSystemUiHost.nativeConfig();
        if (snapshot == null || config == null) {
            clear(root);
            return;
        }
        RotationController.setPlaybackActive(LyricIslandSystemUiHost.nativeIsPlaying());

        synchronized (LOCK) {
            int[] desiredSlots = desiredSlots(config);
            if (desiredSlots.length == 0) {
                // No lyric content is enabled for this native island. The caller may still own a
                // Focus fallback, so restore any stale native state and leave the OEM tree alone.
                clearLocked(root);
                return;
            }

            RootState state = ROOTS.get(root);
            if (state == null) {
                state = new RootState(root);
                ROOTS.put(root, state);
            }
            if (!state.reconcile(desiredSlots, snapshot, config, true)) {
                // Layout variants without the audited resource contract are left untouched. A
                // partial split binding is also unsafe because it would hide only half the
                // player's native lyric surface, so fail closed and use the Focus fallback.
                clearLocked(root);
                return;
            }
            LyricIslandSystemUiHost.suppressFallbackNotification();
        }
    }

    static void refreshAll() {
        LyricSnapshot snapshot = LyricIslandSystemUiHost.nativeSnapshot();
        LyricIslandConfig config = LyricIslandSystemUiHost.nativeConfig();
        synchronized (LOCK) {
            if (!LyricIslandSystemUiHost.isNativeRendererActive()
                    || snapshot == null || config == null) {
                clearAllLocked();
                return;
            }
            for (RootState state : new ArrayList<>(ROOTS.values())) {
                int[] desiredSlots = desiredSlots(config);
                if (!state.reconcile(desiredSlots, snapshot, config, false)) {
                    restore(state);
                    ROOTS.remove(state.root);
                }
            }
            if (!ROOTS.isEmpty()) LyricIslandSystemUiHost.suppressFallbackNotification();
        }
    }

    static void rebindAll() {
        List<ViewGroup> roots;
        synchronized (LOCK) {
            roots = new ArrayList<>(ROOTS.keySet());
        }
        for (ViewGroup root : roots) {
            render(root, null);
        }
    }

    static void updatePositions() {
        long position = LyricIslandSystemUiHost.nativePosition();
        float speed = LyricIslandSystemUiHost.nativePlaybackSpeed();
        boolean playing = LyricIslandSystemUiHost.nativeIsPlaying();
        RotationController.setPlaybackActive(playing);
        synchronized (LOCK) {
            for (RootState state : ROOTS.values()) {
                state.updatePosition(position, speed, playing);
            }
        }
    }

    static void freeze(ViewGroup root) {
        if (root == null) return;
        synchronized (LOCK) {
            RootState state = ROOTS.get(root);
            if (state != null) state.freeze(LyricIslandSystemUiHost.nativePosition());
        }
    }

    static boolean hasAttachedSlot() {
        synchronized (LOCK) {
            return !ROOTS.isEmpty();
        }
    }

    static void clearAll() {
        synchronized (LOCK) {
            clearAllLocked();
        }
    }

    static void clearRoot(ViewGroup root) {
        if (root == null) return;
        synchronized (LOCK) {
            clearLocked(root);
        }
    }

    private static void clear(ViewGroup root) {
        clearRoot(root);
    }

    private static void clearAllLocked() {
        for (RootState state : new ArrayList<>(ROOTS.values())) restore(state);
        ROOTS.clear();
        RotationController.cleanup();
    }

    private static void clearLocked(ViewGroup root) {
        RootState state = ROOTS.remove(root);
        if (state != null) restore(state);
    }

    /** Implements HyperLyric's media-island gate without depending on hidden OEM types. */
    private static boolean isEligibleMediaIsland(ViewGroup root, Object data) {
        Object candidate = data != null ? data : invokeNoArg(root, "getCurrentIslandData");
        Bundle extras = extractExtras(candidate);
        if (extras == null) return false;
        String mediaPackage = extras.getString("miui.pkg.name");
        if (mediaPackage == null || mediaPackage.isBlank()) return false;
        if (!hasPendingIntent(extras)) return false;
        String publisher = LyricIslandSystemUiHost.nativePublisher();
        return publisher != null && !publisher.isBlank() && mediaPackage.equals(publisher);
    }

    /**
     * HyperLyric's split mode owns both native text slots. In the ordinary layout, configured
     * lyric and music-info slots are replaced independently; an explicit NONE slot is untouched.
     */
    private static int[] desiredSlots(LyricIslandConfig config) {
        if (config.getLyricMode() == 1) return new int[] {0, 1};

        boolean leftEnabled = config.getContentLeft() != IslandContentMode.NONE;
        boolean rightEnabled = config.getContentRight() != IslandContentMode.NONE;
        if (leftEnabled && rightEnabled) return new int[] {0, 1};
        if (leftEnabled) return new int[] {0};
        if (rightEnabled) return new int[] {1};
        return new int[0];
    }

    private static Bundle extractExtras(Object data) {
        if (data instanceof Bundle) return (Bundle) data;
        Object extras = invokeNoArg(data, "getExtras");
        return extras instanceof Bundle ? (Bundle) extras : null;
    }

    @SuppressWarnings("deprecation")
    private static boolean hasPendingIntent(Bundle extras) {
        try {
            Parcelable value = extras.getParcelable("miui.pending.intent");
            return value instanceof PendingIntent;
        } catch (Throwable ignored) {
            try {
                return extras.getParcelable("miui.pending.intent", PendingIntent.class) != null;
            } catch (Throwable ignoredAgain) {
                return false;
            }
        }
    }

    private static Object invokeNoArg(Object receiver, String name) {
        if (receiver == null) return null;
        try {
            Method method = receiver.getClass().getMethod(name);
            method.setAccessible(true);
            return method.invoke(receiver);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ViewGroup findTextContainer(ViewGroup root, int slot) {
        String parentName = slot == 1 ? RIGHT_PARENT_NAME : LEFT_PARENT_NAME;
        ViewGroup parent = findNamedGroup(root, parentName);
        if (parent == null) return null;
        return findNamedGroup(parent, TEXT_CONTAINER_NAME);
    }

    private static ViewGroup findNamedGroup(ViewGroup root, String name) {
        int id = resolveId(root, name);
        if (id != 0) {
            View found = root.findViewById(id);
            if (found instanceof ViewGroup) return (ViewGroup) found;
        }
        return findNamedGroupRecursive(root, name);
    }

    private static ViewGroup findNamedGroupRecursive(View view, String name) {
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        if (resourceEntryName(group).equals(name)) return group;
        for (int i = 0; i < group.getChildCount(); i++) {
            ViewGroup found = findNamedGroupRecursive(group.getChildAt(i), name);
            if (found != null) return found;
        }
        return null;
    }

    private static int resolveId(View root, String name) {
        try {
            for (String packageName : RESOURCE_PACKAGES) {
                int id = root.getResources().getIdentifier(name, "id", packageName);
                if (id != 0) return id;
            }
            return root.getResources().getIdentifier(name, "id", root.getContext().getPackageName());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static String resourceEntryName(View view) {
        try {
            int id = view.getId();
            return id == View.NO_ID ? "" : view.getResources().getResourceEntryName(id);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static final class RootState {
        final ViewGroup root;
        final Map<Integer, SlotState> slots = new HashMap<>();

        RootState(ViewGroup root) {
            this.root = root;
        }

        /** Reconciles configured slots and returns false when the complete contract is unavailable. */
        boolean reconcile(
                int[] desiredSlots,
                LyricSnapshot snapshot,
                LyricIslandConfig config,
                boolean activate) {
            Map<Integer, ViewGroup> containers = new HashMap<>();
            for (int slot : desiredSlots) {
                ViewGroup container = findTextContainer(root, slot);
                if (container == null) return false;
                containers.put(slot, container);
            }

            // Restore slots which are no longer lyric-owned before attaching the new layout. This
            // is important when switching from split lyrics back to a music-info/lyric layout.
            List<Integer> stale = new ArrayList<>(slots.keySet());
            for (Integer slot : stale) {
                if (!containers.containsKey(slot)) {
                    restore(slots.remove(slot));
                }
            }

            for (int slot : desiredSlots) {
                SlotState state = slots.get(slot);
                ViewGroup container = containers.get(slot);
                if (state != null && state.container != container) {
                    restore(state);
                    state = null;
                }
                if (state == null) {
                    state = new SlotState(root, container, slot);
                    slots.put(slot, state);
                }
                state.ensureCanvas(snapshot, config, activate);
                state.hideNativeChildren();
            }
            return !slots.isEmpty();
        }

        void updatePosition(long position, float speed, boolean playing) {
            for (SlotState state : slots.values()) state.updatePosition(position, speed, playing);
        }

        void freeze(long position) {
            for (SlotState state : slots.values()) state.freeze(position);
        }
    }

    private static final class SlotState {
        final ViewGroup root;
        final ViewGroup container;
        final int slot;
        final Map<View, Integer> nativeVisibility = new IdentityHashMap<>();
        final int originalContainerVisibility;
        LyricCanvasView canvas;
        boolean metadataMode;
        boolean frozen;
        LyricIslandConfig currentConfig;
        final Map<View, Drawable> nativeDrawables = new IdentityHashMap<>();
        final Map<View, Float> nativeRotations = new IdentityHashMap<>();
        final Map<ImageView, ColorStateList> nativeTints = new IdentityHashMap<>();
        final Map<ImageView, PorterDuff.Mode> nativeTintModes = new IdentityHashMap<>();
        final Map<ImageView, ImageView.ScaleType> nativeScaleTypes = new IdentityHashMap<>();
        final Map<ImageView, ViewOutlineProvider> nativeOutlineProviders = new IdentityHashMap<>();
        final Map<ImageView, Boolean> nativeClipToOutlines = new IdentityHashMap<>();

        SlotState(ViewGroup root, ViewGroup container, int slot) {
            this.root = root;
            this.container = container;
            this.slot = slot;
            this.originalContainerVisibility = container.getVisibility();
        }

        void ensureCanvas(
                LyricSnapshot snapshot, LyricIslandConfig config, boolean activate) {
            currentConfig = config;
            boolean created = false;
            if (canvas == null || canvas.getParent() != container) {
                if (canvas != null && canvas.getParent() instanceof ViewGroup) {
                    ((ViewGroup) canvas.getParent()).removeView(canvas);
                }
                canvas = new LyricCanvasView(container.getContext());
                canvas.setTag(slot == 1 ? "SUPER_ISLAND_LYRIC_RIGHT" : "SUPER_ISLAND_LYRIC_LEFT");
                canvas.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                container.addView(canvas, params);
                created = true;
            }
            if (config.getLyricMode() == 1 ||
                    (slot == 0 && config.getContentLeft() == IslandContentMode.LYRIC) ||
                    (slot == 1 && config.getContentRight() == IslandContentMode.LYRIC)) {
                metadataMode = false;
                canvas.setSnapshot(snapshot, config, config.getLyricMode() == 1 ? slot : -1);
            } else {
                metadataMode = true;
                canvas.setMetadata(snapshot, config);
            }
            canvas.setPosition(
                    LyricIslandSystemUiHost.nativePosition(),
                    LyricIslandSystemUiHost.nativePlaybackSpeed());
            canvas.setPlaybackActive(LyricIslandSystemUiHost.nativeIsPlaying());
            if (created || activate) frozen = false;
        }

        void hideNativeChildren() {
            for (int i = 0; i < container.getChildCount(); i++) {
                View child = container.getChildAt(i);
                if (child == canvas) continue;
                reconcileNativeChild(child);
            }
            container.setVisibility(View.VISIBLE);
            if (canvas != null) canvas.setVisibility(View.VISIBLE);
        }

        /** Hide OEM text nodes while leaving the player's cover/rhythm visuals in place. */
        private void reconcileNativeChild(View child) {
            if (child == null) return;
            String name = resourceEntryName(child);
            boolean textNode = child instanceof TextView || name.contains("text") || name.contains("title");
            if (textNode) {
                rememberVisibility(child);
                // HyperLyric removes the OEM text node from measurement while its injected
                // wrapper owns the slot. Keep the original visibility for full restoration.
                child.setVisibility(View.GONE);
            } else {
                applyMediaVisual(child, name);
                if (child instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) child;
                    for (int i = 0; i < group.getChildCount(); i++) {
                        reconcileNativeChild(group.getChildAt(i));
                    }
                }
            }
        }

        private void rememberVisibility(View child) {
            if (!nativeVisibility.containsKey(child)) nativeVisibility.put(child, child.getVisibility());
        }

        private void applyMediaVisual(View child, String name) {
            if (!(child instanceof ImageView)) return;
            ImageView image = (ImageView) child;
            boolean cover = isCoverName(name);
            boolean wave = isWaveName(name);
            Object tag = image.getTag();
            if (tag instanceof String) {
                String tagName = (String) tag;
                cover |= isCoverName(tagName);
                wave |= isWaveName(tagName);
            }
            if (!cover && !wave) return;
            rememberVisibility(image);
            // Reconcile style changes against the OEM snapshot. Without this reset, switching
            // from app-icon/circle/wave-color back to the default leaves our previous drawable,
            // outline or tint attached until the whole island root is destroyed.
            restoreImageOverrides(image, false);
            if (cover) {
                int style = currentConfig == null ? 0 : currentConfig.getAlbumCoverStyle();
                if (style != 3) {
                    RotationController.detach(image);
                    restoreRotation(image);
                }
                if (style == 4) {
                    image.setVisibility(View.INVISIBLE);
                    return;
                }
                image.setVisibility(View.VISIBLE);
                if (style == 2) {
                    if (!nativeDrawables.containsKey(image)) nativeDrawables.put(image, image.getDrawable());
                    try {
                        String publisher = LyricIslandSystemUiHost.nativePublisher();
                        String packageName = publisher == null ? "" : publisher.split("[/:]")[0];
                        if (!packageName.isEmpty()) {
                            image.setImageDrawable(image.getContext().getPackageManager().getApplicationIcon(packageName));
                        }
                    } catch (Throwable ignored) {
                        // Keep the OEM cover when the publisher icon cannot be resolved.
                    }
                }
                if (style == 1 || style == 3) {
                    if (!nativeScaleTypes.containsKey(image)) nativeScaleTypes.put(image, image.getScaleType());
                    if (!nativeOutlineProviders.containsKey(image)) {
                        nativeOutlineProviders.put(image, image.getOutlineProvider());
                    }
                    if (!nativeClipToOutlines.containsKey(image)) {
                        nativeClipToOutlines.put(image, image.getClipToOutline());
                    }
                    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    image.setClipToOutline(true);
                    image.setOutlineProvider(new ViewOutlineProvider() {
                        @Override public void getOutline(View view, android.graphics.Outline outline) {
                            outline.setOval(0, 0, view.getWidth(), view.getHeight());
                        }
                    });
                    image.invalidateOutline();
                }
                if (style == 3) {
                    if (!nativeRotations.containsKey(image)) nativeRotations.put(image, image.getRotation());
                    RotationController.attach(image);
                }
            }
            if (wave) {
                int style = currentConfig == null ? 0 : currentConfig.getMusicWaveStyle();
                if (style == 3) {
                    image.setVisibility(View.INVISIBLE);
                    return;
                }
                image.setVisibility(View.VISIBLE);
                if (style == 1 || style == 2) {
                    List<Integer> colors = LyricIslandSystemUiHost.nativeArtworkColors();
                    if (!colors.isEmpty()) {
                        if (!nativeTints.containsKey(image)) nativeTints.put(image, image.getImageTintList());
                        if (!nativeTintModes.containsKey(image)) nativeTintModes.put(image, image.getImageTintMode());
                        image.setImageTintList(ColorStateList.valueOf(colors.get(0)));
                    }
                }
            }
        }

        private boolean isCoverName(String name) {
            // IslandIconViewHolder identifies this node by picInfo.pic ==
            // "miui_media_album_icon". View-tree fallback therefore accepts only the audited
            // resource-name aliases; generic image/icon names can belong to unrelated modules.
            return "miui_media_album_icon".equals(name)
                    || "media_album_icon".equals(name)
                    || "island_media_album_icon".equals(name)
                    || "island_album_icon".equals(name);
        }

        private boolean isWaveName(String name) {
            // The OEM pic values are exactly musicWave/musicPause. Keep a small resource alias
            // list for ROMs that expose those values as lower-case resource entry names.
            return "musicWave".equals(name)
                    || "musicPause".equals(name)
                    || "music_wave".equals(name)
                    || "music_pause".equals(name)
                    || "music_wave_view".equals(name)
                    || "music_pause_view".equals(name);
        }

        private void restoreImageOverrides(ImageView image, boolean restoreRotation) {
            Drawable drawable = nativeDrawables.get(image);
            if (drawable != null || nativeDrawables.containsKey(image)) {
                image.setImageDrawable(drawable);
            }
            ImageView.ScaleType scaleType = nativeScaleTypes.get(image);
            if (scaleType != null) image.setScaleType(scaleType);
            if (nativeOutlineProviders.containsKey(image)) {
                image.setOutlineProvider(nativeOutlineProviders.get(image));
            }
            if (nativeClipToOutlines.containsKey(image)) {
                image.setClipToOutline(nativeClipToOutlines.get(image));
            }
            if (nativeTints.containsKey(image)) {
                image.setImageTintList(nativeTints.get(image));
            }
            if (nativeTintModes.containsKey(image)) {
                image.setImageTintMode(nativeTintModes.get(image));
            }
            if (restoreRotation) restoreRotation(image);
            image.invalidateOutline();
        }

        private void restoreRotation(ImageView image) {
            Float rotation = nativeRotations.get(image);
            if (rotation != null) image.setRotation(rotation);
        }

        void updatePosition(long position, float speed, boolean playing) {
            if (canvas == null || canvas.getParent() != container) return;
            if (metadataMode) {
                canvas.updateMetadataPosition(position, speed);
                return;
            }
            canvas.setPosition(position, speed);
            canvas.setPlaybackActive(frozen ? false : playing);
            hideNativeChildren();
        }

        void freeze(long position) {
            if (canvas == null || canvas.getParent() != container) return;
            frozen = true;
            canvas.setPosition(position, 0f);
            canvas.setPlaybackActive(false);
        }
    }

    private static void restore(RootState state) {
        if (state == null) return;
        for (SlotState slot : new ArrayList<>(state.slots.values())) {
            restore(slot);
        }
        state.slots.clear();
    }

    private static void restore(SlotState state) {
        if (state == null) return;
        try {
            if (state.canvas != null && state.canvas.getParent() == state.container) {
                state.container.removeView(state.canvas);
            }
            for (Map.Entry<View, Integer> entry : state.nativeVisibility.entrySet()) {
                View view = entry.getKey();
                if (view != null) {
                    view.setVisibility(entry.getValue());
                }
            }
            for (Map.Entry<View, Drawable> entry : state.nativeDrawables.entrySet()) {
                if (entry.getKey() instanceof ImageView) {
                    ((ImageView) entry.getKey()).setImageDrawable(entry.getValue());
                }
            }
            for (Map.Entry<View, Float> entry : state.nativeRotations.entrySet()) {
                if (entry.getKey() instanceof ImageView) {
                    RotationController.detach((ImageView) entry.getKey());
                }
                entry.getKey().setRotation(entry.getValue());
            }
            for (Map.Entry<ImageView, ColorStateList> entry : state.nativeTints.entrySet()) {
                entry.getKey().setImageTintList(entry.getValue());
            }
            for (Map.Entry<ImageView, PorterDuff.Mode> entry : state.nativeTintModes.entrySet()) {
                entry.getKey().setImageTintMode(entry.getValue());
            }
            for (Map.Entry<ImageView, ImageView.ScaleType> entry : state.nativeScaleTypes.entrySet()) {
                entry.getKey().setScaleType(entry.getValue());
            }
            for (Map.Entry<ImageView, ViewOutlineProvider> entry : state.nativeOutlineProviders.entrySet()) {
                entry.getKey().setOutlineProvider(entry.getValue());
            }
            for (Map.Entry<ImageView, Boolean> entry : state.nativeClipToOutlines.entrySet()) {
                entry.getKey().setClipToOutline(entry.getValue());
                entry.getKey().invalidateOutline();
            }
            state.container.setVisibility(state.originalContainerVisibility);
        } catch (Throwable ignored) {
            // SystemUI may be tearing down the root; fail closed if the tree is already gone.
        }
    }

    /** Small lifecycle-aware controller matching HyperLyric's 20-second cover rotation. */
    private static final class RotationController {
        private static final long DURATION_MS = 20_000L;
        private static final Handler HANDLER = new Handler(Looper.getMainLooper());
        private static final Map<ImageView, RotationState> STATES = new WeakHashMap<>();
        private static boolean playbackActive = true;

        private static final View.OnAttachStateChangeListener ATTACH_LISTENER =
                new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View view) {
                        if (view instanceof ImageView) {
                            RotationState state = STATES.get((ImageView) view);
                            if (state != null) startIfNeeded((ImageView) view, state);
                        }
                    }

                    @Override
                    public void onViewDetachedFromWindow(View view) {
                        if (view instanceof ImageView) {
                            RotationState state = STATES.get((ImageView) view);
                            if (state != null) stop((ImageView) view, state, true);
                        }
                    }
                };

        private RotationController() {}

        static void attach(ImageView view) {
            if (view == null) return;
            runOnMain(() -> {
                RotationState state = STATES.get(view);
                if (state == null) {
                    state = new RotationState(view.getRotation());
                    STATES.put(view, state);
                    view.addOnAttachStateChangeListener(ATTACH_LISTENER);
                }
                startIfNeeded(view, state);
            });
        }

        static void detach(ImageView view) {
            if (view == null) return;
            runOnMain(() -> {
                RotationState state = STATES.remove(view);
                if (state == null) return;
                view.removeOnAttachStateChangeListener(ATTACH_LISTENER);
                stop(view, state, true);
            });
        }

        static boolean isAttached(ImageView view) {
            synchronized (STATES) {
                return STATES.containsKey(view);
            }
        }

        static void setPlaybackActive(boolean active) {
            runOnMain(() -> {
                playbackActive = active;
                for (Map.Entry<ImageView, RotationState> entry :
                        new ArrayList<>(STATES.entrySet())) {
                    if (active) {
                        startIfNeeded(entry.getKey(), entry.getValue());
                    } else {
                        ObjectAnimator animator = entry.getValue().animator;
                        if (animator != null && animator.isStarted() && !animator.isPaused()) {
                            animator.pause();
                        }
                    }
                }
            });
        }

        static void cleanup() {
            runOnMain(() -> {
                for (Map.Entry<ImageView, RotationState> entry :
                        new ArrayList<>(STATES.entrySet())) {
                    ImageView view = entry.getKey();
                    view.removeOnAttachStateChangeListener(ATTACH_LISTENER);
                    stop(view, entry.getValue(), true);
                }
                STATES.clear();
            });
        }

        private static void startIfNeeded(ImageView view, RotationState state) {
            if (!playbackActive || !view.isAttachedToWindow()) return;
            ObjectAnimator existing = state.animator;
            if (existing != null) {
                if (existing.isPaused()) existing.resume();
                return;
            }
            state.animator = ObjectAnimator.ofFloat(
                    view,
                    View.ROTATION,
                    view.getRotation(),
                    view.getRotation() + 360f);
            state.animator.setDuration(DURATION_MS);
            state.animator.setInterpolator(new LinearInterpolator());
            state.animator.setRepeatCount(ValueAnimator.INFINITE);
            state.animator.setRepeatMode(ValueAnimator.RESTART);
            state.animator.start();
        }

        private static void stop(ImageView view, RotationState state, boolean resetRotation) {
            if (state.animator != null) {
                state.animator.cancel();
                state.animator = null;
            }
            if (resetRotation) view.setRotation(state.originalRotation);
        }

        private static void runOnMain(Runnable runnable) {
            if (Looper.myLooper() == Looper.getMainLooper()) runnable.run();
            else HANDLER.post(runnable);
        }

        private static final class RotationState {
            final float originalRotation;
            ObjectAnimator animator;

            RotationState(float originalRotation) {
                this.originalRotation = originalRotation;
            }
        }
    }
}
