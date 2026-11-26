package io.github.danielphan2003.zaloxposed.dex

import com.android.tools.smali.smali.Smali
import com.android.tools.smali.smali.SmaliOptions
import java.nio.file.Files

/** Assembles smali sources into one dex for tests. */
internal object SmaliDex {
    fun assemble(vararg sources: String): Dex {
        val dir = Files.createTempDirectory("smali").toFile()
        try {
            val inputs = sources.mapIndexed { i, src -> java.io.File(dir, "C$i.smali").apply { writeText(src.trimIndent()) }.path }
            val out = java.io.File(dir, "out.dex")
            val options = SmaliOptions().apply {
                outputDexFile = out.path
                apiLevel = 28
            }
            check(Smali.assemble(options, inputs)) { "smali failed" }
            return Dex(out.readBytes())
        } finally {
            dir.deleteRecursively()
        }
    }
}
