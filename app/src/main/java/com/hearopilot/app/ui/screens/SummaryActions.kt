package com.hearopilot.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.hearopilot.app.domain.model.LlmInsight
import com.hearopilot.app.ui.R
import com.hearopilot.app.ui.icons.AppIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Absolute timestamp shown on summary cards. */
internal fun formatSummaryTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

/** Summary title falling back to the generic label. */
internal fun summaryTitle(insight: LlmInsight, fallback: String): String =
    insight.title?.takeIf { it.isNotBlank() } ?: fallback

/**
 * Extracts the human-readable body. Some insight rows store JSON with a
 * "summary" field; the Chinese session summary stores plain Markdown.
 */
internal fun summaryBody(content: String): String {
    if (content.trim().startsWith("{")) {
        try {
            val json = org.json.JSONObject(content)
            if (json.has("summary")) return json.getString("summary")
        } catch (_: Exception) {
            // Not valid JSON — fall through to the raw content.
        }
    }
    return content
}

/** Parses a tasks JSON array, accepting both plain strings and {"description": ...} objects. */
internal fun summaryTasks(tasksJson: String?): List<String> {
    if (tasksJson.isNullOrBlank()) return emptyList()
    return try {
        val array = org.json.JSONArray(tasksJson)
        List(array.length()) { i ->
            when (val item = array.get(i)) {
                is org.json.JSONObject -> item.optString("description", "")
                else -> item.toString()
            }
        }.filter { it.isNotBlank() }
    } catch (_: Exception) {
        emptyList()
    }
}

/** Plain-text payload used for both clipboard copy and sharing. */
internal fun buildSummaryClipboardText(
    insight: LlmInsight,
    fallbackTitle: String,
    timeLabel: String,
    tasksLabel: String
): String = buildString {
    appendLine(summaryTitle(insight, fallbackTitle))
    appendLine("$timeLabel: ${formatSummaryTime(insight.timestamp)}")
    appendLine()
    appendLine(summaryBody(insight.content))
    val tasks = summaryTasks(insight.tasks)
    if (tasks.isNotEmpty()) {
        appendLine()
        appendLine(tasksLabel)
        tasks.forEach { appendLine("• $it") }
    }
}.trim()

/** Copy-to-clipboard action with a short toast confirmation. */
@Composable
internal fun rememberCopySummary(insight: LlmInsight): () -> Unit {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val fallbackTitle = stringResource(R.string.tab_summary)
    val timeLabel = stringResource(R.string.summary_time_label)
    val tasksLabel = stringResource(R.string.action_items)
    val copiedMessage = stringResource(R.string.summary_copied)

    return remember(insight, fallbackTitle, timeLabel, tasksLabel, copiedMessage) {
        {
            val text = buildSummaryClipboardText(insight, fallbackTitle, timeLabel, tasksLabel)
            clipboard.setText(AnnotatedString(text))
            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Copy / delete actions shared by summary cards.
 * Delete is hidden while a summary is not yet persisted (e.g. during generation).
 */
@Composable
internal fun SummaryActions(
    onCopy: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = AppIcons.Copy,
                contentDescription = stringResource(R.string.copy_summary),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp)
            )
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = AppIcons.Delete,
                    contentDescription = stringResource(R.string.delete_summary),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}
