package dev.savequest.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Team advisor tab, weekday / time filters, and filters surviving rotation. Run with :app:connectedDebugAndroidTest. */
class TeamAndWhenScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun open(chip: String) { compose.onNodeWithText(chip, substring = true, useUnmergedTree = true).performClick(); compose.waitForIdle() }
    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    @Test fun teamTabAsksForASaveWhenNoneIsImportedOrShowsAdviceWhenOneIs() {
        open("Team")
        compose.onNodeWithTag("catalog").performScrollToNode(hasTestTag("team"))
        assertTrue(exists("team"))
        // with a save the card says "What to do next"; without one it asks for an import. Either way the tab must render.
        val hasAdvice = compose.onAllNodesWithText("What to do next").fetchSemanticsNodes().isNotEmpty()
        val asks = compose.onAllNodesWithText("Import a save", substring = true).fetchSemanticsNodes().isNotEmpty()
        assertTrue(hasAdvice || asks)
    }

    @Test fun timeFilterChipsExistAndSurviveRotation() {
        open("Pokédex")
        compose.onAllNodesWithText("night", substring = true, ignoreCase = true)[0].performScrollTo().performClick(); compose.waitForIdle()   // "Night" chip, or "Now · night" after dark
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        open("Pokédex")
        compose.onNodeWithText("Showing only entries limited to night", substring = true).assertExists()   // the filter and its note were kept
        compose.onAllNodesWithText("Any")[1].performScrollTo().performClick()                                      // the second "Any" belongs to the Time row
    }
}
