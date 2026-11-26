package io.github.danielphan2003.zaloxposed.features.recall

import java.util.Locale

/** The label put in front of a recalled message's text, in Vietnamese or English. */
internal object RecallMarker {
    private const val VI = "[Đã thu hồi] "
    private const val EN = "[Recalled] "

    fun prefix(locale: Locale): String = if (locale.language == "vi") VI else EN

    fun isMarked(text: String) = text.startsWith(VI) || text.startsWith(EN)

    fun mark(text: String, locale: Locale): String = if (isMarked(text)) text else prefix(locale) + text
}
