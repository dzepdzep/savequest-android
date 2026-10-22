package dev.savequest.android

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.savequest.silver.Silver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Items checklist: collapsible locations, remembered fold state, search reveal, Undo. Run with :app:connectedDebugAndroidTest. */
class ItemsChecklistScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val section = "section:Johto · Azalea Town"
    private val row = "manual:item:EVENT_AZALEA_TOWN_HIDDEN_FULL_HEAL"
    private fun openItems() { compose.onNodeWithText("Items", substring = true, useUnmergedTree = true).performClick(); compose.waitForIdle() }
    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun scrollTo(tag: String) = compose.onNodeWithTag("catalog").performScrollToNode(hasTestTag(tag))

    @Test fun foldStateSurvivesRotationAndSearchTemporarilyRevealsIt() {
        val prefs = Repository.get(InstrumentationRegistry.getInstrumentation().targetContext).prefs
        prefs.edit().remove("collapsed:Items").commit(); compose.activityRule.scenario.recreate(); compose.waitForIdle()
        openItems(); scrollTo(section); scrollTo(row)
        compose.onNodeWithTag(section).performClick(); compose.waitForIdle()
        assertFalse(exists(row))                                              // folded
        compose.activityRule.scenario.recreate(); compose.waitForIdle(); scrollTo(section)
        assertFalse(exists(row))                                              // still folded after rotation / recreation
        compose.onNodeWithText("Search name or location").performScrollTo().performTextInput("Full Heal")
        compose.waitForIdle(); scrollTo(row)                                  // search reveals the match
        assertTrue(exists(row))
        compose.onNodeWithText("Search name or location").performTextClearance(); compose.waitForIdle()
        scrollTo(section); assertFalse(exists(row))                           // folded again; stored state was never touched
        compose.onNodeWithTag("items:expandAll").performScrollTo().performClick(); compose.waitForIdle()
        scrollTo(row); assertTrue(exists(row))
    }

    @Test fun undoRestoresAManualCheckThatLeftTheUnconfirmedOnlyList() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.filesDir, "audit-save.srm")
        assumeTrue(file.exists() && context.packageName.endsWith(".audit"))
        val repo = Repository.get(context); val profile = Silver.parse(file.readBytes()).profile
        runBlocking { repo.importSave(Uri.fromFile(file)) }
        compose.activityRule.scenario.recreate(); compose.waitForIdle(); openItems()
        compose.onNodeWithText("Search name or location").performScrollTo().performTextInput("Full Heal")
        compose.onNodeWithText("Show unconfirmed only").performScrollTo().performClick(); compose.waitForIdle()
        assumeTrue(runCatching { scrollTo(row) }.isSuccess && exists(row))     // this save must not already have the item
        compose.onNodeWithTag(row).performClick()
        compose.waitUntil(5000) { !exists(row) }                              // checked item disappears from the list
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(5000) { runBlocking { repo.db.progress().manual().none { it.profile == profile && it.task == "item:EVENT_AZALEA_TOWN_HIDDEN_FULL_HEAL" && it.checked } } }
        compose.waitUntil(5000) { exists(row) }                               // and is back
    }
}
