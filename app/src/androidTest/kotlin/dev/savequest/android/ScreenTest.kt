package dev.savequest.android
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
class ScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun catalogIsAvailableOfflineAndSearchable() {
        compose.onNodeWithText("SaveQuest").assertIsDisplayed()
        compose.onNodeWithText("Trainers", useUnmergedTree=true).performClick()
        compose.onNodeWithText("Search name or location").performScrollTo().performTextInput("Falkner")
        compose.onNodeWithTag("catalog").performScrollToNode(hasText("Lv 7 Pidgey / Lv 9 Pidgeotto"))
        compose.onNode(hasText("Falkner", substring=false) and !hasSetTextAction()).assertExists()
        compose.onNodeWithText("Lv 7 Pidgey / Lv 9 Pidgeotto").assertExists()
    }
}
