package io.github.danielphan2003.zaloxposed.dex

/**
 * A minimal, read-only view of one dex file, enough to find methods by the strings, calls and
 * field accesses in their code. It never loads or runs anything: it only reads tables.
 *
 * Format reference: https://source.android.com/docs/core/runtime/dex-format
 */
internal class Dex(private val b: ByteArray) {

    init {
        require(b.size >= HEADER_SIZE && b[0] == 'd'.code.toByte() && b[1] == 'e'.code.toByte() &&
            b[2] == 'x'.code.toByte() && b[3] == '\n'.code.toByte()) { "not a dex file" }
    }

    val stringCount = i4(0x38)
    private val stringIdsOff = i4(0x3C)
    val typeCount = i4(0x40)
    private val typeIdsOff = i4(0x44)
    private val protoIdsOff = i4(0x4C)
    private val fieldIdsOff = i4(0x54)
    val methodCount = i4(0x58)
    private val methodIdsOff = i4(0x5C)
    val classDefCount = i4(0x60)
    private val classDefsOff = i4(0x64)

    private val strings = arrayOfNulls<String>(stringCount)

    fun u1(o: Int) = b[o].toInt() and 0xff
    fun u2(o: Int) = u1(o) or (u1(o + 1) shl 8)
    fun i4(o: Int) = u2(o) or (u2(o + 2) shl 16)

    /** Reads an unsigned LEB128 at [o]; returns the value and the offset after it, packed. */
    private fun uleb(o: Int): Long {
        var result = 0
        var shift = 0
        var p = o
        while (true) {
            val byte = u1(p++)
            result = result or ((byte and 0x7f) shl shift)
            if (byte and 0x80 == 0) break
            shift += 7
        }
        return (p.toLong() shl 32) or (result.toLong() and 0xffffffffL)
    }

    fun string(index: Int): String = strings[index] ?: decodeString(index).also { strings[index] = it }

    private fun decodeString(index: Int): String {
        val packed = uleb(i4(stringIdsOff + 4 * index))
        val length = packed.toInt()
        var p = (packed ushr 32).toInt()
        val chars = CharArray(length)
        for (i in 0 until length) {
            val a = u1(p++)
            chars[i] = when {
                a < 0x80 -> a.toChar()
                a and 0xe0 == 0xc0 -> (((a and 0x1f) shl 6) or (u1(p++) and 0x3f)).toChar()
                else -> {
                    val c1 = u1(p++)
                    val c2 = u1(p++)
                    (((a and 0x0f) shl 12) or ((c1 and 0x3f) shl 6) or (c2 and 0x3f)).toChar()
                }
            }
        }
        return String(chars)
    }

    /** Index of [value] in the sorted string table, or -1. */
    fun findString(value: String): Int {
        val i = lowerBound(value)
        return if (i < stringCount && string(i) == value) i else -1
    }

    /** Indices of every string that starts with [prefix]. */
    fun findStringsWithPrefix(prefix: String): IntArray {
        val out = IntList()
        var i = lowerBound(prefix)
        while (i < stringCount && string(i).startsWith(prefix)) out.add(i++)
        return out.toArray()
    }

