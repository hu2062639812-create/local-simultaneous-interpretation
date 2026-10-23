package com.hearopilot.app.domain.model

/**
 * A language supported for real-time translation and UI display.
 *
 * @property code BCP-47 locale code (matches the app's values-XX resource folders)
 * @property englishName Language name in English — used verbatim in LLM prompts
 * @property nativeName Language name in its own script — shown in the UI dropdown
 * @property omnilingualAsr True when speech in this language is recognized by the
 *   Omnilingual 1600-language engine (SenseVoice only covers zh/en). Sessions with
 *   such an output language run the Omnilingual recognizer, and non-Chinese text
 *   in the session is attributed to this language.
 */
data class SupportedLanguage(
    val code: String,
    val englishName: String,
    val nativeName: String,
    val omnilingualAsr: Boolean = false
)

/**
 * Single source of truth for all languages supported by the application.
 *
 * The list is sorted alphabetically by [SupportedLanguage.nativeName] using a
 * root-locale collator so that Latin diacritics (Č, É, Ž…) sort correctly.
 * Non-Latin scripts (Cyrillic, Greek) follow Latin entries per Unicode order.
 * Add new languages here; the UI and LLM layer pick them up automatically.
 *
 * Locale codes match the app's res/values-XX folders:
 * en, zh, bg, cs, da, de, el, es, et, fi, fr, hr, hu, it, lt, lv, mt, nl, pl, pt, ro, ru, sk, sl, sv, uk
 */
object SupportedLanguages {

    val ALL: List<SupportedLanguage> = listOf(
        SupportedLanguage("zh", "Simplified Chinese", "简体中文"),
        SupportedLanguage("ar", "Arabic", "العربية", omnilingualAsr = true),
        SupportedLanguage("bg", "Bulgarian",   "Български"),
        SupportedLanguage("hr", "Croatian",    "Hrvatski"),
        SupportedLanguage("cs", "Czech",       "Čeština"),
        SupportedLanguage("da", "Danish",      "Dansk"),
        SupportedLanguage("nl", "Dutch",       "Nederlands"),
        SupportedLanguage("en", "English",     "English"),
        SupportedLanguage("et", "Estonian",    "Eesti"),
        SupportedLanguage("fi", "Finnish",     "Suomi"),
        SupportedLanguage("fr", "French",      "Français", omnilingualAsr = true),
        SupportedLanguage("de", "German",      "Deutsch"),
        SupportedLanguage("el", "Greek",       "Ελληνικά"),
        SupportedLanguage("hu", "Hungarian",   "Magyar"),
        SupportedLanguage("it", "Italian",     "Italiano"),
        SupportedLanguage("ko", "Korean",      "한국어", omnilingualAsr = true),
        SupportedLanguage("lg", "Luganda",     "Luganda", omnilingualAsr = true),
        SupportedLanguage("lv", "Latvian",     "Latviešu"),
        SupportedLanguage("lt", "Lithuanian",  "Lietuvių"),
        SupportedLanguage("mt", "Maltese",     "Malti"),
        SupportedLanguage("pl", "Polish",      "Polski"),
        SupportedLanguage("pt", "Portuguese",  "Português"),
        SupportedLanguage("ro", "Romanian",    "Română"),
        SupportedLanguage("ru", "Russian",     "Русский"),
        SupportedLanguage("sk", "Slovak",      "Slovenčina"),
        SupportedLanguage("sl", "Slovenian",   "Slovenščina"),
        SupportedLanguage("es", "Spanish",     "Español", omnilingualAsr = true),
        SupportedLanguage("sw", "Swahili (Kiswahili)", "Kiswahili", omnilingualAsr = true),
        SupportedLanguage("sv", "Swedish",     "Svenska"),
        SupportedLanguage("uk", "Ukrainian",   "Українська"),
    ).sortedWith(compareBy(java.text.Collator.getInstance(java.util.Locale.ROOT)) { it.nativeName })

    /** Look up a language by its BCP-47 code. Returns null if not found. */
    fun getByCode(code: String): SupportedLanguage? = ALL.find { it.code == code }

    /** Default translation target: English. */
    val DEFAULT: SupportedLanguage = ALL.first { it.code == "en" }

    /**
     * The session's speech-language code when the given output language requires
     * the Omnilingual ASR engine ("lg", "sw", "fr", "ar", "ko", "es"), else null. In such
     * sessions non-Chinese recognized text is attributed to this language.
     */
    fun omnilingualCodeFor(outputLanguage: String?): String? {
        val code = outputLanguage?.lowercase()?.trim()?.substringBefore('-') ?: return null
        return getByCode(code)?.takeIf { it.omnilingualAsr }?.code
    }

    /** True when the output language requires the Omnilingual ASR engine. */
    fun isOmnilingualMode(outputLanguage: String?): Boolean =
        omnilingualCodeFor(outputLanguage) != null
}
