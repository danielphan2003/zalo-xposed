package io.github.danielphan2003.zaloxposed.targets

import io.github.danielphan2003.zaloxposed.dex.MethodSig

/** Resolved hook targets by key, in a plain text form that can be cached on disk. */
internal class Targets(val entries: Map<String, String>) {

    fun method(key: String): MethodSig? = entries[key]?.takeIf { it.startsWith(METHOD) }
        ?.let { MethodSig.decode(it.removePrefix(METHOD)) }

    fun cls(key: String): String? = entries[key]?.takeIf { it.startsWith(CLASS) }?.removePrefix(CLASS)

    fun classes(key: String): List<String> = entries[key]?.takeIf { it.startsWith(CLASSES) }
        ?.removePrefix(CLASSES)?.split(',')?.filter { it.isNotEmpty() }.orEmpty()

    /** (class, field name, field type) */
    fun field(key: String): Triple<String, String, String>? = entries[key]?.takeIf { it.startsWith(FIELD) }
        ?.removePrefix(FIELD)?.split('|')?.takeIf { it.size == 3 }?.let { Triple(it[0], it[1], it[2]) }

    fun encode(): String = entries.entries.joinToString("\n") { (k, v) -> "$k=$v" }

    override fun equals(other: Any?) = other is Targets && other.entries == entries
    override fun hashCode() = entries.hashCode()

    companion object {
        private const val METHOD = "m:"
        private const val CLASS = "c:"
        private const val CLASSES = "cs:"
        private const val FIELD = "f:"

        fun method(sig: MethodSig) = METHOD + sig.encode()
        fun cls(name: String) = CLASS + name
        fun classes(names: List<String>) = CLASSES + names.joinToString(",")
        fun field(owner: String, name: String, type: String) = "$FIELD$owner|$name|$type"

        fun decode(text: String): Targets = Targets(
            text.lineSequence().filter { '=' in it }.associate { line ->
                val i = line.indexOf('=')
                line.substring(0, i) to line.substring(i + 1)
            },
        )
    }
}
