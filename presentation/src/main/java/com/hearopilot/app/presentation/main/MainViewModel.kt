package com.hearopilot.app.presentation.main

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hearopilot.app.data.config.modelConfigForVariant
import com.hearopilot.app.data.datasource.ModelDownloadManager
import com.hearopilot.app.data.service.AndroidDownloadManager
import com.hearopilot.app.data.service.DownloadStateManager
import com.hearopilot.app.domain.model.BatchInsightProgress
import com.hearopilot.app.domain.model.DownloadState
import com.hearopilot.app.domain.model.InsightStrategy
import com.hearopilot.app.domain.model.LlmModelVariant
import com.hearopilot.app.domain.model.RecordingMode
import com.hearopilot.app.domain.model.SttEngine
import com.hearopilot.app.domain.model.SupportedLanguages
import com.hearopilot.app.domain.repository.LlmRepository
import com.hearopilot.app.domain.repository.SettingsRepository
import com.hearopilot.app.domain.repository.SttRepository
import com.hearopilot.app.domain.repository.TranscriptionRepository
import com.hearopilot.app.domain.service.LlmProcessingServiceController
import com.hearopilot.app.domain.service.RecordingServiceController
import com.hearopilot.app.domain.usecase.llm.GenerateBatchInsightUseCase
import com.hearopilot.app.domain.usecase.llm.GenerateFinalInsightUseCase
import com.hearopilot.app.domain.usecase.llm.GenerateSessionSummaryUseCase
import com.hearopilot.app.domain.usecase.llm.InitializeLlmUseCase
import com.hearopilot.app.domain.usecase.llm.TranslateSegmentUseCase
import com.hearopilot.app.domain.usecase.stt.StartSttStreamingUseCase
import com.hearopilot.app.domain.usecase.stt.StopSttStreamingUseCase
import com.hearopilot.app.domain.usecase.sync.SyncSttLlmUseCase
import com.hearopilot.app.domain.usecase.transcription.DeleteInsightUseCase
import com.hearopilot.app.domain.usecase.transcription.SaveInsightUseCase
import com.hearopilot.app.domain.usecase.transcription.SaveSegmentUseCase
import com.hearopilot.app.domain.usecase.transcription.UpdateSessionDurationUseCase
import com.hearopilot.app.feature.stt.tts.TtsEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.hearopilot.app.domain.model.TranscriptionSegment
import javax.inject.Inject

