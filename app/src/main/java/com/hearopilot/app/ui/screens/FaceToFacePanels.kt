package com.hearopilot.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hearopilot.app.presentation.main.MainUiState
import com.hearopilot.app.ui.R
import com.hearopilot.app.ui.icons.AppIcons
import com.hearopilot.app.ui.ui.theme.BrandPurpleDark
import com.hearopilot.app.ui.ui.theme.ModeEmeraldTint

/** Rotated counterpart-facing half: status, translated text and a hold-to-talk button. */
@Composable
internal fun FacePanel(
    language: String,
    text: String?,
    status: FaceStatus,
    isDark: Boolean,
    autoListening: Boolean,
    isSpeaking: Boolean,
    canTalk: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(if (isDark) BrandPurpleDark else MaterialTheme.colorScheme.background)
            // The panel is rotated 180°, so bottom padding becomes clearance from
            // the physical top edge, clear of the status/mic privacy indicators.
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FaceLanguagePill(language = language, isDark = isDark)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = faceStatusText(status, language == "zh"),
                fontSize = 13.sp,
                color = when {
                    status == FaceStatus.Listening || status == FaceStatus.AutoListening ->
                        if (isDark) Color(0xFF7CE3B1) else ModeEmeraldTint
                    isDark -> Color.White.copy(alpha = 0.70f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            FaceSpeechText(
                text = text,
                fallback = faceStatusText(status, language == "zh"),
                isDark = isDark
            )
        }

        if (autoListening) {
            AutoListeningCard(
                language = language,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            )
        } else {
            PushToTalkButton(
                language = language,
                enabled = canTalk,
                isActive = isSpeaking,
                onPress = onPress,
                onRelease = onRelease,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            )
        }
    }
}

/** Owner-facing half with the timer, phrasebook shortcut and session controls. */
@Composable
internal fun OwnerPanel(
    uiState: MainUiState,
    language: String,
    text: String?,
    status: FaceStatus,
    canRecord: Boolean,
    autoListening: Boolean,
    activeSide: FaceSide?,
    onToggleAuto: () -> Unit,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onOpenPhrasebook: () -> Unit,
    onExit: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FaceLanguagePill(language = language, isDark = false)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatRecordingTime(uiState.recordingDurationMillis),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = faceStatusText(status, language == "zh"),
                    fontSize = 13.sp,
                    color = if (status == FaceStatus.Listening) ModeEmeraldTint
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onOpenPhrasebook) {
                Icon(
                    imageVector = AppIcons.Phrasebook,
                    contentDescription = stringResource(R.string.face_to_face_phrases),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = onExit) {
                Icon(
                    imageVector = AppIcons.Close,
                    contentDescription = stringResource(R.string.face_to_face_exit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            FaceSpeechText(
                text = text,
                fallback = faceStatusText(status, language == "zh"),
                isDark = false
            )
        }

        uiState.error?.let { error ->
            Text(
                text = error,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        if (!uiState.isLlmModelAvailable) {
            Text(
                text = stringResource(R.string.face_to_face_no_llm),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilterChip(
                selected = autoListening,
                onClick = onToggleAuto,
                label = { Text(stringResource(R.string.face_to_face_auto)) },
                leadingIcon = if (autoListening) {
                    {
                        Icon(
                            AppIcons.Headphones,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else null,
                enabled = canRecord && !uiState.isFinalizingSession && activeSide == null
            )

            if (autoListening && uiState.isRecording) {
                AutoListeningCard(
                    language = language,
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                )
            } else {
                PushToTalkButton(
                    language = language,
                    enabled = canRecord && !uiState.isFinalizingSession &&
                        !autoListening &&
                        (activeSide == null || activeSide == FaceSide.Owner),
                    isActive = activeSide == FaceSide.Owner && uiState.isRecording,
                    onPress = onPress,
                    onRelease = onRelease,
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                )
            }

            IconButton(
                onClick = onStop,
                enabled = !uiState.isFinalizingSession,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Stop,
                    contentDescription = stringResource(R.string.stop),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
