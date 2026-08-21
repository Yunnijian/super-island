package io.github.superisland.publisher.focus

/** Shared constants between the app config UI and the injected SystemUI lyric host. */
object LyricIslandContract {
    const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    const val LYRIC_NOTIFICATION_ID = 0x4C5952 // "LYR"

    /** libxposed RemotePreferences name for the lyric host. */
    const val REMOTE_PREFERENCES = "SuperIslandLyricHost"
    const val KEY_ENABLED = "enabled"

    /** Channel id reuses the resident focus channel family but stays distinct. */
    const val CHANNEL_ID = "focus_lyric_island"
}
