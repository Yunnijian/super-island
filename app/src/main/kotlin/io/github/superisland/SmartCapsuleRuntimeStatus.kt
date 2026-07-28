package io.github.superisland

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.superisland.model.SystemUiSmartCapsuleContract

data class SmartCapsuleRuntimeSnapshot(
    val capabilityAvailable: Boolean = false,
    val adapter: String = "尚未收到 SystemUI 状态",
    val lastFailure: String? = null,
    val activeSessionCount: Int = 0,
    val pluginEpoch: Long = 0L,
    val updatedAtMillis: Long = 0L,
)

class SmartCapsuleRuntimeStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun snapshot(): SmartCapsuleRuntimeSnapshot =
        synchronized(RUNTIME_STORE_LOCK) {
            SmartCapsuleRuntimeSnapshot(
                capabilityAvailable = preferences.getBoolean(KEY_CAPABILITY, false),
                adapter = preferences.getString(KEY_ADAPTER, null).orEmpty().ifEmpty { "尚未收到 SystemUI 状态" },
                lastFailure = preferences.getString(KEY_LAST_FAILURE, null)?.takeIf(String::isNotBlank),
                activeSessionCount = preferences.getInt(KEY_ACTIVE_SESSION_COUNT, 0).coerceAtLeast(0),
                pluginEpoch = preferences.getLong(KEY_PLUGIN_EPOCH, 0L).coerceAtLeast(0L),
                updatedAtMillis = preferences.getLong(KEY_UPDATED_AT_MILLIS, 0L).coerceAtLeast(0L),
            )
        }

    fun observeChanges(onChanged: () -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChanged() }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    internal fun updateFrom(extras: Bundle): Boolean {
        val adapter = extras.getString(SystemUiSmartCapsuleContract.EXTRA_RUNTIME_ADAPTER).sanitized(MAX_ADAPTER_LENGTH)
        val failure =
            extras.getString(SystemUiSmartCapsuleContract.EXTRA_RUNTIME_LAST_FAILURE)
                .sanitized(MAX_FAILURE_LENGTH)
                .takeIf(String::isNotEmpty)
        val activeSessions =
            extras.getInt(SystemUiSmartCapsuleContract.EXTRA_RUNTIME_ACTIVE_SESSION_COUNT, 0)
                .coerceIn(0, MAX_ACTIVE_SESSIONS)
        val pluginEpoch =
            extras.getLong(SystemUiSmartCapsuleContract.EXTRA_RUNTIME_PLUGIN_EPOCH, 0L)
                .coerceAtLeast(0L)
        return synchronized(RUNTIME_STORE_LOCK) {
            preferences.edit()
                .putBoolean(
                    KEY_CAPABILITY,
                    extras.getBoolean(SystemUiSmartCapsuleContract.EXTRA_RUNTIME_CAPABILITY, false),
                ).putString(KEY_ADAPTER, adapter)
                .putString(KEY_LAST_FAILURE, failure)
                .putInt(KEY_ACTIVE_SESSION_COUNT, activeSessions)
                .putLong(KEY_PLUGIN_EPOCH, pluginEpoch)
                .putLong(KEY_UPDATED_AT_MILLIS, System.currentTimeMillis())
                .putLong(KEY_PENDING_HANDSHAKE, 0L)
                .commit()
        }
    }

    internal fun beginHandshake(): Long {
        val token = SystemClock.elapsedRealtimeNanos().coerceAtLeast(1L)
        synchronized(RUNTIME_STORE_LOCK) {
            check(
                preferences.edit()
                    .putBoolean(KEY_CAPABILITY, false)
                    .putString(KEY_ADAPTER, "等待 SystemUI 回报")
                    .remove(KEY_LAST_FAILURE)
                    .putInt(KEY_ACTIVE_SESSION_COUNT, 0)
                    .putLong(KEY_PENDING_HANDSHAKE, token)
                    .putLong(KEY_UPDATED_AT_MILLIS, System.currentTimeMillis())
                    .commit(),
            ) { "Could not begin smart-capsule runtime handshake" }
        }
        return token
    }

    internal fun expireHandshake(token: Long) {
        synchronized(RUNTIME_STORE_LOCK) {
            if (token <= 0L || preferences.getLong(KEY_PENDING_HANDSHAKE, 0L) != token) return
            markUnavailableLocked("SystemUI 未响应", "systemui_no_response")
        }
    }

    internal fun markUnavailable(
        adapter: String,
        failure: String,
    ) {
        synchronized(RUNTIME_STORE_LOCK) {
            markUnavailableLocked(adapter, failure)
        }
    }

    private fun markUnavailableLocked(
        adapter: String,
        failure: String,
    ) {
        preferences.edit()
            .putBoolean(KEY_CAPABILITY, false)
            .putString(KEY_ADAPTER, adapter.sanitized(MAX_ADAPTER_LENGTH))
            .putString(KEY_LAST_FAILURE, failure.sanitized(MAX_FAILURE_LENGTH))
            .putInt(KEY_ACTIVE_SESSION_COUNT, 0)
            .putLong(KEY_PENDING_HANDSHAKE, 0L)
            .putLong(KEY_UPDATED_AT_MILLIS, System.currentTimeMillis())
            .commit()
    }

    private fun String?.sanitized(maxLength: Int): String =
        orEmpty()
            .filterNot(Char::isISOControl)
            .trim()
            .take(maxLength)

    companion object {
        private val RUNTIME_STORE_LOCK = Any()

        private const val FILE_NAME = "smart-capsule-runtime"
        private const val KEY_CAPABILITY = "capability"
        private const val KEY_ADAPTER = "adapter"
        private const val KEY_LAST_FAILURE = "last-failure"
        private const val KEY_ACTIVE_SESSION_COUNT = "active-session-count"
        private const val KEY_PLUGIN_EPOCH = "plugin-epoch"
        private const val KEY_UPDATED_AT_MILLIS = "updated-at-millis"
        private const val KEY_PENDING_HANDSHAKE = "pending-handshake"
        private const val MAX_ADAPTER_LENGTH = 160
        private const val MAX_FAILURE_LENGTH = 256
        private const val MAX_ACTIVE_SESSIONS = 256
    }
}

internal object SmartCapsuleRuntimeReportHandler {
    fun handle(
        context: Context,
        extras: Bundle,
    ): Boolean = SmartCapsuleRuntimeStore(context.applicationContext).updateFrom(extras)
}

object SmartCapsuleRuntimeStatusController {
    private const val REPORT_TIMEOUT_MILLIS = 5_000L
    private val mainHandler = Handler(Looper.getMainLooper())

    fun start(context: Context) {
        val appContext = context.applicationContext
        AppBackgroundWork.execute { expectReport(appContext) }
    }

    fun expectReport(context: Context) {
        val store = SmartCapsuleRuntimeStore(context.applicationContext)
        val token = store.beginHandshake()
        mainHandler.postDelayed(
            { store.expireHandshake(token) },
            REPORT_TIMEOUT_MILLIS,
        )
    }

    fun markLsposedUnavailable(context: Context) {
        SmartCapsuleRuntimeStore(context.applicationContext).markUnavailable(
            adapter = "LSPosed 未激活",
            failure = "lsposed_not_active",
        )
    }
}
