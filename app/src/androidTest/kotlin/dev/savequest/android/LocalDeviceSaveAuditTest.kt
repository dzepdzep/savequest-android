package dev.savequest.android

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.savequest.core.EvidenceKind
import dev.savequest.silver.Silver
import dev.savequest.storage.Manual
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Local-only fixtures are pushed into the isolated audit app after installation, never packaged. */
@RunWith(AndroidJUnit4::class)
class LocalDeviceSaveAuditTest {
    @Test fun realSaveMatchesEveryEntryAndRoundTripsWithoutLosingManualProgress() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.filesDir,"audit-save.srm"); val cases = File(context.filesDir,"audit-cases.tsv")
        assumeTrue("Requires opt-in local fixture injection",file.exists() && cases.exists())
        val raw = file.readBytes(); val name = "local-audit-${UUID.randomUUID()}.db"
        val repo = Repository(context,name); val backup = File(context.cacheDir,"audit-${UUID.randomUUID()}.json")
        try {
            repo.importSave(Uri.fromFile(file),linkSource=true)
            val original = repo.db.progress().snapshots().single(); val p = Silver.parse(original.raw)
            assertArrayEquals(raw,original.raw)
            val expected = cases.readLines().associate { val v=it.split('\t');v[0] to v[5] }
            assertEquals(expected.keys,repo.tasks.map { it.id }.toSet())
            repo.tasks.forEach { assertEquals(it.id,expected[it.id],p.evidence(it).kind.name) }
            repo.db.progress().set(Manual(p.profile,"TM09",true,1))
            repo.refresh(); assertEquals(1,repo.db.progress().snapshots().size)
            repo.export(Uri.fromFile(backup)); repo.restore(Uri.fromFile(backup))
            assertEquals(1,repo.db.progress().snapshots().size)
            assertTrue(repo.db.progress().manual().single().checked)
            assertArrayEquals(raw,file.readBytes())
        } finally { repo.db.close(); context.deleteDatabase(name); backup.delete() }
    }
}
