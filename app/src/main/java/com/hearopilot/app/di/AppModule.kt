package com.hearopilot.app.di

import android.content.Context
import com.arm.aichat.InferenceEngine
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.hearopilot.app.ui.SimulateStreamingAsr
import com.hearopilot.app.data.datasource.ModelDownloadManager
import com.hearopilot.app.domain.repository.SettingsRepository
import com.hearopilot.app.service.RecordingNotificationManager
import com.hearopilot.app.service.RecordingSessionManager
import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

/**
 * Hilt module providing application-level dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    fun provideOfflineRecognizer(
        @ApplicationContext context: Context,
        modelDownloadManager: ModelDownloadManager
    ): OfflineRecognizer {
        // Get STT model path from downloaded files
        val sttModelPath = modelDownloadManager.getSttModelPath()
            ?: throw IllegalStateException("STT model not downloaded. Please download the model first.")

        // SenseVoice: zh/en/ja/ko/yue, language is auto-detected and reported per utterance.
        val modelConfig = OfflineModelConfig(
            senseVoice = OfflineSenseVoiceModelConfig(
                model = "$sttModelPath/model.int8.onnx",
                language = "",
                useInverseTextNormalization = true
            ),
            tokens = "$sttModelPath/tokens.txt",
            // Fixed 2 threads for STT to avoid CPU contention with the LLM engine
            // (which also runs concurrently during interpretation).
            numThreads = 2
        )
        Log.i("AppModule", "STT: SenseVoice, ${modelConfig.numThreads} threads (fixed)")

        // Create recognizer WITHOUT assetManager (loading from file system)
        return OfflineRecognizer(
            assetManager = null,
            config = OfflineRecognizerConfig(modelConfig = modelConfig)
        )
    }

    @Provides
    fun provideVad(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository
    ): Vad {
        // Read VAD parameters from settings
        val settings = runBlocking { settingsRepository.getSettings().first() }

        // Initialize using existing SimulateStreamingAsr logic with custom parameters
        SimulateStreamingAsr.initVad(
            assetManager = context.assets,
            minSilenceDuration = settings.vadMinSilenceDuration,
            maxSpeechDuration = settings.vadMaxSpeechDuration,
            threshold = settings.vadThreshold
        )
        return SimulateStreamingAsr.vad
    }

    @Provides
    @Singleton
    fun provideInferenceEngine(
        @ApplicationContext context: Context
    ): InferenceEngine {
        return com.arm.aichat.AiChat.getInferenceEngine(context)
    }

    @Provides
    @Singleton
    fun provideRecordingSessionManager(): RecordingSessionManager {
        return RecordingSessionManager()
    }

    @Provides
    @Singleton
    fun provideRecordingNotificationManager(
        @ApplicationContext context: Context
    ): RecordingNotificationManager {
        return RecordingNotificationManager(context)
    }
}
