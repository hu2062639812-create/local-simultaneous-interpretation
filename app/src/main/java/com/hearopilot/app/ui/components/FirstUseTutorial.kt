package com.hearopilot.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hearopilot.app.ui.R
import com.hearopilot.app.ui.icons.AppIcons
import com.hearopilot.app.ui.ui.theme.BrandPurpleDark
import com.hearopilot.app.ui.ui.theme.PrimaryGradientVertical
import com.hearopilot.app.ui.ui.theme.White

private data class TutorialStep(
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector
)

private val TUTORIAL_STEPS = listOf(
    TutorialStep(R.string.tutorial_step1_title, R.string.tutorial_step1_desc, AppIcons.Add),
    TutorialStep(R.string.tutorial_step2_title, R.string.tutorial_step2_desc, AppIcons.Edit),
    TutorialStep(R.string.tutorial_step3_title, R.string.tutorial_step3_desc, AppIcons.Delete)
)

/**
 * One-time first-use tutorial: a dimmed full-screen dialog with three
 * steps covering new sessions, live subtitles/editing and session management.
 * Skipping or finishing it calls [onDismiss]; the caller persists the
 * seen-once flag.
 */
@Composable
fun FirstUseTutorial(
    onDismiss: () -> Unit
) {
    var page by remember { mutableIntStateOf(0) }
    val isLastPage = page == TUTORIAL_STEPS.lastIndex
    val step = TUTORIAL_STEPS[page]

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .background(BrandPurpleDark, RoundedCornerShape(24.dp))
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OnboardingPageIndicator(
                        totalPages = TUTORIAL_STEPS.size,
                        currentPage = page
                    )
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.tutorial_skip),
                            color = White.copy(alpha = 0.75f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .background(brush = PrimaryGradientVertical, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = step.icon,
                        contentDescription = null,
                        tint = White,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = stringResource(step.titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(step.descRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = White.copy(alpha = 0.82f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.heightIn(min = 72.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                GradientButton(
                    text = stringResource(
                        if (isLastPage) R.string.tutorial_start else R.string.tutorial_next
                    ),
                    onClick = {
                        if (isLastPage) onDismiss() else page++
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    gradient = PrimaryGradientVertical
                )
            }
        }
    }
}
