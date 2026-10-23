package com.hearopilot.app.domain.model

/**
 * On-device speech recognition engine used by a session.
 *
 * [SENSE_VOICE] handles Chinese/English/Japanese/Korean/Cantonese with very low
 * latency and is the default for zh <-> en interpretation.
 *
 * [OMNILINGUAL] is a 1600-language CTC model used for Luganda, Kiswahili,
 * French, Arabic, Korean and Spanish sessions. Sessions in these directions
 * always run the separate multilingual model.
 */
enum class SttEngine {
    SENSE_VOICE,
    OMNILINGUAL
}
