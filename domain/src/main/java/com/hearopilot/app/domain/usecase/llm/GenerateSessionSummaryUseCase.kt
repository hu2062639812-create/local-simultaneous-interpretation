package com.hearopilot.app.domain.usecase.llm

import com.hearopilot.app.domain.model.LlmInsight
import com.hearopilot.app.domain.model.TranscriptionSegment
import com.hearopilot.app.domain.repository.LlmRepository
import kotlinx.coroutines.withTimeout
import java.util.UUID

/**
 * Produces one Chinese end-of-session summary (title, overview, action items)
 * from the bilingual transcript.
 *
 * Long sessions are processed map-reduce: each chunk yields a condensed summary,
 * then the partials are merged into the final structured JSON. This keeps the
 * context within the limits of a 0.8B on-device model.
 */
class GenerateSessionSummaryUseCase(
    private val llmRepository: LlmRepository
) {
    companion object {
        private const val MIN_TRANSCRIPT_CHARS = 24

        // The native context is 4096 tokens. Chinese text tokenizes at roughly
        // one token per character, so chunk sizes must be counted conservatively:
        // 3 000 input chars + ~120 prompt tokens + 700 output tokens stays in budget.
        private const val MAX_SINGLE_PASS_CHARS = 3_000
        private const val MAX_CHUNK_CHARS = 2_400
        private const val MAX_MERGE_INPUT_CHARS = 3_000
        private const val MAX_TOKENS_CHUNK = 320
        private const val MAX_TOKENS_FINAL = 700

        // Upper bound for one summary generation (chunk or merge). Without it a
        // runaway generation keeps the "finalizing" spinner forever on non-thinking
        // models and delays the persisted summary indefinitely.
        private const val INFERENCE_TIMEOUT_MS = 120_000L

        // "译文：" + newline added around each translated line in chunk prompts.
        private const val TRANSLATION_PREFIX_CHARS = 4

        private const val MAP_SYSTEM_PROMPT =
            "你是一名专业的会议记录员。请用简体中文精炼地总结用户提供的对话片段，" +
                "保留关键事实、数字、决定和待办事项。只输出总结正文，不要添加标题或解释。"

        private const val FINAL_SYSTEM_PROMPT =
            "你是一名专业的会议纪要助手。请根据用户提供的对话记录，用简体中文输出一份总结。" +
                "严格只输出 JSON，格式为：\n" +
                "{\"title\":\"简短标题\",\"summary\":\"分点总结\",\"action_items\":[\"待办1\",\"待办2\"]}\n" +
                "summary 使用 2-5 个要点；没有明确待办时 action_items 输出空数组。不要输出 Markdown 代码块。"
    }

    private data class ParsedSummary(
        val title: String,
        val content: String,
        val tasks: String?
    )

    suspend operator fun invoke(
        segments: List<TranscriptionSegment>,
        sessionId: String,
        topic: String? = null
    ): Result<LlmInsight?> {
        val usable = segments.filter { it.text.isNotBlank() }
        if (usable.isEmpty()) return Result.success(null)

        val fullTranscript = buildTranscript(usable, includeTranslations = false)
        if (fullTranscript.length < MIN_TRANSCRIPT_CHARS) return Result.success(null)

        return try {
            llmRepository.reloadModel().onFailure { return Result.failure(it) }
            llmRepository.checkAndCacheMemoryConstraint()

            val rawSummary = if (fullTranscript.length <= MAX_SINGLE_PASS_CHARS) {
                runInference(
                    buildFinalPrompt(fullTranscript, topic),
                    FINAL_SYSTEM_PROMPT,
                    MAX_TOKENS_FINAL
                )
            } else {
                val chunks = splitIntoSegments(usable)
                val partials = chunks.mapIndexed { index, chunk ->
                    runInference(
                        buildMapPrompt(
                            transcript = buildTranscript(chunk, includeTranslations = true),
                            topic = topic,
                            index = index + 1,
                            total = chunks.size
                        ),
                        MAP_SYSTEM_PROMPT,
                        MAX_TOKENS_CHUNK
                    )
                }.filter { it.isNotBlank() }

                if (partials.isEmpty()) return Result.success(null)
                runInference(
                    buildFinalPrompt(
                        partials.joinToString("\n\n---\n\n").take(MAX_MERGE_INPUT_CHARS),
                        topic
                    ),
                    FINAL_SYSTEM_PROMPT,
                    MAX_TOKENS_FINAL
                )
            }

            if (rawSummary.isBlank()) return Result.success(null)
            val parsed = parse(rawSummary)
            Result.success(
                LlmInsight(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    title = parsed.title,
                    content = parsed.content,
                    tasks = parsed.tasks,
                    timestamp = System.currentTimeMillis(),
                    sourceSegmentIds = emptyList()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun runInference(
        prompt: String,
        systemPrompt: String,
        maxTokens: Int
    ): String {
        if (llmRepository.isLargeContext(prompt.length)) {
            llmRepository.useConservativeThreads()
        }
        llmRepository.reloadModel()
        val builder = StringBuilder()
        withTimeout(INFERENCE_TIMEOUT_MS) {
            llmRepository.generateRaw(prompt, systemPrompt, maxTokens)
                .collect { token -> builder.append(token) }
        }
        llmRepository.recordConstrainedInference(prompt.length)
        return InsightOutputParser.stripCodeFences(
            InsightOutputParser.stripThinkingBlock(builder.toString())
        ).trim()
    }

    private fun buildTranscript(
        segments: List<TranscriptionSegment>,
        includeTranslations: Boolean
    ): String {
        return segments.joinToString("\n") { segment ->
            val source = segment.text.trim()
            val translation = segment.translatedText?.trim()
            if (includeTranslations && !translation.isNullOrBlank()) {
                "$source\n译文：$translation"
            } else {
                source
            }
        }
    }

    private fun buildMapPrompt(
        transcript: String,
        topic: String?,
        index: Int,
        total: Int
    ): String {
        return buildString {
            if (!topic.isNullOrBlank()) {
                appendLine("会话主题：$topic")
            }
            appendLine("以下是第 $index/$total 段对话记录：")
            appendLine(transcript)
            append("请用简体中文总结这段对话的要点。")
        }
    }

    private fun buildFinalPrompt(content: String, topic: String?): String {
        return buildString {
            if (!topic.isNullOrBlank()) {
                appendLine("会话主题：$topic")
            }
            appendLine("对话记录：")
            appendLine(content)
            append("请输出 JSON 总结。")
        }
    }

    private fun splitIntoSegments(
        segments: List<TranscriptionSegment>
    ): List<List<TranscriptionSegment>> {
        val chunks = mutableListOf<MutableList<TranscriptionSegment>>()
        var current = mutableListOf<TranscriptionSegment>()
        var currentChars = 0

        for (segment in segments) {
            val sourceLength = segment.text.trim().length + 1
            val translationLength = segment.translatedText
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { it.length + TRANSLATION_PREFIX_CHARS }
                ?: 0
            val length = sourceLength + translationLength
            if (current.isNotEmpty() && currentChars + length > MAX_CHUNK_CHARS) {
                chunks.add(current)
                current = mutableListOf()
                currentChars = 0
            }
            current.add(segment)
            currentChars += length
        }
        if (current.isNotEmpty()) chunks.add(current)
        return chunks
    }

    private fun parse(raw: String): ParsedSummary {
        val title = InsightOutputParser.extractJsonField(raw, "title")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "会话总结"
        val summary = InsightOutputParser.extractJsonField(raw, "summary")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: raw
        val actions = InsightOutputParser.extractJsonArray(raw, "action_items")
            .ifEmpty { InsightOutputParser.extractJsonArray(raw, "tasks") }
        val tasks = if (actions.isNotEmpty()) {
            "[${actions.joinToString(",") { "\"${escapeJson(it)}\"" }}]"
        } else null
        return ParsedSummary(title = title, content = summary, tasks = tasks)
    }

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
