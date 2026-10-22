package com.hearopilot.app.ui.feedback

import android.content.Context
import android.os.Build
import com.hearopilot.app.CrashLogRecorder
import java.util.Locale

/**
 * Builds the diagnostics report copied by the feedback entry in Settings:
 * app version, device info and the most recent crash log, if one was recorded.
 */
object FeedbackDiagnostics {

    fun build(context: Context): String {
        val version = runCatching {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (versionCode ${info.longVersionCode})"
        }.getOrDefault("unknown")

        val crashLog = CrashLogRecorder.latestCrashLog(context)

        return buildString {
            appendLine("Local 同传 诊断信息 / Diagnostics")
            appendLine("版本 Version: $version")
            appendLine("设备 Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("系统 Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            appendLine("界面语言 UI language: ${Locale.getDefault().toLanguageTag()}")
            appendLine()
            if (crashLog != null) {
                appendLine("最近崩溃日志 Latest crash log:")
                appendLine(crashLog)
            } else {
                appendLine("最近崩溃日志 Latest crash log: （无 none）")
            }
        }
    }
}
