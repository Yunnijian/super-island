package io.github.superisland.hook.systemui.lyric;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.PendingIntent;
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
    /**
     * Audited on the supported OS3/OS4 MIUI SystemUI plugin builds. The OEM Lottie callback reads
     * these static fields for every frame, which is the only native path that preserves a two-stop
     * rhythm gradient.
     */
    private static final String ISLAND_ICON_HOLDER_CLASS =
            "miui.systemui.dynamicisland.module.IslandIconViewHolder";
    private static final int NATIVE_WAVE_COLOR_ALPHA = 230;
    private static final String[] RESOURCE_PACKAGES = {
            "miui.systemui.plugin", "com.android.systemui", "miui.systemui.dynamicisland",
            "miui.systemui"
    };
    private static final Object LOCK = new Object();
    private static final Map<ViewGroup, RootState> ROOTS = new WeakHashMap<>();
    private static final WaveGradientController WAVE_GRADIENTS = new WaveGradientController();

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
            refreshWaveGradientsLocked();
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
            List<RootState> detached = new ArrayList<>();
            for (RootState state : new ArrayList<>(ROOTS.values())) {
                int[] desiredSlots = desiredSlots(config);
                if (!state.reconcile(desiredSlots, snapshot, config, false)) {
                    ROOTS.remove(state.root);
                    detached.add(state);
                }
            }
            refreshWaveGradientsLocked(detached);
            for (RootState state : detached) restore(state);
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

    static boolean hasRoot(ViewGroup root) {
        if (root == null) return false;
        synchronized (LOCK) {
            return ROOTS.containsKey(root);
        }
    }

    static boolean clearRoot(ViewGroup root) {
        if (root == null) return false;
        synchronized (LOCK) {
            return clearLocked(root);
        }
    }

    private static void clear(ViewGroup root) {
        clearRoot(root);
    }

    private static void clearAllLocked() {
        List<RootState> states = new ArrayList<>(ROOTS.values());
        WAVE_GRADIENTS.restoreAll(states);
        for (RootState state : states) restore(state);
        ROOTS.clear();
        RotationController.cleanup();
    }

    private static boolean clearLocked(ViewGroup root) {
        RootState state = ROOTS.remove(root);
        if (state == null) return false;
        // Restore the shared OEM Lottie colors before its view becomes visible again. Other
        // attached roots in the same plugin ClassLoader retain the requested override.
        WAVE_GRADIENTS.invalidateRoot(root);
        refreshWaveGradientsLocked(java.util.Collections.singletonList(state));
        restore(state);
        return true;
    }

    /** Reconciles the static Lottie color pair once per content/config update, never per tick. */
    private static void refreshWaveGradientsLocked() {
        refreshWaveGradientsLocked(java.util.Collections.emptyList());
    }

    private static void refreshWaveGradientsLocked(List<RootState> additionalInvalidationRoots) {
        Map<ClassLoader, WaveGradientSpec> desired = new HashMap<>();
        Map<ClassLoader, List<ViewGroup>> rootsByLoader = new HashMap<>();
        for (RootState state : ROOTS.values()) {
            state.contributeWaveGradient(desired, rootsByLoader);
        }
        for (RootState state : additionalInvalidationRoots) {
            state.contributeInvalidationRoot(rootsByLoader);
        }
        WAVE_GRADIENTS.reconcile(desired, rootsByLoader);
    }

    /** Implements HyperLyric's media-island gate without depending on hidden OEM types. */
    private static boolean isEligibleMediaIsland(ViewGroup root, Object data) {
        Object candidate = data != null ? data : invokeNoArg(root, "getCurrentIslandData");
        Bundle extras = extractExtras(candidate);
        if (extras == null) return false;
        String mediaPackage = packagePart(extras.getString("miui.pkg.name"));
        if (mediaPackage == null || mediaPackage.isBlank()) return false;
        if (!hasPendingIntent(extras)) return false;
        String publisher = packagePart(LyricIslandSystemUiHost.nativePublisher());
        return publisher != null && !publisher.isBlank() && mediaPackage.equals(publisher);
    }

    /** OEM data may carry a component/class suffix while MediaSession uses the bare package. */
    private static String packagePart(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return normalized;
        int slash = normalized.indexOf('/');
        int colon = normalized.indexOf(':');
        int end = normalized.length();
        if (slash >= 0) end = Math.min(end, slash);
        if (colon >= 0) end = Math.min(end, colon);
        return normalized.substring(0, end).trim();
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

    private static String slotKey(int[] slots) {
        StringBuilder value = new StringBuilder();
        for (int slot : slots) value.append(slot).append(',');
        return value.toString();
    }

    /**
     * Content/style signature intentionally excludes playback position. Position ticks are
     * handled by SlotState.updatePosition; rebuilding the OEM subtree for every tick caused the
     * status-bar shade animation to compete with recursive child traversal on the main thread.
     */
    private static int contentSignature(LyricSnapshot snapshot, LyricIslandConfig config) {
        if (snapshot == null || config == null) return 0;
        int result = 17;
        result = 31 * result + (snapshot.getPublisher() == null ? 0 : snapshot.getPublisher().hashCode());
        result = 31 * result + (snapshot.getLine() == null ? 0 : snapshot.getLine().hashCode());
        result = 31 * result + (snapshot.getSecondary() == null ? 0 : snapshot.getSecondary().hashCode());
        result = 31 * result + (snapshot.getTranslation() == null ? 0 : snapshot.getTranslation().hashCode());
        result = 31 * result + (snapshot.getTitle() == null ? 0 : snapshot.getTitle().hashCode());
        result = 31 * result + (snapshot.getArtist() == null ? 0 : snapshot.getArtist().hashCode());
        result = 31 * result + (snapshot.getAlbum() == null ? 0 : snapshot.getAlbum().hashCode());
        result = 31 * result + (snapshot.getArtworkColors() == null ? 0 : snapshot.getArtworkColors().hashCode());
        result = 31 * result + (snapshot.getStopped() ? 1 : 0);
        return 31 * result + config.hashCode();
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
        private int contentSignature;
        private LyricSnapshot signatureSnapshot;
        private LyricIslandConfig signatureConfig;
        private String desiredSlotKey = "";
        private boolean hasRendered;
        private WaveGradientSpec waveGradient;

        RootState(ViewGroup root) {
            this.root = root;
        }

        /** Reconciles configured slots and returns false when the complete contract is unavailable. */
        boolean reconcile(
                int[] desiredSlots,
                LyricSnapshot snapshot,
                LyricIslandConfig config,
                boolean activate) {
            int nextSignature;
            if (signatureSnapshot == snapshot && signatureConfig == config) {
                nextSignature = contentSignature;
            } else {
                nextSignature = contentSignature(snapshot, config);
                signatureSnapshot = snapshot;
                signatureConfig = config;
            }
            String nextSlotKey = slotKey(desiredSlots);
            if (hasRendered
                    && contentSignature == nextSignature
                    && desiredSlotKey.equals(nextSlotKey)
                    && canReuseAttachedSlots()) {
                if (activate) {
                    for (SlotState state : slots.values()) state.frozen = false;
                }
                // Playback position is advanced by POSITION_TICK. Re-reading the interpolated
                // clock and driving the rich renderer from every OEM island callback makes shade
                // expansion compete with the lyric animation on the same main looper.
                return true;
            }
            // The OEM may have replaced descendants without changing the root identity. Make the
            // next wave reconciliation walk the rebuilt tree instead of trusting its old holder
            // cache. This is also used when a content/config signature changes.
            WAVE_GRADIENTS.invalidateRoot(root);
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
            contentSignature = nextSignature;
            desiredSlotKey = nextSlotKey;
            hasRendered = !slots.isEmpty();
            waveGradient = WaveGradientSpec.from(config, snapshot.getArtworkColors());
            return !slots.isEmpty();
        }

        void contributeWaveGradient(
                Map<ClassLoader, WaveGradientSpec> desired,
                Map<ClassLoader, List<ViewGroup>> rootsByLoader) {
            ClassLoader loader = nativeClassLoader();
            if (loader == null) return;
            rootsByLoader.computeIfAbsent(loader, ignored -> new ArrayList<>()).add(root);
            if (waveGradient != null && !desired.containsKey(loader)) {
                desired.put(loader, waveGradient);
            }
        }

        void contributeInvalidationRoot(Map<ClassLoader, List<ViewGroup>> rootsByLoader) {
            ClassLoader loader = nativeClassLoader();
            if (loader == null) return;
            rootsByLoader.computeIfAbsent(loader, ignored -> new ArrayList<>()).add(root);
        }

        private ClassLoader nativeClassLoader() {
            return root.getClass().getClassLoader();
        }

        private boolean canReuseAttachedSlots() {
            if (slots.isEmpty()) return false;
            for (SlotState state : slots.values()) {
                if (state.container == null || state.canvas == null
                        || state.canvas.getParent() != state.container
                        || state.container.getVisibility() != View.VISIBLE
                        || state.childStructureSignature != state.childStructureSignature()) {
                    return false;
                }
            }
            return true;
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
        int childStructureSignature;
        LyricCanvasView canvas;
        boolean metadataMode;
        boolean frozen;
        LyricIslandConfig currentConfig;
        final Map<View, Drawable> nativeDrawables = new IdentityHashMap<>();
        final Map<View, Float> nativeRotations = new IdentityHashMap<>();
        final Map<ImageView, ImageView.ScaleType> nativeScaleTypes = new IdentityHashMap<>();
        final Map<ImageView, ViewOutlineProvider> nativeOutlineProviders = new IdentityHashMap<>();
        final Map<ImageView, Boolean> nativeClipToOutlines = new IdentityHashMap<>();
        int lastStatusBarColor = Integer.MIN_VALUE;
        long lastPosition = Long.MIN_VALUE;
        float lastSpeed = Float.NaN;
        boolean lastPlaying;
        boolean hasPosition;

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
                lastStatusBarColor = Integer.MIN_VALUE;
                hasPosition = false;
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
            applyCanvasWidth(config);
            // HyperLyric measures the new line before committing dynamic island geometry. The
            // copied renderer may finish its text measurement one frame after setSnapshot(), so
            // re-evaluate once on the view queue instead of waiting for another OEM media event.
            if (config.getWidthMode() != 0) {
                canvas.post(() -> {
                    if (canvas.getParent() == container && currentConfig == config) {
                        applyCanvasWidth(config);
                    }
                });
            }
            updateStatusBarTextColor();
            long position = LyricIslandSystemUiHost.nativePosition();
            float speed = LyricIslandSystemUiHost.nativePlaybackSpeed();
            boolean playing = frozen ? false : LyricIslandSystemUiHost.nativeIsPlaying();
            canvas.setPosition(position, speed);
            canvas.setPlaybackActive(playing);
            lastPosition = position;
            lastSpeed = speed;
            lastPlaying = playing;
            hasPosition = true;
            if (created || activate) frozen = false;
        }

        private void applyCanvasWidth(LyricIslandConfig config) {
            if (canvas == null || canvas.getParent() != container) return;
            ViewGroup.LayoutParams canvasParams = canvas.getLayoutParams();
            int targetWidth = canvas.configuredWidthPx(config);
            if (canvasParams != null && canvasParams.width != targetWidth) {
                canvasParams.width = targetWidth;
                canvas.setLayoutParams(canvasParams);
                container.requestLayout();
            }
        }

        void hideNativeChildren() {
            for (int i = 0; i < container.getChildCount(); i++) {
                View child = container.getChildAt(i);
                if (child == canvas) continue;
                reconcileNativeChild(child);
            }
            container.setVisibility(View.VISIBLE);
            if (canvas != null) canvas.setVisibility(View.VISIBLE);
            childStructureSignature = childStructureSignature();
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
            if (restoreRotation) restoreRotation(image);
            image.invalidateOutline();
        }

        private void restoreRotation(ImageView image) {
            Float rotation = nativeRotations.get(image);
            if (rotation != null) image.setRotation(rotation);
        }

        void updatePosition(long position, float speed, boolean playing) {
            if (canvas == null || canvas.getParent() != container) return;
            boolean effectivePlaying = frozen ? false : playing;
            if (hasPosition && lastPosition == position && lastSpeed == speed
                    && lastPlaying == effectivePlaying) return;
            updateStatusBarTextColor();
            if (metadataMode) {
                canvas.updateMetadataPosition(position, speed, effectivePlaying);
            } else {
                canvas.setPosition(position, speed);
                canvas.setPlaybackActive(effectivePlaying);
            }
            lastPosition = position;
            lastSpeed = speed;
            lastPlaying = effectivePlaying;
            hasPosition = true;
        }

        private void updateStatusBarTextColor() {
            int statusBarColor = (root.getSystemUiVisibility() & View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) != 0
                    ? 0xFF000000 : 0xFFFFFFFF;
            if (lastStatusBarColor == statusBarColor) return;
            lastStatusBarColor = statusBarColor;
            if (canvas != null) canvas.setStatusBarTextColor(statusBarColor);
        }

        int childStructureSignature() {
            int result = container.getChildCount();
            for (int i = 0; i < container.getChildCount(); i++) {
                View child = container.getChildAt(i);
                result = 31 * result + System.identityHashCode(child);
            }
            return result;
        }

        void freeze(long position) {
            if (canvas == null || canvas.getParent() != container) return;
            frozen = true;
            canvas.setPosition(position, 0f);
            canvas.setPlaybackActive(false);
            lastPosition = position;
            lastSpeed = 0f;
            lastPlaying = false;
            hasPosition = true;
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
        private static volatile boolean playbackActive = true;

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
            if (playbackActive == active) return;
            runOnMain(() -> {
                if (playbackActive == active) return;
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