    private fun lowerBound(value: String): Int {
        var lo = 0
        var hi = stringCount
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (string(mid) < value) lo = mid + 1 else hi = mid
        }
        return lo
    }

    fun typeDescriptor(typeIndex: Int): String = string(i4(typeIdsOff + 4 * typeIndex))

    /** Index of the type with [descriptor], or -1. Type ids are sorted by their string index. */
    fun findType(descriptor: String): Int {
        val s = findString(descriptor)
        if (s < 0) return -1
        var lo = 0
        var hi = typeCount - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val v = i4(typeIdsOff + 4 * mid)
            when {
                v < s -> lo = mid + 1
                v > s -> hi = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    fun protoReturnType(proto: Int) = i4(protoIdsOff + 12 * proto + 4)

    fun protoParamTypes(proto: Int): IntArray {
        val off = i4(protoIdsOff + 12 * proto + 8)
        if (off == 0) return EMPTY
        val size = i4(off)
        return IntArray(size) { u2(off + 4 + 2 * it) }
    }

    fun protoParamCount(proto: Int): Int {
        val off = i4(protoIdsOff + 12 * proto + 8)
        return if (off == 0) 0 else i4(off)
    }

    fun methodClass(method: Int) = u2(methodIdsOff + 8 * method)
    fun methodProto(method: Int) = u2(methodIdsOff + 8 * method + 2)
    fun methodName(method: Int) = i4(methodIdsOff + 8 * method + 4)

    fun fieldClass(field: Int) = u2(fieldIdsOff + 8 * field)
    fun fieldType(field: Int) = u2(fieldIdsOff + 8 * field + 2)
    fun fieldName(field: Int) = i4(fieldIdsOff + 8 * field + 4)

    fun methodRef(method: Int) = MethodRef(
        typeDescriptor(methodClass(method)),
        string(methodName(method)),
        protoParamTypes(methodProto(method)).map(::typeDescriptor),
        typeDescriptor(protoReturnType(methodProto(method))),
    )

    fun fieldRef(field: Int) = FieldRef(
        typeDescriptor(fieldClass(field)),
        string(fieldName(field)),
        typeDescriptor(fieldType(field)),
    )

    /** Every method id that refers to [ref], usually zero or one. */
    fun findMethodIds(ref: MethodRef): IntArray {
        val nameIdx = findString(ref.name)
        val classIdx = findType(ref.className)
        if (nameIdx < 0 || classIdx < 0) return EMPTY
        val out = IntList()
        // Method ids are sorted by class, then name, then proto: binary search the class block.
        var lo = 0
        var hi = methodCount
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (methodClass(mid) < classIdx) lo = mid + 1 else hi = mid
        }
        var m = lo
        while (m < methodCount && methodClass(m) == classIdx) {
            if (methodName(m) == nameIdx && methodRef(m) == ref) out.add(m)
            m++
        }
        return out.toArray()
    }

    /** Calls [visit] for each class defined in this dex. */
    inline fun forEachClass(visit: (ClassDef) -> Unit) {
        for (i in 0 until classDefCount) visit(classDef(i))
    }

    fun classDef(index: Int): ClassDef {
        val o = classDefsOff + 32 * index
        return ClassDef(
            index = index,
            typeIndex = i4(o),
            accessFlags = i4(o + 4),
            superTypeIndex = i4(o + 8),
            classDataOff = i4(o + 24),
        )
    }

    /** Calls [visit] for each method with its access flags and code offset (0 when abstract). */
    inline fun forEachMethod(classDef: ClassDef, visit: (method: Int, access: Int, codeOff: Int) -> Unit) {
        if (classDef.classDataOff == 0) return
        var p = classDef.classDataOff
        var packed = ulebAt(p); val staticFields = packed.toInt(); p = (packed ushr 32).toInt()
        packed = ulebAt(p); val instanceFields = packed.toInt(); p = (packed ushr 32).toInt()
        packed = ulebAt(p); val directMethods = packed.toInt(); p = (packed ushr 32).toInt()
        packed = ulebAt(p); val virtualMethods = packed.toInt(); p = (packed ushr 32).toInt()
        repeat((staticFields + instanceFields) * 2) { p = (ulebAt(p) ushr 32).toInt() }
        for (group in 0..1) {
            val count = if (group == 0) directMethods else virtualMethods
            var method = 0
            for (k in 0 until count) {
                packed = ulebAt(p); method += packed.toInt(); p = (packed ushr 32).toInt()
                packed = ulebAt(p); val access = packed.toInt(); p = (packed ushr 32).toInt()
                packed = ulebAt(p); val codeOff = packed.toInt(); p = (packed ushr 32).toInt()
                visit(method, access, codeOff)
            }
        }
    }

    @PublishedApi
    internal fun ulebAt(o: Int) = uleb(o)

    /**
     * Walks the instructions of the code item at [codeOff], reporting the references the matcher
     * cares about to [sink].
     */
    fun scanCode(codeOff: Int, sink: CodeSink) {
        val units = i4(codeOff + 12)
        val start = codeOff + 16
        val end = start + units * 2
        var p = start
        while (p < end) {
            val op = u1(p)
            var len = OPCODE_UNITS[op].toInt()
            sink.next()
            when (op) {
                0x00 -> len = when (u2(p)) {
                    0x0100 -> u2(p + 2) * 2 + 4
                    0x0200 -> u2(p + 2) * 4 + 2
                    0x0300 -> {
                        val width = u2(p + 2)
                        val count = i4(p + 4).toLong() and 0xffffffffL
                        ((count * width + 1) / 2 + 4).toInt()
                    }
                    else -> 1
                }
                0x1a -> sink.string(u2(p + 2), u1(p + 1))
                0x1b -> sink.string(i4(p + 2), u1(p + 1))
                in 0x52..0x5f -> sink.field(u2(p + 2), static = false, write = op >= 0x59)
                in 0x60..0x6d -> sink.field(u2(p + 2), static = true, write = op >= 0x67)
                in 0x6e..0x72, 0xfa -> {
                    val count = u1(p + 1) ushr 4
                    val regs = u1(p + 4)
                    sink.invoke(
                        u2(p + 2),
                        if (count >= 1) regs and 0xf else -1,
                        if (count >= 2) regs ushr 4 else -1,
                    )
                }
                in 0x74..0x78, 0xfb -> {
                    val count = u1(p + 1)
                    val first = u2(p + 4)
                    sink.invoke(u2(p + 2), if (count >= 1) first else -1, if (count >= 2) first + 1 else -1)
                }
                0x0a, 0x0b -> sink.moveResult(-1)
                0x0c -> sink.moveResult(u1(p + 1))
                else -> sink.other()
            }
            p += len * 2
        }
    }

    data class ClassDef(
        val index: Int,
        val typeIndex: Int,
        val accessFlags: Int,
        val superTypeIndex: Int,
        val classDataOff: Int,
    )

    interface CodeSink {
        /** Called before each instruction (payload pseudo-instructions included). */
        fun next() = Unit
        /** const-string into register [register]. */
        fun string(index: Int, register: Int)
        fun field(index: Int, static: Boolean, write: Boolean)
        /** An invoke; [arg0] and [arg1] are its first two argument registers, or -1. */
        fun invoke(method: Int, arg0: Int, arg1: Int)
        /** move-result; [register] is the target of move-result-object, else -1. */
        fun moveResult(register: Int)
        fun other()
    }

    companion object {
        private const val HEADER_SIZE = 0x70
        private val EMPTY = IntArray(0)
        const val NO_INDEX = -1

        /** Instruction length in 16-bit code units, by opcode. */
        private val OPCODE_UNITS = ByteArray(256).also { t ->
            fun set(range: IntRange, units: Int) = range.forEach { t[it] = units.toByte() }
            set(0x00..0xff, 1)
            set(0x02..0x02, 2); set(0x03..0x03, 3)
            set(0x05..0x05, 2); set(0x06..0x06, 3)
            set(0x08..0x08, 2); set(0x09..0x09, 3)
            set(0x13..0x13, 2); set(0x14..0x14, 3); set(0x15..0x16, 2); set(0x17..0x17, 3)
            set(0x18..0x18, 5); set(0x19..0x19, 2)
            set(0x1a..0x1a, 2); set(0x1b..0x1b, 3); set(0x1c..0x1c, 2)
            set(0x1f..0x20, 2); set(0x22..0x23, 2); set(0x24..0x26, 3)
            set(0x29..0x29, 2); set(0x2a..0x2c, 3)
            set(0x2d..0x3d, 2)
            set(0x44..0x6d, 2)
            set(0x6e..0x72, 3); set(0x74..0x78, 3)
            set(0x90..0xaf, 2)
            set(0xd0..0xe2, 2)
            set(0xfa..0xfb, 4); set(0xfc..0xfd, 3); set(0xfe..0xff, 2)
        }
    }
}

