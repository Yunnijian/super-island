package io.github.superisland.hook.systemui

import io.github.superisland.model.IslandPriority

internal object SmartCapsuleIslandPriorityPolicy {
    const val SUPPORTED_FINGERPRINT =
        "Redmi/warsaw/warsaw:16/BP2A.250605.031.A3/OS3.0.306.0.WHPCNXM:user/release-keys"

    fun effective(
        configured: IslandPriority,
        fingerprint: String,
    ): IslandPriority = configured
}
