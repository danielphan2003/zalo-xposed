package io.github.danielphan2003.zaloxposed.features.telemetry

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Room's insert adapter, as R8 leaves it: one base class shared by every database in the app,
 * with insert methods that take an entity (or a batch) and return nothing, a row id or row ids.
 * Update and delete methods return an affected-row count (int) and are left alone.
 */
internal object RoomInserts {

    private val ENTITY_PARAMS = setOf(Any::class.java, Iterable::class.java, Collection::class.java, List::class.java)
    private val INSERT_RETURNS = setOf(
        Void.TYPE, java.lang.Long.TYPE, LongArray::class.java, Array<Long>::class.java, List::class.java,
    )

    /** The insert methods [adapterBase] declares. */
    fun insertMethods(adapterBase: Class<*>): List<Method> = adapterBase.declaredMethods.filter { m ->
        !Modifier.isStatic(m.modifiers) && !Modifier.isAbstract(m.modifiers) && !m.isSynthetic &&
            m.parameterCount == 1 && (m.parameterTypes[0] in ENTITY_PARAMS || m.parameterTypes[0] == Array<Any>::class.java) &&
            m.returnType in INSERT_RETURNS
    }

    /** The field, on [adapterBase] or a superclass, that holds the owning database. */
    fun databaseField(adapterBase: Class<*>, roomDatabase: Class<*>): Field? =
        generateSequence(adapterBase) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .firstOrNull { !Modifier.isStatic(it.modifiers) && it.type.isAssignableFrom(roomDatabase) && it.type != Any::class.java }
            ?.also { it.isAccessible = true }

    /** What a skipped insert returns: nothing, row id -1, or no row ids. */
    fun skippedResult(method: Method): Any? = when (method.returnType) {
        Void.TYPE -> null
        java.lang.Long.TYPE -> -1L
        LongArray::class.java -> LongArray(0)
        Array<Long>::class.java -> emptyArray<Long>()
        else -> ArrayList<Long>()
    }
}
