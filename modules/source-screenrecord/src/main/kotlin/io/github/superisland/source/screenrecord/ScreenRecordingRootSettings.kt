package io.github.superisland.source.screenrecord

import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.superisland.model.ScreenRecordingConfig
import io.github.superisland.model.ScreenRecordingRootControlContract
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * App-side client for the scoped SystemUI Root-settings bridge.
 *
 * The bridge performs Settings provider / WindowManager work with SystemUI's existing privileged
 * identity. This module deliberately has no `su`, shell, app_process, or Shizuku fallback.
 *
 * Root toggles are best-effort: a bridge timeout or prepare failure must not abort MediaProjection
 * recording. Only a successful prepare returns a session that will attempt restore.
 */
class ScreenRecordingRootSettings(private val context: Context) {
    private val applicationContext = context.applicationContext

    fun capability(): ScreenRecordingRootCapability =
        ScreenRecordingRootCapability(
            supported = true,
            summary = "录制时由 SystemUI 安全桥接处理所选 Root 设置",
        )

    /**
     * Always succeeds with either a prepared session or [ScreenRecordingRootSession.empty].
     * Callers must not treat Root prepare as a hard dependency of MediaProjection capture.
     */
    fun begin(config: ScreenRecordingConfig): Result<ScreenRecordingRootSession> =
        runCatching {
            if (!config.showTouches && !config.bypassScreenShareProtection) {
                return@runCatching ScreenRecordingRootSession.empty(applicationContext)
            }
            if (!capability().supported) {
                Log.w(TAG, "Root settings requested on unsupported device; recording without them")
                return@runCatching ScreenRecordingRootSession.empty(applicationContext)
            }
            val result =
                request(
                    operation = ScreenRecordingRootControlContract.OPERATION_PREPARE,
                    showTouches = config.showTouches,
                    bypassScreenShareProtection = config.bypassScreenShareProtection,
                    original = null,
                )
            if (!result.success) {
                Log.w(
                    TAG,
                    "Root prepare failed (${result.reason.ifBlank { "unknown" }}); " +
                        "recording continues without Root settings",
                )
                return@runCatching ScreenRecordingRootSession.empty(applicationContext)
            }
            ScreenRecordingRootSession(
                applicationContext = applicationContext,
                original = result.original,
                restoreShowTouches = config.showTouches,
                restoreScreenShareProtection = config.bypassScreenShareProtection,
            )
        }.recover { error ->
            Log.w(TAG, "Root prepare threw; recording continues without Root settings", error)
            ScreenRecordingRootSession.empty(applicationContext)
        }

    /**
     * Sets AppOps PROJECT_MEDIA for this module via the closed Root `cmd appops` path.
     * Fail closed: returns failure without claiming the system projection dialog will be skipped.
     */
    fun setProjectMediaAllowed(allowed: Boolean): Result<Unit> =
        ScreenRecordingProjectMedia.setAllowed(applicationContext, allowed)

