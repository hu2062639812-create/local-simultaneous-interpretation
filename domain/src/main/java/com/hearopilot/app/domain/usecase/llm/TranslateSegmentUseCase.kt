package com.hearopilot.app.domain.usecase.llm

import com.hearopilot.app.domain.model.TranscriptionSegment
import com.hearopilot.app.domain.repository.LlmRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Translates a single recognized utterance on-device.
 *
 * The target is derived from the ASR-detected language:
 * - "auto" performs bidirectional zh <-> en interpretation.
 * - "zh" / "en" force a single target language.
 * Utterances already in the target language are skipped.
 */
class TranslateSegmentUseCase(
    private val llmRepository: LlmRepository
) {
    companion object {
        private const val MAX_INPUT_CHARS = 1_500
        private const val MIN_OUTPUT_TOKENS = 64
        private const val MAX_OUTPUT_TOKENS = 384

        // Bounds a single utterance translation so one stalled generation cannot
        // block the queue (and the end-of-session summary) indefinitely.
        private const val TRANSLATION_TIMEOUT_MS = 45_000L

        private val CJK = Regex("[\\u3400-\\u9fff\\uf900-\\ufaff]")
        private val PREFIX = Regex(
            "^(translation|translated text|翻译|译文|英文翻译|中文翻译)\\s*[:：]\\s*",
            RegexOption.IGNORE_CASE
        )
    }

    data class Translation(
        val text: String,
        val targetLanguage: String
    )

    suspend operator fun invoke(
        segment: TranscriptionSegment,
        outputLanguage: String?
    ): Result<Translation?> {
        val sourceText = segment.text.trim()
        if (sourceText.isBlank()) return Result.success(null)

        val sourceLanguage = resolveSourceLanguage(segment)
        val targetLanguage = resolveTargetLanguage(sourceLanguage, outputLanguage)
            ?: return Result.success(null)

        val sourceName = languageName(sourceLanguage)
        val targetName = languageName(targetLanguage)
        val systemPrompt = """
            You are a professional simultaneous interpreter.
            Translate the user's $sourceName text into $targetName.
            Output ONLY the translation, without quotes, notes, explanations, or the original text.
            Keep names, numbers and technical terms accurate. Never answer the content as a question.
        """.trimIndent()

        val maxTokens = (sourceText.length * 2).coerceIn(MIN_OUTPUT_TOKENS, MAX_OUTPUT_TOKENS)
        val builder = StringBuilder()
        try {
            withTimeout(TRANSLATION_TIMEOUT_MS) {
                llmRepository.generateRaw(
                    prompt = sourceText.take(MAX_INPUT_CHARS),
                    systemPrompt = systemPrompt,
                    maxTokens = maxTokens
                ).collect { token -> builder.append(token) }
            }
        } catch (e: TimeoutCancellationException) {
            return Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val translated = cleanOutput(builder.toString(), targetLanguage)
        if (translated.isBlank() || translated.equals(sourceText, ignoreCase = true)) {
            return Result.success(null)
        }
        return Result.success(Translation(translated, targetLanguage))
    }

    fun resolveSourceLanguage(segment: TranscriptionSegment): String {
        val detected = segment.detectedLanguage
            ?.lowercase()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        return when {
            detected == null -> if (CJK.containsMatchIn(segment.text)) "zh" else "en"
            detected.startsWith("zh") || detected.startsWith("yue") -> "zh"
            detected.startsWith("en") -> "en"
            else -> detected.take(2)
        }
    }

    fun resolveTargetLanguage(sourceLanguage: String, outputLanguage: String?): String? {
        val preference = outputLanguage?.lowercase()?.trim()
        return when {
            preference.isNullOrBlank() || preference == "auto" ->
                if (sourceLanguage == "zh") "en" else "zh"
            preference.startsWith("zh") ->
                if (sourceLanguage == "zh") null else "zh"
            preference.startsWith("en") ->
                if (sourceLanguage == "en") null else "en"
            else ->
                if (sourceLanguage == "zh") "en" else "zh"
        }
    }

    private fun languageName(code: String): String = when {
        code.startsWith("zh") -> "Simplified Chinese"
        code.startsWith("en") -> "English"
        code.startsWith("ja") -> "Japanese"
        code.startsWith("ko") -> "Korean"
        else -> code
    }

    private fun cleanOutput(raw: String, targetLanguage: String): String {
        var text = InsightOutputParser.stripCodeFences(
            InsightOutputParser.stripThinkingBlock(raw)
        ).trim()

        text = text.removeSurrounding("\"").removeSurrounding("“", "”").trim()
        text = PREFIX.replace(text, "").trim()

        // Some small models append a short note after a blank line; keep the first block.
        val parts = text.split(Regex("\\n\\s*\\n"))
        if (parts.size > 1 && parts.first().isNotBlank()) {
            text = parts.first().trim()
        }

        val targetIsChinese = targetLanguage.startsWith("zh")
        val containsCjk = CJK.containsMatchIn(text)
        if (targetIsChinese && !containsCjk) return ""
        return text
    }
}