/**
 * ViewModel for the main screen.
 *
 * Coordinates STT streaming, LLM inference, UI state management, foreground service,
 * and persistent storage of transcription sessions.
 *
 * Each recording session is isolated - LLM context is reset when entering this screen
 * to ensure clean separation between different transcription sessions.
 *
 * @property syncSttLlmUseCase Use case for synchronized STT-LLM processing
 * @property generateBatchInsightUseCase Use case for end-of-session batch analysis
 * @property startSttStreamingUseCase Use case to initialize STT
 * @property stopSttStreamingUseCase Use case to stop STT
 * @property initializeLlmUseCase Use case to initialize LLM
 * @property saveSegmentUseCase Use case to persist transcription segments
 * @property saveInsightUseCase Use case to persist LLM insights
 * @property settingsRepository Repository for app settings
 * @property sttRepository Direct access to STT for transcription segments
 * @property llmRepository Direct access to LLM for context reset
 * @property transcriptionRepository Repository for accessing session details
 * @property serviceController Controller for recording foreground service
 * @property savedStateHandle Navigation arguments (contains sessionId)
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val syncSttLlmUseCase: SyncSttLlmUseCase,
    private val generateFinalInsightUseCase: GenerateFinalInsightUseCase,
    private val generateBatchInsightUseCase: GenerateBatchInsightUseCase,
    private val generateSessionSummaryUseCase: GenerateSessionSummaryUseCase,
    private val translateSegmentUseCase: TranslateSegmentUseCase,
    private val startSttStreamingUseCase: StartSttStreamingUseCase,
    private val stopSttStreamingUseCase: StopSttStreamingUseCase,
    private val initializeLlmUseCase: InitializeLlmUseCase,
    private val saveSegmentUseCase: SaveSegmentUseCase,
    private val saveInsightUseCase: SaveInsightUseCase,
    private val deleteInsightUseCase: DeleteInsightUseCase,
    private val updateSessionDurationUseCase: UpdateSessionDurationUseCase,
    private val settingsRepository: SettingsRepository,
    private val sttRepository: SttRepository,
    private val llmRepository: LlmRepository,
    private val transcriptionRepository: TranscriptionRepository,
    private val modelDownloadManager: ModelDownloadManager,
    private val androidDownloadManager: AndroidDownloadManager,
    private val downloadStateManager: DownloadStateManager,
    private val serviceController: RecordingServiceController,
    private val llmProcessingServiceController: LlmProcessingServiceController,
    private val ttsEngine: TtsEngine,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Session ID for this recording session (passed via navigation)
    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"]) {
        "Session ID is required for recording"
    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    /** Expose LLM download state directly for the LlmDownloadScreen in AI Insights tab */
    val llmDownloadState: StateFlow<DownloadState> = downloadStateManager.llmDownloadState

    private var transcriptionJob: Job? = null
    private var insightsJob: Job? = null
    private var translationJob: Job? = null
    private var translationQueue: Channel<TranscriptionSegment>? = null
    private var timerJob: Job? = null
    // Serializes pause teardown with a resume/stop so two AudioRecord pipelines
    // can never coexist when the user taps Pause and Resume in quick succession.
    private var streamTransitionJob: Job? = null
    // Cumulative duration of all completed recording segments within this session.
    // Updated on each stop so the next start can offset the timer correctly.
    private var accumulatedDurationMs: Long = 0L
    private var isInitialized = false // Track if STT and LLM are already initialized
    private var currentRecordingMode: RecordingMode = RecordingMode.SHORT_MEETING
    private var currentInputLanguage: String = "it"
    private var currentOutputLanguage: String? = null
    private var currentInsightStrategy: InsightStrategy = InsightStrategy.REAL_TIME
    private var currentTopic: String? = null

    // Shared STT stream — retained so insightsJob can be restarted mid-session
    private var activeSharedSttStream: SharedFlow<TranscriptionSegment>? = null

    // Whether useConservativeThreads() has already been called for this recording segment.
    // Reset on each startStreaming() so the threshold is re-evaluated per segment.
    // Kept for the timer-based LONG_MEETING guard (RAM check after 3 min).
    private var conservativeThreadsApplied = false

    // Set once per recording so a failing translation does not overwrite a more
    // specific error banner on every queued utterance.
    private var translationFailureReported = false

    // Accumulates complete segment text not yet covered by a periodic LLM insight.
    // Thread-safe: written by transcriptionJob coroutine, cleared by insightsJob coroutine.
    private val pendingContent = AtomicReference<String>("")

    companion object {
        private const val TAG = "MainViewModel"

        // Sessions at or beyond this duration are considered "long" on RAM-constrained devices:
        // at this point both STT and LLM running simultaneously can saturate all CPU cores and
        // trigger an ANR. Switching to conservative thread count (2 threads) reduces peak CPU
        // load at the cost of ~1.6x slower inference, which is acceptable for long sessions.
        private const val MIN_CONSERVATIVE_THREADS_DURATION_MS = 3 * 60_000L
    }

    init {
        // Load settings
        viewModelScope.launch {
            settingsRepository.getSettings().collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }

        // Load session recording mode, languages, strategy, and topic
        viewModelScope.launch {
            transcriptionRepository.getSession(sessionId)
                .first()
                ?.let { session ->
                    currentRecordingMode = session.mode
                    currentInputLanguage = session.inputLanguage
                    currentOutputLanguage = session.outputLanguage
                    currentInsightStrategy = session.insightStrategy
                    currentTopic = session.topic
                    _uiState.update {
                        it.copy(
                            insightStrategy = session.insightStrategy,
                            outputLanguage = session.outputLanguage
                        )
                    }
                    Log.i(TAG, "Session config: mode=$currentRecordingMode, input=$currentInputLanguage, output=$currentOutputLanguage, strategy=$currentInsightStrategy, topic=$currentTopic")
                }
        }

        // Load existing segments and insights from database
        viewModelScope.launch {
            transcriptionRepository.getSegmentsBySession(sessionId).collect { segments ->
                _uiState.update { it.copy(completedSegments = segments) }
                Log.d(TAG, "Loaded ${segments.size} segments from database")
            }
        }

        viewModelScope.launch {
            transcriptionRepository.getInsightsBySession(sessionId).collect { insights ->
                _uiState.update { it.copy(insights = insights) }
                Log.d(TAG, "Loaded ${insights.size} insights from database")
            }
        }
    }

    /**
     * Initialize STT and LLM for this recording session.
     *
     * LLM is only initialized if the model is already downloaded.
     * If not downloaded, the user will be prompted in the AI Insights tab.
     *
     * Idempotent: safe to call multiple times, will skip if already initialized.
     */
    fun initialize() {
        if (isInitialized) {
            Log.i(TAG, "Already initialized, skipping")
            return
        }

        Log.i(TAG, "Initializing recording session: $sessionId")

        // Observe LLM download state for user-triggered downloads
        observeLlmDownloadState()

        viewModelScope.launch {
            // Await the first DataStore emission to guarantee we have the persisted settings
            // (llmModelVariant, llmModelPath, etc.) rather than the in-memory default that
            // the init-block collector may not have applied yet.
            val settings = settingsRepository.getSettings().first()
            _uiState.update { it.copy(settings = settings) }

            // Initialize STT. Sessions whose output language is spoken with the
            // Omnilingual 1600-language engine (Luganda/Swahili/French/Arabic) use
            // that recognizer; everything else uses SenseVoice. Read the session
            // directly to avoid a race with the init-block coroutine that sets
            // currentOutputLanguage.
            val session = transcriptionRepository.getSession(sessionId).first()
            val sessionOutputLanguage = session?.outputLanguage ?: currentOutputLanguage
            val omnilingualMode = SupportedLanguages.isOmnilingualMode(sessionOutputLanguage)

            if (omnilingualMode) {
                val omnilingualModelPath = modelDownloadManager.getLugandaModelPath()
                if (omnilingualModelPath == null) {
                    Log.e(TAG, "Omnilingual model not downloaded, cannot initialize STT")
                    _uiState.update { it.copy(error = "多语言识别模型未下载。请在新建会话窗口下载。") }
                } else {
                    startSttStreamingUseCase(omnilingualModelPath, SttEngine.OMNILINGUAL)
                        .onFailure { e ->
                            Log.e(TAG, "Omnilingual STT initialization failed", e)
                            _uiState.update { it.copy(error = "STT init failed: ${e.message}") }
                        }
                        .onSuccess {
                            Log.i(TAG, "Omnilingual STT initialized successfully")
                        }
                }
            } else if (settings.sttModelPath.isNotBlank()) {
                startSttStreamingUseCase(settings.sttModelPath, SttEngine.SENSE_VOICE)
                    .onFailure { e ->
                        Log.e(TAG, "STT initialization failed", e)
                        _uiState.update { it.copy(error = "STT init failed: ${e.message}") }
                    }
                    .onSuccess {
                        Log.i(TAG, "STT initialized successfully")
                    }
            } else {
                Log.w(TAG, "STT model path not configured")
                _uiState.update { it.copy(error = "STT model path not configured") }
            }

            // Check if LLM is enabled by the user
            if (!settings.llmEnabled) {
                Log.i(TAG, "LLM disabled by user, skipping initialization")
                _uiState.update {
                    it.copy(
                        isLlmModelAvailable = false,
                        llmStatus = "Disabled"
                    )
                }
            } else {
                // Resolve the LLM model path: prefer the user-selected variant,
                // fall back to any other downloaded variant so that a variant change
                // in Settings (without downloading) does not block inference.
                // The "Download model" prompt in AI Insights is only shown when
                // NO variant is on disk (i.e. user skipped the onboarding download).
                val preferredFilename = modelConfigForVariant(settings.llmModelVariant).llmFilename
                val llmModelPath = modelDownloadManager.getLlmModelPath(preferredFilename)
                    ?: LlmModelVariant.entries
                        .filter { it != settings.llmModelVariant }
                        .firstNotNullOfOrNull { variant ->
                            modelDownloadManager.getLlmModelPath(modelConfigForVariant(variant).llmFilename)
                        }

                if (llmModelPath == null) {
                    Log.i(TAG, "No LLM model on disk, user can download from AI Insights tab")
                    _uiState.update {
                        it.copy(
                            isLlmModelAvailable = false,
                            llmStatus = "Model not downloaded"
                        )
                    }
                } else {
                    // Model already downloaded — initialize, but only load into RAM if needed now.
                    _uiState.update { it.copy(isLlmModelAvailable = true, llmStatus = "Initializing...") }
                    // Read session fields directly to avoid a race with the init-block coroutine
                    // that sets currentRecordingMode / currentInsightStrategy: initialize() can be
                    // called from the UI before those coroutines have completed.
                    val session = transcriptionRepository.getSession(sessionId).first()
                    val sessionMode = session?.mode ?: currentRecordingMode
                    val sessionStrategy = session?.insightStrategy ?: currentInsightStrategy
                    // Interpretation needs the LLM from the first utterance onward.
                    // Other modes defer loading when the model is only needed in batch.
                    val loadImmediately = sessionMode == RecordingMode.REAL_TIME_TRANSLATION ||
                        (sessionMode != RecordingMode.LONG_MEETING
                            && sessionStrategy != InsightStrategy.END_OF_SESSION)
                    // Apply conservative threads before loading if the device was previously
                    // detected as constrained (cached in DataStore across sessions).
                    // LlmRepositoryImpl also reads this at construction from DataStore, but calling
                    // it here is an explicit guard in case the async init block hasn't completed yet.
                    if (settings.memoryConstrainedDetected) {
                        Log.d(TAG, "LLM init: applying conservative threads (memoryConstrainedDetected=true from DataStore)")
                        llmRepository.useConservativeThreads()
                    }
                    initializeLlmUseCase(llmModelPath, loadImmediately)
                        .onFailure { e ->
                            Log.e(TAG, "LLM initialization failed", e)
                            _uiState.update {
                                it.copy(
                                    isLlmModelAvailable = false,
                                    llmStatus = "LLM unavailable: ${e.message}",
                                    error = "本地 AI 模型加载失败，翻译和总结暂不可用：${e.message}"
                                )
                            }
                        }
                        .onSuccess {
                            Log.i(TAG, "LLM initialized successfully")
                            _uiState.update { it.copy(llmStatus = "Ready") }
                            // Post-load check: model is in memory so RAM reading is accurate.
                            // Skipped for lazy-init modes (LONG_MEETING, END_OF_SESSION) where
                            // the first actual load happens inside SyncSttLlmUseCase /
                            // GenerateBatchInsightUseCase — they call checkAndCacheMemoryConstraint().
                            if (loadImmediately) {
                                llmRepository.checkAndCacheMemoryConstraint()
                            }
                        }
                }
            }

            // Mark as initialized (prevents re-initialization)
            isInitialized = true
            _uiState.update { it.copy(isInitializing = false) }
            Log.i(TAG, "Initialization complete")
        }
    }

    /**
     * Observe LLM download state from DownloadStateManager.
     * Used when the user triggers a download from the AI Insights tab.
     */
    private fun observeLlmDownloadState() {
        viewModelScope.launch {
            downloadStateManager.llmDownloadState.collect { state ->
                when (state) {
                    is DownloadState.Downloading -> {
                        _uiState.update {
                            it.copy(
                                isDownloadingModel = true,
                                downloadProgress = state.progress.percentage,
                                downloadSpeedMbps = state.speedMbps,
                                downloadEtaSeconds = state.etaSeconds
                            )
                        }
                    }
                    is DownloadState.Completed -> {
                        _uiState.update {
                            it.copy(
                                isDownloadingModel = false,
                                isLlmModelAvailable = true,
                                llmStatus = "Initializing..."
                            )
                        }
                        // Initialize LLM now that the model is available.
                        // For END_OF_SESSION, defer loading — model is only needed after stop.
                        //
                        // Race: DownloadState.Completed fires ~155 ms before SetupViewModel
                        // persists the new llmModelVariant/llmModelPath to DataStore. A plain
                        // .first() would still return stale settings. Instead, collect the
                        // settings flow until we see an emission whose selected variant's file
                        // is verifiably present on disk — that signals persistence is done.
                        val freshSettings = settingsRepository.getSettings().first { settings ->
                            val filename = modelConfigForVariant(settings.llmModelVariant).llmFilename
                            modelDownloadManager.getLlmModelPath(filename) != null
                        }
                        _uiState.update { it.copy(settings = freshSettings) }
                        val downloadedFilename = modelConfigForVariant(
                            freshSettings.llmModelVariant
                        ).llmFilename
                        val path = modelDownloadManager.getLlmModelPath(downloadedFilename)
                        if (path != null) {
                            // Never load the LLM into RAM while recording is active: the STT model
                            // (~670 MB) is already resident, and adding ~1 GB LLM causes OOM/freeze.
                            // loadImmediately=false still registers lastModelPath so that
                            // reloadModel() (called by batch/real-time use cases at session end)
                            // has a valid path — inference for THIS session works correctly.
                            // Paused sessions still hold the STT model, so treat them as active.
                            val loadNow = !_uiState.value.isSessionActive
                                && (currentRecordingMode == RecordingMode.REAL_TIME_TRANSLATION
                                    || currentInsightStrategy != InsightStrategy.END_OF_SESSION)
                            initializeLlmUseCase(path, loadNow)
                                .onFailure { e ->
                                    Log.e(TAG, "LLM initialization failed after download", e)
                                    _uiState.update {
                                        it.copy(
                                            isLlmModelAvailable = false,
                                            llmStatus = "LLM unavailable: ${e.message}",
                                            error = "本地 AI 模型加载失败，翻译和总结暂不可用：${e.message}"
                                        )
                                    }
                                }
                                .onSuccess {
                                    Log.i(TAG, "LLM initialized after download (loadNow=$loadNow)")
                                    _uiState.update { it.copy(llmStatus = "Ready") }
                                }
                        }
                    }
                    is DownloadState.Error -> {
                        _uiState.update {
                            it.copy(
                                isDownloadingModel = false,
                                llmStatus = "Download failed: ${state.message}"
                            )
                        }
                    }
                    is DownloadState.Idle -> { /* no-op */ }
                }
            }
        }
    }

    /**
     * Start LLM model download — triggered by user from the AI Insights tab.
     */
    fun downloadLlmModel() {
        val variant = _uiState.value.settings.llmModelVariant
        Log.i(TAG, "User requested LLM model download (variant=$variant)")
        androidDownloadManager.startLlmDownload(variant)
    }

    /**
     * Start or resume STT streaming and on-device inference.
     *
     * REFACTORED: Uses a single shared STT stream for both UI display and LLM processing.
     * This prevents the crash caused by duplicate AudioRecord instances.
     *
     * A session has three states before Stop: recording, paused and idle. Starting from
     * idle opens a fresh capture pipeline; starting while paused waits for pause teardown
     * and then reuses the loaded STT/LLM models and the existing translation queue.
     */
    fun startStreaming() {
        val current = _uiState.value
        if (current.isRecording || current.isFinalizingSession) {
            Log.w(TAG, "Cannot start: recording or finalizing already in progress")
            return
        }
        // Reset so the conservative-thread threshold is re-evaluated from the current
        // accumulated duration each time the user starts (or resumes) recording.
        conservativeThreadsApplied = false

        // Check if the required STT model is downloaded before starting
        val omnilingualMode = SupportedLanguages.isOmnilingualMode(
            _uiState.value.outputLanguage ?: currentOutputLanguage
        )
        if (omnilingualMode && !modelDownloadManager.isLugandaModelDownloaded()) {
            Log.e(TAG, "Omnilingual model not downloaded, cannot start recording")
            _uiState.update {
                it.copy(error = "多语言识别模型未下载。请在新建会话窗口下载。")
            }
            return
        }
        if (!omnilingualMode && !modelDownloadManager.isSttModelDownloaded()) {
            Log.e(TAG, "STT model not downloaded, cannot start recording")
            _uiState.update {
                it.copy(error = "Speech recognition model not downloaded. Please download it from Settings.")
            }
            return
        }

        _uiState.update {
            it.copy(
                isRecording = true,
                isPaused = false,
                error = null,
                recordingDurationMillis = accumulatedDurationMs
            )
        }

        // A resume can arrive while pauseCapture() is still draining the old AudioRecord.
        // Serialize the two so two capture pipelines never coexist.
        val pendingTransition = streamTransitionJob
        if (pendingTransition != null && pendingTransition.isActive) {
            viewModelScope.launch {
                pendingTransition.join()
                beginCaptureIfStillActive()
            }
        } else {
            beginCaptureIfStillActive()
        }
    }

    /**
     * Pause the current session without ending it.
     *
     * STT capture and the timer stop immediately; the session duration is persisted.
     * The STT model, LLM and translation queue stay alive, so resume is instant and
     * translations queued before the pause keep draining while the mic is off.
     */
    fun pauseStreaming() {
        if (!_uiState.value.isRecording) {
            Log.w(TAG, "Cannot pause: not recording")
            return
        }
        // Capture the elapsed time synchronously so an immediate resume cannot race
        // with pauseCapture() and reset the duration to a stale accumulated value.
        val pausedDuration = _uiState.value.recordingDurationMillis
        accumulatedDurationMs = pausedDuration
        _uiState.update {
            it.copy(
                isRecording = false,
                isPaused = true,
                recordingDurationMillis = pausedDuration
            )
        }
        // Push-to-talk can queue a pause while the previous pause is still draining.
        // Chain the teardown so two pause pipelines never run concurrently.
        val previousTransition = streamTransitionJob
        streamTransitionJob = viewModelScope.launch {
            previousTransition?.takeIf { it.isActive }?.join()
            pauseCapture(pausedDuration)
        }
        Log.i(TAG, "Session paused at ${pausedDuration}ms")
    }

    /**
     * Push-to-talk press: start (or resume) capture while the user holds the button.
     * Release is handled by [stopPushToTalk], which pauses and flushes the utterance.
     */
    fun startPushToTalk() {
        val current = _uiState.value
        if (current.isRecording || current.isFinalizingSession) return
        Log.i(TAG, "Push-to-talk pressed")
        startStreaming()
    }

    /**
     * Push-to-talk release: pause capture immediately. The STT pipeline's stop flush
     * emits the last utterance and the translation worker keeps draining it.
     */
    fun stopPushToTalk() {
        if (!_uiState.value.isRecording) return
        Log.i(TAG, "Push-to-talk released")
        pauseStreaming()
    }

    /**
     * Toggle the read-aloud feature from the session screen. Persisted globally;
     * disabling also interrupts any playback in progress.
     */
    fun toggleTts() {
        val enabled = !_uiState.value.settings.ttsEnabled
        viewModelScope.launch {
            settingsRepository.updateSettings(
                _uiState.value.settings.copy(ttsEnabled = enabled)
            )
        }
        if (!enabled) ttsEngine.stop()
    }

    private suspend fun pauseCapture(pausedDuration: Long) {
        timerJob?.cancel()
        timerJob = null

        updateSessionDurationUseCase(sessionId, pausedDuration)
            .onFailure { e -> Log.e(TAG, "Failed to persist paused duration: ${e.message}") }

        // Stop the mic and drain the segments still buffered in the STT pipeline.
        stopSttStreamingUseCase()
        withTimeoutOrNull(4_000L) { transcriptionJob?.join() }
        transcriptionJob?.cancel()
        transcriptionJob = null

        // Real-time insight collection is restarted on resume; the translation worker
        // is intentionally left running to finish the utterances queued before pause.
        insightsJob?.cancel()
        insightsJob = null

        activeSharedSttStream = null
        _uiState.update { it.copy(currentPartialSegment = null) }
        serviceController.stopService()
        Log.i(TAG, "STT capture paused; models and translation queue retained")
    }

    private fun beginCaptureIfStillActive() {
        if (!_uiState.value.isRecording) return

        // Timer is offset by any previously accumulated duration so that resuming
        // within the same session produces a cumulative elapsed time display.
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            val segmentStart = System.currentTimeMillis()
            while (true) {
                delay(1000) // Update every second
                val segmentElapsed = System.currentTimeMillis() - segmentStart
                val totalDurationMs = accumulatedDurationMs + segmentElapsed
                _uiState.update { it.copy(recordingDurationMillis = totalDurationMs) }

                // Conservative thread management is handled adaptively by LlmRepositoryImpl:
                // - Post-load check (checkAndCacheMemoryConstraint) detects RAM pressure accurately.
                // - Per-inference recording (recordConstrainedInference) learns the char threshold.
                // - isLargeContext() proactively applies conservative threads before each reload.
                // The timer-based check is no longer needed.
            }
        }

        // Create a SINGLE shared STT stream
        val sharedSttStream = sttRepository.startStreaming()
            .catch { e ->
                Log.e(TAG, "STT error", e)
                timerJob?.cancel()
                timerJob = null
                _uiState.update {
                    it.copy(
                        error = "Transcription error: ${e.message}",
                        isRecording = false,
                        isPaused = false
                    )
                }
            }
            .shareIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(),
                replay = 0
            )

        activeSharedSttStream = sharedSttStream

        // Register stream with service controller for service observation
        serviceController.registerTranscriptionFlow(sharedSttStream)

        // Start foreground service for screen-off recording
        serviceController.startService()

        // Interpretation translates each completed utterance sequentially. The worker
        // and its unbounded queue survive a pause, so it is created only once per session.
        if (currentRecordingMode == RecordingMode.REAL_TIME_TRANSLATION && translationJob == null) {
            startTranslationWorker()
        }

        // Collect transcription segments for UI display AND persistence
        // DEDUPLICATION: Partial segments (isComplete=false) replace the current one
        // Complete segments (isComplete=true) are added to the completed list and saved to DB
        transcriptionJob?.cancel()
        transcriptionJob = viewModelScope.launch {
            sharedSttStream.collect { segment ->
                // While TTS reads a translation aloud, the live microphone picks up
                // the playback; drop those segments to prevent an echo loop. The
                // user's own speech in this short window is dropped too — the
                // trade-off that makes loudspeaker read-aloud safe without an
                // echo-cancelling signal path.
                if (ttsEngine.isSpeaking.value) {
                    Log.d(TAG, "TTS speaking — dropping segment to avoid mic echo")
                    return@collect
                }

                Log.d(TAG, "Received segment: ${segment.text} (complete=${segment.isComplete})")

                // Add sessionId to segment
                val segmentWithSession = segment.copy(sessionId = sessionId)

                // Save complete segments to database — completed segments update
                // automatically via the database Flow collector in init block
                if (segmentWithSession.isComplete) {
                    saveSegmentUseCase(segmentWithSession)
                        .onFailure { e ->
                            Log.e(TAG, "Failed to save segment to DB: ${e.message}")
                        }
                        .onSuccess {
                            Log.d(TAG, "Segment saved to DB: ${segmentWithSession.id}")
                        }
                    // Track accumulated text for potential final insight on stop
                    pendingContent.getAndUpdate { existing ->
                        if (existing.isBlank()) segmentWithSession.text
                        else "$existing ${segmentWithSession.text}"
                    }
                    // Queue the completed utterance for on-device translation.
                    enqueueTranslation(segmentWithSession)
                    // Clear partial since this segment is now complete
                    _uiState.update { state ->
                        state.copy(currentPartialSegment = null)
                    }
                } else {
                    // Partial segment: replace the current partial (no duplication!)
                    _uiState.update { state ->
                        state.copy(currentPartialSegment = segmentWithSession)
                    }
                }
            }
        }

        // Collect LLM insights from the SAME shared stream AND persist them.
        // For END_OF_SESSION strategy, skip the real-time insights job entirely —
        // the batch pipeline will run after stop. Restarted after a pause.
        if (currentRecordingMode != RecordingMode.REAL_TIME_TRANSLATION
            && currentInsightStrategy == InsightStrategy.REAL_TIME
        ) {
            insightsJob?.cancel()
            insightsJob = viewModelScope.launch {
                syncSttLlmUseCase(
                    transcriptionFlow = sharedSttStream,
                    sessionId = sessionId,
                    mode = currentRecordingMode,
                    inputLanguage = currentInputLanguage,
                    outputLanguage = currentOutputLanguage,
                    topic = currentTopic
                )
                    .catch { e ->
                        // Handle errors without crashing
                        Log.e(TAG, "LLM error", e)
                        _uiState.update {
                            it.copy(llmStatus = "LLM error: ${e.message}")
                        }
                    }
                    .collect { insight ->
                        Log.d(TAG, "Received insight: ${insight.content}")

                        // A periodic insight was generated — clear pending buffer so stopStreaming()
                        // does not double-cover the same content in the final insight call.
                        pendingContent.set("")

                        // Save insight to database — UI updates automatically via
                        // the database Flow collector in init block
                        saveInsightUseCase(insight)
                            .onFailure { e ->
                                Log.e(TAG, "Failed to save insight to DB: ${e.message}")
                            }
                            .onSuccess {
                                Log.d(TAG, "Insight saved to DB: ${insight.id}")
                            }
                    }
            }
        } else {
            Log.i(TAG, "END_OF_SESSION strategy or interpretation mode: skipping real-time LLM job")
        }

        Log.i(TAG, "STT streaming started with service support - screen-off recording enabled")
    }

    /**
     * Starts the sequential translation worker for the current recording.
     *
     * The queue is unbounded but only receives complete VAD segments, so its size
     * is bounded by speech rate. Processing serially keeps the single llama.cpp
     * context consistent and preserves utterance order.
     */
    private fun startTranslationWorker() {
        translationQueue?.close()
        val queue = Channel<TranscriptionSegment>(Channel.UNLIMITED)
        translationQueue = queue
        translationFailureReported = false
        _uiState.update { it.copy(pendingTranslations = 0, pendingTranslationIds = emptySet()) }

        translationJob = viewModelScope.launch {
            // No-op when the model is already resident; loads it on demand when a
            // download completed after the session was initialized.
            llmRepository.reloadModel()
                .onFailure { e ->
                    Log.e(TAG, "Translation model unavailable: ${e.message}")
                    reportTranslationFailure()
                }

            try {
                for (segment in queue) {
                    val expectedTarget = translateSegmentUseCase.resolveTargetLanguage(
                        translateSegmentUseCase.resolveSourceLanguage(segment, currentOutputLanguage),
                        currentOutputLanguage
                    )
                    translateSegmentUseCase(segment, currentOutputLanguage)
                        .onSuccess { translation ->
                            if (translation != null) {
                                transcriptionRepository.updateSegmentTranslation(
                                    segmentId = segment.id,
                                    translatedText = translation.text
                                ).onFailure { e ->
                                    Log.e(TAG, "Failed to store translation: ${e.message}")
                                }
                                // Read the translation aloud when enabled. Only zh/en
                                // targets are spoken — the offline TTS model has no
                                // voice for Luganda or other languages (their Chinese
                                // translations are still spoken, since those target "zh").
                                if (_uiState.value.settings.ttsEnabled &&
                                    translation.text.isNotBlank() &&
                                    (expectedTarget?.startsWith("zh") == true ||
                                        expectedTarget?.startsWith("en") == true)
                                ) {
                                    ttsEngine.speak(translation.text)
                                }
                            } else if (expectedTarget != null) {
                                // A null translation is expected only when the utterance
                                // already is in the requested output language.
                                Log.w(TAG, "Translation returned no text for ${segment.id}")
                            }
                        }
                        .onFailure { e ->
                            // One timed-out or empty segment must not surface a session-wide
                            // error banner — the remaining queue can still succeed.
                            Log.e(TAG, "Translation failed for ${segment.id}: ${e.message}")
                        }

                    _uiState.update { state ->
                        state.copy(
                            pendingTranslations = (state.pendingTranslations - 1).coerceAtLeast(0),
                            pendingTranslationIds = state.pendingTranslationIds - segment.id
                        )
                    }
                }
                Log.i(TAG, "Translation queue drained")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Translation worker crashed", e)
                reportTranslationFailure()
            } finally {
                // The worker can end through cancellation or an unexpected error; clear
                // in-flight markers so no subtitle keeps a permanent spinner.
                _uiState.update { state ->
                    state.copy(pendingTranslations = 0, pendingTranslationIds = emptySet())
                }
            }
        }
    }

    private fun reportTranslationFailure() {
        if (translationFailureReported) return
        translationFailureReported = true
        _uiState.update { state ->
            if (state.error != null) state
            else state.copy(error = "翻译失败：本地模型没有返回有效译文。请重新开始同传再试。")
        }
    }

    /**
     * Queues a completed utterance for translation when interpretation is active
     * and the LLM is ready. No-op for other modes.
     */
    private fun enqueueTranslation(segment: TranscriptionSegment) {
        if (currentRecordingMode != RecordingMode.REAL_TIME_TRANSLATION) return
        if (!_uiState.value.isLlmModelAvailable) return
        if (segment.text.isBlank()) return
        // VAD fragments that contain only punctuation or a single Latin character
        // carry no translatable content and reliably make small models return an
        // empty completion, wasting ~20-40 s of the inference queue.
        val meaningfulChars = segment.text.count { it.isLetterOrDigit() }
        val hasHan = segment.text.any { it.code in 0x4E00..0x9FFF }
        if (meaningfulChars == 0 || (meaningfulChars < 2 && !hasHan)) return

        val queue = translationQueue ?: return
        val result = queue.trySend(segment)
        if (result.isSuccess) {
            _uiState.update { state ->
                state.copy(
                    pendingTranslations = state.pendingTranslations + 1,
                    pendingTranslationIds = state.pendingTranslationIds + segment.id
                )
            }
        } else {
            Log.w(TAG, "Translation queue closed; dropping segment ${segment.id}")
        }
    }

    /**
     * Stop STT streaming and foreground service.
     *
     * If enough transcription content has accumulated since the last periodic LLM insight,
     * generates one final insight before tearing down. This ensures no meaningful content
     * is lost at the tail of a session.
     */
    fun stopStreaming() {
        val state = _uiState.value
        if (!state.isRecording && !state.isPaused) {
            Log.w(TAG, "Not recording or paused")
            return
        }

        // Immediately reflect the stop in the UI so the button responds at once.
        // isFinalizingSession=true blocks back navigation and shows a spinner during
        // the STT drain (up to 3 s) and the optional final LLM insight generation.
        _uiState.update {
            it.copy(isRecording = false, isPaused = false, isFinalizingSession = true)
        }
        ttsEngine.stop()
        val pendingTransition = streamTransitionJob

        viewModelScope.launch {
            // If Stop was pressed while a pause was still tearing down the mic,
            // wait for that teardown before draining and releasing the STT model.
            pendingTransition?.join()

            // Cancel non-essential jobs immediately.
            timerJob?.cancel()
            timerJob = null

            // Persist total cumulative recording duration before tearing down.
            // accumulatedDurationMs is updated so that a subsequent startStreaming() call
            // within the same session continues from where the timer left off.
            accumulatedDurationMs = _uiState.value.recordingDurationMillis
            updateSessionDurationUseCase(sessionId, accumulatedDurationMs)
                .onFailure { e -> Log.e(TAG, "Failed to persist session duration: ${e.message}") }

            // Capture any partial (in-progress) segment text as a fallback for the final
            // insight. This is needed when no complete segment was emitted yet — e.g. the
            // user stops during a sentence that never hit a 0.5s silence. The flush in
            // SherpaOnnxDataSource will emit it as complete, but if the drain times out
            // we use the partial as a backstop so the content is not silently discarded.
            val partialFallback = _uiState.value.currentPartialSegment?.text?.trim() ?: ""

            // Stop STT BEFORE cancelling transcriptionJob: stopping the AudioRecord
            // signals Sherpa to flush remaining buffered audio (including any in-flight
            // speech) and emit final complete segments. transcriptionJob must stay alive
            // to receive and save those last segments.
            stopSttStreamingUseCase()

            // Wait for transcriptionJob to drain the final STT segments.
            // Root cause of the timeout: sharedSttStream is a SharedFlow (from shareIn) which
            // never emits a terminal event even after the upstream STT channelFlow completes.
            // transcriptionJob.collect{} therefore never returns on its own — the timeout IS
            // the exit mechanism, not a safety net. Keep it tight: the flush-on-stop inference
            // decodes at most MAX_INFERENCE_AUDIO_SAMPLES (30s) of audio in ~2–3 s on device.
            // 4 s gives 33–100% margin without adding 4–8 s of dead wait before LLM reload.
            withTimeoutOrNull(4_000L) { transcriptionJob?.join() }
            transcriptionJob?.cancel()
            transcriptionJob = null

            // Release the Sherpa-ONNX native model from memory (~670 MB native heap).
            // Must happen after the transcription drain so the model is no longer in use.
            // The heap is then available for LLM batch processing below.
            sttRepository.releaseModel()

            // Clear any lingering partial segment from the UI. The STT flush (SherpaOnnxDataSource)
            // already emits the remaining speech as a complete segment before closing the channel,
            // so saving currentPartialSegment here would produce a duplicate with stale content
            // (the async partial inference may race with and arrive after the flush result).
            _uiState.update { it.copy(currentPartialSegment = null) }

            // Interpretation: drain queued translations (bounded) so the summary sees
            // the bilingual transcript, then summarize below.
            val wasInterpretation = currentRecordingMode == RecordingMode.REAL_TIME_TRANSLATION
            if (wasInterpretation) {
                translationQueue?.close()
                translationQueue = null
                withTimeoutOrNull(40_000L) { translationJob?.join() }
                // Cancel a segment that overran the drain window and wait for the
                // flow's cancellation handler to return the engine to Ready before
                // the summary starts on the same single-threaded dispatcher.
                withTimeoutOrNull(5_000L) { translationJob?.cancelAndJoin() }
                translationJob = null
                _uiState.update { it.copy(pendingTranslations = 0, pendingTranslationIds = emptySet()) }
            }

            // Snapshot pending text BEFORE touching insightsJob (which may clear it).
            // If a periodic inference is actively generating, wait for it to finish so
            // its insight is saved — cancelling it here would silently discard the result.
            // Once the job completes the final insight is skipped to avoid a race on
            // singleThreadDispatcher between the periodic inference cleanup and
            // generateFinalInsightUseCase.
            val periodicInferenceActive = !wasInterpretation && llmRepository.isGenerating
            if (periodicInferenceActive) {
                // Allow the in-flight inference to complete and save its insight.
                // Poll isGenerating instead of joining insightsJob: insightsJob collects
                // from a SharedFlow that never emits a terminal event, so join() would
                // always block for the full 60 s timeout. isGenerating is set to false by
                // onCompletion in generateInsight(), i.e. exactly when the inference ends.
                // Timeout matches the worst-case LLM inference duration on device.
                withTimeoutOrNull(60_000L) {
                    while (llmRepository.isGenerating) delay(50)
                }
            }
            insightsJob?.cancel()
            insightsJob = null

            val snapshot = pendingContent.getAndSet("").trim()
            // Fall back to the captured partial text if no complete segments arrived
            // (e.g. short session ending mid-sentence before the drain could complete).
            val effectiveSnapshot = snapshot.ifBlank { partialFallback }
            activeSharedSttStream = null

            // Stop foreground service.
            serviceController.stopService()

            // Hint to ART to reclaim session garbage (STT audio buffers, flow update objects,
            // finished coroutines) before the ~1 GB LLM model load that follows. Reducing Java
            // heap pressure at this point lowers the risk of ART triggering a stop-the-world
            // MarkCompact GC on the main thread mid-allocation. Safe: STT is fully stopped and
            // released, all segment processing is complete, no audio data is in flight.
            @Suppress("ExplicitGarbageCollectionCall")
            System.gc()

            if (wasInterpretation) {
                // Interpretation: one Chinese summary for the whole session, using the
                // fresh bilingual rows after the translation drain.
                val allSegments = transcriptionRepository.getSegmentsBySession(sessionId).first()
                val willSummarize = _uiState.value.isLlmModelAvailable && allSegments.isNotEmpty()
                _uiState.update { it.copy(isFinalizingSession = willSummarize) }
                Log.i(TAG, "Streaming stopped (interpretation), willSummarize=$willSummarize, segments=${allSegments.size}")

                if (willSummarize) {
                    launch {
                        llmProcessingServiceController.startProcessing()
                        try {
                            generateSessionSummaryUseCase(
                                segments = allSegments,
                                sessionId = sessionId,
                                topic = currentTopic
                            )
                                .onFailure { e ->
                                    Log.e(TAG, "Session summary generation failed: ${e.message}")
                                    _uiState.update {
                                        it.copy(error = "总结生成失败：${e.message ?: "未知错误"}")
                                    }
                                }
                                .onSuccess { insight ->
                                    if (insight != null) {
                                        saveInsightUseCase(insight)
                                            .onFailure { e -> Log.e(TAG, "Failed to save summary: ${e.message}") }
                                            .onSuccess { Log.i(TAG, "Session summary saved: ${insight.id}") }
                                    } else {
                                        Log.d(TAG, "Session summary skipped: not enough content")
                                        _uiState.update {
                                            it.copy(error = "未能生成总结：本次会话的语音内容不足。")
                                        }
                                    }
                                }
                        } finally {
                            _uiState.update { it.copy(isFinalizingSession = false) }
                            llmProcessingServiceController.stopProcessing()
                            try {
                                llmRepository.cleanup()
                                Log.i(TAG, "LLM resources released after session summary")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error releasing LLM resources after summary", e)
                            }
                        }
                    }
                } else {
                    _uiState.update { it.copy(isFinalizingSession = false) }
                    launch {
                        try {
                            llmRepository.cleanup()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error releasing LLM resources on stop", e)
                        }
                    }
                }
            } else if (currentInsightStrategy == InsightStrategy.END_OF_SESSION) {
                // Batch pipeline: analyze entire session after recording stops.
                val allSegments = _uiState.value.completedSegments
                val willRunBatch = _uiState.value.isLlmModelAvailable && allSegments.isNotEmpty()
                _uiState.update { it.copy(isFinalizingSession = willRunBatch) }
                Log.i(TAG, "Streaming stopped (batch mode), willRunBatch=$willRunBatch, segments=${allSegments.size}")

                if (willRunBatch) {
                    launch {
                        Log.i(TAG, "Starting batch insight pipeline: ${allSegments.size} segments")
                        // Conservative threads for batch are handled inside
                        // GenerateBatchInsightUseCase via checkAndCacheMemoryConstraint()
                        // (post first-load, accurate) and isLargeContext() (per-chunk).
                        // Protect inference from being killed when app is backgrounded.
                        llmProcessingServiceController.startProcessing()
                        generateBatchInsightUseCase(
                            segments = allSegments,
                            sessionId = sessionId,
                            mode = currentRecordingMode,
                            outputLanguage = currentOutputLanguage,
                            topic = currentTopic,
                            onProgress = { progress ->
                                _uiState.update { it.copy(batchProgress = progress) }
                            },
                            onChunkInsight = { chunkInsight ->
                                saveInsightUseCase(chunkInsight)
                                    .onFailure { e -> Log.e(TAG, "Failed to save chunk insight: ${e.message}") }
                                    .onSuccess { Log.d(TAG, "Chunk insight saved: ${chunkInsight.id}") }
                            }
                        )
                            .onFailure { e ->
                                Log.e(TAG, "Batch insight generation failed: ${e.message}")
                            }
                            .onSuccess { insight ->
                                if (insight != null) {
                                    saveInsightUseCase(insight)
                                        .onFailure { e -> Log.e(TAG, "Failed to save batch insight: ${e.message}") }
                                        .onSuccess { Log.i(TAG, "Batch insight saved: ${insight.id}") }
                                } else {
                                    Log.d(TAG, "Batch insight skipped: not enough content")
                                }
                            }
                        _uiState.update { it.copy(isFinalizingSession = false, batchProgress = BatchInsightProgress.Idle) }

                        llmProcessingServiceController.stopProcessing()

                        try {
                            llmRepository.cleanup()
                            Log.i(TAG, "LLM resources released after batch insight")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error releasing LLM resources after batch insight", e)
                        }
                    }
                } else {
                    launch {
                        try {
                            llmRepository.cleanup()
                            Log.i(TAG, "LLM resources released on stop (batch, no segments)")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error releasing LLM resources on stop", e)
                        }
                    }
                }
            } else {
                // Real-time strategy: generate a final insight for any pending content.
                // Skip if a periodic inference was actively running when Stop was pressed —
                // that insight already covers the session content up to this point, and
                // the few seconds accumulated since the trigger are not worth a separate call.
                val willGenerateFinal = _uiState.value.isLlmModelAvailable
                    && effectiveSnapshot.isNotBlank()
                    && !periodicInferenceActive
                _uiState.update { it.copy(isFinalizingSession = willGenerateFinal) }
                Log.i(TAG, "Streaming stopped (real-time mode), finalizing=$willGenerateFinal, snapshotLen=${effectiveSnapshot.length}, usedPartialFallback=${snapshot.isBlank() && partialFallback.isNotBlank()}")

                if (willGenerateFinal) {
                    launch {
                        Log.i(TAG, "Generating final insight (effectiveSnapshot: ${effectiveSnapshot.length} chars)")
                        // Protect inference from being killed when app is backgrounded.
                        llmProcessingServiceController.startProcessing()
                        generateFinalInsightUseCase(
                            pendingText = effectiveSnapshot,
                            sessionId = sessionId,
                            mode = currentRecordingMode,
                            outputLanguage = currentOutputLanguage,
                            topic = currentTopic
                        )
                            .onFailure { e ->
                                Log.e(TAG, "Final insight generation failed: ${e.message}")
                            }
                            .onSuccess { insight ->
                                if (insight != null) {
                                    saveInsightUseCase(insight)
                                        .onFailure { e -> Log.e(TAG, "Failed to save final insight: ${e.message}") }
                                        .onSuccess { Log.i(TAG, "Final insight saved: ${insight.id}") }
                                } else {
                                    Log.d(TAG, "Final insight skipped: not enough content")
                                }
                            }
                        _uiState.update { it.copy(isFinalizingSession = false) }

                        llmProcessingServiceController.stopProcessing()

                        // Free the LLM model immediately after the final insight — the user
                        // is done recording and the model is no longer needed. Without this,
                        // ~3.2 GB (weights + KV-cache) would stay resident until onCleared().
                        try {
                            llmRepository.cleanup()
                            Log.i(TAG, "LLM resources released after final insight")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error releasing LLM resources after final insight", e)
                        }
                    }
                } else {
                    // No final insight to generate — free the model immediately.
                    launch {
                        try {
                            llmRepository.cleanup()
                            Log.i(TAG, "LLM resources released on stop (no pending content)")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error releasing LLM resources on stop", e)
                        }
                    }
                }
            }
        }
    }

    /**
     * Called when the user denies microphone permission.
     * Updates the UI state to show an appropriate error.
     */
    fun onMicPermissionDenied() {
        _uiState.update { it.copy(isInitializing = false, isMicPermissionDenied = true) }
        Log.e(TAG, "Microphone permission denied")
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Clear all transcriptions and insights.
     */
    fun clearHistory() {
        _uiState.update {
            it.copy(
                completedSegments = emptyList(),
                currentPartialSegment = null,
                insights = emptyList()
            )
        }
    }

    /**
     * Permanently delete one generated insight/summary from this session.
     * The Room flow refreshes the list automatically after deletion.
     */
    fun deleteInsight(insightId: String) {
        viewModelScope.launch {
            deleteInsightUseCase(insightId)
                .onFailure { e ->
                    Log.e(TAG, "Failed to delete insight $insightId", e)
                    _uiState.update { it.copy(error = "Failed to delete summary: ${e.message}") }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()

        // Interrupt any read-aloud playback immediately — the session is gone.
        ttsEngine.stop()

        // IMPORTANT: viewModelScope is already cancelled by the time onCleared() runs —
        // lifecycle-viewmodel calls clear() which closes all tagged Closeables (including
        // viewModelScope's SupervisorJob) BEFORE invoking onCleared(). Any viewModelScope.launch {}
        // here would be a no-op. Use runBlocking for all cleanup that must actually execute.

        // Stop the AudioRecord if the session is still active (e.g. user pressed back mid-session).
        // stopRecording() is fast: sets isRecording=false, stops and releases the AudioRecord.
        // A paused session has no AudioRecord, but the service/model teardown still applies.
        if (_uiState.value.isSessionActive) {
            Log.i(TAG, "ViewModel cleared while session active - stopping AudioRecord")
            runBlocking {
                try {
                    stopSttStreamingUseCase()
                    serviceController.stopService()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping STT in onCleared", e)
                }
            }
        }

        // Release the STT model native heap (~670 MB ONNX) and the LLM model.
        // Both use runBlocking because viewModelScope.launch would be a no-op here.
        runBlocking {
            try {
                sttRepository.releaseModel()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing STT model in onCleared", e)
            }
            try {
                llmRepository.cleanup()
                Log.i(TAG, "LLM resources released on session end")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing LLM resources in onCleared", e)
            }
        }

        transcriptionJob?.cancel()
        insightsJob?.cancel()
        translationJob?.cancel()
        translationQueue?.close()
        timerJob?.cancel()
    }
}
