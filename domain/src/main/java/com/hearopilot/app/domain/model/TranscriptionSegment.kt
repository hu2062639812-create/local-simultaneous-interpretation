package com.hearopilot.app.domain.model

/**
 * Represents a single segment of transcribed text from speech-to-text.
 *
 * Each segment belongs to a specific transcription session, allowing multiple
 * independent recording sessions to be stored separately.
 *
 * @property id Unique identifier for this segment
 * @property sessionId ID of the session this segment belongs to
 * @property text The transcribed text content
 * @property timestamp Unix timestamp (milliseconds) when this segment was created
 * @property isComplete Whether this segment represents a complete utterance (speech ended)
 * @property translatedText On-device translation of [text], null while pending or unavailable
 * @property detectedLanguage Language detected by the ASR model (e.g. "zh", "en")
 */
data class TranscriptionSegment(
    val id: String,
    val sessionId: String,
    val text: String,
    val timestamp: Long,
    val isComplete: Boolean,
    val translatedText: String? = null,
    val detectedLanguage: String? = null
)
