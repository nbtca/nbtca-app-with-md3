package space.nbtca.app.core.academic

import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicStoreTest {
    private val term = AcademicTerm("2026", "3", "2026-2027", "第一学期")
    private val alice = AcademicSnapshot(studentId = "alice", termLabel = term.label, term = term)
    private val bob = alice.copy(studentId = "bob")

    @Test fun switchingAccountsAndSigningOutDoNotExposeAnotherStudentsSnapshots() {
        val preferences = MemoryPreferences()
        val store = AcademicStore(preferences)
        store.saveSnapshot(alice, "session=alice")

        store.saveWebSession("bob", "session=bob")
        assertTrue(store.loadSnapshots().isEmpty())
        store.saveSnapshot(bob, "session=bob")
        assertEquals(listOf(bob), AcademicStore(preferences).loadSnapshots())

        store.clearCredentials()
        assertTrue(store.loadSnapshots().isEmpty())
        assertNull(store.cookies)

        store.saveWebSession("alice", "session=new-alice")
        assertEquals(listOf(alice), store.loadSnapshots())
    }

    @Test fun legacyDataRequiresAVerifiedOwnerAndAnonymousSnapshotsStayHidden() {
        val preferences = MemoryPreferences()
        val store = AcademicStore(preferences)
        preferences.edit()
            .putString("student_id", "alice")
            .putString("cookies", "session=legacy")
            .putString("snapshot", Json.encodeToString(alice))
            .apply()

        assertTrue(store.loadSnapshots().isEmpty())
        store.saveWebSession("bob", "session=bob")
        assertTrue(store.loadSnapshots().isEmpty())
        store.saveWebSession("alice", "session=alice")
        assertEquals(listOf(alice), store.loadSnapshots())

        preferences.edit().putString("snapshot", Json.encodeToString(alice.copy(studentId = ""))).apply()
        assertTrue(store.loadSnapshots().isEmpty())
    }

    @Test fun webSyncRequiresAnIdentifiedStudentEvenForArrayTimetables() {
        val withoutIdentity = """{"tt":"[]","exams":[],"grades":[]}"""
        assertThrows(AcademicException::class.java) { AcademicWebSync.parse(withoutIdentity, term) }

        val withIdentity = """{"studentId":"bob","tt":"[]","exams":[],"grades":[]}"""
        assertEquals("bob", AcademicWebSync.parse(withIdentity, term).studentId)
    }

    private class MemoryPreferences : SharedPreferences {
        private val values = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
        override fun contains(key: String): Boolean = values.containsKey(key)
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = error("Unused")
        override fun getInt(key: String, defValue: Int): Int = error("Unused")
        override fun getLong(key: String, defValue: Long): Long = error("Unused")
        override fun getFloat(key: String, defValue: Float): Float = error("Unused")
        override fun getBoolean(key: String, defValue: Boolean): Boolean = error("Unused")
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val changes = mutableMapOf<String, String?>()

            override fun putString(key: String, value: String?) = apply { changes[key] = value }
            override fun remove(key: String) = putString(key, null)
            override fun clear(): SharedPreferences.Editor = error("Unused")
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = error("Unused")
            override fun putInt(key: String, value: Int): SharedPreferences.Editor = error("Unused")
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = error("Unused")
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = error("Unused")
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = error("Unused")

            override fun apply() {
                changes.forEach { (key, value) ->
                    if (value == null) values.remove(key) else values[key] = value
                }
            }

            override fun commit(): Boolean {
                apply()
                return true
            }
        }
    }
}
