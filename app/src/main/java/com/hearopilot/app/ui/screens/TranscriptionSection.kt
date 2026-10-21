package com.hearopilot.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hearopilot.app.ui.R
import com.hearopilot.app.ui.icons.AppIcons
import com.hearopilot.app.ui.ui.theme.*
import com.hearopilot.app.domain.model.SupportedLanguages
import com.hearopilot.app.domain.model.TranscriptionSegment

/**
 * Bilingual subtitle display.
 * - Source utterance plus its on-device translation
 * - Auto-scroll to the latest segment
 */
@Composable
fun TranscriptionSection(
    segments: List<TranscriptionSegment>,
    isRecording: Boolean,
    isPaused: Boolean = false,
    pendingTranslationIds: Set<String>,
    nonCjkCode: String? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(segments.size) {
        if (segments.isNotEmpty()) {
            listState.animateScrollToItem(segments.size - 1)
        }
    }

    Box(modifier = modifier) {
        if (segments.isEmpty()) {
            TranscriptionEmptyState(
                isRecording = isRecording,
                isPaused = isPaused,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(segments) { segment ->
                    TranscriptionItem(
                        segment = segment,
                        isTranslating = segment.id in pendingTranslationIds,
                        nonCjkCode = nonCjkCode
                    )
                }
            }
        }
    }
}

/**
 * Empty state: centered icon + text, no gradient hero.
 *
 * Idle:      static mic icon, "Press Record to start" hint.
 * Recording: mic icon pulses, text changes to "Listening…".
 * Paused:    static mic icon, paused hint.
 */
@Composable
private fun TranscriptionEmptyState(
    isRecording: Boolean,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    // Pulse animation — always created, only applied when recording.
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_scale"
    )
    val iconScale = if (isRecording) pulseScale else 1f

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = AppIcons.Mic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f),
            modifier = Modifier
                .size(56.dp)
                .scale(iconScale)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when {
                isRecording -> stringResource(R.string.transcription_listening)
                isPaused -> stringResource(R.string.recording_paused)
                else -> stringResource(R.string.transcription_empty)
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        if (!isRecording) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isPaused) stringResource(R.string.session_paused_hint)
                       else stringResource(R.string.transcription_press_record),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 40.dp)
            )
        }
    }
}

@Composable
private fun TranscriptionItem(
    segment: TranscriptionSegment,
    isTranslating: Boolean,
    nonCjkCode: String?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (segment.isComplete) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        },
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LanguageBadge(segment, nonCjkCode)
                if (!segment.isComplete) {
                    Text(
                        text = stringResource(R.string.transcription_listening),
                        style = MaterialTheme.typography.labelSmall,
                        color = ModeEmeraldTint
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = segment.text,
                style = MaterialTheme.typography.transcription,
                color = MaterialTheme.colorScheme.onSurface
            )

            val translated = segment.translatedText?.takeIf { it.isNotBlank() }
            if (translated != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Text(
                    text = translated,
                    style = MaterialTheme.typography.transcription,
                    fontWeight = FontWeight.Medium,
                    color = ModeEmeraldTint
                )
            } else if (segment.isComplete && isTranslating) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = ModeEmeraldTint
                    )
                    Text(
                        text = stringResource(R.string.translation_pending),
                        style = MaterialTheme.typography.labelSmall,
                        color = ModeEmeraldTint
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageBadge(segment: TranscriptionSegment, nonCjkCode: String?) {
    val detected = segment.detectedLanguage?.lowercase()
    val sessionLang = nonCjkCode?.let { SupportedLanguages.getByCode(it) }
    val isChinese = detected?.startsWith("zh") == true ||
        detected?.startsWith("yue") == true ||
        (detected == null && segment.text.any { it.code in 0x4E00..0x9FFF })
    val isNonCjk = sessionLang != null && !isChinese &&
        (detected == null || detected.startsWith(sessionLang.code))
    val badge = when {
        isChinese -> stringResource(R.string.language_badge_zh)
        isNonCjk -> sessionLang?.nativeName?.uppercase() ?: ""
        detected?.startsWith("en") == true -> stringResource(R.string.language_badge_en)
        detected != null -> SupportedLanguages.getByCode(detected.substringBefore('-'))
            ?.nativeName?.uppercase() ?: detected.take(2).uppercase()
        else -> stringResource(R.string.language_badge_en)
    }

    Text(
        text = badge,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(
                when {
                    isChinese -> ModeEmeraldTint
                    isNonCjk -> ModeAmberTint
                    else -> ModeSkyBlueTint
                }
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
