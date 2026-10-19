package dev.savequest.android

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.savequest.silver.Silver
import dev.savequest.storage.Manual
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class LocalRealSaveScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun importedSummaryAndManualProgressSurviveReimportAndActivityRestart() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.filesDir,"audit-save.srm")
        assumeTrue(file.exists() && context.packageName.endsWith(".audit"))
        val parsed=Silver.parse(file.readBytes());val repo=Repository.get(context)
        runBlocking { repo.importSave(Uri.fromFile(file)); repo.db.progress().set(Manual(parsed.profile,"TM09",false,0)) }
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("${parsed.trainer} · ${parsed.badges}/16 badges · ${parsed.caughtCount} caught · ${parsed.seenCount} seen",substring=true).assertExists()
        compose.onNodeWithText("Search name or location").performScrollTo().performTextInput("TM09")
        compose.onNodeWithTag("catalog").performScrollToNode(hasTestTag("manual:TM09"))
        compose.onNodeWithTag("manual:TM09").performClick()
        compose.waitUntil(5000) { runBlocking { repo.db.progress().manual().any { it.profile==parsed.profile && it.task=="TM09" && it.checked } } }
        val count=runBlocking { repo.db.progress().snapshots().size }
        runBlocking { repo.importSave(Uri.fromFile(file)) }
        compose.activityRule.scenario.recreate();compose.waitForIdle()
        compose.onNodeWithText("Search name or location").performScrollTo().performTextInput("TM09")
        compose.onNodeWithTag("catalog").performScrollToNode(hasTestTag("manual:TM09"))
        compose.onNodeWithTag("manual:TM09").assertIsOn()
        assertEquals(count,runBlocking { repo.db.progress().snapshots().size })
    }
}
