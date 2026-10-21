package com.hearopilot.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.hearopilot.app.ui.R
import com.hearopilot.app.ui.icons.AppIcons
import com.hearopilot.app.ui.ui.theme.ModeEmeraldTint

/**
 * Full-screen phrase card. The flip toggle rotates the text 180 degrees so the
 * person across the table can read it without turning the phone around.
 */
@Composable
fun PhrasebookDetailDialog(
    phrase: PhraseEntry,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    var flipped by remember { mutableStateOf(false) }
    val copiedMessage = stringResource(R.string.phrasebook_copied)

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        val controller = context.findFaceActivity()?.window?.let {
            WindowCompat.getInsetsController(it, view)
        }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            view.keepScreenOn = false
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(phrase.category.labelRes),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            val clip = ClipData.newPlainText(
                                "phrase",
                                "${phrase.zh}\n${phrase.en}"
                            )
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as ClipboardManager
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = AppIcons.Copy,
                            contentDescription = stringResource(R.string.phrasebook_copy),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { flipped = !flipped }) {
                        Icon(
                            imageVector = AppIcons.Flip,
                            contentDescription = stringResource(R.string.phrasebook_flip),
                            tint = if (flipped) ModeEmeraldTint
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = AppIcons.Close,
                            contentDescription = stringResource(R.string.phrasebook_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .graphicsLayer { rotationZ = if (flipped) 180f else 0f },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = phrase.zh,
                            fontSize = phraseFontSize(phrase.zh, isChinese = true),
                            lineHeight = phraseFontSize(phrase.zh, isChinese = true) * 1.3f,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = phrase.en,
                            fontSize = phraseFontSize(phrase.en, isChinese = false),
                            lineHeight = phraseFontSize(phrase.en, isChinese = false) * 1.3f,
                            fontWeight = FontWeight.SemiBold,
                            color = ModeEmeraldTint,
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 10,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = if (flipped) {
                        stringResource(R.string.phrasebook_flipped_hint)
                    } else {
                        stringResource(R.string.phrasebook_show_hint)
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }
        }
    }
}

private fun phraseFontSize(text: String, isChinese: Boolean): androidx.compose.ui.unit.TextUnit {
    val length = text.length
    return when {
        length <= 18 -> if (isChinese) 40.sp else 34.sp
        length <= 40 -> if (isChinese) 32.sp else 28.sp
        length <= 80 -> 24.sp
        else -> 20.sp
    }
}
