package io.github.superisland.source.screenrecord

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Scoped MediaStore / SAF output only. No raw storage paths are accepted from configuration. */
class ScreenRecordingStorage(private val context: Context) {
    fun createOutput(storageTreeUri: String): ScreenRecordingOutput {
        val displayName = recordingFileName()
        return if (storageTreeUri.isBlank()) {
            createDefaultOutput(displayName)
        } else {
            createTreeOutput(Uri.parse(storageTreeUri), displayName)
        }
    }

    fun finalize(output: ScreenRecordingOutput) {
        if (!output.isDocumentUri) {
            context.contentResolver.update(
                output.uri,
                ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                null,
                null,
            )
        }
    }

    fun discard(output: ScreenRecordingOutput) {
        runCatching { output.fileDescriptor.close() }
        if (output.isDocumentUri) {
            DocumentsContract.deleteDocument(context.contentResolver, output.uri)
        } else {
            context.contentResolver.delete(output.uri, null, null)
        }
    }

    private fun createDefaultOutput(displayName: String): ScreenRecordingOutput {
        val values =
            ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, MIME_TYPE_MP4)
                put(MediaStore.Video.Media.RELATIVE_PATH, DEFAULT_RELATIVE_PATH)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        val uri =
            requireNotNull(
                context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values),
            ) { "Unable to create MediaStore recording output" }
        val descriptor =
            requireNotNull(context.contentResolver.openFileDescriptor(uri, "w")) {
                "Unable to open MediaStore recording output"
            }
        return ScreenRecordingOutput(uri, displayName, descriptor, isDocumentUri = false)
    }

    private fun createTreeOutput(
        treeUri: Uri,
        displayName: String,
    ): ScreenRecordingOutput {
        require(treeUri.scheme == ContentResolver.SCHEME_CONTENT) { "Storage tree must use content scheme" }
        require(DocumentsContract.isTreeUri(treeUri)) { "Storage URI is not a tree URI" }
        val parent =
            DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
        val outputUri =
            requireNotNull(
                DocumentsContract.createDocument(
                    context.contentResolver,
                    parent,
                    MIME_TYPE_MP4,
                    displayName,
                ),
            ) { "Unable to create recording document" }
        val descriptor =
            requireNotNull(context.contentResolver.openFileDescriptor(outputUri, "w")) {
                "Unable to open recording document"
            }
        return ScreenRecordingOutput(outputUri, displayName, descriptor, isDocumentUri = true)
    }

    private fun recordingFileName(): String =
        "SuperIsland-${SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(Date())}.mp4"

    companion object {
        const val DEFAULT_STORAGE_PATH = "/storage/emulated/0/DCIM/screenrecorder"
        private const val DEFAULT_RELATIVE_PATH = "DCIM/screenrecorder"
        private const val MIME_TYPE_MP4 = "video/mp4"
    }
}

data class ScreenRecordingOutput(
    val uri: Uri,
    val displayName: String,
    val fileDescriptor: ParcelFileDescriptor,
    val isDocumentUri: Boolean,
)
