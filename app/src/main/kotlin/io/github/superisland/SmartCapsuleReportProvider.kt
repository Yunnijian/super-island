package io.github.superisland

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import android.util.Log
import io.github.superisland.model.ScreenRecordingRootControlContract
import io.github.superisland.model.SystemUiSmartCapsuleContract
import io.github.superisland.source.screenrecord.ScreenRecordingRootControlResults

/** Privileged IPC endpoint for small SystemUI smart-capsule reports. */
class SmartCapsuleReportProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle {
        val appContext = requireNotNull(context).applicationContext
        enforceSystemUiCaller(appContext)
        val accepted =
            runCatching {
                when (method) {
                    SystemUiSmartCapsuleContract.METHOD_REPORT_RUNTIME ->
                        SmartCapsuleRuntimeReportHandler.handle(appContext, extras ?: Bundle.EMPTY)
                    SystemUiSmartCapsuleContract.METHOD_REPORT_CHANNELS ->
                        SmartCapsuleChannelCatalogReportHandler.handle(appContext, extras ?: Bundle.EMPTY)
                    SystemUiSmartCapsuleContract.METHOD_REPORT_CONFIG_ACCEPTANCE ->
                        SmartCapsuleConfigAcceptanceReportHandler.handle(
                            appContext,
                            extras ?: Bundle.EMPTY,
                        )
                    ScreenRecordingRootControlContract.METHOD_REPORT_RESULT ->
                        reportScreenRecordingRootControl(extras ?: Bundle.EMPTY)
                    else -> false
                }
            }.onFailure { error ->
                Log.w(TAG, "Rejected malformed $method report", error)
            }.getOrDefault(false)
        return Bundle().apply {
            putBoolean(SystemUiSmartCapsuleContract.EXTRA_REPORT_ACCEPTED, accepted)
        }
    }

    private fun reportScreenRecordingRootControl(extras: Bundle): Boolean {
        val required =
            listOf(
                ScreenRecordingRootControlContract.EXTRA_RESULT_SUCCESS,
                ScreenRecordingRootControlContract.EXTRA_ORIGINAL_SHOW_TOUCHES,
                ScreenRecordingRootControlContract.EXTRA_ORIGINAL_PROTECTION,
                ScreenRecordingRootControlContract.EXTRA_ORIGINAL_PROTECTION_ON,
            )
        if (required.any { key -> !extras.containsKey(key) }) return false
        return ScreenRecordingRootControlResults.reportFromSystemUi(
            requestId = extras.getString(ScreenRecordingRootControlContract.EXTRA_REQUEST_ID),
            success = extras.getBoolean(ScreenRecordingRootControlContract.EXTRA_RESULT_SUCCESS),
            reason = extras.getString(ScreenRecordingRootControlContract.EXTRA_RESULT_REASON),
            originalShowTouches =
                extras.getBoolean(ScreenRecordingRootControlContract.EXTRA_ORIGINAL_SHOW_TOUCHES),
            originalProtection =
                extras.getBoolean(ScreenRecordingRootControlContract.EXTRA_ORIGINAL_PROTECTION),
            originalProtectionOn =
                extras.getBoolean(ScreenRecordingRootControlContract.EXTRA_ORIGINAL_PROTECTION_ON),
        )
    }

    private fun enforceSystemUiCaller(context: Context) {
        context.enforceCallingPermission(
            SystemUiSmartCapsuleContract.RUNTIME_SENDER_PERMISSION,
            "Smart-capsule reports require STATUS_BAR_SERVICE",
        )
        val senderUid = Binder.getCallingUid()
        val sameAndroidUser =
            senderUid >= 0 &&
                android.os.UserHandle.getUserHandleForUid(senderUid) == Process.myUserHandle()
        val senderPackages = context.packageManager.getPackagesForUid(senderUid).orEmpty()
        if (!sameAndroidUser || SystemUiSmartCapsuleContract.SYSTEM_UI_PACKAGE !in senderPackages) {
            throw SecurityException("Smart-capsule report rejected for uid=$senderUid")
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private companion object {
        const val TAG = "SmartCapsuleReports"
    }
}