    private fun request(
        operation: String,
        showTouches: Boolean,
        bypassScreenShareProtection: Boolean,
        original: ScreenRecordingRootOriginalState?,
        projectMediaAllowed: Boolean? = null,
    ): ScreenRecordingRootControlResult {
        val requestId = UUID.randomUUID().toString()
        val response = ScreenRecordingRootControlResults.register(requestId)
        val request =
            Intent(ScreenRecordingRootControlContract.ACTION_REQUEST)
                .setPackage(ScreenRecordingRootControlContract.SYSTEM_UI_PACKAGE)
                .putExtra(ScreenRecordingRootControlContract.EXTRA_REQUEST_ID, requestId)
                .putExtra(ScreenRecordingRootControlContract.EXTRA_OPERATION, operation)
                .putExtra(ScreenRecordingRootControlContract.EXTRA_SHOW_TOUCHES, showTouches)
                .putExtra(
                    ScreenRecordingRootControlContract.EXTRA_BYPASS_PROTECTION,
                    bypassScreenShareProtection,
                )
        if (projectMediaAllowed != null) {
            request.putExtra(
                ScreenRecordingRootControlContract.EXTRA_PROJECT_MEDIA_ALLOWED,
                projectMediaAllowed,
            )
        }
        original?.let {
            request.putExtra(ScreenRecordingRootControlContract.EXTRA_ORIGINAL_SHOW_TOUCHES, it.showTouches)
            request.putExtra(ScreenRecordingRootControlContract.EXTRA_ORIGINAL_PROTECTION, it.protection)
            request.putExtra(ScreenRecordingRootControlContract.EXTRA_ORIGINAL_PROTECTION_ON, it.protectionOn)
        }
        try {
            // Sender authorization is enforced by the SystemUI registerReceiver broadcastPermission.
            // Do not pass a receiverPermission here: SystemUI does not hold the module's
            // signature-level SEND_SCREEN_RECORDING_CONTROL permission, and requiring it causes a
            // silent drop of the request (observed as a prepare timeout).
            applicationContext.sendBroadcast(request)
            return response.get(ROOT_BRIDGE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            return ScreenRecordingRootControlResult(
                success = false,
                reason = "SystemUI Root 设置桥接超时",
                original = ScreenRecordingRootOriginalState(false, false, false),
            )
        } catch (error: Throwable) {
            return ScreenRecordingRootControlResult(
                success = false,
                reason = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName,
                original = ScreenRecordingRootOriginalState(false, false, false),
            )
        } finally {
            ScreenRecordingRootControlResults.remove(requestId)
        }
    }

    companion object {
        private const val TAG = "SuperIslandScreenRecord"
        const val ROOT_BRIDGE_TIMEOUT_MILLIS = 4_000L

        internal fun restore(
            context: Context,
            original: ScreenRecordingRootOriginalState,
            restoreShowTouches: Boolean,
            restoreScreenShareProtection: Boolean,
        ): Result<Unit> =
            runCatching {
                val client = ScreenRecordingRootSettings(context)
                val result =
                    client.request(
                        operation = ScreenRecordingRootControlContract.OPERATION_RESTORE,
                        showTouches = restoreShowTouches,
                        bypassScreenShareProtection = restoreScreenShareProtection,
                        original = original,
                    )
                check(result.success) { result.reason.ifBlank { "SystemUI Root 设置恢复失败" } }
            }
    }
}

data class ScreenRecordingRootCapability(
    val supported: Boolean,
    val summary: String,
)

/** Owns the original SystemUI-observed values returned from a successful prepare request. */
class ScreenRecordingRootSession internal constructor(
    private val applicationContext: Context,
    private val original: ScreenRecordingRootOriginalState?,
    private val restoreShowTouches: Boolean,
    private val restoreScreenShareProtection: Boolean,
) {
    fun restore(): Result<Unit> =
        original?.let {
            ScreenRecordingRootSettings.restore(
                context = applicationContext,
                original = it,
                restoreShowTouches = restoreShowTouches,
                restoreScreenShareProtection = restoreScreenShareProtection,
            )
        } ?: Result.success(Unit)

    companion object {
        internal fun empty(context: Context): ScreenRecordingRootSession =
            ScreenRecordingRootSession(
                applicationContext = context.applicationContext,
                original = null,
                restoreShowTouches = false,
                restoreScreenShareProtection = false,
            )
    }
}

data class ScreenRecordingRootOriginalState(
    val showTouches: Boolean,
    val protection: Boolean,
    val protectionOn: Boolean,
)

data class ScreenRecordingRootControlResult(
    val success: Boolean,
    val reason: String,
    val original: ScreenRecordingRootOriginalState,
)

/**
 * The app provider receives SystemUI's authenticated result and completes the pending bounded
 * request. Requests are generated locally and expire after one response or the fixed timeout.
 */
object ScreenRecordingRootControlResults {
    private val pending = ConcurrentHashMap<String, CompletableFuture<ScreenRecordingRootControlResult>>()

    fun register(requestId: String): CompletableFuture<ScreenRecordingRootControlResult> {
        require(ScreenRecordingRootControlContract.isValidRequestId(requestId))
        return CompletableFuture<ScreenRecordingRootControlResult>().also { future ->
            check(pending.putIfAbsent(requestId, future) == null) { "Duplicate root-control request" }
        }
    }

    fun remove(requestId: String) {
        pending.remove(requestId)
    }

    /** Called only by the app's STATUS_BAR_SERVICE-protected provider endpoint. */
    fun reportFromSystemUi(
        requestId: String?,
        success: Boolean,
        reason: String?,
        originalShowTouches: Boolean,
        originalProtection: Boolean,
        originalProtectionOn: Boolean,
    ): Boolean {
        if (!ScreenRecordingRootControlContract.isValidRequestId(requestId)) return false
        val future = pending.remove(requestId) ?: return false
        return future.complete(
            ScreenRecordingRootControlResult(
                success = success,
                reason = reason.orEmpty().take(MAX_REASON_LENGTH),
                original =
                    ScreenRecordingRootOriginalState(
                        showTouches = originalShowTouches,
                        protection = originalProtection,
                        protectionOn = originalProtectionOn,
                    ),
            ),
        )
    }

    private const val MAX_REASON_LENGTH = 160
}
