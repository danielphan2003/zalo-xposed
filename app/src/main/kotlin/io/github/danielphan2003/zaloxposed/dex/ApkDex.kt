package io.github.danielphan2003.zaloxposed.dex

import java.io.File
import java.util.zip.ZipFile

/** Reads `classes.dex`, `classes2.dex`, ... out of an APK, in the order the runtime loads them. */
internal object ApkDex {

    fun read(apk: File): List<Dex> = ZipFile(apk).use { zip ->
        generateSequence(1) { it + 1 }
            .map { zip.getEntry(if (it == 1) "classes.dex" else "classes$it.dex") }
            .takeWhile { it != null }
            .map { entry -> Dex(zip.getInputStream(entry).use { it.readBytes() }) }
            .toList()
    }

    /** True when [apk] is a readable zip with at least one dex. */
    fun hasDex(apk: File): Boolean = runCatching { ZipFile(apk).use { it.getEntry("classes.dex") != null } }
        .getOrDefault(false)
}
