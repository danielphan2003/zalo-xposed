package io.github.danielphan2003.zaloxposed.dex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DexScannerTest {

    private val feed = """
        .class public final La/Feed;
        .super Ljava/lang/Object;

        .method public static check(Lorg/json/JSONObject;)Z
            .registers 4
            const-string v0, "pre_check"
            invoke-virtual {p0, v0}, Lorg/json/JSONObject;->optJSONArray(Ljava/lang/String;)Lorg/json/JSONArray;
            move-result-object v1
            const/4 v2, 0x0
            invoke-virtual {v1, v2}, Lorg/json/JSONArray;->get(I)Ljava/lang/Object;
            const/4 v0, 0x1
            return v0
        .end method

        .method public static hasCheck(Lorg/json/JSONObject;)Z
            .registers 3
            const-string v0, "pre_check"
            invoke-virtual {p0, v0}, Lorg/json/JSONObject;->has(Ljava/lang/String;)Z
            move-result v1
            return v1
        .end method

        .method public static tracker()V
            .registers 2
            const-string v0, "ZINSTANT_AD_CHECK_TRACK_ACTIVE_VIEW_42"
            const-string v1, "pre_check"
            return-void
        .end method
    """

    private val message = """
        .class public final La/Msg;
        .super La/Base;

        .field public text:Ljava/lang/String;
        .field public sender:Ljava/lang/String;

        .method public constructor <init>(ILjava/lang/String;)V
            .registers 3
            invoke-direct {p0}, La/Base;-><init>()V
            return-void
        .end method

        .method public final text()Ljava/lang/String;
            .registers 2
            iget-object v0, p0, La/Msg;->text:Ljava/lang/String;
            return-object v0
        .end method

        .method public final mine()Z
            .registers 3
            iget-object v0, p0, La/Msg;->sender:Ljava/lang/String;
            sget-object v1, La/Core;->uid:Ljava/lang/String;
            invoke-virtual {v0, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
            move-result v0
            return v0
        .end method
    """

    private val caller = """
        .class public final La/Caller;
        .super Ljava/lang/Object;

        .method public static use(La/Msg;)V
            .registers 4
            const-string v2, "getMessage(...)"
            invoke-virtual {p0}, La/Msg;->text()Ljava/lang/String;
            move-result-object v0
            invoke-static {v0, v2}, La/Checks;->notNull(Ljava/lang/Object;Ljava/lang/String;)V
            invoke-virtual {p0}, La/Msg;->text()Ljava/lang/String;
            move-result-object v1
            invoke-static {v1, v2}, La/Checks;->notNull(Ljava/lang/Object;Ljava/lang/String;)V
            invoke-virtual {p0}, La/Msg;->mine()Z
            return-void
        .end method

        .method public static payload(Ljava/lang/Object;)V
            .registers 2
            invoke-virtual {p0}, Ljava/lang/Object;->hashCode()I
            return-void
        .end method
    """

    private val base = """
        .class public La/Base;
        .super Ljava/lang/Object;

        .method public constructor <init>()V
            .registers 1
            invoke-direct {p0}, Ljava/lang/Object;-><init>()V
            return-void
        .end method
    """

    private fun scanner() = DexScanner(listOf(SmaliDex.assemble(feed, message, caller, base)))

    @Test
    fun findsStringsAndCallsTogether() {
        val result = scanner().scan(
            listOf(
                MethodQuery(
                    "check", returnType = "boolean", params = listOf("org.json.JSONObject"), isStatic = true,
                    strings = listOf("pre_check"), invokes = listOf("Lorg/json/JSONArray;->get(I)Ljava/lang/Object;"),
                ),
            ),
        )
        assertEquals(listOf("La/Feed;->check(Lorg/json/JSONObject;)Z"), result.hits.getValue("check").map { it.toString() })
    }

    @Test
    fun stringOnlyQueryMatchesEveryUser() {
        val hits = scanner().scan(listOf(MethodQuery("users", strings = listOf("pre_check")))).hits.getValue("users")
        assertEquals(setOf("check", "hasCheck", "tracker"), hits.map { it.ref.name }.toSet())
    }

    @Test
    fun prefixMatchesConcatenatedConstants() {
        val hits = scanner().scan(listOf(MethodQuery("p", stringPrefixes = listOf("ZINSTANT_AD_CHECK_TRACK_ACTIVE_VIEW_")))).hits
        assertEquals(listOf("tracker"), hits.getValue("p").map { it.ref.name })
    }

    @Test
    fun missingStringMatchesNothing() {
        val hits = scanner().scan(listOf(MethodQuery("none", strings = listOf("not in this dex")))).hits
        assertTrue(hits.getValue("none").isEmpty())
    }

    @Test
    fun shapeFilterAndCustomFilter() {
        val hits = scanner().scan(
            listOf(MethodQuery("s", returnType = "void", paramCount = 1, isStatic = true, filter = { it.className == "a.Caller" })),
        ).hits.getValue("s")
        assertEquals(setOf("use", "payload"), hits.map { it.ref.name }.toSet())
    }

    @Test
    fun recoversGetterFromKotlinNullCheck() {
        val counts = scanner().scan(emptyList(), listOf(CallBeforeStringQuery("g", "getMessage(...)"))).callsBeforeString.getValue("g")
        assertEquals(mapOf(MethodRef("La/Msg;", "text", emptyList(), "Ljava/lang/String;") to 2), counts)
    }

    @Test
    fun methodsOfReportsFieldsAndCalls() {
        val methods = scanner().methodsOf("a.Msg").associateBy { it.ref.name }
        val text = methods.getValue("text")
        assertEquals(listOf(FieldAccess(FieldRef("La/Msg;", "text", "Ljava/lang/String;"), static = false, write = false)), text.fields)
        val mine = methods.getValue("mine")
        assertEquals(listOf("Ljava/lang/String;->equals(Ljava/lang/Object;)Z"), mine.invokes.map { it.toString() })
        assertTrue(mine.fields.any { it.static && it.ref.className == "La/Core;" })
        assertEquals(listOf("int", "java.lang.String"), methods.getValue("<init>").params)
    }

    @Test
    fun superclassAndPresence() {
        val s = scanner()
        assertEquals("a.Base", s.superclassOf("a.Msg"))
        assertEquals("java.lang.Object", s.superclassOf("a.Base"))
        assertNull(s.superclassOf("a.Missing"))
        assertTrue(s.hasClass("a.Feed"))
    }

    @Test
    fun invokesAnyOfAcceptsOneOfSeveral() {
        val hits = scanner().scan(
            listOf(
                MethodQuery(
                    "any", invokesAnyOf = listOf(
                        "La/Msg;->mine()Z", "La/Nope;->x()V",
                    ),
                ),
            ),
        ).hits.getValue("any")
        assertEquals(listOf("use"), hits.map { it.ref.name })
    }

    @Test
    fun stringTableLookups() {
        val dex = SmaliDex.assemble(feed)
        val idx = dex.findString("pre_check")
        assertTrue(idx >= 0)
        assertEquals("pre_check", dex.string(idx))
        assertEquals(-1, dex.findString("pre_chec"))
        assertEquals(1, dex.findStringsWithPrefix("ZINSTANT_AD").size)
    }
}
