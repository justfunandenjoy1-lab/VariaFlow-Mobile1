package com.variaflow.mobile.processing

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.variaflow.mobile.data.model.OutputFormat
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

class OutputFileManager(private val context: Context) {

    companion object {
        const val OUTPUT_DIR_NAME = "VariaFlow"
        const val SUB_DIR = "Output"
    }

    /**
     * Saves a processed file to VariaFlow/Output/ using MediaStore.
     * Returns the public URI of the saved file.
     */
    fun saveToOutput(tempFile: File, baseName: String, format: OutputFormat): Result<Uri> {
        return try {
            val displayName = generateUniqueName(baseName, format.extension)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(tempFile, displayName, format)
            } else {
                saveViaLegacyStorage(tempFile, displayName, format)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun saveViaMediaStore(tempFile: File, displayName: String, format: OutputFormat): Result<Uri> {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, format.mimeType)
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_MOVIES}/$OUTPUT_DIR_NAME/$SUB_DIR"
            )
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = context.contentResolver.insert(collection, values)
            ?: return Result.failure(IllegalStateException("Failed to create MediaStore entry"))

        context.contentResolver.openOutputStream(uri)?.use { out ->
            FileInputStream(tempFile).use { input ->
                input.copyTo(out)
            }
        } ?: return Result.failure(IllegalStateException("Cannot open output stream"))

        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)

        return Result.success(uri)
    }

    @Suppress("DEPRECATION")
    private fun saveViaLegacyStorage(tempFile: File, displayName: String, format: OutputFormat): Result<Uri> {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "$OUTPUT_DIR_NAME/$SUB_DIR"
        )
        if (!dir.exists()) dir.mkdirs()
        val outputFile = File(dir, displayName)
        tempFile.copyTo(outputFile, overwrite = false)
        return Result.success(Uri.fromFile(outputFile))
    }

    /**
     * Generates a unique filename like "video_001_processed.mp4".
     */
    private fun generateUniqueName(baseName: String, extension: String): String {
        val cleanBase = baseName.substringBeforeLast(".").take(40)
        var counter = 1
        var candidate: String
        do {
            candidate = "${cleanBase}_${"%03d".format(counter)}_processed.$extension"
            counter++
        } while (fileExistsInOutput(candidate))
        return candidate
    }

    private fun fileExistsInOutput(name: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val projection = arrayOf(MediaStore.Video.Media._ID)
                val selection = "${MediaStore.Video.Media.DISPLAY_NAME} = ?"
                context.contentResolver.query(collection, projection, selection, arrayOf(name), null)?.use {
                    it.count > 0
                } ?: false
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "$OUTPUT_DIR_NAME/$SUB_DIR"
                )
                File(dir, name).exists()
            }
        } catch (_: Exception) {
            false
        }
    }

    fun generateBaseName(originalFileName: String): String {
        return originalFileName.substringBeforeLast(".")
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .take(40)
    }
}
