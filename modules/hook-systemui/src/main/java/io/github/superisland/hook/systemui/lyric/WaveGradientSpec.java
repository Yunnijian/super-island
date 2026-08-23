package io.github.superisland.hook.systemui.lyric;

import io.github.superisland.source.lyric.LyricIslandConfig;
import java.util.List;
import java.util.Objects;

/** Immutable two-stop palette for the OEM music-wave animation. */
final class WaveGradientSpec {
    final int top;
    final int bottom;

    private WaveGradientSpec(int top, int bottom) {
        this.top = top;
        this.bottom = bottom;
    }

    static WaveGradientSpec from(LyricIslandConfig config, List<Integer> colors) {
        if (config == null || config.getMusicWaveStyle() != 2 || colors == null || colors.isEmpty()) {
            return null;
        }
        int first = withNativeAlpha(colors.get(0));
        int second = withNativeAlpha(colors.size() > 1 ? colors.get(1) : colors.get(0));
        return new WaveGradientSpec(first, second);
    }

    private static int withNativeAlpha(int color) {
        return (color & 0x00FFFFFF) | (230 << 24);
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof WaveGradientSpec)) return false;
        WaveGradientSpec value = (WaveGradientSpec) other;
        return top == value.top && bottom == value.bottom;
    }

    @Override public int hashCode() {
        return Objects.hash(top, bottom);
    }
}
