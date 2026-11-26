package io.github.danielphan2003.zaloxposed.dex

/** Conversions between dex type descriptors (`Ljava/lang/String;`, `[I`) and Java names. */
internal object Descriptors {

    private val primitives = mapOf(
        'V' to "void", 'Z' to "boolean", 'B' to "byte", 'S' to "short", 'C' to "char",
        'I' to "int", 'J' to "long", 'F' to "float", 'D' to "double",
    )
    private val primitiveDescriptors = primitives.entries.associate { (k, v) -> v to k.toString() }

    /** `Ljava/lang/String;` to `java.lang.String`, `[I` to `int[]`. */
    fun toJava(descriptor: String): String {
        var dims = 0
        while (descriptor[dims] == '[') dims++
        val base = descriptor.substring(dims)
        val name = if (base.length == 1) {
            primitives[base[0]] ?: error("bad descriptor $descriptor")
        } else {
            require(base.startsWith("L") && base.endsWith(";")) { "bad descriptor $descriptor" }
            base.substring(1, base.length - 1).replace('/', '.')
        }
        return name + "[]".repeat(dims)
    }

    /** `java.lang.String` to `Ljava/lang/String;`, `int[]` to `[I`. */
    fun fromJava(name: String): String {
        var base = name
        var dims = 0
        while (base.endsWith("[]")) {
            base = base.removeSuffix("[]")
            dims++
        }
        val d = primitiveDescriptors[base] ?: "L${base.replace('.', '/')};"
        return "[".repeat(dims) + d
    }

    /** Splits a parameter descriptor list such as `ILjava/lang/String;[J` into its types. */
    fun splitParams(params: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < params.length) {
            val start = i
            while (params[i] == '[') i++
            if (params[i] == 'L') i = params.indexOf(';', i)
            i++
            out += params.substring(start, i)
        }
        return out
    }
}
