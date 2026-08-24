package io.github.superisland

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.github.superisland.publisher.focus.LyricIslandContract
import io.github.superisland.source.lyric.LyricIslandConfig
import io.github.superisland.source.lyric.LyricIslandConfigCodec

/**
 * Owns the lyric-island switch: local SharedPreferences for UI echo, libxposed
 * RemotePreferences for the injected SystemUI host.
 */
object LyricIslandHostConfigSync {
    private const val TAG = "SuperIslandLyricConfig"
    private const val LOCAL_FILE = "lyric_island_config"
    private const val LOCAL_KEY_ENABLED = "enabled"
    private const val LOCAL_KEY_JSON = "config_json"

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var started = false

    @Volatile
    private var runtimeSyncObserverRegistered = false

    fun start(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            appContext = context.applicationContext
            XposedRuntimeController.start()
            started = true
            if (!runtimeSyncObserverRegistered) {
                runtimeSyncObserverRegistered = true
                XposedRuntimeController.observe { runtime ->
                    if (runtime.active) {
                        // The libxposed service is bound asynchronously. Mirror the persisted
                        // document only after that bind, otherwise the SystemUI runtime keeps
                        // the previous HyperLyric keys until a user changes a setting again.
                        sync(loadConfig())
                    }
                }
            }
        }
    }

    fun isEnabled(): Boolean {
        val context = appContext ?: return false
        return localPreferences(context).getBoolean(LOCAL_KEY_ENABLED, false)
    }

    fun loadConfig(): LyricIslandConfig {
        val context = appContext ?: return LyricIslandConfig()
        val preferences = localPreferences(context)
        val raw = preferences.getString(LOCAL_KEY_JSON, null)
        return if (raw.isNullOrBlank()) {
            LyricIslandConfig(enabled = preferences.getBoolean(LOCAL_KEY_ENABLED, false))
        } else {
            LyricIslandConfigCodec.decode(raw).copy(
                enabled = preferences.getBoolean(LOCAL_KEY_ENABLED, false),
            )
        }
    }

    fun sync(enabled: Boolean): Result<Unit> =
        sync(loadConfig().copy(enabled = enabled))

    fun sync(config: LyricIslandConfig): Result<Unit> =
        runCatching {
            val context = checkNotNull(appContext) { "Lyric host config sync has not started" }
            val normalized = config.normalized()
            val encoded = LyricIslandConfigCodec.encode(normalized)
            val local = localPreferences(context)
            val previousEnabled = local.getBoolean(LOCAL_KEY_ENABLED, false)
            val previousJson = local.getString(LOCAL_KEY_JSON, null)
            try {
                // Commit the local echo only as part of the same logical transaction as the
                // protected SystemUI write. A missing LSPosed bridge must not leave stale UI state.
                check(
                    local.edit()
                        .putBoolean(LOCAL_KEY_ENABLED, normalized.enabled)
                        .putString(LOCAL_KEY_JSON, encoded)
                        .commit(),
                ) { "Lyric island local config write failed" }
                val preferences =
                    checkNotNull(
                        XposedRuntimeController.remotePreferences(
                            LyricIslandContract.REMOTE_PREFERENCES,
                        ),
                    ) { "LSPosed SystemUI scope is not active" }
                check(
                    preferences.edit()
                        .putBoolean(LyricIslandContract.KEY_ENABLED, normalized.enabled)
                        .putString(LyricIslandContract.KEY_CONFIG_JSON, encoded)
                        .writeHyperLyricRuntimeConfig(normalized)
                        .commit(),
                ) { "Lyric island RemotePreferences write failed" }
            } catch (error: Throwable) {
                val rollback = local.edit().putBoolean(LOCAL_KEY_ENABLED, previousEnabled)
                if (previousJson == null) rollback.remove(LOCAL_KEY_JSON) else rollback.putString(LOCAL_KEY_JSON, previousJson)
                rollback.commit()
                throw error
            }
        }.onFailure { error ->
            Log.w(TAG, "Could not sync lyric-island config", error)
        }

    private fun localPreferences(context: Context): SharedPreferences =
        context.getSharedPreferences(LOCAL_FILE, Context.MODE_PRIVATE)

    /** Writes HyperLyric's unmodified SystemUI preference contract beside the versioned UI blob. */
    private fun SharedPreferences.Editor.writeHyperLyricRuntimeConfig(
        config: LyricIslandConfig,
    ): SharedPreferences.Editor = apply {
        putBoolean("key_hook_enable_super_island", config.enabled)
        putString("key_hook_lyric_source", config.sourceMode.wireValue)
        putInt("key_hook_lyric_mode", config.lyricMode)
        config.lyriconProviderDelays.forEach { (packageName, delay) ->
            putInt("key_hook_lyricon_provider_delay_$packageName", delay)
        }

        putInt("key_hook_island_content_left", config.contentLeft.wireValue)
        putInt("key_hook_island_content_right", config.contentRight.wireValue)
        putString("key_hook_island_music_info_first_line", config.musicInfoFirstLine)
        putString("key_hook_island_music_info_second_line", config.musicInfoSecondLine)
        putString("key_hook_island_music_info_separator", config.musicInfoSeparator)
        putBoolean("key_hook_center_music_info", config.centerMusicInfo)
        putBoolean("key_hook_center_lyric", config.centerLyric)
        putBoolean("key_hook_right_lyric", config.rightLyric)
        putInt("key_hook_island_width_mode", config.widthMode)
        putInt("key_hook_island_dynamic_width_basis", config.dynamicWidthBasis)
        putInt("key_hook_island_right_content_max_width", config.rightContentMaxWidth)
        putInt("key_hook_island_dynamic_min_width", config.dynamicMinWidth)
        putInt("key_hook_island_dynamic_max_width", config.dynamicMaxWidth)
        putBoolean("key_hook_island_disable_width_limit", config.disableWidthLimit)
        putInt("key_hook_island_left_padding_left", config.leftPaddingLeft)
        putInt("key_hook_island_left_padding_right", config.leftPaddingRight)
        putInt("key_hook_island_right_padding_left", config.rightPaddingLeft)
        putInt("key_hook_island_right_padding_right", config.rightPaddingRight)
        putInt("key_hook_island_album_cover_style", config.albumCoverStyle)
        putInt("key_hook_island_music_wave_style", config.musicWaveStyle)
        putInt("key_hook_island_behavior_after_pause", 0)

        putInt("key_hook_text_size", config.textSizeSp.toInt())
        putFloat("key_hook_text_size_ratio", config.textSizeRatio)
        putInt("key_hook_text_color_style", config.textColorStyle)
        putString("key_hook_custom_font_path", config.customFontPath)
        putInt("key_hook_font_weight", config.fontWeight)
        putBoolean("key_hook_font_italic", config.fontItalic)
        putBoolean("key_hook_narrow_latin_font", config.narrowLatinFont)
        putInt("key_hook_fading_edge_length", config.fadingEdgeLengthDp)
        putBoolean("key_hook_gradient_progress", config.gradientProgressStyle)
        putInt("key_hook_placeholder_format", config.placeholder.wireValue)

        putBoolean("key_hook_marquee_mode", config.marqueeMode)
        putInt("key_hook_marquee_speed", config.marqueeSpeed)
        putInt("key_hook_marquee_delay", config.marqueeDelay)
        putInt("key_hook_marquee_loop_delay", config.marqueeLoopDelay)
        putBoolean("key_hook_marquee_infinite", config.marqueeInfinite)
        putBoolean("key_hook_marquee_stop_end", config.marqueeStopEnd)
        putBoolean("key_hook_marquee_metadata_mode", config.metadataMarqueeMode)
        putInt("key_hook_marquee_metadata_speed", config.metadataMarqueeSpeed)
        putInt("key_hook_marquee_metadata_delay", config.metadataMarqueeDelay)
        putInt("key_hook_marquee_metadata_loop_delay", config.metadataMarqueeLoopDelay)
        putBoolean("key_hook_marquee_metadata_infinite", config.metadataMarqueeInfinite)

        putBoolean("key_hook_syllable_relative", config.syllableRelative)
        putBoolean("key_hook_syllable_highlight", config.syllableHighlight)
        putBoolean("key_hook_syllable_line_display", config.syllableLineDisplay)
        putBoolean("key_hook_disable_translation", config.disableTranslation)
        putBoolean("key_hook_translation_only", config.translationOnly)
        putBoolean("key_hook_swap_translation", config.swapTranslation)
        putBoolean("key_hook_next_lyric_line", config.nextLyricLine)
        putBoolean("key_hook_auto_switch_translation", config.autoSwitchTranslation)
        putBoolean("key_hook_word_motion_enabled", config.wordMotionEnabled)
        putBoolean("key_hook_word_motion_latin_by_character", config.wordMotionLatinByCharacter)
        putFloat("key_hook_word_motion_cjk_lift", config.wordMotionCjkLift)
        putFloat("key_hook_word_motion_cjk_wave", config.wordMotionCjkWave)
        putFloat("key_hook_word_motion_latin_lift", config.wordMotionLatinLift)
        putFloat("key_hook_word_motion_latin_wave", config.wordMotionLatinWave)
        putBoolean("key_hook_anim_enable", config.animEnabled)
        putString("key_hook_anim_id", config.animId)

        // HyperLyric media-card runtime owns these keys. Keep the host write contract one-to-one
        // with RootConstants so one RemotePreferences commit contains the complete card snapshot.
        putBoolean("key_hook_remove_island_whitelist", config.mediaCard.removeIslandWhitelist)
        val notification = config.mediaCard.notification
        putBoolean("key_hook_notification_media_card_switcher_enabled", notification.cardSwitcherEnabled)
        putInt("key_hook_notification_media_card_switcher_mode", notification.cardSwitcherMode)
        putInt("key_hook_notification_media_card_switcher_max_count", notification.cardSwitcherMaxCount)
        putInt("key_hook_notification_media_layout_style", notification.layoutStyle)
        putInt("key_hook_notification_media_ambient_flow_mode", notification.ambientFlowMode)
        putInt("key_hook_notification_media_card_theme", notification.cardTheme)
        putInt("key_hook_notification_media_cover_style", notification.coverStyle)
        putInt("key_hook_notification_media_progress_style", notification.progressStyle)
        putBoolean("key_hook_notification_media_progress_head_glow", notification.progressHeadGlow)
        putInt("key_hook_notification_media_thumb_style", notification.thumbStyle)
        putBoolean("key_hook_notification_media_hide_cover_source", notification.hideCoverSource)
        putBoolean("key_hook_notification_media_hide_cover_shadow", notification.hideCoverShadow)
        putBoolean("key_hook_notification_media_disable_cover_flip", notification.disableCoverFlip)
        putBoolean("key_hook_notification_media_hide_device_switch", notification.hideDeviceSwitch)
        putBoolean("key_hook_notification_media_hide_custom_actions", notification.hideCustomActions)
        putBoolean("key_hook_notification_media_hide_time", notification.hideTime)
        putBoolean("key_hook_notification_media_action_align_left", notification.actionAlignLeft)
        putInt("key_hook_notification_media_action_order", notification.actionOrder)
        putInt("key_hook_notification_media_background_style", notification.backgroundStyle)
        putInt("key_hook_notification_media_background_blur", notification.backgroundBlur)
        putBoolean("key_hook_notification_media_background_color_animation", notification.backgroundColorAnimation)
        putBoolean("key_hook_notification_media_background_auto_invert", notification.backgroundAutoInvert)
        putInt("key_hook_notification_media_soft_cover_tone", notification.softCoverTone)

        val island = config.mediaCard.islandExpanded
        putInt("key_hook_island_expanded_media_layout_style", island.layoutStyle)
        putInt("key_hook_island_expanded_media_ambient_flow_mode", island.ambientFlowMode)
        putInt("key_hook_island_expanded_media_card_theme", island.cardTheme)
        putInt("key_hook_island_expanded_media_cover_style", island.coverStyle)
        putInt("key_hook_island_expanded_media_progress_style", island.progressStyle)
        putBoolean("key_hook_island_expanded_media_progress_head_glow", island.progressHeadGlow)
        putInt("key_hook_island_expanded_media_thumb_style", island.thumbStyle)
        putBoolean("key_hook_island_expanded_media_hide_cover_source", island.hideCoverSource)
        putBoolean("key_hook_island_expanded_media_disable_cover_flip", island.disableCoverFlip)
        putBoolean("key_hook_island_expanded_media_hide_device_switch", island.hideDeviceSwitch)
        putBoolean("key_hook_island_expanded_media_hide_custom_actions", island.hideCustomActions)
        putBoolean("key_hook_island_expanded_media_hide_time", island.hideTime)
        putBoolean("key_hook_island_expanded_media_action_align_left", island.actionAlignLeft)
        putInt("key_hook_island_expanded_media_action_order", island.actionOrder)
        putInt("key_hook_island_expanded_media_background_style", island.backgroundStyle)
        putInt("key_hook_island_expanded_media_background_blur", island.backgroundBlur)
        putBoolean("key_hook_island_expanded_media_background_color_animation", island.backgroundColorAnimation)
        putBoolean("key_hook_island_expanded_media_background_auto_invert", island.backgroundAutoInvert)
        putInt("key_hook_island_expanded_media_soft_cover_tone", island.softCoverTone)

        putBoolean(
            "key_hook_aod_disable_media_card_collapsing",
            config.mediaCard.alwaysOnDisplay.disableMediaCardCollapsing,
        )
    }
}
