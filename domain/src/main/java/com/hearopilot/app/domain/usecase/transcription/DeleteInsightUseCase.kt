package com.hearopilot.app.domain.usecase.transcription

import com.hearopilot.app.domain.repository.TranscriptionRepository

/**
 * Use case for deleting a single LLM insight from a session.
 */
class DeleteInsightUseCase(
    private val repository: TranscriptionRepository
) {
    suspend operator fun invoke(insightId: String): Result<Unit> =
        repository.deleteInsight(insightId)
}
