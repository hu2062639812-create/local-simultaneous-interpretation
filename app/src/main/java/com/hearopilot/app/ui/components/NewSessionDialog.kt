package com.hearopilot.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hearopilot.app.ui.R
import com.hearopilot.app.domain.model.AppSettings
import com.hearopilot.app.domain.model.DownloadState
import com.hearopilot.app.domain.model.InsightStrategy
import com.hearopilot.app.domain.model.RecordingMode
import com.hearopilot.app.ui.icons.AppIcons
import com.hearopilot.app.ui.ui.theme.*

/**
 * New-session dialog for the offline interpretation app.
 *
 * Every session is created as [RecordingMode.REAL_TIME_TRANSLATION] with an
 * end-of-session summary. The only user choices are the translation direction
 * (auto / into Chinese / into English / Chinese-Luganda) and an optional session topic.
 */
@Composable
fun NewSessionDialog(
    onDismiss: () -> Unit,
    onConfirm: (String?, RecordingMode, String?, InsightStrategy, String?) -> Unit,
    settings: AppSettings = AppSettings(),
    isLugandaModelDownloaded: Boolean = false,
    lugandaDownloadState: DownloadState = DownloadState.Idle,
    onDownloadLugandaModel: () -> Unit = {}
) {
    // null = auto (bidirectional), "zh" = force Chinese output, "en" = force English output,
    // "lg" = Chinese <-> Luganda (Uganda).
    var direction by remember { mutableStateOf<String?>(null) }
    var sessionTopic by remember { mutableStateOf("") }

    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.88f

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .padding(horizontal = 20.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .background(BrandPurpleDark)
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.interpretation_dialog_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.interpretation_dialog_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                AppIcons.Close,
                                contentDescription = stringResource(R.string.cancel),
                                tint = Color.White
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.interpretation_direction_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        DirectionOption(
                            title = stringResource(R.string.interpretation_direction_auto),
                            description = stringResource(R.string.interpretation_direction_auto_desc),
                            isSelected = direction == null,
                            onClick = { direction = null }
                        )
                        DirectionOption(
                            title = stringResource(R.string.interpretation_direction_zh),
                            description = stringResource(R.string.interpretation_direction_zh_desc),
                            isSelected = direction == "zh",
                            onClick = { direction = "zh" }
                        )
                        DirectionOption(
                            title = stringResource(R.string.interpretation_direction_en),
                            description = stringResource(R.string.interpretation_direction_en_desc),
                            isSelected = direction == "en",
                            onClick = { direction = "en" }
                        )
                        DirectionOption(
                            title = stringResource(R.string.interpretation_direction_lg),
                            description = stringResource(R.string.interpretation_direction_lg_desc),
                            isSelected = direction == "lg",
                            onClick = { direction = "lg" }
                        )
                    }

                    if (direction == "lg" && !isLugandaModelDownloaded) {
                        LugandaDownloadCard(
                            downloadState = lugandaDownloadState,
                            onDownload = onDownloadLugandaModel
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = sessionTopic,
                            onValueChange = { sessionTopic = it },
                            label = { Text(stringResource(R.string.interpretation_topic_label)) },
                            placeholder = { Text(stringResource(R.string.interpretation_topic_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium
                        )
                        if (settings.llmEnabled) {
                            Text(
                                text = stringResource(R.string.session_topic_helper),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = ModeEmeraldTint.copy(alpha = 0.10f),
                        tonalElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = AppIcons.Info,
                                contentDescription = null,
                                tint = ModeEmeraldTint,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.interpretation_offline_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    com.hearopilot.app.ui.components.GradientButton(
                        text = stringResource(R.string.interpretation_start),
                        onClick = {
                            val nameAndTopic = sessionTopic.trim().takeIf { it.isNotBlank() }
                            onConfirm(
                                nameAndTopic,
                                RecordingMode.REAL_TIME_TRANSLATION,
                                direction,
                                InsightStrategy.END_OF_SESSION,
                                nameAndTopic
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !(direction == "lg" && !isLugandaModelDownloaded)
                    )
                }
            }
        }
    }
}

@Composable
private fun LugandaDownloadCard(
    downloadState: DownloadState,
    onDownload: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = ModeAmberTint.copy(alpha = 0.08f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.luganda_model_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.luganda_model_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when (val state = downloadState) {
                is DownloadState.Downloading -> {
                    LinearProgressIndicator(
                        progress = { state.progress.percentage / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(
                            R.string.luganda_model_progress, state.progress.percentage
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is DownloadState.Error -> {
                    Text(
                        text = stringResource(R.string.luganda_model_error, state.message),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    OutlinedButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.luganda_model_retry))
                    }
                }
                else -> {
                    OutlinedButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.luganda_model_download))
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectionOption(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) ModeEmeraldTint else MaterialTheme.colorScheme.outlineVariant
    val bgColor = if (isSelected) {
        ModeEmeraldTint.copy(alpha = 0.07f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(bgColor)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = MaterialTheme.shapes.medium
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) ModeEmeraldTint else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isSelected) {
            Icon(
                imageVector = AppIcons.CheckCircle,
                contentDescription = null,
                tint = ModeEmeraldTint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun LanguageSelector(
    languages: List<com.hearopilot.app.domain.model.SupportedLanguage>,
    selectedCode: String,
    onLanguageSelected: (String) -> Unit,
    label: String = stringResource(R.string.new_session_output_language),
    showAutoOption: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }

    val autoLabel = stringResource(R.string.new_session_language_auto)
    val displayName = if (selectedCode.isEmpty()) autoLabel
                      else languages.find { it.code == selectedCode }?.nativeName ?: selectedCode

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(displayName, modifier = Modifier.weight(1f))
                Icon(
                    imageVector = AppIcons.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 320.dp)
            ) {
                if (showAutoOption) {
                    DropdownMenuItem(
                        text = { Text(autoLabel) },
                        onClick = { onLanguageSelected(""); expanded = false },
                        leadingIcon = if (selectedCode.isEmpty()) {
                            { Icon(AppIcons.CheckCircle, null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                    HorizontalDivider()
                }
                languages.forEach { lang ->
                    DropdownMenuItem(
                        text = { Text(lang.nativeName) },
                        onClick = { onLanguageSelected(lang.code); expanded = false },
                        leadingIcon = if (lang.code == selectedCode) {
                            { Icon(AppIcons.CheckCircle, null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }
        }
    }
}
