package io.github.superisland.model

enum class BatterySystemEvent(
    val displayName: String,
    val shortStatus: String,
) {
    POWER_CONNECTED("已接入电源", "已接电源"),
    POWER_DISCONNECTED("已断开电源", "已断电源"),
    CHARGING_STARTED("开始充电", "充电中"),
    FULL("电池已充满", "已充满"),
    DISCHARGING_STARTED("开始放电", "放电中"),
    WIRED_HEADSET_CONNECTED("已连接有线耳机", "耳机已连"),
    WIRED_HEADSET_DISCONNECTED("已断开有线耳机", "耳机已断"),
    BLUETOOTH_DEVICE_CONNECTED("已连接蓝牙设备", "蓝牙已连"),
    BLUETOOTH_DEVICE_DISCONNECTED("已断开蓝牙设备", "蓝牙已断"),
    BLUETOOTH_DISABLED("蓝牙已关闭", "蓝牙已关"),
    WIFI_NETWORK_CONNECTED("已切换至 Wi-Fi", "Wi-Fi 已连"),
    CELLULAR_NETWORK_CONNECTED("已切换至移动网络", "移动网络"),
    ETHERNET_NETWORK_CONNECTED("已切换至有线网络", "有线网络"),
    VPN_NETWORK_CONNECTED("已切换至 VPN 网络", "VPN 网络"),
    OTHER_NETWORK_CONNECTED("默认网络已连接", "网络已连"),
    NETWORK_DISCONNECTED("默认网络已断开", "网络已断"),
    ;

    companion object {
        fun fromWiredHeadsetState(state: Int): BatterySystemEvent =
            if (state == 1) WIRED_HEADSET_CONNECTED else WIRED_HEADSET_DISCONNECTED

        fun fromBluetoothConnectionState(state: Int): BatterySystemEvent? =
            when (state) {
                2 -> BLUETOOTH_DEVICE_CONNECTED
                0 -> BLUETOOTH_DEVICE_DISCONNECTED
                else -> null
            }
    }
}

/** A privacy-preserving classification of the app's default network, with no network identity. */
enum class DefaultNetworkTransport {
    OFFLINE,
    WIFI,
    CELLULAR,
    ETHERNET,
    VPN,
    OTHER,
}

/** Emits changes after a silent initial default-network baseline. */
class DefaultNetworkEventTracker {
    private var previous: DefaultNetworkTransport? = null

    fun observe(current: DefaultNetworkTransport): BatterySystemEvent? {
        val prior = previous
        previous = current
        if (prior == null || prior == current) return null
        return when (current) {
            DefaultNetworkTransport.OFFLINE -> BatterySystemEvent.NETWORK_DISCONNECTED
            DefaultNetworkTransport.WIFI -> BatterySystemEvent.WIFI_NETWORK_CONNECTED
            DefaultNetworkTransport.CELLULAR -> BatterySystemEvent.CELLULAR_NETWORK_CONNECTED
            DefaultNetworkTransport.ETHERNET -> BatterySystemEvent.ETHERNET_NETWORK_CONNECTED
            DefaultNetworkTransport.VPN -> BatterySystemEvent.VPN_NETWORK_CONNECTED
            DefaultNetworkTransport.OTHER -> BatterySystemEvent.OTHER_NETWORK_CONNECTED
        }
    }
}

/** Emits only meaningful charge-state transitions after its initial baseline sample. */
class BatteryChargeEventTracker {
    private var previous: BatteryChargeState? = null

    fun observe(current: BatteryChargeState): BatterySystemEvent? {
        val prior = previous
        previous = current
        if (prior == null || prior == current) return null
        return when (current) {
            BatteryChargeState.CHARGING -> BatterySystemEvent.CHARGING_STARTED
            BatteryChargeState.FULL -> BatterySystemEvent.FULL
            BatteryChargeState.DISCHARGING -> BatterySystemEvent.DISCHARGING_STARTED
            BatteryChargeState.NOT_CHARGING,
            BatteryChargeState.UNKNOWN,
            -> null
        }
    }
}
