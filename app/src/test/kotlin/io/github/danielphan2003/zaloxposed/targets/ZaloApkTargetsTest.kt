package io.github.danielphan2003.zaloxposed.targets

import io.github.danielphan2003.zaloxposed.dex.ApkDex
import io.github.danielphan2003.zaloxposed.dex.DexScanner
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Resolves every target against a real Zalo APK. Zalo is not redistributable, so this only runs
 * when `ZALO_APK` points at a local copy; CI skips it.
 */
class ZaloApkTargetsTest {

    @Test
    fun resolvesEveryTarget() {
        val path = System.getenv("ZALO_APK")
        assumeTrue("ZALO_APK not set", !path.isNullOrBlank())
        val started = System.nanoTime()
        val dexes = ApkDex.read(File(path!!))
        val read = System.nanoTime()
        val result = ZaloTargets.resolve(DexScanner(dexes))
        val done = System.nanoTime()

        println("read ${dexes.size} dex in ${(read - started) / 1_000_000} ms, resolved in ${(done - read) / 1_000_000} ms")
        result.targets.entries.forEach { (k, v) -> println("TARGET $k = $v") }
        result.problems.forEach { println("PROBLEM $it") }
        assertEquals(emptyList<String>(), result.problems)
    }
}
