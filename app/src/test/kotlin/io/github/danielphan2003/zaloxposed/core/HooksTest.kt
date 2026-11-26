package io.github.danielphan2003.zaloxposed.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HooksTest {

    @Test
    fun neutralValues() {
        assertEquals(false, Hooks.neutralValue(java.lang.Boolean.TYPE))
        assertEquals(0, Hooks.neutralValue(Integer.TYPE))
        assertNull(Hooks.neutralValue(Void.TYPE))
        assertNull(Hooks.neutralValue(Any::class.java))
        assertEquals(ArrayList<Any>(), Hooks.neutralValue(List::class.java))
        assertEquals(0, (Hooks.neutralValue(Array<String>::class.java) as Array<*>).size)
    }
}
