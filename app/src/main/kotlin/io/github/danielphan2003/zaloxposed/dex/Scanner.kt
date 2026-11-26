package io.github.danielphan2003.zaloxposed.dex

/**
 * Describes a method by things that survive obfuscation: the strings and calls in its code, its
 * shape, and, when a library keeps them, its names. Every constraint given must hold.
 *
 * Types are Java names (`java.lang.String`, `int`, `org.json.JSONObject`). Calls are dex method
 * references (`Lorg/json/JSONArray;->get(I)Ljava/lang/Object;`): the method must call every entry
 * of [invokes] and at least one entry of [invokesAnyOf] when that list is not empty.
 */
internal data class MethodQuery(
    val id: String,
    val className: String? = null,
    val name: String? = null,
    val returnType: String? = null,
    val params: List<String>? = null,
    val paramCount: Int? = null,
    val isStatic: Boolean? = null,
    val strings: List<String> = emptyList(),
    val stringPrefixes: List<String> = emptyList(),
    val invokes: List<String> = emptyList(),
    val invokesAnyOf: List<String> = emptyList(),
    val filter: ((MethodHit) -> Boolean)? = null,
) {
    val needsCode
        get() = strings.isNotEmpty() || stringPrefixes.isNotEmpty() || invokes.isNotEmpty() ||
            invokesAnyOf.isNotEmpty()
}

/**
 * Counts which methods are called right before a given string constant is loaded. Kotlin's null
 * checks pass a getter's source name (`"getMessage(...)"`) right after calling it, which recovers
 * the name R8 removed.
 */
internal data class CallBeforeStringQuery(val id: String, val string: String)

internal data class FieldAccess(val ref: FieldRef, val static: Boolean, val write: Boolean)

internal class MethodHit(
    val ref: MethodRef,
    val isStatic: Boolean,
    val isAbstract: Boolean,
    val strings: List<String>,
    val invokes: List<MethodRef>,
    val fields: List<FieldAccess>,
) {
    val sig get() = MethodSig.of(ref)
    val className get() = Descriptors.toJava(ref.className)
    val returnType get() = Descriptors.toJava(ref.returnType)
    val params get() = ref.params.map(Descriptors::toJava)
    override fun toString() = ref.toString()
}

internal class ScanResult(
    val hits: Map<String, List<MethodHit>>,
    val callsBeforeString: Map<String, Map<MethodRef, Int>>,
)

/** Runs [MethodQuery]s over a set of dex files in one pass over their code. */
internal class DexScanner(private val dexes: List<Dex>) {

    private val classIndex: Map<String, Pair<Dex, Dex.ClassDef>> by lazy {
        val map = HashMap<String, Pair<Dex, Dex.ClassDef>>()
        for (dex in dexes) dex.forEachClass { map.putIfAbsent(dex.typeDescriptor(it.typeIndex), dex to it) }
        map
    }

    fun scan(queries: List<MethodQuery>, callsBefore: List<CallBeforeStringQuery> = emptyList()): ScanResult {
        val hits = LinkedHashMap<String, MutableList<MethodHit>>()
        queries.forEach { hits[it.id] = ArrayList() }
        val counts = callsBefore.associate { it.id to HashMap<MethodRef, Int>() }

        for (dex in dexes) {
            val prepared = queries.mapNotNull { prepare(dex, it) }
            val watch = HashMap<Int, String>()
            for (q in callsBefore) dex.findString(q.string).let { if (it >= 0) watch[it] = q.id }
            if (prepared.isEmpty() && watch.isEmpty()) continue
            val facts = Facts(watch)
            dex.forEachClass { cls ->
                dex.forEachMethod(cls) { method, access, codeOff ->
                    var scanned = false
                    if (watch.isNotEmpty() && codeOff != 0) {
                        facts.reset()
                        dex.scanCode(codeOff, facts)
                        scanned = true
                    }
                    for (p in prepared) {
                        if (!p.matchesShape(dex, cls, method, access)) continue
                        if (!scanned) {
                            facts.reset()
                            if (codeOff != 0) dex.scanCode(codeOff, facts)
                            scanned = true
                        }
                        if (p.query.needsCode && (codeOff == 0 || !p.matchesCode(facts))) continue
                        val hit = decode(dex, method, access, facts)
                        if (p.query.filter?.invoke(hit) != false) hits.getValue(p.query.id) += hit
                    }
                    if (facts.callBefore.isNotEmpty()) {
                        for ((key, n) in facts.callBefore) {
                            val id = watch.getValue((key ushr 32).toInt())
                            counts.getValue(id).merge(dex.methodRef(key.toInt()), n, Int::plus)
                        }
                        facts.callBefore.clear()
                    }
                }
            }
        }
        return ScanResult(hits, counts)
    }

