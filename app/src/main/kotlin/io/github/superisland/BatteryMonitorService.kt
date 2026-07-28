package io.github.superisland

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothLeAudio
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import io.github.superisland.model.BatteryChargeEventTracker
import io.github.superisland.model.BatteryMetricSnapshot
import io.github.superisland.model.BatterySystemEvent
import io.github.superisland.model.ResidentMonitorConfig
import io.github.superisland.model.DefaultNetworkEventTracker
import io.github.superisland.model.DefaultNetworkTransport
import io.github.superisland.model.ThermalDiagnosticSnapshot
import io.github.superisland.model.WarsawFanMetricSnapshot
import io.github.superisland.model.WarsawPerformanceMetricSnapshot
import io.github.superisland.publisher.focus.SystemUiResidentIslandPublisher
import io.github.superisland.source.system.BatteryMetricSource
import io.github.superisland.source.root.RootDeviceAdapterRegistry
import io.github.superisland.source.root.RootDeviceFeature
import io.github.superisland.source.root.WarsawFanMetricSource
import io.github.superisland.source.root.RootThermalMetricSource
import io.github.superisland.source.root.WarsawPerformanceMetricSource
import java.util.concurrent.CopyOnWriteArraySet

/**
 * A user-started foreground service that supplies data for the persistent system-status island.
 *
 * The service deliberately uses a bounded refresh interval instead of an Activity coroutine so
 * the island is updated while the app is backgrounded. The foreground-service notification is
 * intentionally ordinary; the island itself is owned by the scoped SystemUI LSPosed host. Thus an
 * app task cleanup removes neither the already-published resident island nor its SystemUI backing
 * notification. Only [stopMonitoring] sends the explicit withdrawal request.
 */
class BatteryMonitorService : Service() {
    private val source by lazy { BatteryMetricSource(this) }
    private val rootFanSource by lazy { WarsawFanMetricSource() }
    private val rootThermalSource by lazy { RootThermalMetricSource() }
    private val rootPerformanceSource by lazy { WarsawPerformanceMetricSource() }
    private val rootDeviceAdapter by lazy {
        RootDeviceAdapterRegistry.capability(
            device = Build.DEVICE,
            fingerprint = Build.FINGERPRINT,
        )
    }
    private val residentIslandPublisher by lazy { SystemUiResidentIslandPublisher(this) }
    private val residentMonitorConfigStore by lazy { ResidentMonitorConfigStore(this) }
    private val handler = Handler(Looper.getMainLooper())
    private val chargeEventTracker = BatteryChargeEventTracker()
    private val defaultNetworkEventTracker = DefaultNetworkEventTracker()
    private val connectivityManager by lazy { checkNotNull(getSystemService(ConnectivityManager::class.java)) }
    private var stopped = false
    private var powerReceiverRegistered = false
    private var bluetoothReceiverRegistered = false
    private var defaultNetworkCallbackRegistered = false
    private var rootTelemetryMonitoringEnabled = false
    private var rootFanMonitoringEnabled = false
    private var rootPerformanceMonitoringEnabled = false
    private var rootTelemetryReadInFlight = false
    private var latestSnapshotGeneration = 0L
    private var lastBroadcastEvent: BatterySystemEvent? = null
    private var lastBroadcastEventAtMillis = Long.MIN_VALUE
    private var refreshIntervalMillis = ResidentMonitorConfig.DEFAULT_REFRESH_INTERVAL_MILLIS
    /** The active config is updated directly by the settings screen; storage is for restart recovery. */
    private var residentMonitorConfig = ResidentMonitorConfig()

