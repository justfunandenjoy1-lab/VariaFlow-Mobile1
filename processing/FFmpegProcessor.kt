package com.variaflow.mobile.processing

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.variaflow.mobile.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FFmpegProcessor(private val context: Context) {

    /**
     * Builds the FFmpeg command from settings.
     * Output is written to a cache file first, then moved to the final destination.
     */
    fun buildCommand(
        inputPath: String,
        outputPath: String,
        settings: ProcessingSettings,
        sourceInfo: VideoInfo
    ): Array<String> {
        val cmd = mutableListOf<String>()

        // Global
        cmd.add("-y") // overwrite
        cmd.add("-i")
        cmd.add(inputPath)

        // Video codec
        if (settings.useCopyVideo || settings.videoCodec == VideoCodecOption.COPY) {
            cmd.add("-c:v")
            cmd.add("copy")
        } else {
            cmd.add("-c:v")
            cmd.add(settings.videoCodec.ffmpegName)

            // Quality: CRF or bitrate
            if (settings.useCrf) {
                cmd.add("-crf")
                cmd.add(settings.crfValue.toString())
                cmd.add("-preset")
                cmd.add("medium")
            } else {
                cmd.add("-b:v")
                cmd.add("${settings.videoBitrateKbps}k")
            }

            // Resolution
            if (settings.resolution != ResolutionOption.ORIGINAL) {
                cmd.add("-vf")
                cmd.add("scale=${settings.resolution.width}:${settings.resolution.height}:force_original_aspect_ratio=decrease")
            }

            // FPS
            if (settings.fps != FpsOption.ORIGINAL) {
                cmd.add("-r")
                cmd.add(settings.fps.value.toString())
            }

            // Pixel format for compatibility
            cmd.add("-pix_fmt")
            cmd.add("yuv420p")
        }

        // Audio codec
        if (settings.audioCodec == AudioCodecOption.NONE) {
            cmd.add("-an")
        } else if (settings.useCopyAudio || settings.audioCodec == AudioCodecOption.COPY) {
            cmd.add("-c:a")
            cmd.add("copy")
        } else {
            cmd.add("-c:a")
            cmd.add(settings.audioCodec.ffmpegName)
            cmd.add("-b:a")
            cmd.add("${settings.audioBitrateKbps}k")
            cmd.add("-ar")
            cmd.add(settings.sampleRate.value.toString())
            cmd.add("-ac")
            cmd.add(settings.channels.value.toString())
        }

        // Container / format
        when (settings.outputFormat) {
            OutputFormat.MP4 -> {
                cmd.add("-movflags")
                cmd.add("+faststart")
            }
            OutputFormat.MKV -> { /* MKV handles most codecs natively */ }
            OutputFormat.MOV -> {
                cmd.add("-movflags")
                cmd.add("+faststart")
            }
        }

        cmd.add("-f")
        cmd.add(settings.outputFormat.extension)

        cmd.add(outputPath)
        return cmd.toTypedArray()
    }

    /**
     * Executes FFmpeg and reports progress via callback.
     */
    suspend fun process(
        inputUri: Uri,
        settings: ProcessingSettings,
        sourceInfo: VideoInfo,
        onProgress: (Int, String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val inputPath = UriUtils.getPathFromUri(context, inputUri)
                ?: return@withContext Result.failure(
                    IllegalStateException("Cannot resolve video file path. The file may be on a remote drive.")
                )

            val cacheDir = File(context.cacheDir, "variaflow_tmp")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val tempOutput = File(cacheDir, "output_${System.currentTimeMillis()}.${settings.outputFormat.extension}")

            val command = buildCommand(inputPath, tempOutput.absolutePath, settings, sourceInfo)

            onProgress(0, "Preparing...")

            val session: FFmpegSession = FFmpegKit.executeAsync(
                command.joinToString(" "),
                { /* session complete */ },
                { /* log */ },
                { statistics ->
                    val timeMs = statistics.time
                    val durationMs = sourceInfo.durationMs
                    if (durationMs > 0) {
                        val pct = ((timeMs.toFloat() / durationMs.toFloat()) * 100f)
                            .toInt().coerceIn(0, 99)
                        onProgress(pct, "Encoding...")
                    }
                }
            ).get()

            val returnCode = session.returnCode
            if (ReturnCode.isSuccess(returnCode)) {
                onProgress(100, "Finalizing...")
                Result.success(tempOutput)
            } else {
                val logs = session.allLogsAsString
                Result.failure(
                    RuntimeException("FFmpeg failed (code ${returnCode.value}): ${logs.takeLast(500)}")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun cancel() {
        FFmpegKit.cancel()
    }
}
