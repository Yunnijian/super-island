package io.github.superisland.source.screenrecord

internal object ScreenRecordingCompletionPolicy {
    fun finishedState(
        recordingFailure: Throwable?,
        outputUri: String?,
        rootRestoreFailure: Throwable?,
        successMessage: String,
        elapsedDurationMillis: Long,
    ): ScreenRecordingRuntimeState {
        val persistedOutputUri = outputUri?.takeIf(::isValidContentUri)
        return when {
            recordingFailure != null || persistedOutputUri == null ->
                ScreenRecordingRuntimeState(
                    phase = ScreenRecordingPhase.ERROR,
                    message = failureMessage(recordingFailure),
                    elapsedDurationMillis = elapsedDurationMillis.coerceAtLeast(0L),
                )
            rootRestoreFailure != null ->
                ScreenRecordingRuntimeState(
                    phase = ScreenRecordingPhase.ERROR,
                    message = "录屏已保存，但系统设置恢复失败",
                    outputUri = persistedOutputUri,
                    elapsedDurationMillis = elapsedDurationMillis.coerceAtLeast(0L),
                )
            else ->
                ScreenRecordingRuntimeState(
                    phase = ScreenRecordingPhase.IDLE,
                    message = successMessage,
                    outputUri = persistedOutputUri,
                    elapsedDurationMillis = elapsedDurationMillis.coerceAtLeast(0L),
                )
        }
    }

    fun shouldPublish(
        recordingFailure: Throwable?,
        outputUri: String?,
        statePersisted: Boolean,
    ): Boolean =
        recordingFailure == null &&
            statePersisted &&
            isValidContentUri(outputUri)

    private fun failureMessage(recordingFailure: Throwable?): String {
        val detail =
            recordingFailure
                ?.let { failure ->
                    failure.message?.trim()?.takeIf(String::isNotEmpty)
                        ?: failure.javaClass.simpleName
                }
                ?.take(160)
                ?.takeIf(String::isNotBlank)
        return if (detail != null) {
            "录屏失败：$detail"
        } else {
            "录屏失败，未保留不完整文件"
        }
    }

    private fun isValidContentUri(value: String?): Boolean {
        if (value == null || !value.startsWith(CONTENT_URI_PREFIX)) return false
        val authority = value.substring(CONTENT_URI_PREFIX.length).substringBefore('/')
        return authority.isNotBlank()
    }

    private const val CONTENT_URI_PREFIX = "content://"
}
