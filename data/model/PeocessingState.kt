package com.variaflow.mobile.data.model

sealed class ProcessingState {
    data object Idle : ProcessingState()
    data class Analyzing(val fileName: String) : ProcessingState()
    data class Processing(
        val fileName: String,
        val progressPercent: Int,
        val stage: String,
        val currentFileIndex: Int,
        val totalFiles: Int
    ) : ProcessingState()
    data class Completed(
        val outputFileNames: List<String>,
        val outputUris: List<android.net.Uri>
    ) : ProcessingState()
    data class Error(val message: String, val detail: String = "") : ProcessingState()
    data object Cancelled : ProcessingState()
}
