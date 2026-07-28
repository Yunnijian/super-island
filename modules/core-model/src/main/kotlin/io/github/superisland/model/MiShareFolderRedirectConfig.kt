package io.github.superisland.model

/** Persisted user choice for redirecting Mi Share's received-folder action. */
data class MiShareFolderRedirectConfig(
    val enabled: Boolean = false,
)

/**
 * Narrow, version-probed contract for the Mi Share received-folder extension.
 *
 * It deliberately contains no user-provided package, component, or path. The hook accepts only
 * the two OEM folder-open actions observed on HyperOS and only Mi Share's fixed Download/MiShare
 * directory for the current Android user.
 */
object MiShareFolderRedirectContract {
    const val REMOTE_PREFERENCES = "SuperIslandMiShareFolder"
    const val KEY_ENABLED = "enabled"

    const val MISHARE_PACKAGE = "com.miui.mishare.connectivity"
    const val MT_MANAGER_PACKAGE = "bin.mt.plus"
    const val MT_MANAGER_SHORTCUT_ACTIVITY = "bin.mt.plus.ShortcutActivity"
    const val MT_MANAGER_SHORTCUT_ACTION = "bin.mt.plus.ACTION_SHORTCUT"
    const val MT_MANAGER_EXTRA_PATH = "path"
    const val MT_MANAGER_EXTRA_OPERATION = "operation"
    const val MT_MANAGER_EXTRA_FOLDER_COLOR_ICON = "folderColorIcon"
    const val MT_MANAGER_OPERATION_GOTO = "goto"
    const val MT_MANAGER_FOLDER_ICON = "ic_folder"

    const val OEM_NEW_FILE_EXPLORER_ACTION = "com.android.fileexplorer.tapShare.START_TRANSFER"
    const val OEM_LEGACY_FILE_EXPLORER_ACTION = "miui.intent.action.OPEN"
    const val OEM_LEGACY_EXPLORER_PATH_EXTRA = "explorer_path"

    @JvmStatic
    fun isMiShareFolderIntentAction(action: String?): Boolean =
        action == OEM_NEW_FILE_EXPLORER_ACTION || action == OEM_LEGACY_FILE_EXPLORER_ACTION

    /** Allows only Android's per-user external-storage alias for Download/MiShare. */
    @JvmStatic
    fun isCanonicalReceiveDirectory(path: String?): Boolean {
        val normalized = path?.trim()?.removeSuffix("/") ?: return false
        return normalized == "/sdcard/Download/MiShare" ||
            Regex("^/storage/emulated/[0-9]+/Download/MiShare$").matches(normalized)
    }
}
