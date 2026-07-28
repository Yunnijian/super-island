package io.github.superisland.source.root

/** Hardware capabilities that require an exact, validated Root device adapter. */
enum class RootDeviceFeature {
    FAN_TELEMETRY,
    PERFORMANCE_TELEMETRY,
}

/**
 * Result of local device/ROM matching. An unsupported capability must never trigger `su`.
 */
class RootDeviceAdapterCapability internal constructor(
    val adapterId: String?,
    private val supportedFeatures: Set<RootDeviceFeature>,
    val summary: String,
) {
    val isSupported: Boolean
        get() = adapterId != null

    fun supports(feature: RootDeviceFeature): Boolean = feature in supportedFeatures
}

/**
 * Registry boundary for release-time Root device generalisation.
 *
 * Adapters are local, compile-time entries. A future device cannot inherit warsaw's nodes merely
 * by having Root; it must add its own feature list, fingerprint matcher, schema, bounds and
 * validation evidence before it is registered here.
 */
object RootDeviceAdapterRegistry {
    fun capability(
        device: String,
        fingerprint: String,
    ): RootDeviceAdapterCapability {
        val adapter = adapters.firstOrNull { it.matches(device, fingerprint) }
        if (adapter != null) return adapter.capability()
        return when (device) {
            WARSAW_DEVICE ->
                RootDeviceAdapterCapability(
                    adapterId = null,
                    supportedFeatures = emptySet(),
                    summary = "warsaw ROM 指纹未通过适配校验；不会请求 Root 或读取节点",
                )
            else ->
                RootDeviceAdapterCapability(
                    adapterId = null,
                    supportedFeatures = emptySet(),
                    summary = "当前设备为 ${device.ifBlank { "未知" }}；未适配 Root 硬件节点，不会请求 Root",
                )
        }
    }

    private val adapters = listOf(WarsawRootDeviceAdapter)
    private const val WARSAW_DEVICE = "warsaw"
}

private interface RootDeviceAdapter {
    fun matches(device: String, fingerprint: String): Boolean

    fun capability(): RootDeviceAdapterCapability
}

private object WarsawRootDeviceAdapter : RootDeviceAdapter {
    override fun matches(device: String, fingerprint: String): Boolean =
        device == WARSAW_DEVICE && WARSAW_HYPEROS_FINGERPRINT.matches(fingerprint)

    override fun capability(): RootDeviceAdapterCapability =
        RootDeviceAdapterCapability(
            adapterId = WARSAW_DEVICE,
            supportedFeatures =
                setOf(
                    RootDeviceFeature.FAN_TELEMETRY,
                    RootDeviceFeature.PERFORMANCE_TELEMETRY,
                ),
            summary = "warsaw / HyperOS 指纹已验证 · 仅在你主动读取时请求 Root",
        )

    private const val WARSAW_DEVICE = "warsaw"
    private val WARSAW_HYPEROS_FINGERPRINT =
        Regex("""^[^/]+/warsaw/warsaw:\d+/[^/]+/OS\d+(?:\.\d+){3}\.[A-Za-z0-9]+:user/release-keys$""")
}
