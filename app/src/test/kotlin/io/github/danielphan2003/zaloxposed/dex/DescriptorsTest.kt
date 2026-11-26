package io.github.danielphan2003.zaloxposed.dex

import org.junit.Assert.assertEquals
import org.junit.Test

class DescriptorsTest {
    @Test
    fun convertsBothWays() {
        val pairs = listOf(
            "I" to "int", "V" to "void", "Ljava/lang/String;" to "java.lang.String",
            "[I" to "int[]", "[[Lorg/json/JSONObject;" to "org.json.JSONObject[][]",
        )
        for ((d, j) in pairs) {
            assertEquals(j, Descriptors.toJava(d))
            assertEquals(d, Descriptors.fromJava(j))
        }
    }

    @Test
    fun splitsParameterLists() {
        assertEquals(
            listOf("I", "Ljava/lang/String;", "[J", "[[La/B;", "Z"),
            Descriptors.splitParams("ILjava/lang/String;[J[[La/B;Z"),
        )
    }

    @Test
    fun parsesMethodRefs() {
        val ref = MethodRef.parse("La/B;->m(ILjava/lang/String;)V")
        assertEquals(MethodRef("La/B;", "m", listOf("I", "Ljava/lang/String;"), "V"), ref)
        assertEquals("La/B;->m(ILjava/lang/String;)V", ref.toString())
    }

    @Test
    fun methodSigRoundTrips() {
        val sig = MethodSig("a.B", "m", listOf("int", "java.lang.String"), "void")
        assertEquals(sig, MethodSig.decode(sig.encode()))
        val noArgs = MethodSig("a.B", "n", emptyList(), "boolean")
        assertEquals(noArgs, MethodSig.decode(noArgs.encode()))
    }
}
