package io.github.superisland.model

/**
 * Closed IPC contract for the Root-only recording settings bridge in SystemUI.
 *
 * The app is the only permitted sender. There is no setting name, shell fragment, URI, or class
 * name in this transport: callers can request only prepare/restore of the two toggles displayed
 * in the recording screen, or PROJECT_MEDIA AppOps for this module package.
 *
 * The previous warsaw fingerprint gate has been removed: any Root-capable HyperOS device may use
 * the bridge. The constants remain for migration diagnostics only.
 */
object ScreenRecordingRootControlContract {
    const val MODULE_PACKAGE = "io.github.superisland"
    const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    const val SENDER_PERMISSION = "io.github.superisland.permission.SEND_SCREEN_RECORDING_CONTROL"

    const val ACTION_REQUEST = "io.github.superisland.action.SCREEN_RECORDING_ROOT_CONTROL"
    const val OPERATION_PREPARE = "prepare"
    const val OPERATION_RESTORE = "restore"
    const val OPERATION_SET_PROJECT_MEDIA = "set_project_media"

    const val METHOD_REPORT_RESULT = "report_screen_recording_root_control_v1"

    const val EXTRA_REQUEST_ID = "io.github.superisland.extra.SCREEN_RECORDING_ROOT_REQUEST_ID"
    const val EXTRA_OPERATION = "io.github.superisland.extra.SCREEN_RECORDING_ROOT_OPERATION"
    const val EXTRA_SHOW_TOUCHES = "io.github.superisland.extra.SCREEN_RECORDING_SHOW_TOUCHES"
    const val EXTRA_BYPASS_PROTECTION =
        "io.github.superisland.extra.SCREEN_RECORDING_BYPASS_SCREEN_SHARE_PROTECTION"
    const val EXTRA_ORIGINAL_SHOW_TOUCHES =
        "io.github.superisland.extra.SCREEN_RECORDING_ORIGINAL_SHOW_TOUCHES"
    const val EXTRA_ORIGINAL_PROTECTION =
        "io.github.superisland.extra.SCREEN_RECORDING_ORIGINAL_PROTECTION"
    const val EXTRA_ORIGINAL_PROTECTION_ON =
        "io.github.superisland.extra.SCREEN_RECORDING_ORIGINAL_PROTECTION_ON"
    const val EXTRA_PROJECT_MEDIA_ALLOWED =
        "io.github.superisland.extra.SCREEN_RECORDING_PROJECT_MEDIA_ALLOWED"
    const val EXTRA_RESULT_SUCCESS = "io.github.superisland.extra.SCREEN_RECORDING_ROOT_SUCCESS"
    const val EXTRA_RESULT_REASON = "io.github.superisland.extra.SCREEN_RECORDING_ROOT_REASON"

    const val VERIFIED_DEVICE = "warsaw"
    const val VERIFIED_FINGERPRINT =
        "Redmi/warsaw/warsaw:16/BP2A.250605.031.A3/OS3.0.306.0.WHPCNXM:user/release-keys"

    private val requestIdPattern = Regex("[A-Za-z0-9-]{16,64}")

    fun isValidRequestId(value: String?): Boolean = value != null && requestIdPattern.matches(value)

    fun isVerifiedDevice(
        device: String,
        fingerprint: String,
    ): Boolean = true
}
