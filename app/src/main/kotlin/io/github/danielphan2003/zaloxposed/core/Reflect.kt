package io.github.danielphan2003.zaloxposed.core

import io.github.danielphan2003.zaloxposed.dex.MethodSig
import java.lang.reflect.Executable
import java.lang.reflect.Field

/** Looks resolved targets up in Zalo's class loader. Every lookup throws when the target is gone. */
internal class Reflect(private val classLoader: ClassLoader) {

    fun cls(name: String): Class<*> = PRIMITIVES[name] ?: if (name.endsWith("[]")) {
        java.lang.reflect.Array.newInstance(cls(name.removeSuffix("[]")), 0).javaClass
    } else {
        Class.forName(name, false, classLoader)
    }

    fun clsOrNull(name: String): Class<*>? = runCatching { cls(name) }.getOrNull()

    fun method(sig: MethodSig): Executable {
        val owner = cls(sig.className)
        val params = sig.params.map(::cls).toTypedArray()
        val found: Executable = if (sig.name == "<init>") owner.getDeclaredConstructor(*params)
        else owner.getDeclaredMethod(sig.name, *params)
        if (found is java.lang.reflect.Method) {
            check(found.returnType == cls(sig.returnType)) { "$sig: return type is ${found.returnType}" }
        }
        found.isAccessible = true
        return found
    }

    fun field(owner: String, name: String, type: String): Field {
        val f = cls(owner).getDeclaredField(name)
        check(f.type == cls(type)) { "$owner.$name: type is ${f.type}" }
        f.isAccessible = true
        return f
    }

    companion object {
        private val PRIMITIVES = mapOf(
            "void" to Void.TYPE, "boolean" to java.lang.Boolean.TYPE, "byte" to java.lang.Byte.TYPE,
            "short" to java.lang.Short.TYPE, "char" to Character.TYPE, "int" to Integer.TYPE,
            "long" to java.lang.Long.TYPE, "float" to java.lang.Float.TYPE, "double" to java.lang.Double.TYPE,
        )

        /** Every instance field of [type] and its superclasses. */
        fun instanceFields(type: Class<*>): List<Field> = generateSequence(type) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .filter { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .onEach { it.isAccessible = true }
            .toList()
    }
}
