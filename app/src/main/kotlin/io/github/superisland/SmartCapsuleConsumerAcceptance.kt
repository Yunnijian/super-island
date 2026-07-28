package io.github.superisland

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Process
import android.os.UserHandle
import io.github.superisland.model.SmartCapsuleConfigSnapshot
import io.github.superisland.model.SmartCapsuleRemoteSnapshot
import io.github.superisland.model.SystemUiSmartCapsuleContract
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class SmartCapsuleConsumerAcceptanceStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun prepare(snapshot: SmartCapsuleConfigSnapshot) {
        val normalized = snapshot.normalized()
        val digest = SmartCapsuleRemoteSnapshot.fromConfig(normalized).digest
        synchronized(LOCK) {
            if (
                preferences.getLong(KEY_EXPECTED_REVISION, -1L) == normalized.revision &&
                preferences.getString(KEY_EXPECTED_DIGEST, null) == digest
            ) {
                return
            }
            check(
                preferences.edit()
                    .putLong(KEY_EXPECTED_REVISION, normalized.revision)
                    .putString(KEY_EXPECTED_DIGEST, digest)
                    .putBoolean(KEY_SYSTEM_UI_ACCEPTED, false)
                    .putBoolean(KEY_XMSF_ACCEPTED, false)
                    .commit(),
            ) { "Could not prepare smart-capsule consumer acceptance" }
        }
    }

    fun accept(
        consumer: String,
        revision: Long,
        digest: String,
    ): Boolean =
        synchronized(LOCK) {
            if (
                revision <= 0L ||
                preferences.getLong(KEY_EXPECTED_REVISION, -1L) != revision ||
                preferences.getString(KEY_EXPECTED_DIGEST, null) != digest
            ) {
                return@synchronized false
            }
            val key =
                when (consumer) {
                    SystemUiSmartCapsuleContract.ACCEPTANCE_CONSUMER_SYSTEM_UI -> KEY_SYSTEM_UI_ACCEPTED
                    SystemUiSmartCapsuleContract.ACCEPTANCE_CONSUMER_XMSF -> KEY_XMSF_ACCEPTED
                    else -> return@synchronized false
                }
            check(preferences.edit().putBoolean(key, true).commit()) {
                "Could not persist smart-capsule consumer acceptance"
            }
            true
        }

    fun isAccepted(snapshot: SmartCapsuleConfigSnapshot): Boolean {
        val normalized = snapshot.normalized()
        val digest = SmartCapsuleRemoteSnapshot.fromConfig(normalized).digest
        return synchronized(LOCK) {
            preferences.getLong(KEY_EXPECTED_REVISION, -1L) == normalized.revision &&
                preferences.getString(KEY_EXPECTED_DIGEST, null) == digest &&
                preferences.getBoolean(KEY_SYSTEM_UI_ACCEPTED, false) &&
                preferences.getBoolean(KEY_XMSF_ACCEPTED, false)
        }
    }

    fun observe(onChanged: () -> Unit): () -> Unit {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key in OBSERVED_KEYS) onChanged()
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private companion object {
        val LOCK = Any()
        const val FILE_NAME = "smart-capsule-consumer-acceptance"
        const val KEY_EXPECTED_REVISION = "expected-revision"
        const val KEY_EXPECTED_DIGEST = "expected-digest"
        const val KEY_SYSTEM_UI_ACCEPTED = "systemui-accepted"
        const val KEY_XMSF_ACCEPTED = "xmsf-accepted"
        val OBSERVED_KEYS =
            setOf(
                KEY_EXPECTED_REVISION,
                KEY_EXPECTED_DIGEST,
                KEY_SYSTEM_UI_ACCEPTED,
                KEY_XMSF_ACCEPTED,
            )
    }
}

internal object SmartCapsuleConfigAcceptanceReportHandler {
    fun handle(
        context: Context,
        extras: android.os.Bundle,
    ): Boolean =
        SmartCapsuleConsumerAcceptanceStore(context).accept(
            consumer = SystemUiSmartCapsuleContract.ACCEPTANCE_CONSUMER_SYSTEM_UI,
            revision = extras.getLong(SystemUiSmartCapsuleContract.EXTRA_CONFIG_REVISION, -1L),
            digest = extras.getString(SystemUiSmartCapsuleContract.EXTRA_CONFIG_DIGEST).orEmpty(),
        )
}

class SmartCapsuleXmsfAcceptanceReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != SystemUiSmartCapsuleContract.ACTION_REPORT_XMSF_ACCEPTANCE) return
        val senderUid = sentFromUid
        val senderPackage = sentFromPackage
        val revision = intent.getLongExtra(SystemUiSmartCapsuleContract.EXTRA_CONFIG_REVISION, -1L)
        val digest = intent.getStringExtra(SystemUiSmartCapsuleContract.EXTRA_CONFIG_DIGEST).orEmpty()
        val pendingResult = goAsync()
        try {
            ACCEPTANCE_EXECUTOR.execute {
                try {
                    val sameUser =
                        senderUid >= 0 &&
                            UserHandle.getUserHandleForUid(senderUid) == Process.myUserHandle()
                    val senderPackages = context.packageManager.getPackagesForUid(senderUid).orEmpty()
                    if (
                        sameUser &&
                        senderPackage == SystemUiSmartCapsuleContract.XMSF_PACKAGE &&
                        SystemUiSmartCapsuleContract.XMSF_PACKAGE in senderPackages
                    ) {
                        SmartCapsuleConsumerAcceptanceStore(context).accept(
                            consumer = SystemUiSmartCapsuleContract.ACCEPTANCE_CONSUMER_XMSF,
                            revision = revision,
                            digest = digest,
                        )
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        } catch (_: RejectedExecutionException) {
            pendingResult.finish()
        }
    }

    private companion object {
        val ACCEPTANCE_EXECUTOR =
            ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                ArrayBlockingQueue(8),
                { runnable ->
                    Thread(runnable, "SuperIslandXmsfAcceptance").apply { isDaemon = true }
                },
                ThreadPoolExecutor.AbortPolicy(),
            )
    }
}