/** A method reference in dex descriptor form, e.g. `Lorg/json/JSONArray;`, `get`, `[I]`, `Ljava/lang/Object;`. */
internal data class MethodRef(
    val className: String,
    val name: String,
    val params: List<String>,
    val returnType: String,
) {
    override fun toString() = "$className->$name(${params.joinToString("")})$returnType"

    companion object {
        /** Parses `Lpkg/Cls;->name(II)V`. */
        fun parse(text: String): MethodRef {
            val arrow = text.indexOf("->")
            val open = text.indexOf('(', arrow)
            val close = text.indexOf(')', open)
            return MethodRef(
                text.substring(0, arrow),
                text.substring(arrow + 2, open),
                Descriptors.splitParams(text.substring(open + 1, close)),
                text.substring(close + 1),
            )
        }
    }
}

internal data class FieldRef(val className: String, val name: String, val type: String) {
    override fun toString() = "$className->$name:$type"
}

internal class IntList(capacity: Int = 8) {
    private var data = IntArray(capacity)
    var size = 0
        private set

    fun add(v: Int) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = v
    }

    operator fun get(i: Int) = data[i]
    fun clear() { size = 0 }
    fun contains(v: Int): Boolean {
        for (i in 0 until size) if (data[i] == v) return true
        return false
    }
    fun toArray(): IntArray = data.copyOf(size)
}
