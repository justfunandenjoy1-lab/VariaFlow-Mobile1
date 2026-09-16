package com.variaflow.mobile.viewmodel

import android.net.Uri
import com.variaflow.mobile.data.model.ProcessingSettings
import com.variaflow.mobile.data.model.ProcessingState
import com.variaflow.mobile.data.model.VideoInfo

data class MainUiState(
    val videoInfos: List<VideoInfo> = emptyList(),
    val selectedUris: List<Uri> = emptyList(),
    val currentVideoInfo: VideoInfo? = null,
    val settings: ProcessingSettings = ProcessingSettings(),
    val processingState: ProcessingState = ProcessingState.Idle,
    val showSettingsScreen: Boolean = false,
    val showComparisonScreen: Boolean = false,
    val showBatchScreen: Boolean = false,
    val qualityWarning: String? = null,
    val snackbarMessage: String? = null
)
