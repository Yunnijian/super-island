package io.github.superisland.model

/**
 * App-owned settings for the Root-only screen recording extension.
 *
 * These values never cross the third-party notification transport and intentionally use a small,
 * closed set of wire values. Storage is represented as a persisted SAF tree URI string; the
 * Android framework layer performs the final DocumentsContract validation before using it.
 */
const val SCREEN_RECORDING_CONFIG_SCHEMA_VERSION = 2

enum class ScreenRecordingResolution(
    val wireValue: String,
    private val targetShortSide: Int,
) {
    NATIVE("native", 0),
    FHD("1080p", 1080),
    HD("720p", 720),
    SD("480p", 480),
    LOW("360p", 360),
    ;

    fun resolveDimensions(
        screenWidth: Int,
        screenHeight: Int,
    ): Pair<Int, Int> {
        require(screenWidth > 0) { "screenWidth must be positive" }
        require(screenHeight > 0) { "screenHeight must be positive" }

        val shortSide = minOf(screenWidth, screenHeight)
        if (targetShortSide == 0 || targetShortSide >= shortSide) {
            return screenWidth.toEvenDown() to screenHeight.toEvenDown()
        }

        val scale = targetShortSide.toDouble() / shortSide
        val resolvedShortSide = targetShortSide.toEvenDown()
        val resolvedLongSide = (maxOf(screenWidth, screenHeight) * scale).toInt().toEvenDown()
        return if (screenWidth <= screenHeight) {
            resolvedShortSide to resolvedLongSide
        } else {
            resolvedLongSide to resolvedShortSide
        }
    }

    companion object {
        fun fromWire(value: String?): ScreenRecordingResolution? =
            entries.firstOrNull { it.wireValue == value }
    }
}

enum class ScreenRecordingBitrate(
    val wireValue: String,
    val bitsPerSecond: Int?,
) {
    AUTO("auto", null),
    MBPS_1("1m", 1_000_000),
    MBPS_4("4m", 4_000_000),
    MBPS_6("6m", 6_000_000),
    MBPS_8("8m", 8_000_000),
    MBPS_16("16m", 16_000_000),
    MBPS_24("24m", 24_000_000),
    MBPS_32("32m", 32_000_000),
    MBPS_50("50m", 50_000_000),
    MBPS_100("100m", 100_000_000),
    ;

    fun resolveBitsPerSecond(
        width: Int,
        height: Int,
        frameRate: Int,
    ): Int {
        bitsPerSecond?.let { return it }
        require(width > 0) { "width must be positive" }
        require(height > 0) { "height must be positive" }
        require(frameRate > 0) { "frameRate must be positive" }

        // A bounded VBR target. It is deliberately not configurable as arbitrary user text.
        val estimated = width.toLong() * height * frameRate * 18L / 100L
        return estimated.coerceIn(MIN_AUTO_BITRATE, MAX_AUTO_BITRATE).toInt()
    }

    companion object {
        const val MIN_AUTO_BITRATE = 1_000_000L
        const val MAX_AUTO_BITRATE = 100_000_000L

        fun fromWire(value: String?): ScreenRecordingBitrate? =
            entries.firstOrNull { it.wireValue == value }
    }
}

enum class ScreenRecordingOrientation(val wireValue: String) {
    AUTO("auto"),
    PORTRAIT("portrait"),
    LANDSCAPE("landscape"),
    ;

    fun applyTo(width: Int, height: Int): Pair<Int, Int> =
        when (this) {
            AUTO -> width to height
            PORTRAIT -> if (width <= height) width to height else height to width
            LANDSCAPE -> if (width >= height) width to height else height to width
        }

    companion object {
        fun fromWire(value: String?): ScreenRecordingOrientation? =
            entries.firstOrNull { it.wireValue == value }
    }
}

enum class ScreenRecordingAudioSource(val wireValue: String) {
    NONE("none"),
    INTERNAL("internal"),
    MICROPHONE("microphone"),
    BOTH("both"),
    ;

    val requiresRecordAudioPermission: Boolean
        get() = this != NONE

