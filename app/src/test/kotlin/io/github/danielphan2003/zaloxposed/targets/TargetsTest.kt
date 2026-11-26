package io.github.danielphan2003.zaloxposed.targets

import io.github.danielphan2003.zaloxposed.dex.MethodSig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class TargetsTest {

    private val sample = Targets(
        linkedMapOf(
            "m" to Targets.method(MethodSig("a.B", "c", listOf("int", "java.lang.String"), "void")),
            "c" to Targets.cls("a.View"),
            "cs" to Targets.classes(listOf("a.X", "a.Y")),
            "f" to Targets.field("a.Msg", "f", "java.lang.String"),
        ),
    )

    @Test
    fun typedAccessors() {
        assertEquals(MethodSig("a.B", "c", listOf("int", "java.lang.String"), "void"), sample.method("m"))
        assertEquals("a.View", sample.cls("c"))
        assertEquals(listOf("a.X", "a.Y"), sample.classes("cs"))
        assertEquals(Triple("a.Msg", "f", "java.lang.String"), sample.field("f"))
        assertNull(sample.method("c"))
        assertNull(sample.cls("missing"))
    }

    @Test
    fun encodeDecodeRoundTrip() {
        assertEquals(sample, Targets.decode(sample.encode()))
    }

    @Test
    fun cacheHitsOnlyForTheSameKey() {
        val file = Files.createTempDirectory("cache").toFile().resolve("x/targets.txt")
        val cache = TargetCache(file)
        assertNull(cache.load("k1"))
        cache.save("k1", sample)
        assertEquals(sample, cache.load("k1"))
        assertNull(cache.load("k2"))
    }

    @Test
    fun cacheKeyTracksApkAndVersions() {
        val dir = Files.createTempDirectory("apk").toFile()
        val apk = dir.resolve("base.apk").apply { writeText("one") }
        val k1 = TargetCache.key(listOf(apk), "0.1.0")
        assertEquals(k1, TargetCache.key(listOf(apk), "0.1.0"))
        assert(k1 != TargetCache.key(listOf(apk), "0.1.1"))
        apk.writeText("longer content")
        assert(k1 != TargetCache.key(listOf(apk), "0.1.0"))
    }
}
