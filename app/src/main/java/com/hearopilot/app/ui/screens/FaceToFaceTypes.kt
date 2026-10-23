package com.hearopilot.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hearopilot.app.domain.model.SupportedLanguages
import com.hearopilot.app.domain.model.TranscriptionSegment

internal enum class FaceSide { Owner, Counterpart }

internal enum class FaceStatus {
    Idle,
    Listening,
    OtherSpeaking,
    Translating,
    AutoListening
}

internal fun resolveFaceStatus(
    side: FaceSide,
    panelLanguage: String,
    activeSide: FaceSide?,
    autoListening: Boolean,
    isRecording: Boolean,
    latest: TranscriptionSegment?,
    latestSource: String?,
    pendingIds: Set<String>
): FaceStatus {
    val waitingForThisPanel = latestSource != null && latestSource != panelLanguage
    val latestIsPartial = latest != null && !latest.isComplete
    return when {
        activeSide == side && !autoListening -> FaceStatus.Listening
        latestIsPartial && waitingForThisPanel -> FaceStatus.OtherSpeaking
        latest != null && latest.isComplete && latest.id in pendingIds && waitingForThisPanel ->
            FaceStatus.Translating
        autoListening && isRecording -> FaceStatus.AutoListening
        else -> FaceStatus.Idle
    }
}

internal fun faceStatusText(status: FaceStatus, chinese: Boolean): String = when (status) {
    FaceStatus.Listening -> if (chinese) "正在聆听…" else "Listening…"
    FaceStatus.OtherSpeaking -> if (chinese) "对方正在说话…" else "Other side speaking…"
    FaceStatus.Translating -> if (chinese) "正在翻译…" else "Translating…"
    FaceStatus.AutoListening -> if (chinese) "自动聆听中" else "Auto listening"
    FaceStatus.Idle -> if (chinese) "按住说话" else "Hold to speak"
}

/**
 * Source-language resolution shared by the face-to-face panels.
 *
 * [nonCjkCode] is the session's speech-language code in Omnilingual sessions
 * (Luganda/Swahili/French/Arabic/Korean/Spanish); in those sessions
 * non-Chinese text is attributed to that language.
 */
internal fun TranscriptionSegment.languageCode(nonCjkCode: String? = null): String {
    val detected = detectedLanguage?.lowercase()?.trim()
    return when {
        detected == null -> when {
            text.any { it.code in 0x4E00..0x9FFF } -> "zh"
            nonCjkCode != null -> nonCjkCode
            else -> "en"
        }
        detected.startsWith("zh") || detected.startsWith("yue") -> "zh"
        detected.startsWith("en") -> "en"
        detected.startsWith("lg") || detected.startsWith("lug") -> "lg"
        else -> detected.take(2)
    }
}

internal fun TranscriptionSegment.textFor(
    language: String,
    nonCjkCode: String? = null
): String? {
    val source = languageCode(nonCjkCode)
    return if (source == language) {
        text.takeIf { it.isNotBlank() }
    } else {
        translatedText?.takeIf { it.isNotBlank() }
    }
}

internal tailrec fun Context.findFaceActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findFaceActivity()
    else -> null
}

@Composable
internal fun FaceLanguagePill(language: String, isDark: Boolean) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isDark) Color.White.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (isDark) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Text(
            text = when (language) {
                "zh" -> "中文"
                else -> SupportedLanguages.getByCode(language)
                    ?.nativeName
                    ?.uppercase()
                    ?: "ENGLISH"
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
internal fun FaceSpeechText(text: String?, fallback: String, isDark: Boolean) {
    val content = text?.takeIf { it.isNotBlank() }
    val fontSize = when {
        content == null -> 26.sp
        content.length <= 24 -> 36.sp
        content.length <= 64 -> 29.sp
        content.length <= 120 -> 23.sp
        else -> 19.sp
    }
    Text(
        text = content ?: fallback,
        modifier = Modifier.fillMaxWidth(),
        color = when {
            content == null -> if (isDark) Color.White.copy(alpha = 0.55f)
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
            isDark -> Color.White
            else -> MaterialTheme.colorScheme.onSurface
        },
        fontSize = fontSize,
        lineHeight = fontSize * 1.25f,
        fontWeight = if (content == null) FontWeight.Normal else FontWeight.SemiBold,
        maxLines = 6,
        overflow = TextOverflow.Ellipsis
    )
}
