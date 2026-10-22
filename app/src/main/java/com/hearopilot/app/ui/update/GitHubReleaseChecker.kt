package com.hearopilot.app.ui.update

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class AvailableRelease(
    val version: String,
    val name: String,
    val notes: String,
    val pageUrl: String,
    val downloadUrl: String?
)

internal object GitHubReleaseChecker {

    private const val API_URL =
        "https://api.github.com/repos/hu2062639812-create/local-simultaneous-interpretation/releases/latest"
    private const val TIMEOUT_MS = 10_000
    private val LIST_MARKER = Regex("^[-*+]\\s+")
    private val MARKDOWN_LINK = Regex("\\[([^]]+)]\\([^)]*\\)")

    /**
     * Returns the latest published release, or null when the repository has none yet.
     * Throws on network/HTTP failures so callers can distinguish "no release" from "offline".
     */
    fun fetchLatest(): AvailableRelease? {
        val connection = URL(API_URL).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Local-Interpreter-Android")
            when (val code = connection.responseCode) {
                200 -> connection.inputStream.bufferedReader().use { reader ->
                    parse(reader.readText())
                }
                404 -> null
                else -> throw IOException("GitHub API returned HTTP $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(json: String): AvailableRelease? {
        val root = JSONObject(json)
        if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
        val tag = root.optString("tag_name").trim()
        val pageUrl = root.optString("html_url").trim()
        if (!pageUrl.startsWith("https://")) return null
        val version = AppVersion.normalize(tag)
        if (version.isEmpty()) return null

        val apkUrl = root.optJSONArray("assets")?.let { assets ->
            (0 until assets.length())
                .mapNotNull { assets.optJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                ?.optString("browser_download_url")
                ?.takeIf { it.startsWith("https://") }
        }

        return AvailableRelease(
            version = version,
            name = root.optString("name").trim().ifEmpty { tag },
            notes = cleanNotes(root.optString("body")),
            pageUrl = pageUrl,
            downloadUrl = apkUrl
        )
    }

    private fun cleanNotes(markdown: String): String {
        val text = markdown.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("<!--") }
            .map { line ->
                line.trimStart('#').trim()
                    .replace(LIST_MARKER, "• ")
                    .replace(MARKDOWN_LINK, "$1")
                    .replace("**", "")
                    .replace("`", "")
            }
            .joinToString("\n")
        return if (text.length > 700) text.take(700).trimEnd() + "…" else text
    }
}
