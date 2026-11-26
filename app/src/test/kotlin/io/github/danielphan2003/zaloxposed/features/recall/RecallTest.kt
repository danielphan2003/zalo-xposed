package io.github.danielphan2003.zaloxposed.features.recall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.util.Locale

class RecallTest {

    private fun tempFile() = Files.createTempDirectory("recall").toFile().resolve("sub/recalled.tsv")

    @Test
    fun storeSurvivesReload() {
        val file = tempFile()
        RecallStore(file).apply {
            put("MessageId(1_2_3_4)", "hello\tworld\nline two \\ done", time = 10)
            put("MessageId(5_6_7_8)", "", time = 11)
        }
        val reloaded = RecallStore(file)
        assertEquals("hello\tworld\nline two \\ done", reloaded.get("MessageId(1_2_3_4)"))
        assertEquals("", reloaded.get("MessageId(5_6_7_8)"))
        assertNull(reloaded.get("other"))
        assertFalse(reloaded.isEmpty())
    }

    @Test
    fun laterEntryWins() {
        val file = tempFile()
        RecallStore(file).apply {
            put("k", "first")
            put("k", "second")
        }
        assertEquals("second", RecallStore(file).get("k"))
    }

    @Test
    fun storeDropsOldestPastLimit() {
        val file = tempFile()
        RecallStore(file, limit = 2).apply {
            put("a", "1"); put("b", "2"); put("c", "3")
        }
        val reloaded = RecallStore(file, limit = 2)
        assertNull(reloaded.get("a"))
        assertEquals("3", reloaded.get("c"))
    }

    @Test
    fun escapeRoundTrips() {
        val s = "a\\b\tc\nd\re\\t"
        assertEquals(s, RecallStore.unescape(RecallStore.escape(s)))
        assertFalse(RecallStore.escape(s).contains('\t'))
        assertFalse(RecallStore.escape(s).contains('\n'))
    }

    @Test
    fun markerIsLocalisedAndIdempotent() {
        val vi = Locale.forLanguageTag("vi-VN")
        val marked = RecallMarker.mark("xin chào", vi)
        assertEquals("[Đã thu hồi] xin chào", marked)
        assertEquals(marked, RecallMarker.mark(marked, Locale.ENGLISH))
        assertEquals("[Recalled] hi", RecallMarker.mark("hi", Locale.ENGLISH))
        assertTrue(RecallMarker.isMarked("[Recalled] hi"))
    }
}
