package com.variaflow.mobile.data.repository

import android.content.Context
import android.net.Uri
import com.variaflow.mobile.data.model.ProcessingSettings
import com.variaflow.mobile.data.model.VideoInfo
import com.variaflow.mobile.processing.FFmpegProcessor
import com.variaflow.mobile.processing.OutputFileManager
import com.variaflow.mobile.processing.VideoMetadataExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class VideoRepository(private val context: Context) {

    private val metadataExtractor = VideoMetadataExtractor(context)
    private val processor = FFmpegProcessor(context)
    private val outputManager = OutputFileManager(context)

    suspend fun analyzeVideo(uri: Uri): Result<VideoInfo> =
        metadataExtractor.extract(uri)

    suspend fun processVideo(
        uri: Uri,
        settings: ProcessingSettings,
        sourceInfo: VideoInfo,
        onProgress: (Int, String) -> Unit
    ): Result<Uri> = withContext(Dispatchers.IO) {
        // 1. Process with FFmpeg
        val result = processor.process(uri, settings, sourceInfo, onProgress)
        if (result.isFailure) {
            return@withContext Result.failure(
                result.exceptionOrNull() ?: RuntimeException("Processing failed")
            )
        }

        val tempFile = result.getOrThrow()

        // 2. Save to output directory
        val baseName = outputManager.generateBaseName(sourceInfo.fileName)
        val saveResult = outputManager.saveToOutput(tempFile, baseName, settings.outputFormat)

        // 3. Cleanup temp file
        try { tempFile.delete() } catch (_: Exception) {}

        saveResult
    }

    fun cancelProcessing() {
        processor.cancel()
    }

    /**
     * Returns the app-specific output directory path string.
     */
    fun getOutputDirectoryHint(): String {
        return "Movies/VariaFlow/Output"
    }
}
