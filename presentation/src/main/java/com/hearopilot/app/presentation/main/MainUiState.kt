package com.hearopilot.app.presentation.main

import com.hearopilot.app.domain.model.AppSettings
import com.hearopilot.app.domain.model.BatchInsightProgress
import com.hearopilot.app.domain.model.InsightStrategy
import com.hearopilot.app.domain.model.LlmInsight
import com.hearopilot.app.domain.model.RecordingMode
import com.hearopilot.app.domain.model.TranscriptionSegment

/**
 * UI state for the main screen.
 *
 * @property isRecording Whether audio recording and STT is currently active
 * @property isPaused Whether the current session is paused. The STT model and LLM stay
 *   loaded, but the microphone, timer and foreground service are stopped until resume.
 * @property isFinalizingSession Whether a final LLM insight is being generated after stop.
 *   Recording is already stopped (isRecording = false) but the session has not fully
 *   concluded yet. The UI shows a shimmer placeholder while this is true.
 * @property completedSegments List of completed transcription segments (isComplete=true)
 * @property currentPartialSegment Current partial transcription segment being built (isComplete=false)
 * @property insights List of LLM-generated insights
 * @property settings Current application settings
 * @property error Error message to display, null if no error
 * @property llmStatus Status message for LLM (e.g., "Ready", "Processing", "Unavailable")
 * @property isDownloadingModel Whether LLM model is currently being downloaded
 * @property downloadProgress Download progress percentage (0-100)
 * @property insightStrategy The insight generation strategy for the current session
 * @property batchProgress Progress state of the end-of-session batch pipeline
 * @property pendingTranslations Number of completed segments still waiting for on-device translation
 * @property pendingTranslationIds IDs of segments whose translation is still in flight
 * @property isTranslating Whether live translation is currently behind the transcript
 */
data class MainUiState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val isFinalizingSession: Boolean = false,
    val insightStrategy: InsightStrategy = InsightStrategy.REAL_TIME,
    val batchProgress: BatchInsightProgress = BatchInsightProgress.Idle,
    val isInitializing: Boolean = true,
    val isMicPermissionDenied: Boolean = false,
    val completedSegments: List<TranscriptionSegment> = emptyList(),
    val currentPartialSegment: TranscriptionSegment? = null,
    val insights: List<LlmInsight> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val error: String? = null,
    val llmStatus: String = "Ready",
    val isLlmModelAvailable: Boolean = false,
    val isDownloadingModel: Boolean = false,
    val downloadProgress: Int = 0,
    val downloadSpeedMbps: Float = 0f,
    val downloadEtaSeconds: Int = 0,
    val recordingDurationMillis: Long = 0,
    val pendingTranslations: Int = 0,
    val pendingTranslationIds: Set<String> = emptySet(),
    // Whether the offline TTS voice model files are present on disk.
    val ttsModelDownloaded: Boolean = true,
    // Interpretation direction of the current session. An Omni code ("lg", "sw", "fr",
    // "ar", "ko", "es") marks Chinese <-> that language; it drives engine selection,
    // language badges and the face-to-face layout.
    val outputLanguage: String? = null,
    // Recording mode of the current session. Drives whether the subtitle edit
    // affordance (fix original text -> re-translate) is offered.
    val recordingMode: RecordingMode = RecordingMode.SHORT_MEETING
) {
    /** A session is active from the first start until Stop, including paused time. */
    val isSessionActive: Boolean
        get() = isRecording || isPaused

    val isTranslating: Boolean
        get() = pendingTranslations > 0

    /**
     * Returns all segments for display: completed + current partial (if exists).
     * Use this in UI to show the full transcription without duplicates.
     */
    val allSegments: List<TranscriptionSegment>
        get() = completedSegments + listOfNotNull(currentPartialSegment)

    /**
     * Returns insights sorted by timestamp (creation time) instead of arrival time.
     * This prevents insights from appearing out of order when longer insights
     * take more time to generate than shorter ones.
     */
    val sortedInsights: List<LlmInsight>
        get() = insights.sortedBy { it.timestamp }
}