    /** Every method declared by [className] (a Java name), with its code facts; empty if absent. */
    fun methodsOf(className: String): List<MethodHit> {
        val (dex, cls) = classIndex[Descriptors.fromJava(className)] ?: return emptyList()
        val out = ArrayList<MethodHit>()
        val facts = Facts(emptyMap())
        dex.forEachMethod(cls) { method, access, codeOff ->
            facts.reset()
            if (codeOff != 0) dex.scanCode(codeOff, facts)
            out += decode(dex, method, access, facts)
        }
        return out
    }

    fun hasClass(className: String) = classIndex.containsKey(Descriptors.fromJava(className))

    /** The superclass of [className] as a Java name, or null when unknown here. */
    fun superclassOf(className: String): String? {
        val (dex, cls) = classIndex[Descriptors.fromJava(className)] ?: return null
        if (cls.superTypeIndex == Dex.NO_INDEX) return null
        return Descriptors.toJava(dex.typeDescriptor(cls.superTypeIndex))
    }

    private fun decode(dex: Dex, method: Int, access: Int, facts: Facts) = MethodHit(
        ref = dex.methodRef(method),
        isStatic = access and ACC_STATIC != 0,
        isAbstract = access and ACC_ABSTRACT != 0,
        strings = (0 until facts.strings.size).map { dex.string(facts.strings[it]) },
        invokes = (0 until facts.invokes.size).map { dex.methodRef(facts.invokes[it]) },
        fields = (0 until facts.fields.size).map {
            val v = facts.fields[it]
            FieldAccess(dex.fieldRef(v ushr 2), static = v and 2 != 0, write = v and 1 != 0)
        },
    )

    /** A query with its strings, types and calls looked up in one dex; null if it cannot match there. */
    private fun prepare(dex: Dex, q: MethodQuery): Prepared? {
        fun type(javaName: String): Int = dex.findType(Descriptors.fromJava(javaName))
        val classType = q.className?.let { type(it).also { t -> if (t < 0) return null } } ?: -1
        val name = q.name?.let { dex.findString(it).also { s -> if (s < 0) return null } } ?: -1
        val returnType = q.returnType?.let { type(it).also { t -> if (t < 0) return null } } ?: -1
        val params = q.params?.map { p -> type(p).also { if (it < 0) return null } }?.toIntArray()
        val strings = q.strings.map { s -> dex.findString(s).also { if (it < 0) return null } }.toIntArray()
        val prefixes = q.stringPrefixes.map { p -> dex.findStringsWithPrefix(p).also { if (it.isEmpty()) return null } }
        val invokes = q.invokes.map { r ->
            dex.findMethodIds(MethodRef.parse(r)).also { if (it.isEmpty()) return null }
        }
        val anyOf = q.invokesAnyOf.flatMap { dex.findMethodIds(MethodRef.parse(it)).toList() }.toIntArray()
        if (q.invokesAnyOf.isNotEmpty() && anyOf.isEmpty()) return null
        return Prepared(q, classType, name, returnType, params, strings, prefixes, invokes, anyOf)
    }

