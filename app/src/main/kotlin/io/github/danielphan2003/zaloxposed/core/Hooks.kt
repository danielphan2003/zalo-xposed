package io.github.danielphan2003.zaloxposed.core

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.ExceptionMode
import java.lang.reflect.Executable
import java.lang.reflect.Method

/**
 * Thin helpers over libxposed hooks. Hooks run in PROTECTIVE mode: if a hook body throws before it
 * calls through, Zalo's original method runs instead, and an exception after it keeps the
 * original result, so a broken hook cannot crash Zalo.
 */
internal class Hooks(private val xposed: XposedInterface, private val log: Log) {

    /** Installs [body] around [target]; returns false (and logs) when the framework refuses. */
    fun intercept(target: Executable, label: String, body: (Chain) -> Any?): Boolean = try {
        xposed.hook(target)
            .setExceptionMode(ExceptionMode.PROTECTIVE)
            .intercept { chain -> body(chain) }
        true
    } catch (t: Throwable) {
        log.w("$label: could not hook $target", t)
        false
    }

    /** Skips [target] entirely and returns [result] instead. */
    fun replace(target: Executable, label: String, result: Any?) = intercept(target, label) { result }

    /** Skips [target] and returns the neutral value for its return type. */
    fun skip(target: Executable, label: String) = replace(target, label, neutralResult(target))

    /** Runs [block] after [target] returns normally. */
    fun after(target: Executable, label: String, block: (Chain, Any?) -> Unit) = intercept(target, label) { chain ->
        val result = chain.proceed()
        try {
            block(chain, result)
        } catch (t: Throwable) {
            log.w("$label: after-hook failed", t)
        }
        result
    }

    companion object {
        private val LIST_TYPES = setOf(
            List::class.java, Collection::class.java, Iterable::class.java,
            ArrayList::class.java,
        )

        /** null for void and objects, false, zero, or an empty array or list. */
        fun neutralResult(target: Executable): Any? = (target as? Method)?.returnType?.let(::neutralValue)

        fun neutralValue(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            Character.TYPE -> 0.toChar()
            Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            else -> when {
                type.isArray -> java.lang.reflect.Array.newInstance(type.componentType!!, 0)
                type in LIST_TYPES -> java.util.ArrayList<Any?>()
                else -> null
            }
        }
    }
}