    companion object {
        fun fromWire(value: String?): ScreenRecordingAudioSource? =
            entries.firstOrNull { it.wireValue == value }
    }
}

enum class ScreenRecordingFrameRate(
    val wireValue: String,
    val framesPerSecond: Int?,
) {
    AUTO("auto", null),
    FPS_15("15", 15),
    FPS_24("24", 24),
    FPS_30("30", 30),
    FPS_48("48", 48),
    FPS_60("60", 60),
    FPS_90("90", 90),
    FPS_120("120", 120),
    ;

    fun resolveFramesPerSecond(displayMaximumFramesPerSecond: Int): Int =
        (framesPerSecond ?: displayMaximumFramesPerSecond)
            .coerceIn(MIN_FRAME_RATE, MAX_FRAME_RATE)

    companion object {
        const val MIN_FRAME_RATE = 15
        const val MAX_FRAME_RATE = 120

        fun fromWire(value: String?): ScreenRecordingFrameRate? =
            entries.firstOrNull { it.wireValue == value }
    }
}

enum class ScreenRecordingVideoCodec(
    val wireValue: String,
    val mimeType: String,
    val requiresHdrProfile: Boolean = false,
) {
    H264("h264", "video/avc"),
    H265("h265", "video/hevc"),
    H265_HDR("h265_hdr", "video/hevc", requiresHdrProfile = true),
    ;

    companion object {
        fun fromWire(value: String?): ScreenRecordingVideoCodec? =
            entries.firstOrNull { it.wireValue == value }
    }
}

enum class ScreenRecordingTileStyle(val wireValue: String) {
    RECORDING("recording"),
    APP_ICON("app_icon"),
    ;

    companion object {
        fun fromWire(value: String?): ScreenRecordingTileStyle? =
            entries.firstOrNull { it.wireValue == value }
    }
}

data class ScreenRecordingConfig(
    val schemaVersion: Int = SCREEN_RECORDING_CONFIG_SCHEMA_VERSION,
    val resolution: ScreenRecordingResolution = ScreenRecordingResolution.FHD,
    val bitrate: ScreenRecordingBitrate = ScreenRecordingBitrate.AUTO,
    val orientation: ScreenRecordingOrientation = ScreenRecordingOrientation.AUTO,
    val audioSource: ScreenRecordingAudioSource = ScreenRecordingAudioSource.NONE,
    val frameRate: ScreenRecordingFrameRate = ScreenRecordingFrameRate.AUTO,
    val videoCodec: ScreenRecordingVideoCodec = ScreenRecordingVideoCodec.H265,
    val showTouches: Boolean = false,
    val stopOnLockScreen: Boolean = false,
    // This changes a Xiaomi privacy setting during an active recording. Keep it opt-in.
    val bypassScreenShareProtection: Boolean = false,
    /**
     * When true, starting recording first shows an in-app confirm dialog (IslandRecorder-style)
     * before the system MediaProjection consent path.
     */
    val confirmBeforeStart: Boolean = true,
    /**
     * User preference to keep [android.app.AppOpsManager.OPSTR_PROJECT_MEDIA] allowed for this
     * package via the warsaw-gated SystemUI Root bridge. Actual AppOps state is checked live.
     */
    val projectMediaEnabled: Boolean = false,
    val tileStyle: ScreenRecordingTileStyle = ScreenRecordingTileStyle.RECORDING,
    val storageTreeUri: String = "",
) {
    fun normalized(): ScreenRecordingConfig =
        copy(
            schemaVersion = SCREEN_RECORDING_CONFIG_SCHEMA_VERSION,
            storageTreeUri = storageTreeUri.takeIf(::isSafeStorageTreeUri).orEmpty(),
        )

    companion object {
        const val MAX_STORAGE_TREE_URI_LENGTH = 4_096

        fun isSafeStorageTreeUri(value: String): Boolean =
            value.isEmpty() ||
                (value.length <= MAX_STORAGE_TREE_URI_LENGTH &&
                    value.startsWith("content://") &&
                    value.none { it.isWhitespace() })
    }
}

private fun Int.toEvenDown(): Int =
    when {
        this <= 1 -> 2
        else -> this - (this and 1)
    }
