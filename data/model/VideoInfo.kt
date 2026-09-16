package com.variaflow.mobile.data.model

import android.net.Uri

data class VideoInfo(
    val uri: Uri,
    val fileName: String,
    val fileSizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fps: Float,
    val videoCodec: String,
    val audioCodec: String,
    val bitrate: Long,
    val containerFormat: String,
    val audioSampleRate: Int,
    val audioChannels: Int
) {
    val resolution: String get() = "${width}x${height}"
    val durationFormatted: String get() {
        val totalSec = durationMs / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return "%d:%02d".format(m, s)
    }
    val fileSizeFormatted: String get() = formatFileSize(fileSizeBytes)
    val bitrateFormatted: String get() = "${bitrate / 1000} kbps"

    companion object {
        fun formatFileSize(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
        }
    }
}
