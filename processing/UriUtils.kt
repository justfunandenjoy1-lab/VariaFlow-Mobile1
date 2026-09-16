package com.variaflow.mobile.processing

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

object UriUtils {

    /**
     * Resolves a content:// URI to a local file path.
     * If the URI points to a remote/cloud file, copies it to cache first.
     */
    fun getPathFromUri(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.path

        return try {
            // Try to get a direct path (works for many local files)
            val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
            var fileName = "input_video"

            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) fileName = cursor.getString(idx)
                }
            }

            // Copy content to a temporary cache file
            val cacheDir = File(context.cacheDir, "variaflow_input")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val tempFile = File(cacheDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output, bufferSize = 8192)
                }
            } ?: return null

            tempFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}
