package io.github.danielphan2003.zaloxposed.targets

import java.io.File

/**
 * Keeps resolved targets on disk so the dex scan runs once per Zalo build, not on every launch.
 * The file holds a key line naming the APKs it came from (path, size, modification time), the
 * module version and the query version; any change invalidates it.
 */
internal class TargetCache(private val file: File) {

    fun load(key: String): Targets? = runCatching {
        if (!file.isFile) return null
        val text = file.readText()
        val firstLine = text.substringBefore('\n')
        if (firstLine != "$HEADER$key") return null
        Targets.decode(text.substringAfter('\n', ""))
    }.getOrNull()

    fun save(key: String, targets: Targets) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.${System.nanoTime()}.tmp")
            tmp.writeText("$HEADER$key\n${targets.encode()}\n")
            if (!tmp.renameTo(file)) tmp.delete()
        }
    }

    companion object {
        private const val HEADER = "# zalo-xposed targets "

        fun key(apks: List<File>, moduleVersion: String): String =
            (apks.map { "${it.path}:${it.length()}:${it.lastModified()}" } +
                "module=$moduleVersion" + "spec=${ZaloTargets.SPEC_VERSION}").joinToString(";")
    }
}
