package com.hearopilot.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.hearopilot.app.domain.model.SupportedLanguages
import com.hearopilot.app.presentation.main.MainUiState

/**
 * Face-to-face interpretation view.
 *
 * The top half is rotated 180 degrees for the person sitting opposite; the
 * bottom half faces the phone owner. Each side can hold its own button to talk,
 * and the latest utterance is shown in each side's language as soon as the
 * on-device translation is available.
 */
@Composable
fun FaceToFaceScreen(
    uiState: MainUiState,
    canRecord: Boolean,
    onExit: () -> Unit,
    onStop: () -> Unit,
    onOpenPhrasebook: () -> Unit,
    onPushToTalkStart: () -> Unit,
    onPushToTalkStop: () -> Unit,
    onAutoListenStart: () -> Unit,
    onAutoListenStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current

    var activeSide by remember { mutableStateOf<FaceSide?>(null) }
    var autoListening by rememberSaveable { mutableStateOf(uiState.isRecording) }

    val activeSideState by rememberUpdatedState(activeSide)
    val onPttStopState by rememberUpdatedState(onPushToTalkStop)

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
            if (activeSideState != null) onPttStopState()
        }
    }

    // Owner's language follows the interface locale; the other side gets the
    // complementary language. The STT / translation pipeline is zh <-> en,
    // except in Omnilingual sessions (Luganda/Swahili/French/Arabic output)
    // where the counterpart speaks the session language.
    val counterpartCode = SupportedLanguages.omnilingualCodeFor(uiState.outputLanguage)
    val ownerIsChinese = LocalConfiguration.current.locales[0]
        .language.lowercase().startsWith("zh")
    val bottomLanguage = if (counterpartCode != null || ownerIsChinese) "zh" else "en"
    val topLanguage = counterpartCode
        ?: if (ownerIsChinese) "en" else "zh"

    val segments = uiState.allSegments
    val latest = segments.lastOrNull()
    val latestSource = latest?.languageCode(counterpartCode)
    val zhText = segments.asReversed().firstNotNullOfOrNull { it.textFor("zh", counterpartCode) }
    val counterpartText = counterpartCode?.let { code ->
        segments.asReversed().firstNotNullOfOrNull { it.textFor(code, code) }
    }
    val enText = segments.asReversed().firstNotNullOfOrNull { it.textFor("en", counterpartCode) }
    val bottomText = when (bottomLanguage) {
        "zh" -> zhText
        else -> counterpartText ?: enText
    }
    val topText = when (topLanguage) {
        "zh" -> zhText
        else -> counterpartText ?: enText
    }

    val bottomStatus = resolveFaceStatus(
        side = FaceSide.Owner,
        panelLanguage = bottomLanguage,
        activeSide = activeSide,
        autoListening = autoListening,
        isRecording = uiState.isRecording,
        latest = latest,
        latestSource = latestSource,
        pendingIds = uiState.pendingTranslationIds
    )
    val topStatus = resolveFaceStatus(
        side = FaceSide.Counterpart,
        panelLanguage = topLanguage,
        activeSide = activeSide,
        autoListening = autoListening,
        isRecording = uiState.isRecording,
        latest = latest,
        latestSource = latestSource,
        pendingIds = uiState.pendingTranslationIds
    )

    fun setAutoListening(enabled: Boolean) {
        if (autoListening == enabled) return
        autoListening = enabled
        if (enabled) onAutoListenStart() else onAutoListenStop()
    }

    fun press(side: FaceSide) {
        if (activeSide != null || autoListening || !canRecord ||
            uiState.isFinalizingSession
        ) return
        activeSide = side
        onPushToTalkStart()
    }

    fun release(side: FaceSide) {
        if (activeSide != side) return
        activeSide = null
        onPushToTalkStop()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            FacePanel(
                language = topLanguage,
                text = topText,
                status = topStatus,
                isDark = true,
                autoListening = autoListening && uiState.isRecording,
                isSpeaking = activeSide == FaceSide.Counterpart && uiState.isRecording,
                canTalk = canRecord && !uiState.isFinalizingSession &&
                    !autoListening &&
                    (activeSide == null || activeSide == FaceSide.Counterpart),
                onPress = { press(FaceSide.Counterpart) },
                onRelease = { release(FaceSide.Counterpart) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer { rotationZ = 180f }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.Black.copy(alpha = 0.18f))
            )

            OwnerPanel(
                uiState = uiState,
                language = bottomLanguage,
                text = bottomText,
                status = bottomStatus,
                canRecord = canRecord,
                autoListening = autoListening,
                activeSide = activeSide,
                onToggleAuto = { setAutoListening(!autoListening) },
                onPress = { press(FaceSide.Owner) },
                onRelease = { release(FaceSide.Owner) },
                onOpenPhrasebook = onOpenPhrasebook,
                onExit = onExit,
                onStop = onStop,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}
