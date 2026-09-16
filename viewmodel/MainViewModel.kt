package com.variaflow.mobile.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.variaflow.mobile.data.model.*
import com.variaflow.mobile.data.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VideoRepository(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // ---------- Video Selection ----------

    fun onVideoSelected(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(processingState = ProcessingState.Analyzing(uris.first().lastPathSegment ?: "video")) }

            val infos = mutableListOf<VideoInfo>()
            for (uri in uris) {
                val result = repository.analyzeVideo(uri)
                if (result.isSuccess) {
                    infos.add(result.getOrThrow())
                } else {
                    _uiState.update {
                        it.copy(
                            processingState = ProcessingState.Error(
                                "Cannot read video file. It may be corrupted or unsupported.",
                                result.exceptionOrNull()?.message ?: ""
                            )
                        )
                    }
                    return@launch
                }
            }

            val firstInfo = infos.firstOrNull()
            val warning = firstInfo?.let { checkQualityWarning(it, _uiState.value.settings) }

            _uiState.update {
                it.copy(
                    videoInfos = infos,
                    selectedUris = uris,
                    currentVideoInfo = firstInfo,
                    processingState = ProcessingState.Idle,
                    qualityWarning = warning
                )
            }
        }
    }

    // ---------- Settings ----------

    fun updateSettings(settings: ProcessingSettings) {
        val warning = _uiState.value.currentVideoInfo?.let { checkQualityWarning(it, settings) }
        _uiState.update { it.copy(settings = settings, qualityWarning = warning) }
    }

    fun applyPreset(preset: QualityPreset) {
        val newSettings = ProcessingSettings.forPreset(preset, _uiState.value.currentVideoInfo)
        updateSettings(newSettings)
    }

    // ---------- Processing ----------

    fun processCurrentVideo() {
        val info = _uiState.value.currentVideoInfo ?: return
        val settings = _uiState.value.settings

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    processingState = ProcessingState.Processing(
                        fileName = info.fileName,
                        progressPercent = 0,
                        stage = "Starting...",
                        currentFileIndex = 1,
                        totalFiles = 1
                    )
                )
            }

            val result = repository.processVideo(info.uri, settings, info) { pct, stage ->
                _uiState.update {
                    it.copy(
                        processingState = ProcessingState.Processing(
                            fileName = info.fileName,
                            progressPercent = pct,
                            stage = stage,
                            currentFileIndex = 1,
                            totalFiles = 1
                        )
                    )
                }
            }

            result.fold(
                onSuccess = { outputUri ->
                    _uiState.update {
                        it.copy(
                            processingState = ProcessingState.Completed(
                                outputFileNames = listOf(info.fileName),
                                outputUris = listOf(outputUri)
                            )
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            processingState = ProcessingState.Error(
                                friendlyError(e),
                                e.message ?: ""
                            )
                        )
                    }
                }
            )
        }
    }

    fun processBatch() {
        val infos = _uiState.value.videoInfos
        if (infos.isEmpty()) return
        val settings = _uiState.value.settings

        viewModelScope.launch {
            val outputUris = mutableListOf<Uri>()
            val outputNames = mutableListOf<String>()
            val failed = mutableListOf<String>()

            for ((index, info) in infos.withIndex()) {
                _uiState.update {
                    it.copy(
                        processingState = ProcessingState.Processing(
                            fileName = info.fileName,
                            progressPercent = 0,
                            stage = "Starting...",
                            currentFileIndex = index + 1,
                            totalFiles = infos.size
                        )
                    )
                }

                val result = repository.processVideo(info.uri, settings, info) { pct, stage ->
                    _uiState.update {
                        it.copy(
                            processingState = ProcessingState.Processing(
                                fileName = info.fileName,
                                progressPercent = pct,
                                stage = stage,
                                currentFileIndex = index + 1,
                                totalFiles = infos.size
                            )
                        )
                    }
                }

                result.fold(
                    onSuccess = { uri ->
                        outputUris.add(uri)
                        outputNames.add(info.fileName)
                    },
                    onFailure = { failed.add(info.fileName) }
                )
            }

            if (failed.isNotEmpty() && outputUris.isEmpty()) {
                _uiState.update {
                    it.copy(processingState = ProcessingState.Error("All files failed to process. ${failed.size} error(s)."))
                }
            } else {
                _uiState.update {
                    it.copy(
                        processingState = ProcessingState.Completed(outputNames, outputUris)
                    )
                }
            }
        }
    }

    fun cancelProcessing() {
        repository.cancelProcessing()
        _uiState.update { it.copy(processingState = ProcessingState.Cancelled) }
    }

    // ---------- Navigation ----------

    fun navigateToSettings() = _uiState.update { it.copy(showSettingsScreen = true) }
    fun navigateBackFromSettings() = _uiState.update { it.copy(showSettingsScreen = false) }
    fun navigateToComparison() = _uiState.update { it.copy(showComparisonScreen = true) }
    fun navigateBackFromComparison() = _uiState.update { it.copy(showComparisonScreen = false) }
    fun navigateToBatch() = _uiState.update { it.copy(showBatchScreen = true) }
    fun navigateBackFromBatch() = _uiState.update { it.copy(showBatchScreen = false) }

    fun clearSnackbar() = _uiState.update { it.copy(snackbarMessage = null) }
    fun showSnackbar(msg: String) = _uiState.update { it.copy(snackbarMessage = msg) }

    // ---------- Helpers ----------

    private fun checkQualityWarning(info: VideoInfo, settings: ProcessingSettings): String? {
        val warnings = mutableListOf<String>()

        if (settings.resolution != ResolutionOption.ORIGINAL &&
            settings.resolution.height > info.height) {
            warnings.add("Upscaling from ${info.height}p to ${settings.resolution.height}p will not improve quality.")
        }

        if (settings.useCrf && settings.crfValue > 28 && settings.videoCodec == VideoCodecOption.H264) {
            warnings.add("CRF ${settings.crfValue} is very high and may cause visible quality loss.")
        }

        if (settings.audioBitrateKbps < 64 && settings.audioCodec != AudioCodecOption.COPY && settings.audioCodec != AudioCodecOption.NONE) {
            warnings.add("Audio bitrate below 64 kbps may sound muffled.")
        }

        if (settings.fps != FpsOption.ORIGINAL && settings.fps.value < info.fps.toInt() / 2) {
            warnings.add("Reducing FPS from ${info.fps.toInt()} to ${settings.fps.value} may cause choppy playback.")
        }

        return warnings.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private fun friendlyError(e: Throwable): String {
        val msg = e.message ?: ""
        return when {
            msg.contains("No space left", ignoreCase = true) ->
                "Insufficient storage space. Free up some space and try again."
            msg.contains("Permission denied", ignoreCase = true) ->
                "Permission denied. Please grant storage access and try again."
            msg.contains("out of memory", ignoreCase = true) ->
                "Device memory is insufficient. Try a smaller resolution or fewer files."
            msg.contains("codec", ignoreCase = true) ->
                "Unsupported codec. Try a different output codec."
            msg.contains("Invalid data", ignoreCase = true) || msg.contains("corrupt", ignoreCase = true) ->
                "The video file appears to be corrupted or unsupported."
            else -> "Processing failed. ${msg.take(200)}"
        }
    }
}
