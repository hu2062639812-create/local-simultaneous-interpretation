package com.hearopilot.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import com.hearopilot.app.ui.icons.AppIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hearopilot.app.ui.R
import com.hearopilot.app.ui.ui.theme.*
import com.hearopilot.app.domain.model.DownloadState
import com.hearopilot.app.presentation.main.MainViewModel
import com.hearopilot.app.ui.components.ShimmerInsightCard
import java.util.concurrent.TimeUnit

/**
 * Formats milliseconds to mm:ss or h:mm:ss format for recording timer display.
 * Shows hours only when recording exceeds 60 minutes.
 */
internal fun formatRecordingTime(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

/**
 * Libellula - Mobile-First Design
 * - Tabs instead of side-by-side columns
 * - Floating Action Button for Record
 * - Full-width content area
 *
 * @param onNavigateToSettings Callback to navigate to Settings screen
 * @param onNavigateBack Callback to navigate back to sessions list
 * @param onNavigateToLlmDownload Callback to navigate to LLM download screen
 * @param viewModel MainViewModel for managing app state
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToLlmDownload: () -> Unit = {},
    onNavigateToPhrasebook: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val llmDownloadState by viewModel.llmDownloadState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showFaceToFace by rememberSaveable { mutableStateOf(false) }
    var pushToTalkMode by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    // Intercept hardware back button while recording is active.
    var showStopConfirmDialog by remember { mutableStateOf(false) }
    // Block back navigation while recording or while the session is being finalized.
    BackHandler(enabled = (uiState.isSessionActive || uiState.isFinalizingSession) && !showFaceToFace) {
        if (!uiState.isFinalizingSession) {
            showStopConfirmDialog = true
        }
        // When finalizing: silently consume the back press — user must wait.
    }

    // Request RECORD_AUDIO (critical) + POST_NOTIFICATIONS (Android 13+, best-effort).
    // POST_NOTIFICATIONS is required for the foreground service notification to appear.
    // Without it on API 33+, the notification is silently suppressed.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // If RECORD_AUDIO is absent from results it was not requested (already granted).
        // Fall back to a runtime check rather than treating absence as denial.
        val micGranted = results[Manifest.permission.RECORD_AUDIO]
            ?: (ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED)
        if (micGranted) {
            viewModel.initialize()
        } else {
            viewModel.onMicPermissionDenied()
        }
        // POST_NOTIFICATIONS denial is non-blocking: recording works, notification won't show.
    }

    LaunchedEffect(Unit) {
        val micGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val permissionsToRequest = buildList {
            if (!micGranted) add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val notifGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!notifGranted) add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isEmpty()) {
            viewModel.initialize()
        } else {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    // Confirmation dialog shown when user presses back (hardware or nav icon) while recording.
    if (showStopConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showStopConfirmDialog = false },
            title = { Text(stringResource(R.string.stop_recording_confirm_title)) },
            text = { Text(stringResource(R.string.stop_recording_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirmDialog = false
                    viewModel.stopStreaming()
                    onNavigateBack()
                }) {
                    Text(stringResource(R.string.stop_and_go_back))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Show dialog and navigate back when microphone permission is denied
    if (uiState.isMicPermissionDenied) {
        AlertDialog(
            onDismissRequest = onNavigateBack,
            title = { Text(stringResource(R.string.permission_mic_required)) },
            text = { Text(stringResource(R.string.permission_mic_denied)) },
            confirmButton = {
                TextButton(onClick = onNavigateBack) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Box(modifier = Modifier.background(BrandPurpleDark)) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                fontSize = 20.sp,
                                fontFamily = SpaceGroteskFont,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (uiState.isSessionActive) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(
                                                if (uiState.isRecording) Color.Red else ModeAmberTint,
                                                CircleShape
                                            )
                                    )
                                }
                                Text(
                                    text = when {
                                        uiState.isRecording ->
                                            formatRecordingTime(uiState.recordingDurationMillis)
                                        uiState.isPaused ->
                                            "${stringResource(R.string.recording_paused)} · ${formatRecordingTime(uiState.recordingDurationMillis)}"
                                        else -> stringResource(R.string.recording_session)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = if (uiState.isSessionActive) 1f else 0.75f),
                                    fontWeight = if (uiState.isSessionActive) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                when {
                                    uiState.isFinalizingSession -> { /* blocked: session is finalizing */ }
                                    uiState.isSessionActive -> showStopConfirmDialog = true
                                    else -> onNavigateBack()
                                }
                            },
                            enabled = !uiState.isFinalizingSession
                        ) {
                            Icon(
                                imageVector = AppIcons.Back,
                                contentDescription = stringResource(R.string.back_to_sessions)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onNavigateToPhrasebook) {
                            Icon(
                                imageVector = AppIcons.Phrasebook,
                                contentDescription = stringResource(R.string.phrasebook_title)
                            )
                        }
                        // Download progress indicator shown in top bar during model download
                        if (uiState.isDownloadingModel) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(end = 16.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Text(
                                    text = "${uiState.downloadProgress}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            }
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp
            ) {
                // Left slot: Stop while a session is active (recording or paused),
                // otherwise Clear history.
                if (uiState.isSessionActive) {
                    val stopTint = if (uiState.isFinalizingSession) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                    IconButton(
                        onClick = { viewModel.stopStreaming() },
                        enabled = !uiState.isFinalizingSession,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                AppIcons.Stop,
                                stringResource(R.string.stop),
                                tint = stopTint,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = stringResource(R.string.stop),
                                style = MaterialTheme.typography.labelSmall,
                                color = stopTint
                            )
                        }
                    }
                } else {
                    IconButton(
                        onClick = { viewModel.clearHistory() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                AppIcons.Delete,
                                stringResource(R.string.clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = stringResource(R.string.clear),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Center FAB: Mic (idle) → Pause (recording) → Play (paused).
                // Disabled while STT is initializing (models still loading).
                // Shows frozen icon + spinner overlay while session is finalizing.
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    val haptic = LocalHapticFeedback.current
                    val canRecord = !uiState.isInitializing && !uiState.isMicPermissionDenied
                    val infiniteTransition = rememberInfiniteTransition(label = "fab_pulse")
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = if (uiState.isRecording) 1.08f else 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "fab_scale"
                    )
                    val fabEnabled = !uiState.isFinalizingSession &&
                        (uiState.isSessionActive || canRecord)
                    val pttDescription = stringResource(
                        if (uiState.isRecording) R.string.push_to_talk_release
                        else R.string.push_to_talk_hold
                    )
                    val fabGradient = when {
                        pushToTalkMode && uiState.isRecording -> Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.error,
                                MaterialTheme.colorScheme.error
                            )
                        )
                        pushToTalkMode -> PrimaryGradient
                        uiState.isPaused -> ModeTranslationGradient
                        uiState.isRecording -> Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.error,
                                MaterialTheme.colorScheme.error
                            )
                        )
                        else -> PrimaryGradient
                    }

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .scale(if (uiState.isFinalizingSession) 1f else scale)
                            .alpha(
                                when {
                                    uiState.isFinalizingSession -> 0.5f
                                    fabEnabled -> 1f
                                    else -> 0.4f
                                }
                            )
                            .background(brush = fabGradient, shape = CircleShape)
                            .then(
                                if (pushToTalkMode) {
                                    Modifier
                                        .pointerInput(fabEnabled) {
                                            if (!fabEnabled) return@pointerInput
                                            awaitEachGesture {
                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                down.consume()
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.startPushToTalk()
                                                try {
                                                    waitForUpOrCancellation()
                                                } finally {
                                                    viewModel.stopPushToTalk()
                                                    haptic.performHapticFeedback(
                                                        HapticFeedbackType.TextHandleMove
                                                    )
                                                }
                                            }
                                        }
                                        .semantics {
                                            contentDescription = pttDescription
                                            role = Role.Button
                                        }
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (pushToTalkMode) {
                            Icon(
                                imageVector = AppIcons.Mic,
                                contentDescription = pttDescription,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        } else {
                            IconButton(
                                onClick = {
                                    if (!uiState.isFinalizingSession) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        when {
                                            uiState.isPaused -> viewModel.startStreaming()
                                            uiState.isRecording -> viewModel.pauseStreaming()
                                            else -> viewModel.startStreaming()
                                        }
                                    }
                                },
                                enabled = fabEnabled,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(
                                    imageVector = when {
                                        uiState.isPaused -> AppIcons.Play
                                        uiState.isRecording -> AppIcons.Pause
                                        else -> AppIcons.Mic
                                    },
                                    contentDescription = when {
                                        uiState.isPaused -> stringResource(R.string.resume)
                                        uiState.isRecording -> stringResource(R.string.pause)
                                        else -> stringResource(R.string.record)
                                    },
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    // Spinner overlay shown while finalizing the session.
                    if (uiState.isFinalizingSession) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(72.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // AI status — icon tint shows readiness, no dot
                val aiReady = uiState.llmStatus.contains("Ready")
                IconButton(
                    onClick = { /* no-op */ },
                    modifier = Modifier.weight(1f),
                    enabled = false
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            AppIcons.AutoAwesome,
                            contentDescription = null,
                            tint = if (aiReady) AccentSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(R.string.llm),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (aiReady) AccentSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Error banner
            uiState.error?.let { error ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                AppIcons.Info,
                                null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { viewModel.clearError() }) {
                            Icon(AppIcons.Close, stringResource(R.string.dismiss), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Quick controls for the offline face-to-face scenario.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {
                        // Hand the mic to the two hold-to-talk buttons in the
                        // face-to-face panel instead of leaving auto capture on.
                        if (uiState.isRecording) viewModel.pauseStreaming()
                        showFaceToFace = true
                    },
                    label = { Text(stringResource(R.string.face_to_face_open)) },
                    leadingIcon = {
                        Icon(
                            AppIcons.FaceToFace,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    enabled = !uiState.isInitializing && !uiState.isMicPermissionDenied &&
                        !uiState.isFinalizingSession
                )
                FilterChip(
                    selected = pushToTalkMode,
                    onClick = {
                        pushToTalkMode = !pushToTalkMode
                        if (pushToTalkMode && uiState.isRecording) {
                            viewModel.pauseStreaming()
                        }
                    },
                    label = { Text(stringResource(R.string.push_to_talk_toggle)) },
                    leadingIcon = {
                        Icon(
                            AppIcons.HoldToTalk,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    enabled = !uiState.isFinalizingSession
                )
            }

            if (pushToTalkMode) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ) {
                    Text(
                        text = stringResource(
                            if (uiState.isRecording) R.string.push_to_talk_active_hint
                            else R.string.push_to_talk_hint
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = BrandPrimary,
                contentColor = Color.White,
                divider = {},
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        val tab = tabPositions[selectedTab]
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .wrapContentSize(Alignment.BottomStart, unbounded = true)
                                .offset(x = tab.left)
                                .width(tab.width)
                                .height(3.dp)
                                .background(Color.White)
                        )
                    }
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            stringResource(R.string.tab_subtitles),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = {
                        Icon(AppIcons.Translate, null)
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            stringResource(R.string.tab_summary),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = {
                        Icon(AppIcons.Summarize, null)
                    }
                )
            }

            // Content Area - Full Width
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (uiState.isInitializing) {
                    // Loading state with shimmer skeleton
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        repeat(3) {
                            ShimmerInsightCard()
                        }
                    }
                } else {
                    when (selectedTab) {
                        0 -> TranscriptionSection(
                            segments = uiState.allSegments,
                            isRecording = uiState.isRecording,
                            isPaused = uiState.isPaused,
                            pendingTranslationIds = uiState.pendingTranslationIds,
                            modifier = Modifier.fillMaxSize()
                        )
                        1 -> {
                            InsightsSection(
                                insights = uiState.sortedInsights,
                                segments = uiState.completedSegments,
                                llmStatus = uiState.llmStatus,
                                isLlmEnabled = uiState.settings.llmEnabled,
                                isLlmAvailable = uiState.isLlmModelAvailable,
                                isFinalizingSession = uiState.isFinalizingSession,
                                isSessionActive = uiState.isSessionActive,
                                insightStrategy = uiState.insightStrategy,
                                batchProgress = uiState.batchProgress,
                                onDownloadLlm = {
                                    onNavigateToLlmDownload()
                                },
                                onDeleteInsight = { viewModel.deleteInsight(it) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            // Translation backlog indicator while the LLM catches up with STT.
            if (uiState.isTranslating && !uiState.isFinalizingSession) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = ModeEmeraldTint.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = ModeEmeraldTint
                        )
                        Text(
                            text = stringResource(R.string.translation_pending),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Processing banner: shown after stop while the final LLM insight is being generated.
            // Placed below the content so it does not displace the transcription/insights view.
            if (uiState.isFinalizingSession) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = stringResource(R.string.summary_finalizing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }

    // Full-screen face-to-face conversation view. Rendered as a dialog so it
    // covers the top/bottom bars and can hide the system bars while open.
    if (showFaceToFace) {
        Dialog(
            onDismissRequest = { showFaceToFace = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            FaceToFaceScreen(
                uiState = uiState,
                canRecord = !uiState.isInitializing && !uiState.isMicPermissionDenied,
                onExit = { showFaceToFace = false },
                onStop = {
                    showFaceToFace = false
                    viewModel.stopStreaming()
                },
                onOpenPhrasebook = {
                    showFaceToFace = false
                    onNavigateToPhrasebook()
                },
                onPushToTalkStart = viewModel::startPushToTalk,
                onPushToTalkStop = viewModel::stopPushToTalk,
                onAutoListenStart = viewModel::startStreaming,
                onAutoListenStop = viewModel::pauseStreaming
            )
        }
    }
}

