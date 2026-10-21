package com.hearopilot.app.data.config

/**
 * Download configuration for the optional Luganda speech-recognition model.
 *
 * Luganda is not supported by SenseVoice or Whisper, so Luganda sessions use the
 * Sherpa-ONNX Omnilingual 1600-language CTC model (int8, ~365 MB) instead.
 */
object LugandaModelConfig {

    const val DIR_NAME = "stt-omnilingual"

    const val BASE_URL =
        "https://huggingface.co/csukuangfj/sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-int8-2025-11-12/resolve/main"

    val FILES: List<String> = listOf("model.int8.onnx", "tokens.txt")

    const val MODEL_BYTES = 365_352_120L
    const val TOKENS_BYTES = 86_423L

    fun estimatedSize(filename: String): Long =
        if (filename == "model.int8.onnx") MODEL_BYTES else TOKENS_BYTES
}
