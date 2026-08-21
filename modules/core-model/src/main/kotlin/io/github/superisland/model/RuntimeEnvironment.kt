package io.github.superisland.model

/**
 * Minimal product runtime environment for the LSPosed-only direction.
 *
 * Product UI only shows Root permission and LSPosed module activation. Activation is supplied
 * by the official libxposed service binder, not by manager package or filesystem guesses.
 */
data class RuntimeEnvironmentSnapshot(
    val rootAvailable: Boolean,
    val rootDetail: String,
    val lsposedActive: Boolean,
    val lsposedDetail: String,
    /** Concise, probe-derived Root implementation label for the visual status card. */
    val rootSummary: String = if (rootAvailable) "Root 已授权" else "未授权",
    /** Root-read LSPosed daemon version when the manager package is not installed. */
    val lsposedVersion: String? = null,
    /** Actual API reported by the official libxposed service; not a manager-version guess. */
    val lsposedApiVersion: Int? = null,
    /** True while the initial async probe has not yet completed; card should not flash error. */
    val isLoading: Boolean = false,
) {
    val environmentAcceptable: Boolean
        get() = !isLoading && rootAvailable && lsposedActive

    val statusLabel: String
        get() =
            when {
                isLoading -> "正在检查环境…"
                environmentAcceptable -> "环境已就绪"
                rootAvailable && !lsposedActive -> "已 Root · LSPosed 未激活"
                !rootAvailable && lsposedActive -> "LSPosed 已激活 · 未检测到 Root"
                else -> "环境未满足"
            }
}

/** Pure label helpers for unit tests without Android APIs. */
object RuntimeEnvironmentLabels {
    fun rootDetail(available: Boolean): String =
        if (available) {
            "Root 权限可用"
        } else {
            "未取得 Root 权限"
        }

    fun lsposedDetail(active: Boolean): String =
        if (active) {
            "LSPosed 已激活"
        } else {
            "LSPosed 未激活"
        }
}
