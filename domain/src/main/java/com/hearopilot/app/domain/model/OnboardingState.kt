package com.hearopilot.app.domain.model

/**
 * Onboarding step tracking.
 */
enum class OnboardingStep {
    INTERFACE_LANGUAGE, // First-run UI language selection (Chinese / English)
    WELCOME,        // Welcome screen
    LANGUAGES,      // Supported languages showcase (informational, no selection)
    STT_DOWNLOAD,   // STT model download
    LLM_DOWNLOAD,   // LLM model download
    COMPLETED       // Onboarding complete
}

/**
 * Model type for download management.
 */
enum class ModelType {
    STT,  // Speech-to-text model
    LLM   // Large language model
}
