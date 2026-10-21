package com.hearopilot.app.ui.update

internal object AppVersion {

    fun normalize(raw: String): String =
        raw.trim().removePrefix("v").removePrefix("V")

    fun isNewer(candidate: String, current: String): Boolean {
        val candidateParts = parts(candidate)
        val currentParts = parts(current)
        if (candidateParts.isEmpty() || currentParts.isEmpty()) return false
        for (index in 0 until maxOf(candidateParts.size, currentParts.size)) {
            val candidatePart = candidateParts.getOrElse(index) { 0 }
            val currentPart = currentParts.getOrElse(index) { 0 }
            if (candidatePart != currentPart) return candidatePart > currentPart
        }
        return false
    }

    private fun parts(raw: String): List<Int> {
        val core = normalize(raw).substringBefore('-').substringBefore('+')
        if (core.isBlank()) return emptyList()
        return core.split('.').mapNotNull { segment ->
            segment.takeWhile(Char::isDigit).toIntOrNull()
        }
    }
}
