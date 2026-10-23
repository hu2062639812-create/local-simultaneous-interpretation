package com.hearopilot.app.presentation.sessions

import com.hearopilot.app.domain.model.AppSettings
import com.hearopilot.app.domain.model.DownloadState
import com.hearopilot.app.domain.model.LlmInsight
import com.hearopilot.app.domain.model.TranscriptionSession

/**
 * UI state for the sessions list screen.
 *
 * @property sessions List of transcription sessions (most recent first)
 * @property isLoading Whether data is being loaded
 * @property error Error message if loading failed
 * @property showNewSessionDialog Whether to show the new session dialog
 * @property settings Current app settings (used in new-session dialog to show intervals)
 * @property summaries Latest end-of-session summary per session, keyed by session ID
 * @property isLugandaModelDownloaded Whether the optional Luganda ASR model is on disk
 * @property lugandaDownloadState Download state of the optional Luganda ASR model
 */
data class SessionsUiState(
    val sessions: List<TranscriptionSession> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showNewSessionDialog: Boolean = false,
    val settings: AppSettings = AppSettings(),
    // Latest end-of-session summary per session, keyed by session ID.
    val summaries: Map<String, LlmInsight> = emptyMap(),
    // Total bytes of all on-device transcript + insight text (segments + insights).
    val totalDataSizeBytes: Long = 0L,
    val isLugandaModelDownloaded: Boolean = false,
    val lugandaDownloadState: DownloadState = DownloadState.Idle,
    // One-time first-use tutorial overlay; resolved once from DataStore on init.
    val showFirstUseTutorial: Boolean = false
)
