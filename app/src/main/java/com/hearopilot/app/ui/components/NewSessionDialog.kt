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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hearopilot.app.ui.R
import com.hearopilot.app.domain.model.AppSettings
import com.hearopilot.app.domain.model.DownloadState
import com.hearopilot.app.domain.model.InsightStrategy
import com.hearopilot.app.domain.model.RecordingMode
import com.hearopilot.app.domain.model.SupportedLanguages
import com.hearopilot.app.ui.icons.AppIcons
import com.hearopilot.app.ui.ui.theme.*

/**
 * New-session dialog for the offline interpretation app.
 *
 * Every session is created as [RecordingMode.REAL_TIME_TRANSLATION] with an
 * end-of-session summary. The only user choices are the language pair, picked
 * in a single bilingual row (two dropdowns with a swap button), and an
 * optional session topic.
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
    // Bilingual pair chosen in a single row. AUTO = auto-detect Chinese/English;
    // picking an Omnilingual language (lg/sw/fr/ar) on either side pins the
    // other side to Chinese. resolveDirection() maps the pair to the session's
    // outputLanguage: null = auto, "zh"/"en" = forced output, "lg"/"sw"/"fr"/"ar".
    var leftLang by remember { mutableStateOf(AUTO) }
    var rightLang by remember { mutableStateOf(AUTO) }
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
                        BilingualDirectionRow(
                            left = leftLang,
                            right = rightLang,
                            onLeftSelected = { code ->
                                val (l, r) = normalizePair(code, rightLang)
                                leftLang = l
                                rightLang = r
                            },
                            onRightSelected = { code ->
                                val (l, r) = normalizePair(leftLang, code)
                                leftLang = l
                                rightLang = r
                            },
                            onSwap = {
                                val (l, r) = normalizePair(rightLang, leftLang)
                                leftLang = l
                                rightLang = r
                            }
                        )
                    }

                    if (SupportedLanguages.isOmnilingualMode(resolveDirection(leftLang, rightLang)) &&
                        !isLugandaModelDownloaded
                    ) {
                        OmnilingualDownloadCard(
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
                                resolveDirection(leftLang, rightLang),
                                InsightStrategy.END_OF_SESSION,
                                nameAndTopic
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !(SupportedLanguages.isOmnilingualMode(
                            resolveDirection(leftLang, rightLang)
                        ) && !isLugandaModelDownloaded)
                    )
                }
            }
        }
    }
}

@Composable
private fun OmnilingualDownloadCard(
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
                text = stringResource(R.string.omnilingual_model_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.omnilingual_model_desc),
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
                            R.string.omnilingual_model_progress, state.progress.percentage
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is DownloadState.Error -> {
                    Text(
                        text = stringResource(R.string.omnilingual_model_error, state.message),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    OutlinedButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.omnilingual_model_retry))
                    }
                }
                else -> {
                    OutlinedButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.omnilingual_model_download))
                    }
                }
            }
        }
    }
}

/** Sentinel for the auto-detect (Chinese ⇄ English) side of the pair picker. */
private const val AUTO = "auto"

/** Directions that run the Omnilingual ASR engine; always paired with Chinese. */
private val OMNILINGUAL_CODES = setOf("lg", "sw", "fr", "ar")

private val DIRECTION_CODES = listOf(AUTO, "zh", "en", "lg", "sw", "fr", "ar")

/**
 * Maps a normalized language pair to the session's outputLanguage value:
 * null = auto-detect Chinese/English, "zh"/"en" = forced output language,
 * "lg"/"sw"/"fr"/"ar" = bidirectional Chinese <-> that language.
 */
private fun resolveDirection(left: String, right: String): String? = when {
    left == AUTO || right == AUTO -> null
    left in OMNILINGUAL_CODES -> left
    right in OMNILINGUAL_CODES -> right
    else -> right // (zh, en) -> "en"; (en, zh) -> "zh"
}

/**
 * Enforces the only valid combinations: auto pairs only with zh/en, an
 * Omnilingual side forces the other side to Chinese, and the two sides of a
 * zh/en pair must differ.
 */
private fun normalizePair(left: String, right: String): Pair<String, String> {
    var l = left
    var r = right
    when {
        l == AUTO -> if (r in OMNILINGUAL_CODES) r = "zh"
        r == AUTO -> if (l in OMNILINGUAL_CODES) l = "zh"
        l in OMNILINGUAL_CODES -> r = "zh"
        r in OMNILINGUAL_CODES -> l = "zh"
        l == r -> r = if (l == "zh") "en" else "zh"
    }
    return l to r
}

/**
 * Single-row bilingual picker: two compact language dropdowns joined by a
 * swap button. Replaces the old stacked direction list.
 */
@Composable
private fun BilingualDirectionRow(
    left: String,
    right: String,
    onLeftSelected: (String) -> Unit,
    onRightSelected: (String) -> Unit,
    onSwap: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CompactLanguageDropdown(
            selected = left,
            onSelected = onLeftSelected,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onSwap) {
            Icon(
                imageVector = AppIcons.Swap,
                contentDescription = stringResource(R.string.language_swap),
                tint = ModeEmeraldTint,
                modifier = Modifier.size(22.dp)
            )
        }
        CompactLanguageDropdown(
            selected = right,
            onSelected = onRightSelected,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CompactLanguageDropdown(
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = directionDisplayName(selected),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
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
            DIRECTION_CODES.forEach { code ->
                DropdownMenuItem(
                    text = { Text(directionDisplayName(code)) },
                    onClick = {
                        onSelected(code)
                        expanded = false
                    },
                    leadingIcon = if (code == selected) {
                        { Icon(AppIcons.CheckCircle, null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun directionDisplayName(code: String): String =
    if (code == AUTO) stringResource(R.string.direction_auto_label)
    else SupportedLanguages.getByCode(code)?.nativeName ?: code

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
