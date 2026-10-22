package com.hearopilot.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captures uncaught exceptions to files (files/crash-logs/) so users can send
 * the stack trace with a feedback report. After writing, the exception is
 * handed to the previous handler (normally the system default, which kills
 * the process).
 */
object CrashLogRecorder {

    private const val DIR_NAME = "crash-logs"
    private const val MAX_LOGS = 10

    private var installed = false

    fun init(context: Context) {
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val appContext = context.applicationContext
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashLog(appContext, thread, throwable)
            } catch (_: Throwable) {
                // Never let the recorder itself break the crash path.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Returns the contents of the most recent crash log, or null if none exist. */
    fun latestCrashLog(context: Context): String? {
        val newest = crashDir(context)
            .listFiles { file -> file.isFile && file.name.endsWith(".txt") }
            ?.maxByOrNull { it.lastModified() }
            ?: return null
        return runCatching { newest.readText() }.getOrNull()
    }

    private fun writeCrashLog(context: Context, thread: Thread, throwable: Throwable) {
        val dir = crashDir(context)
        dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        File(dir, "crash-$stamp.txt").writeText(buildReport(context, thread, throwable))
        trimOldLogs(dir)
    }

    private fun buildReport(context: Context, thread: Thread, throwable: Throwable): String {
        val version = try {
            @Suppress("DEPRECATION")
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (versionCode ${info.longVersionCode})"
        } catch (e: PackageManager.NameNotFoundException) {
            "unknown"
        }
        val stack = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        return buildString {
            appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("Version: $version")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(stack)
        }
    }

    private fun crashDir(context: Context): File = File(context.filesDir, DIR_NAME)

    private fun trimOldLogs(dir: File) {
        val logs = dir.listFiles { file -> file.isFile && file.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        logs.drop(MAX_LOGS).forEach { it.delete() }
    }
}
