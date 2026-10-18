package dev.savequest.android
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import dev.savequest.storage.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    @Test fun snapshotsDeduplicateWithoutOverwritingManualOrOtherProfiles() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, SaveQuestDatabase::class.java).build()
        try {
            val dao = db.progress(); dao.set(Manual("a","TM01",true,1))
            dao.insert(Snapshot("1","a","same",1,byteArrayOf(1),false))
            assertEquals(-1L, dao.insert(Snapshot("2","a","same",2,byteArrayOf(1),false)))
            dao.insert(Snapshot("3","b","same",3,byteArrayOf(1),false))
            assertEquals(2,dao.snapshots().size); assertTrue(dao.manual().single().checked)
            dao.set(Manual("b","TM01",false,2)); assertEquals(2,dao.manual().size)
        } finally { db.close() }
    }
}
