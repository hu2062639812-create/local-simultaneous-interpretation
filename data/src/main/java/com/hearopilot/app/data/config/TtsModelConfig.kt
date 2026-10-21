package com.hearopilot.app.data.config

/**
 * Download configuration for the optional offline TTS (speech synthesis) model.
 *
 * Uses sherpa-onnx vits-melo-tts-zh_en: a single-voice VITS-Melo model that
 * natively speaks both Chinese and English (no speaker id selection needed).
 *
 * Total download size is ~169 MB (dominated by the 6.8 MB lexicon used by the
 * G2P frontend).
 */
object TtsModelConfig {

    const val DIR_NAME = "tts-melo"

    const val BASE_URL =
        "https://huggingface.co/csukuangfj/vits-melo-tts-zh_en/resolve/main"

    val FILES: List<String> = listOf("model.onnx", "tokens.txt", "lexicon.txt")

    const val MODEL_BYTES = 170_429_550L
    const val TOKENS_BYTES = 655L
    const val LEXICON_BYTES = 6_837_671L

    fun estimatedSize(filename: String): Long = when (filename) {
        "model.onnx"  -> MODEL_BYTES
        "tokens.txt"  -> TOKENS_BYTES
        "lexicon.txt" -> LEXICON_BYTES
        else          -> 100_000L
    }
}
