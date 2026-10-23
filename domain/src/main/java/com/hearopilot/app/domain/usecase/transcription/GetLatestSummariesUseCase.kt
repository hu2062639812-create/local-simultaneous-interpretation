package com.hearopilot.app.domain.usecase.transcription

import com.hearopilot.app.domain.model.LlmInsight
import com.hearopilot.app.domain.repository.TranscriptionRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case for the home screen's summary previews.
 *
 * Exposes the latest end-of-session summary of every session so the session
 * list can show a summary snippet without opening each session.
 */
class GetLatestSummariesUseCase(
    private val transcriptionRepository: TranscriptionRepository
) {
    /**
     * @return Flow emitting session ID -> latest summary
     */
    operator fun invoke(): Flow<Map<String, LlmInsight>> {
        return transcriptionRepository.getLatestSummaries()
    }
}
