package io.github.hyperisland.xposed

import io.github.libxposed.api.XposedModule

/**
 * Minimal compatibility surface required by the pinned HyperIsland notification renderer.
 *
 * Product rules belong to Super Island's immutable A/B snapshot and are deliberately unavailable
 * through this object. Upstream appearance options therefore retain their explicit call-site
 * defaults instead of opening a second configuration authority.
 */
object ConfigManager {
    @Volatile
    private var xposedModule: XposedModule? = null

    @JvmStatic
    fun init(module: XposedModule) {
        xposedModule = module
    }

    @JvmStatic
    fun getBoolean(
        key: String,
        default: Boolean,
    ): Boolean = default

    @JvmStatic
    fun isDebugLogEnabled(): Boolean = false

    @JvmStatic
    fun module(): XposedModule? = xposedModule
}