    private val powerReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val snapshot = source.read()
                val event =
                    when (intent.action) {
                        Intent.ACTION_BATTERY_CHANGED -> chargeEventTracker.observe(snapshot.chargeState)
                        Intent.ACTION_HEADSET_PLUG -> intent.wiredHeadsetSystemEvent()
                        else -> intent.action.toBatterySystemEvent(snapshot)
                    } ?: return
                if (shouldPublishBroadcastEvent(event)) {
                    publishSnapshot(snapshot, event)
                }
            }
        }

    /**
     * Bluetooth broadcasts are sent by the privileged Bluetooth process, not the system UID.
     * They must therefore use an exported dynamic receiver. This receiver only accepts the
     * protected platform Bluetooth actions and never reads or records device identity.
     */
    private val bluetoothReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val event = intent.bluetoothSystemEvent() ?: return
                if (shouldPublishBroadcastEvent(event)) {
                    Log.i(TAG, "Received Bluetooth connection event: $event")
                    publishSnapshot(source.read(), event)
                }
            }
        }

    /**
     * Tracks only the app's default-network transport. It never reads SSID, BSSID, IP addresses,
     * link properties, or network traffic. Callback work is returned to the main handler so it
     * shares the foreground-service state path with broadcasts and periodic refreshes.
     */
    private val defaultNetworkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val transport = networkCapabilities.toDefaultNetworkTransport()
                handler.post { observeDefaultNetworkTransport(transport) }
            }

            override fun onLost(network: Network) {
                handler.post { observeDefaultNetworkTransport(currentDefaultNetworkTransport()) }
            }
        }

    private val refreshRunnable =
        object : Runnable {
            override fun run() {
                publishSnapshot()
                if (!stopped) {
                    handler.postDelayed(this, refreshIntervalMillis)
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
        current = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMonitoring()
            return START_NOT_STICKY
        }

        // Migration guard for a stale debug action or old notification PendingIntent: resident
        // status is now sampled by SystemUI itself and must never start an app foreground service.
        val config = residentMonitorConfigStore.load().copy(enabled = true)
        ResidentIslandHostConfigSync.sync(config)
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (current === this) current = null
        stopped = true
        rootPerformanceMonitoringEnabled = false
        handler.removeCallbacksAndMessages(null)
        unregisterPowerReceiverIfNeeded()
        unregisterBluetoothReceiverIfNeeded()
        unregisterDefaultNetworkCallbackIfNeeded()
        stopForeground(STOP_FOREGROUND_REMOVE)
        // Do not withdraw the SystemUI-owned resident island here. onDestroy also happens when
        // Android clears this app process/task; preserving that island is the feature contract.
        val current = BatteryMonitorRuntime.current()
        BatteryMonitorRuntime.update(
            current.copy(
                running = false,
                message = if (current.running) "监控岛已结束" else current.message,
                rootTelemetryMonitoringEnabled = false,
                rootFanMonitoringEnabled = false,
                latestRootFanSnapshot = null,
                latestRootThermalSnapshot = null,
                latestRootPerformanceSnapshot = null,
            ),
        )
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun publishSnapshot(
        snapshot: BatteryMetricSnapshot = source.read(),
        broadcastEvent: BatterySystemEvent? = null,
    ) {
        val detectedEvent = chargeEventTracker.observe(snapshot.chargeState)
        val event = broadcastEvent ?: detectedEvent
        val generation = ++latestSnapshotGeneration
        publishMonitorUpdate(snapshot, event)
        scheduleRootTelemetryRead(snapshot, event, generation)
    }

    private fun publishMonitorUpdate(
        snapshot: BatteryMetricSnapshot,
        event: BatterySystemEvent?,
        rootFanSnapshot: WarsawFanMetricSnapshot? = null,
        rootThermalSnapshot: ThermalDiagnosticSnapshot? = null,
        rootPerformanceSnapshot: WarsawPerformanceMetricSnapshot? = null,
    ) {
        val request =
            snapshot.monitorFocusNotificationRequest(
                config = residentMonitorConfig,
                event,
                rootFanSnapshot,
                rootThermalSnapshot,
                rootPerformanceSnapshot,
            )
        startForeground(
            BATTERY_MONITOR_NOTIFICATION_ID,
            buildForegroundMonitorNotification(request),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        residentIslandPublisher
            .post(request, Icon.createWithResource(this, R.drawable.ic_stat_island))
            .onFailure { error ->
                Log.w(TAG, "Could not send resident island update to the SystemUI host", error)
            }
        val current = BatteryMonitorRuntime.current()
        BatteryMonitorRuntime.update(
            BatteryMonitorRuntimeState(
                running = true,
                latestSnapshot = snapshot,
                message = "常驻超级岛正在更新（约每 ${refreshIntervalMillis / 1_000} 秒）",
                lastSystemEvent = event?.displayName ?: current.lastSystemEvent,
                rootTelemetryMonitoringEnabled = rootTelemetryMonitoringEnabled,
                rootFanMonitoringEnabled = rootFanMonitoringEnabled,
                latestRootFanSnapshot = rootFanSnapshot,
                latestRootThermalSnapshot = rootThermalSnapshot,
                latestRootPerformanceSnapshot = rootPerformanceSnapshot,
            ),
        )
    }

    /**
     * Root work is deliberately off the service main thread. A foreground notification is already
     * visible before either bounded allowlist read starts, so a denied/slow Root request cannot
     * delay foreground-service startup or block regular system-event handling.
     */
    private fun scheduleRootTelemetryRead(
        snapshot: BatteryMetricSnapshot,
        event: BatterySystemEvent?,
        generation: Long,
    ) {
        if (!rootTelemetryMonitoringEnabled || rootTelemetryReadInFlight) return
        rootTelemetryReadInFlight = true
        Thread(
            {
                val fanSnapshot =
                    if (rootFanMonitoringEnabled) {
                        rootFanSource.read(rootDeviceAdapter).onFailure { error ->
                            Log.w(TAG, "Root fan monitoring read failed; no RPM will be displayed", error)
                        }.getOrNull()
                    } else {
                        null
                    }
                val thermalSnapshot =
                    rootThermalSource.read().onFailure { error ->
                        Log.w(TAG, "Root thermal monitoring read failed; no temperatures will be displayed", error)
                    }.getOrNull()
                val performanceSnapshot =
                    if (rootPerformanceMonitoringEnabled) {
                        rootPerformanceSource.read(rootDeviceAdapter).onFailure { error ->
                            Log.w(TAG, "Root performance monitoring read failed; no frequencies will be displayed", error)
                        }.getOrNull()
                    } else {
                        null
                    }
                handler.post {
                    rootTelemetryReadInFlight = false
                    if (stopped || generation != latestSnapshotGeneration) return@post
                    publishMonitorUpdate(snapshot, event, fanSnapshot, thermalSnapshot, performanceSnapshot)
                }
            },
            "super-island-root-telemetry",
        ).start()
    }

    private fun registerPowerReceiverIfNeeded() {
        if (powerReceiverRegistered) return
        ContextCompat.registerReceiver(
            this,
            powerReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_HEADSET_PLUG)
                addAction(android.os.BatteryManager.ACTION_CHARGING)
                addAction(android.os.BatteryManager.ACTION_DISCHARGING)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        powerReceiverRegistered = true
    }

    private fun registerBluetoothReceiverIfNeeded() {
        if (bluetoothReceiverRegistered || !hasBluetoothConnectPermission()) return
        ContextCompat.registerReceiver(
            this,
            bluetoothReceiver,
            IntentFilter().apply {
                // ACL events are per-device and are emitted for real headset/device links.
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                // Audio profiles cover device-initiated disconnects on vendors that do not
                // emit a public ACL-disconnect broadcast while their stack is being reset.
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED)
                // Retain the adapter-level action as a vendor-compatible fallback.
                addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        bluetoothReceiverRegistered = true
    }

    private fun unregisterPowerReceiverIfNeeded() {
        if (!powerReceiverRegistered) return
        unregisterReceiver(powerReceiver)
        powerReceiverRegistered = false
    }

    private fun unregisterBluetoothReceiverIfNeeded() {
        if (!bluetoothReceiverRegistered) return
        unregisterReceiver(bluetoothReceiver)
        bluetoothReceiverRegistered = false
    }

    private fun registerDefaultNetworkCallbackIfNeeded() {
        if (defaultNetworkCallbackRegistered) return
        defaultNetworkEventTracker.observe(currentDefaultNetworkTransport())
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(defaultNetworkCallback)
        }.onSuccess {
            defaultNetworkCallbackRegistered = true
        }.onFailure { error ->
            Log.w(TAG, "Unable to register default network callback", error)
        }
    }

    private fun unregisterDefaultNetworkCallbackIfNeeded() {
        if (!defaultNetworkCallbackRegistered) return
        runCatching {
            connectivityManager.unregisterNetworkCallback(defaultNetworkCallback)
        }.onFailure { error ->
            Log.w(TAG, "Unable to unregister default network callback", error)
        }
        defaultNetworkCallbackRegistered = false
    }

    private fun reconfigureSystemEventReceiver() {
        unregisterBluetoothReceiverIfNeeded()
        registerBluetoothReceiverIfNeeded()
    }

    private fun observeDefaultNetworkTransport(transport: DefaultNetworkTransport) {
        if (stopped) return
        val event = defaultNetworkEventTracker.observe(transport) ?: return
        if (shouldPublishBroadcastEvent(event)) {
            Log.i(TAG, "Received default network event: $event")
            publishSnapshot(source.read(), event)
        }
    }

    private fun currentDefaultNetworkTransport(): DefaultNetworkTransport {
        val defaultNetwork = connectivityManager.activeNetwork ?: return DefaultNetworkTransport.OFFLINE
        val capabilities = connectivityManager.getNetworkCapabilities(defaultNetwork)
            ?: return DefaultNetworkTransport.OFFLINE
        return capabilities.toDefaultNetworkTransport()
    }

    private fun shouldPublishBroadcastEvent(event: BatterySystemEvent): Boolean {
        val now = SystemClock.elapsedRealtime()
        val duplicate =
            event == lastBroadcastEvent && now - lastBroadcastEventAtMillis < BROADCAST_DEDUP_WINDOW_MILLIS
        lastBroadcastEvent = event
        lastBroadcastEventAtMillis = now
        return !duplicate
    }

    private fun String?.toBatterySystemEvent(snapshot: BatteryMetricSnapshot): BatterySystemEvent? =
        when (this) {
            Intent.ACTION_POWER_CONNECTED ->
                if (snapshot.chargeState == io.github.superisland.model.BatteryChargeState.FULL) {
                    BatterySystemEvent.FULL
                } else {
                    BatterySystemEvent.POWER_CONNECTED
                }
            Intent.ACTION_POWER_DISCONNECTED -> BatterySystemEvent.POWER_DISCONNECTED
            android.os.BatteryManager.ACTION_CHARGING ->
                if (snapshot.chargeState == io.github.superisland.model.BatteryChargeState.FULL) {
                    BatterySystemEvent.FULL
                } else {
                    BatterySystemEvent.CHARGING_STARTED
                }
            android.os.BatteryManager.ACTION_DISCHARGING -> BatterySystemEvent.DISCHARGING_STARTED
            else -> null
        }

    private fun Intent.wiredHeadsetSystemEvent(): BatterySystemEvent =
        BatterySystemEvent.fromWiredHeadsetState(getIntExtra(HEADSET_STATE_EXTRA, 0))

    private fun Intent.bluetoothSystemEvent(): BatterySystemEvent? =
        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> BatterySystemEvent.BLUETOOTH_DEVICE_CONNECTED
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> BatterySystemEvent.BLUETOOTH_DEVICE_DISCONNECTED
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED ->
                BatterySystemEvent.fromBluetoothConnectionState(
                    getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothAdapter.ERROR),
                )
            BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED ->
                BatterySystemEvent.fromBluetoothConnectionState(
                    getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothAdapter.ERROR),
                )
            BluetoothAdapter.ACTION_STATE_CHANGED ->
                if (getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) == BluetoothAdapter.STATE_OFF) {
                    BatterySystemEvent.BLUETOOTH_DISABLED
                } else {
                    null
                }
            else -> null
        }

    private fun NetworkCapabilities.toDefaultNetworkTransport(): DefaultNetworkTransport =
        when {
            hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> DefaultNetworkTransport.WIFI
            hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> DefaultNetworkTransport.CELLULAR
            hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> DefaultNetworkTransport.ETHERNET
            hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> DefaultNetworkTransport.VPN
            else -> DefaultNetworkTransport.OTHER
        }

    private fun hasNotificationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun hasBluetoothConnectPermission(): Boolean =
        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun launchIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                action = DEBUG_BATTERY_MONITOR_ACTION
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun buildForegroundMonitorNotification(
        request: io.github.superisland.model.FocusNotificationRequest,
    ): Notification {
        ensureForegroundMonitorChannel()
        return NotificationCompat.Builder(this, FOREGROUND_MONITOR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_island)
            .setContentTitle(request.title)
            .setContentText(request.text)
            .setContentIntent(launchIntent())
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .addAction(0, "停止监控", stopIntent())
            .build()
    }

    private fun ensureForegroundMonitorChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(FOREGROUND_MONITOR_CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                FOREGROUND_MONITOR_CHANNEL_ID,
                getString(R.string.resident_monitor_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.resident_monitor_channel_description)
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun stopIntent(): PendingIntent =
        PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            Intent(this, BatteryMonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun stopMonitoring() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        val config = residentMonitorConfigStore.load().copy(enabled = false)
        residentMonitorConfigStore.save(config).onFailure { error ->
            Log.w(TAG, "Could not persist the resident-island stop request", error)
        }
        stopSelf()
    }

    /**
     * Applies a saved persistent-status configuration to an already-running monitor.
     *
     * This intentionally republishes immediately instead of waiting for the previous refresh
     * deadline. In particular, changing 60 seconds to 1 second must take effect now, and the
     * notification that SystemUI has already rendered must receive the new title, details and
     * short-status value in the same interaction.
     */
    private fun applySavedConfiguration(config: ResidentMonitorConfig) {
        if (stopped) return
        residentMonitorConfig = config.normalized()
        refreshIntervalMillis = residentMonitorConfig.titleRefreshIntervalMillis
        handler.removeCallbacks(refreshRunnable)
        publishSnapshot()
        if (!stopped) {
            handler.postDelayed(refreshRunnable, refreshIntervalMillis)
        }
    }

    companion object {
        const val ACTION_START = "io.github.superisland.action.START_BATTERY_MONITOR"
        const val ACTION_STOP = "io.github.superisland.action.STOP_BATTERY_MONITOR"
        const val BROADCAST_DEDUP_WINDOW_MILLIS = 2_000L
        const val HEADSET_STATE_EXTRA = "state"
        const val EXTRA_ENABLE_ROOT_TELEMETRY_MONITORING =
            "io.github.superisland.extra.ENABLE_ROOT_TELEMETRY_MONITORING"

        fun start(
            context: Context,
            enableRootTelemetryMonitoring: Boolean = false,
        ): Result<Unit> = runCatching {
            // Persistent status has no app-owned foreground service and therefore no ordinary
            // notification. SystemUI reads the public battery state directly after this settings
            // sync, surviving an app task cleanup or force-stop.
            val store = ResidentMonitorConfigStore(context)
            val config = store.load().copy(enabled = true).normalized()
            store.save(config).getOrThrow()
            context.stopService(Intent(context, BatteryMonitorService::class.java))
            BatteryMonitorRuntime.update(
                BatteryMonitorRuntimeState(
                    running = true,
                    latestSnapshot = BatteryMetricSource(context).read(),
                    message = "常驻超级岛已开启",
                    lastSystemEvent = "SystemUI 电池采集已启用",
                    rootTelemetryMonitoringEnabled = false,
                    rootFanMonitoringEnabled = false,
                ),
            )
        }

        fun stop(context: Context) {
            val store = ResidentMonitorConfigStore(context)
            val config = store.load().copy(enabled = false).normalized()
            store.save(config).onFailure { error ->
                Log.w(TAG, "Could not persist the resident-island stop request", error)
            }
            context.stopService(Intent(context, BatteryMonitorService::class.java))
            val current = BatteryMonitorRuntime.current()
            BatteryMonitorRuntime.update(
                current.copy(
                    running = false,
                    message = "常驻超级岛已关闭",
                    rootTelemetryMonitoringEnabled = false,
                    rootFanMonitoringEnabled = false,
                ),
            )
        }

        /** Reached only from MainActivity's BuildConfig.DEBUG-only command handler. */
        fun debugSimulateWiredHeadset(connected: Boolean) {
            current?.publishSnapshot(
                broadcastEvent = BatterySystemEvent.fromWiredHeadsetState(if (connected) 1 else 0),
            )
        }

        /** Reached only from MainActivity's BuildConfig.DEBUG-only command handler. */
        fun debugSimulateBluetoothDevice(connected: Boolean) {
            current?.publishSnapshot(
                broadcastEvent =
                    if (connected) {
                        BatterySystemEvent.BLUETOOTH_DEVICE_CONNECTED
                    } else {
                        BatterySystemEvent.BLUETOOTH_DEVICE_DISCONNECTED
                    },
            )
        }

        fun onBluetoothPermissionChanged() {
            current?.reconfigureSystemEventReceiver()
        }

        /**
         * Delivers the exact value selected in the settings screen to a stale in-process monitor.
         * ResidentMonitorConfigStore.save() already owns the one SystemUI preference mirror; this
         * compatibility path must not repeat that cross-process commit and reload broadcast.
         */
        fun onResidentMonitorConfigurationChanged(config: ResidentMonitorConfig) {
            val service = current ?: return
            service.handler.post { service.applySavedConfiguration(config) }
        }

        private const val STOP_REQUEST_CODE = 0x4253
        private const val FOREGROUND_MONITOR_CHANNEL_ID = "resident_monitor_runtime"
        private const val TAG = "BatteryMonitorService"

        @Volatile
        private var current: BatteryMonitorService? = null
    }
}

