package com.hearopilot.app.domain.usecase.stt

import com.hearopilot.app.domain.model.SttEngine
import com.hearopilot.app.domain.repository.SttRepository

/**
 * Use case to initialize and start STT streaming.
 *
 * @property sttRepository Repository for STT operations
 */
class StartSttStreamingUseCase(
    private val sttRepository: SttRepository
) {
    /**
     * Initialize STT with the specified model.
     *
     * @param modelPath Absolute path to the ONNX model directory
     * @param engine Recognizer variant to load
     * @return Result indicating success or failure
     */
    suspend operator fun invoke(
        modelPath: String,
        engine: SttEngine = SttEngine.SENSE_VOICE
    ): Result<Unit> {
        return sttRepository.initialize(modelPath, engine)
    }
}
