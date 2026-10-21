package com.hearopilot.app.tts

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hearopilot.app.data.datasource.ModelDownloadManager
import com.hearopilot.app.feature.stt.tts.TtsEngine
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device verification of the offline melo TTS engine.
 *
 * Requires the vits-melo-tts-zh_en model to be downloaded in app storage.
 * speak() loads the engine lazily and streams to an AudioTrack; the test
 * asserts playback was observed for a plausible duration (engine load + a
 * short utterance). Logcat shows "TTS engine loaded: rate=..." on success.
 */
@RunWith(AndroidJUnit4::class)
class TtsEngineTest {

    @Test
    fun speakEnglish() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = TtsEngine(ModelDownloadManager(context))
        try {
            assertTrue("TTS model should be downloaded", engine.isModelReady())
            var speakingMs = 0L
            engine.speak("Hello, this is an offline voice test.")
            val deadline = System.currentTimeMillis() + 90_000
            while (System.currentTimeMillis() < deadline) {
                if (engine.isSpeaking.value) speakingMs += 200
                Thread.sleep(200)
                // Stop early once speech completed and we observed a real window.
                if (speakingMs > 0 && !engine.isSpeaking.value) break
            }
            assertTrue(
                "Expected observable synthesis/playback window, got ${speakingMs}ms",
                speakingMs >= 1_000
            )
        } finally {
            engine.stop()
        }
    }

    @Test
    fun speakChinese() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = TtsEngine(ModelDownloadManager(context))
        try {
            assertTrue("TTS model should be downloaded", engine.isModelReady())
            var speakingMs = 0L
            engine.speak("你好，这是离线语音合成测试。")
            val deadline = System.currentTimeMillis() + 90_000
            while (System.currentTimeMillis() < deadline) {
                if (engine.isSpeaking.value) speakingMs += 200
                Thread.sleep(200)
                if (speakingMs > 0 && !engine.isSpeaking.value) break
            }
            assertTrue(
                "Expected observable synthesis/playback window, got ${speakingMs}ms",
                speakingMs >= 1_000
            )
        } finally {
            engine.stop()
        }
    }
}
