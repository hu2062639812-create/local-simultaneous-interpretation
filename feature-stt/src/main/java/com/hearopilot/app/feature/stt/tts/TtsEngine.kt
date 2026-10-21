package com.hearopilot.app.feature.stt.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.hearopilot.app.data.datasource.ModelDownloadManager
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fully on-device speech synthesis for the interpretation subtitles.
 *
 * Loads the sherpa-onnx vits-melo-tts-zh_en model (~169 MB, downloaded on demand)
 * and plays the generated audio through an [AudioTrack]. Synthesis of a single
 * utterance runs to completion before playback starts; the blocking generate()
 * API is used because the JNI generateWithCallback path expects a javac-style
 * boxed bridge (invoke([F)Ljava/lang/Integer;) that Kotlin lambdas never have.
 *
 * Language handling: the melo zh_en model has a single voice that natively
 * speaks both Chinese and English, so no sid selection is needed. Luganda
 * text has no offline TTS model and is never passed here; callers speak the
 * Chinese translation instead.
 *
 * Concurrency: a new [speak] call interrupts any playback in progress; a
 * superseded job checks its generation token before touching shared state,
 * so a burst of completed utterances never queues up stale audio.
 */
@Singleton
class TtsEngine @Inject constructor(
    private val modelDownloadManager: ModelDownloadManager
) {

    companion object {
        private const val TAG = "TtsEngine"

        // Playback-out timeout: audio is fully generated at this point; only the
        // sink may still be draining its buffer.
        private const val DRAIN_TIMEOUT_MS = 5_000L
    }

    private val _isSpeaking = MutableStateFlow(false)
    /** True while audio is being synthesized or played. Callers use this to suppress mic echo. */
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engineMutex = Mutex()

    private var tts: OfflineTts? = null
    private var loadedModelDir: String? = null
    private var activeTrack: AudioTrack? = null
    private var playJob: Job? = null

    // Incremented by every speak()/stop(); a playback only mutates shared state
    // while its token is still current, so a superseded job can never stop the
    // track of its successor.
    private val generation = AtomicInteger(0)

    /** True when the TTS model files are present on disk (engine may still be loading). */
    fun isModelReady(): Boolean = modelDownloadManager.getTtsModelDir() != null

    /**
     * Speak [text] aloud. The melo zh_en model has a single voice that natively
     * handles both Chinese and English, so no speaker selection is needed.
     * Interrupts any playback in progress.
     */
    fun speak(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        speak(text = trimmed, sid = 0)
    }

    /** Speak [text] with an explicit melo speaker id. See [speak]. */
    fun speak(text: String, sid: Int) {
        if (text.isBlank()) return

        val myGen = generation.incrementAndGet()
        stopActiveTrack()
        _isSpeaking.value = true

        playJob = scope.launch {
            var track: AudioTrack? = null
            try {
                val engine = ensureEngine() ?: throw IllegalStateException("TTS model unavailable")

                val audio = engine.generate(text = text, sid = sid, speed = 1.0f)
                if (generation.get() != myGen || audio.samples.isEmpty()) return@launch

                track = createTrack(audio.sampleRate).also {
                    activeTrack = it
                    it.play()
                }
                val active = track

                // WRITE_BLOCKING paces playback; stop()/a superseding speak()
                // releases the track, which unblocks the write with an error.
                var offset = 0
                while (offset < audio.samples.size && generation.get() == myGen) {
                    val written = active.write(
                        audio.samples, offset, audio.samples.size - offset,
                        AudioTrack.WRITE_BLOCKING
                    )
                    if (written < 0) break
                    offset += written
                }

                if (generation.get() == myGen) {
                    drainAndRelease(active, offset)
                    activeTrack = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "TTS playback failed", e)
            } finally {
                if (generation.get() == myGen) {
                    stopActiveTrack()
                    _isSpeaking.value = false
                }
            }
        }
    }

    /** Stop any synthesis/playback in progress. */
    fun stop() {
        generation.incrementAndGet()
        stopActiveTrack()
        playJob?.cancel()
        _isSpeaking.value = false
    }

    /**
     * Loads the model once and keeps it resident. Model loading takes a few
     * seconds on a 170 MB VITS model, so the first utterance after app start
     * (or after the download completes) has a one-time delay.
     */
    private suspend fun ensureEngine(): OfflineTts? = engineMutex.withLock {
        val modelDir = modelDownloadManager.getTtsModelDir()
        if (modelDir == null) {
            Log.w(TAG, "TTS model not downloaded")
            return null
        }
        if (tts == null || loadedModelDir != modelDir) {
            try {
                val config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        vits = OfflineTtsVitsModelConfig(
                            model = "$modelDir/model.onnx",
                            lexicon = "$modelDir/lexicon.txt",
                            tokens = "$modelDir/tokens.txt"
                        ),
                        numThreads = 2,
                        debug = false,
                        provider = "cpu"
                    )
                )
                tts = OfflineTts(config = config)
                loadedModelDir = modelDir
                Log.i(TAG, "TTS engine loaded: rate=${tts!!.sampleRate()}, speakers=${tts!!.numSpeakers()}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize TTS engine", e)
                tts = null
                loadedModelDir = null
            }
        }
        tts
    }

    private fun createTrack(sampleRate: Int): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuffer, sampleRate * 4)) // ~1 s of float mono
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    /** Waits for the already-written samples to play out, then releases the track. */
    private suspend fun drainAndRelease(track: AudioTrack, framesWritten: Int) {
        val deadline = System.currentTimeMillis() + DRAIN_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline &&
            track.playbackHeadPosition < framesWritten
        ) {
            delay(50)
        }
        try {
            track.stop()
        } catch (_: IllegalStateException) {
        }
        track.release()
    }

    private fun stopActiveTrack() {
        activeTrack?.let { track ->
            activeTrack = null
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.release() }
        }
    }
}
