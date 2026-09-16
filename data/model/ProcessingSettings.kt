package com.variaflow.mobile.data.model

enum class OutputFormat(val label: String, val extension: String, val mimeType: String) {
    MP4("MP4", "mp4", "video/mp4"),
    MKV("MKV", "mkv", "video/x-matroska"),
    MOV("MOV", "mov", "video/quicktime")
}

enum class ResolutionOption(val label: String, val width: Int, val height: Int) {
    ORIGINAL("Original", 0, 0),
    P1080("1080p", 1920, 1080),
    P720("720p", 1280, 720),
    P480("480p", 854, 480),
    P360("360p", 640, 360)
}

enum class FpsOption(val label: String, val value: Int) {
    ORIGINAL("Original", 0),
    FPS60("60 fps", 60),
    FPS30("30 fps", 30),
    FPS24("24 fps", 24),
    FPS15("15 fps", 15)
}

enum class QualityPreset(val label: String) {
    HIGH_QUALITY("High Quality"),
    BALANCED("Balanced"),
    SMALL_FILE("Small File"),
    CUSTOM("Custom")
}

enum class VideoCodecOption(val label: String, val ffmpegName: String) {
    H264("H.264 (AVC)", "libx264"),
    H265("H.265 (HEVC)", "libx265"),
    MPEG4("MPEG-4", "mpeg4"),
    VP9("VP9", "libvpx-vp9"),
    COPY("Copy (no re-encode)", "copy")
}

enum class AudioCodecOption(val label: String, val ffmpegName: String) {
    AAC("AAC", "aac"),
    MP3("MP3", "libmp3lame"),
    OPUS("Opus", "libopus"),
    VORBIS("Vorbis", "libvorbis"),
    COPY("Copy (no re-encode)", "copy"),
    NONE("No Audio", "none")
}

enum class SampleRateOption(val label: String, val value: Int) {
    R44100("44.1 kHz", 44100),
    R48000("48 kHz", 48000),
    R22050("22.05 kHz", 22050),
    R16000("16 kHz", 16000)
}

enum class ChannelOption(val label: String, val value: Int) {
    STEREO("Stereo", 2),
    MONO("Mono", 1)
}

data class ProcessingSettings(
    val preset: QualityPreset = QualityPreset.BALANCED,
    val outputFormat: OutputFormat = OutputFormat.MP4,
    val resolution: ResolutionOption = ResolutionOption.ORIGINAL,
    val fps: FpsOption = FpsOption.ORIGINAL,
    val videoCodec: VideoCodecOption = VideoCodecOption.H264,
    val videoBitrateKbps: Int = 4000,          // 0 = auto (CRF-based)
    val useCrf: Boolean = true,
    val crfValue: Int = 23,
    val audioCodec: AudioCodecOption = AudioCodecOption.AAC,
    val audioBitrateKbps: Int = 128,
    val sampleRate: SampleRateOption = SampleRateOption.R44100,
    val channels: ChannelOption = ChannelOption.STEREO,
    val useCopyVideo: Boolean = false,
    val useCopyAudio: Boolean = false
) {
    companion object {
        fun forPreset(preset: QualityPreset, sourceInfo: VideoInfo? = null): ProcessingSettings {
            return when (preset) {
                QualityPreset.HIGH_QUALITY -> ProcessingSettings(
                    preset = preset,
                    outputFormat = OutputFormat.MP4,
                    resolution = ResolutionOption.ORIGINAL,
                    fps = FpsOption.ORIGINAL,
                    videoCodec = VideoCodecOption.H264,
                    useCrf = true,
                    crfValue = 18,
                    audioCodec = AudioCodecOption.AAC,
                    audioBitrateKbps = 192,
                    sampleRate = SampleRateOption.R44100,
                    channels = ChannelOption.STEREO
                )
                QualityPreset.BALANCED -> ProcessingSettings(
                    preset = preset,
                    outputFormat = OutputFormat.MP4,
                    resolution = ResolutionOption.P720,
                    fps = FpsOption.FPS30,
                    videoCodec = VideoCodecOption.H264,
                    useCrf = true,
                    crfValue = 23,
                    audioCodec = AudioCodecOption.AAC,
                    audioBitrateKbps = 128,
                    sampleRate = SampleRateOption.R44100,
                    channels = ChannelOption.STEREO
                )
                QualityPreset.SMALL_FILE -> ProcessingSettings(
                    preset = preset,
                    outputFormat = OutputFormat.MP4,
                    resolution = ResolutionOption.P480,
                    fps = FpsOption.FPS24,
                    videoCodec = VideoCodecOption.H264,
                    useCrf = true,
                    crfValue = 28,
                    audioCodec = AudioCodecOption.AAC,
                    audioBitrateKbps = 96,
                    sampleRate = SampleRateOption.R44100,
                    channels = ChannelOption.MONO
                )
                QualityPreset.CUSTOM -> ProcessingSettings(preset = preset)
            }
        }
    }
}
