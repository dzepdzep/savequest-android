package dev.savequest.android

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.savequest.storage.Manual
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private fun fixture(id: Int = 1, bag: Int = 0): ByteArray {
        val b = ByteArray(32768); b[0x2008] = 99; b[0x2d6b] = 127; b[0x2009] = id.toByte()
        b[0x200b] = 0x93.toByte(); b[0x200c] = 0x50; b[0x23e6] = bag.toByte()
        b[0x288b] = 0xff.toByte()
        val sum = (0x2009..0x2d68).sumOf { b[it].toInt() and 255 }; b[0x2d69] = sum.toByte(); b[0x2d6a] = (sum ushr 8).toByte()
        return b
    }
    @Test fun importRefreshRollbackAndBackupAreNondestructive() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-${UUID.randomUUID()}.db"; val name2 = "test-${UUID.randomUUID()}.db"
        val repo = Repository(context,name); val restored = Repository(context,name2)
        val input = File(context.cacheDir,"${UUID.randomUUID()}.sav"); val backup = File(context.cacheDir,"${UUID.randomUUID()}.json")
        try {
            input.writeBytes(fixture()); repo.importSave(Uri.fromFile(input))
            val first = repo.db.progress().snapshots().single(); val profile = first.profile
            repo.db.progress().set(Manual(profile,"TM02",true,10))
            input.writeBytes(fixture(bag=1)); repo.importSave(Uri.fromFile(input),profile)
            assertEquals(2, repo.db.progress().snapshots().size); assertTrue(repo.db.progress().manual().single().checked)
            input.writeBytes(fixture()); repo.importSave(Uri.fromFile(input))
            assertEquals(2, repo.db.progress().snapshots().size)
            assertEquals(first.hash,repo.prefs.getString("currentHash:$profile",null))
            input.writeBytes(fixture(id=2))
            try { repo.importSave(Uri.fromFile(input),profile); fail("Changed trainer must be rejected on refresh") } catch (_: IllegalArgumentException) { }
            assertEquals(2,repo.db.progress().snapshots().size)
            repo.export(Uri.fromFile(backup)); restored.restore(Uri.fromFile(backup)); restored.restore(Uri.fromFile(backup))
            assertEquals(2,restored.db.progress().snapshots().size); assertTrue(restored.db.progress().manual().single().checked)
            assertEquals(first.hash,restored.prefs.getString("currentHash:$profile",null))
            restored.db.progress().set(Manual(profile,"TM02",false,20)); restored.restore(Uri.fromFile(backup))
            assertFalse(restored.db.progress().manual().single().checked)
            // An older duplicate late in a backup must not overwrite its newest entry.
            val duplicate = JSONObject(backup.readText()); val records = duplicate.getJSONArray("manual")
            records.put(JSONObject(records.getJSONObject(0).toString()).put("at",30).put("checked",true))
            records.put(JSONObject(records.getJSONObject(0).toString()).put("at",25).put("checked",false))
            backup.writeText(duplicate.toString()); restored.restore(Uri.fromFile(backup))
            assertTrue(restored.db.progress().manual().single().checked)
            assertEquals(30L,restored.db.progress().manual().single().updatedAt)
            // Legacy v1 backups stay readable.
            duplicate.put("format","savequest-backup-v1").remove("current")
            backup.writeText(duplicate.toString()); restored.restore(Uri.fromFile(backup))
            // Failed explicit import must leave the existing linked source and its identity intact.
            input.writeBytes(fixture()); repo.importSave(Uri.fromFile(input),linkSource=true)
            val linked = repo.prefs.getString("uri",null)
            input.writeBytes(ByteArray(32768))
            try { repo.importSave(Uri.fromFile(input),linkSource=false); fail("Invalid save accepted") } catch (_: IllegalArgumentException) { }
            assertEquals(linked,repo.prefs.getString("uri",null))
            backup.writeText("{\"format\":\"wrong\"}")
            try { restored.restore(Uri.fromFile(backup)); fail("Bad backup must fail") } catch (_: IllegalArgumentException) { }
            assertEquals(2,restored.db.progress().snapshots().size)
        } finally { repo.db.close(); restored.db.close(); context.deleteDatabase(name); context.deleteDatabase(name2); input.delete(); backup.delete() }
    }
}
