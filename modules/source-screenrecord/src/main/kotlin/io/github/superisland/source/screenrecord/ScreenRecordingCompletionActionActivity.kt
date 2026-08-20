package io.github.superisland.source.screenrecord

import android.app.Activity
import android.app.PendingIntent
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log

/**
 * Restores the recording app's URI-grant identity after HyperOS unwraps a Focus Activity
 * PendingIntent and starts its raw Intent from SystemUI.
 */
class ScreenRecordingCompletionActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching(::launchRequestedAction).onFailure { failure ->
            Log.w(TAG, "Unable to open completed screen recording", failure)
        }
        overrideActivityTransition(
            Activity.OVERRIDE_TRANSITION_CLOSE,
            0,
            0,
            Color.TRANSPARENT,
        )
        finish()
    }

    private fun launchRequestedAction() {
        val outputUri = intent.data ?: return
        val persistedOutputUri = ScreenRecordingRuntimeStore(this).load().outputUri
        if (
            !ScreenRecordingCompletionActionRequest.isAllowed(
                action = intent.action,
                requestedOutputUri = outputUri.toString(),
                persistedOutputUri = persistedOutputUri,
            )
        ) {
            Log.w(TAG, "Rejected stale or invalid completed recording action")
            return
        }

        val target =
            when (intent.action) {
                ScreenRecordingCompletionActionRequest.ACTION_VIEW ->
                    Intent(Intent.ACTION_VIEW).setDataAndType(outputUri, VIDEO_MIME_TYPE)
                ScreenRecordingCompletionActionRequest.ACTION_SHARE ->
                    Intent(Intent.ACTION_SEND)
                        .setType(VIDEO_MIME_TYPE)
                        .putExtra(Intent.EXTRA_STREAM, outputUri)
                else -> return
            }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .apply { clipData = ClipData.newRawUri("screen-recording", outputUri) }

        val chooserTitle =
            if (intent.action == ScreenRecordingCompletionActionRequest.ACTION_VIEW) {
                "查看录屏"
            } else {
                "分享录屏"
            }
        startActivity(
            Intent.createChooser(target, chooserTitle)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }

    companion object {
        private const val TAG = "SuperIslandScreenRecord"
        private const val VIEW_PENDING_INTENT_REQUEST_CODE = 28_373
        private const val SHARE_PENDING_INTENT_REQUEST_CODE = 28_374
        private const val VIDEO_MIME_TYPE = "video/mp4"

        fun viewPendingIntent(
            context: Context,
            outputUri: Uri,
        ): PendingIntent =
            pendingIntent(
                context = context,
                outputUri = outputUri,
                action = ScreenRecordingCompletionActionRequest.ACTION_VIEW,
                requestCode = VIEW_PENDING_INTENT_REQUEST_CODE,
            )

        fun sharePendingIntent(
            context: Context,
            outputUri: Uri,
        ): PendingIntent =
            pendingIntent(
                context = context,
                outputUri = outputUri,
                action = ScreenRecordingCompletionActionRequest.ACTION_SHARE,
                requestCode = SHARE_PENDING_INTENT_REQUEST_CODE,
            )

        private fun pendingIntent(
            context: Context,
            outputUri: Uri,
            action: String,
            requestCode: Int,
        ): PendingIntent =
            PendingIntent.getActivity(
                context,
                requestCode,
                Intent(context, ScreenRecordingCompletionActionActivity::class.java)
                    .setAction(action)
                    .setData(outputUri),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}

internal object ScreenRecordingCompletionActionRequest {
    const val ACTION_VIEW = "io.github.superisland.action.VIEW_SCREEN_RECORDING"
    const val ACTION_SHARE = "io.github.superisland.action.SHARE_SCREEN_RECORDING"

    fun isAllowed(
        action: String?,
        requestedOutputUri: String?,
        persistedOutputUri: String?,
    ): Boolean =
        (action == ACTION_VIEW || action == ACTION_SHARE) &&
            requestedOutputUri?.startsWith("content://") == true &&
            requestedOutputUri == persistedOutputUri
}