    private class Prepared(
        val query: MethodQuery,
        val classType: Int,
        val name: Int,
        val returnType: Int,
        val params: IntArray?,
        val strings: IntArray,
        val prefixes: List<IntArray>,
        val invokes: List<IntArray>,
        val anyOf: IntArray,
    ) {
        fun matchesShape(dex: Dex, cls: Dex.ClassDef, method: Int, access: Int): Boolean {
            if (classType >= 0 && cls.typeIndex != classType) return false
            if (name >= 0 && dex.methodName(method) != name) return false
            query.isStatic?.let { if ((access and ACC_STATIC != 0) != it) return false }
            val proto = dex.methodProto(method)
            if (returnType >= 0 && dex.protoReturnType(proto) != returnType) return false
            query.paramCount?.let { if (dex.protoParamCount(proto) != it) return false }
            if (params != null && !dex.protoParamTypes(proto).contentEquals(params)) return false
            return true
        }

        fun matchesCode(f: Facts): Boolean {
            for (s in strings) if (!f.strings.contains(s)) return false
            for (set in prefixes) if (set.none { f.strings.contains(it) }) return false
            for (set in invokes) if (set.none { f.invokes.contains(it) }) return false
            if (anyOf.isNotEmpty() && anyOf.none { f.invokes.contains(it) }) return false
            return true
        }
    }

    /** What one method's code references, collected by [Dex.scanCode]. */
    private class Facts(private val watch: Map<Int, String>) : Dex.CodeSink {
        val strings = IntList()
        val invokes = IntList()
        val fields = IntList()

        /** (string index shl 32 or method index) to occurrences. */
        val callBefore = HashMap<Long, Int>()

        private var insn = 0
        private var lastInvoke = -1
        private var lastInvokeAt = -10
        private var resultReg = -1
        private var resultMethod = -1
        private var resultAt = -10

        /** Watched string held by each low register, or -1. */
        private val regString = IntArray(256) { -1 }
        private val touched = IntList()

        fun reset() {
            strings.clear(); invokes.clear(); fields.clear()
            insn = 0; lastInvoke = -1; lastInvokeAt = -10; resultReg = -1; resultMethod = -1; resultAt = -10
            for (i in 0 until touched.size) regString[touched[i]] = -1
            touched.clear()
        }

        override fun next() {
            insn++
        }

        override fun string(index: Int, register: Int) {
            strings.add(index)
            if (watch.isEmpty()) return
            if (watch.containsKey(index)) {
                regString[register] = index
                touched.add(register)
            } else if (regString[register] >= 0) {
                regString[register] = -1
            }
        }

        override fun field(index: Int, static: Boolean, write: Boolean) {
            fields.add((index shl 2) or (if (static) 2 else 0) or (if (write) 1 else 0))
        }

        override fun invoke(method: Int, arg0: Int, arg1: Int) {
            invokes.add(method)
            // invoke X; move-result-object vR; invoke check(vR, vS) where vS holds a watched string.
            if (resultAt == insn - 1 && arg0 == resultReg && arg1 in 0..255 && regString[arg1] >= 0) {
                callBefore.merge((regString[arg1].toLong() shl 32) or resultMethod.toLong(), 1, Int::plus)
            }
            lastInvoke = method
            lastInvokeAt = insn
        }

        override fun moveResult(register: Int) {
            if (register >= 0 && lastInvokeAt == insn - 1) {
                resultReg = register
                resultMethod = lastInvoke
                resultAt = insn
            }
        }

        override fun other() = Unit
    }

    companion object {
        const val ACC_STATIC = 0x8
        const val ACC_ABSTRACT = 0x400
    }
}

/** A method by Java names, used to find it again through reflection. */
data class MethodSig(val className: String, val name: String, val params: List<String>, val returnType: String) {
    fun encode() = "$className|$name|${params.joinToString(",")}|$returnType"
    override fun toString() = "$className.$name(${params.joinToString()})$returnType"

    companion object {
        internal fun of(ref: MethodRef) = MethodSig(
            Descriptors.toJava(ref.className),
            ref.name,
            ref.params.map(Descriptors::toJava),
            Descriptors.toJava(ref.returnType),
        )

        fun decode(text: String): MethodSig {
            val parts = text.split('|')
            require(parts.size == 4) { "bad method signature: $text" }
            return MethodSig(parts[0], parts[1], parts[2].split(',').filter { it.isNotEmpty() }, parts[3])
        }
    }
}