data class BatteryMonitorRuntimeState(
    val running: Boolean,
    val latestSnapshot: BatteryMetricSnapshot?,
    val message: String,
    val lastSystemEvent: String,
    val rootTelemetryMonitoringEnabled: Boolean = false,
    val rootFanMonitoringEnabled: Boolean = false,
    val latestRootFanSnapshot: WarsawFanMetricSnapshot? = null,
    val latestRootThermalSnapshot: ThermalDiagnosticSnapshot? = null,
    val latestRootPerformanceSnapshot: WarsawPerformanceMetricSnapshot? = null,
)

/** In-process state bridge for the Miuix monitor screen; it does not schedule background work. */
object BatteryMonitorRuntime {
    private val listeners = CopyOnWriteArraySet<(BatteryMonitorRuntimeState) -> Unit>()

    @Volatile
    private var current =
        BatteryMonitorRuntimeState(
            running = false,
            latestSnapshot = null,
            message = "尚未开始持续监控",
            lastSystemEvent = "尚未收到系统事件",
        )

    fun current(): BatteryMonitorRuntimeState = current

    fun observe(listener: (BatteryMonitorRuntimeState) -> Unit): () -> Unit {
        listeners += listener
        listener(current)
        return { listeners -= listener }
    }

    fun update(value: BatteryMonitorRuntimeState) {
        current = value
        listeners.forEach { it(value) }
    }
}
