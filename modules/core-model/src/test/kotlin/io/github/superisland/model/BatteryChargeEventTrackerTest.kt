package io.github.superisland.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryChargeEventTrackerTest {
    @Test
    fun emitsOnlyStateTransitionsAfterBaseline() {
        val tracker = BatteryChargeEventTracker()

        assertNull(tracker.observe(BatteryChargeState.NOT_CHARGING))
        assertEquals(BatterySystemEvent.CHARGING_STARTED, tracker.observe(BatteryChargeState.CHARGING))
        assertNull(tracker.observe(BatteryChargeState.CHARGING))
        assertEquals(BatterySystemEvent.FULL, tracker.observe(BatteryChargeState.FULL))
        assertEquals(BatterySystemEvent.DISCHARGING_STARTED, tracker.observe(BatteryChargeState.DISCHARGING))
    }

    @Test
    fun mapsWiredHeadsetStateWithoutAndroidBroadcastDependency() {
        assertEquals(BatterySystemEvent.WIRED_HEADSET_CONNECTED, BatterySystemEvent.fromWiredHeadsetState(1))
        assertEquals(BatterySystemEvent.WIRED_HEADSET_DISCONNECTED, BatterySystemEvent.fromWiredHeadsetState(0))
    }

    @Test
    fun mapsOnlyFinalBluetoothConnectionStates() {
        assertEquals(
            BatterySystemEvent.BLUETOOTH_DEVICE_CONNECTED,
            BatterySystemEvent.fromBluetoothConnectionState(2),
        )
        assertEquals(
            BatterySystemEvent.BLUETOOTH_DEVICE_DISCONNECTED,
            BatterySystemEvent.fromBluetoothConnectionState(0),
        )
        assertNull(BatterySystemEvent.fromBluetoothConnectionState(1))
    }

    @Test
    fun emitsDefaultNetworkChangesAfterSilentBaseline() {
        val tracker = DefaultNetworkEventTracker()

        assertNull(tracker.observe(DefaultNetworkTransport.WIFI))
        assertNull(tracker.observe(DefaultNetworkTransport.WIFI))
        assertEquals(
            BatterySystemEvent.CELLULAR_NETWORK_CONNECTED,
            tracker.observe(DefaultNetworkTransport.CELLULAR),
        )
        assertEquals(
            BatterySystemEvent.NETWORK_DISCONNECTED,
            tracker.observe(DefaultNetworkTransport.OFFLINE),
        )
        assertEquals(
            BatterySystemEvent.WIFI_NETWORK_CONNECTED,
            tracker.observe(DefaultNetworkTransport.WIFI),
        )
    }
}
