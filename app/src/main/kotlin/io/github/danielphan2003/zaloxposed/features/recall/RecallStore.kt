package io.github.danielphan2003.zaloxposed.features.recall

import java.io.File

/**
 * Remembers, on this phone only, which messages their senders recalled and what they said. One
 * line per message: key, time, text, tab-separated with tabs, newlines and backslashes escaped.
 * The oldest entries are dropped past [limit].
 */
internal class RecallStore(private val file: File, private val limit: Int = 20_000) {

    private class Entry(val time: Long, val text: String)

    private var entries: LinkedHashMap<String, Entry>? = null

    @Synchronized
    fun isEmpty(): Boolean = load().isEmpty()

    @Synchronized
    fun get(key: String): String? = load()[key]?.text

    @Synchronized
    fun put(key: String, text: String, time: Long = System.currentTimeMillis()) {
        val map = load()
        if (map[key]?.text == text) return
        map[key] = Entry(time, text)
        if (map.size > limit) {
            val drop = map.size - limit
            map.keys.take(drop).forEach(map::remove)
            rewrite(map)
        } else {
            append(key, map.getValue(key))
        }
    }

    private fun load(): LinkedHashMap<String, Entry> = entries ?: LinkedHashMap<String, Entry>().also { map ->
        runCatching {
            if (file.isFile) file.forEachLine { line ->
                val parts = line.split('\t')
                if (parts.size == 3) {
                    val key = unescape(parts[0])
                    map.remove(key)
                    map[key] = Entry(parts[1].toLongOrNull() ?: 0L, unescape(parts[2]))
                }
            }
        }
        entries = map
    }

    private fun line(key: String, e: Entry) = "${escape(key)}\t${e.time}\t${escape(e.text)}\n"

    private fun append(key: String, e: Entry) {
        runCatching {
            file.parentFile?.mkdirs()
            file.appendText(line(key, e))
        }
    }

    private fun rewrite(map: Map<String, Entry>) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(map.entries.joinToString("") { (k, e) -> line(k, e) })
            if (!tmp.renameTo(file)) tmp.delete()
        }
    }

    companion object {
        fun escape(s: String): String = buildString(s.length) {
            for (c in s) when (c) {
                '\\' -> append("\\\\")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(c)
            }
        }

        fun unescape(s: String): String = buildString(s.length) {
            var i = 0
            while (i < s.length) {
                val c = s[i++]
                if (c != '\\' || i == s.length) {
                    append(c)
                    continue
                }
                when (val n = s[i++]) {
                    't' -> append('\t')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    else -> append(n)
                }
            }
        }
    }
}
