package io.github.danielphan2003.zaloxposed.features.telemetry

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Stand-ins shaped like R8's merged Room classes. */
@Suppress("unused", "UNUSED_PARAMETER")
abstract class FakeRoomDatabase
class FakeAnalyticsDatabase : FakeRoomDatabase()
abstract class FakeSharedStatement(@JvmField val db: FakeRoomDatabase)
abstract class FakeAdapterBase(db: FakeRoomDatabase) : FakeSharedStatement(db) {
    abstract fun bind(statement: Any, entity: Any)
    fun handle(entity: Any): Int = 1
    fun handleMultiple(entities: List<Any>): Int = 1
    fun insert(entity: Any) = Unit
    fun insertAndReturnId(entity: Any): Long = 1
    fun insertAll(entities: List<Any>): List<Long> = emptyList()
    fun insertIds(entities: Array<Any>): LongArray = LongArray(0)
    fun unrelated(name: String) = Unit
}

class RoomInsertsTest {

    @Test
    fun selectsOnlyInserts() {
        val names = RoomInserts.insertMethods(FakeAdapterBase::class.java).map { it.name }.toSet()
        assertEquals(setOf("insert", "insertAndReturnId", "insertAll", "insertIds"), names)
    }

    @Test
    fun findsTheDatabaseFieldOnASuperclass() {
        val field = RoomInserts.databaseField(FakeAdapterBase::class.java, FakeRoomDatabase::class.java)
        assertEquals("db", field?.name)
    }

    @Test
    fun skippedResultsMatchReturnTypes() {
        val m = FakeAdapterBase::class.java.declaredMethods.associateBy { it.name }
        assertNull(RoomInserts.skippedResult(m.getValue("insert")))
        assertEquals(-1L, RoomInserts.skippedResult(m.getValue("insertAndReturnId")))
        assertEquals(emptyList<Long>(), RoomInserts.skippedResult(m.getValue("insertAll")))
        assertArrayEquals(LongArray(0), RoomInserts.skippedResult(m.getValue("insertIds")) as LongArray)
    }
}
