package com.variaflow.mobile.processing

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.variaflow.mobile.data.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoMetadataExtractor(private val context: Context) {

    suspend fun extract(uri: Uri): Result<VideoInfo> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver

            // File name
            val fileName = queryFileName(uri) ?: "unknown"

            // File size
            val fileSize = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L

            // Use MediaMetadataRetriever for basic info
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)

                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""

                // Determine container from MIME
                val container = when {
                    mimeType.contains("mp4", ignoreCase = true) -> "MP4"
                    mimeType.contains("matroska", ignoreCase = true) || mimeType.contains("mkv", ignoreCase = true) -> "MKV"
                    mimeType.contains("quicktime", ignoreCase = true) || mimeType.contains("mov", ignoreCase = true) -> "MOV"
                    mimeType.contains("webm", ignoreCase = true) -> "WebM"
                    mimeType.contains("avi", ignoreCase = true) -> "AVI"
                    else -> mimeType.substringAfter("/").uppercase()
                }

                // Use MediaExtractor for codec details
                var videoCodec = "Unknown"
                var audioCodec = "Unknown"
                var sampleRate = 0
                var channels = 0
                var fps = 0f

                try {
                    val extractor = MediaExtractor()
                    extractor.setDataSource(context, uri, null)
                    for (i in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(i)
                        val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                        if (mime.startsWith("video/")) {
                            videoCodec = mime.removePrefix("video/").uppercase()
                            if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                                fps = format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                            }
                        } else if (mime.startsWith("audio/")) {
                            audioCodec = mime.removePrefix("audio/").uppercase()
                            if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            }
                            if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            }
                        }
                    }
                    extractor.release()
                } catch (_: Exception) {
                    // Fallback: leave codecs as Unknown
                }

                Result.success(
                    VideoInfo(
                        uri = uri,
                        fileName = fileName,
                        fileSizeBytes = fileSize,
                        durationMs = durationMs,
                        width = if (rotation == 90 || rotation == 270) height else width,
                        height = if (rotation == 90 || rotation == 270) width else height,
                        fps = fps,
                        videoCodec = videoCodec,
                        audioCodec = audioCodec,
                        bitrate = bitrate,
                        containerFormat = container,
                        audioSampleRate = sampleRate,
                        audioChannels = channels
                    )
                )
            } finally {
                retriever.release()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun queryFileName(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
        return cursor.use {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && it.moveToFirst()) it.getString(nameIndex) else null
        }
    }
}
