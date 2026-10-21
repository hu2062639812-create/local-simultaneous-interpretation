package com.hearopilot.app.domain.model

/**
 * On-device speech recognition engine used by a session.
 *
 * [SENSE_VOICE] handles Chinese/English/Japanese/Korean/Cantonese with very low
 * latency and is the default for zh <-> en interpretation.
 *
 * [OMNILINGUAL] is a 1600-language CTC model used for Luganda sessions. Luganda
 * is not supported by SenseVoice or Whisper, so it requires this separate model.
 */
enum class SttEngine {
    SENSE_VOICE,
    OMNILINGUAL
}
