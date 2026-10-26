package dev.savequest.core
import org.junit.Assert.*
import org.junit.Test
class ChecklistTest {
    private fun item(id: String, area: String, region: String = "johto", kind: String = "ball", title: String = id, howto: String = "") =
        Task(id, title, "Items", area = area, region = region, kind = kind, howto = howto)
    private val a1 = item("a1", "Azalea Town"); private val a2 = item("a2", "Azalea Town"); private val a3 = item("a3", "Azalea Town", kind = "hidden")
    private val b1 = item("b1", "Route 3", "kanto"); private val s1 = item("s1", "Shops", "johto,kanto", "shop")
    private val all = listOf(b1, s1, a2, a1, a3)
    private val doneIds = setOf("a1", "a2", "b1")
    private val isDone = { t: Task -> t.id in doneIds }

    @Test fun progressIgnoresUnconfirmedOnlySwitch() {
        val off = Checklist.sections(all, isDone, false).associate { it.key to (it.done to it.total) }
        val on = Checklist.sections(all, isDone, true).associate { it.key to (it.done to it.total) }
        assertEquals(2 to 3, off["Johto · Azalea Town"]); assertEquals(off["Johto · Azalea Town"], on["Johto · Azalea Town"])
    }
    @Test fun unconfirmedOnlyListsOnlyOpenRowsAndDropsFinishedSections() {
        val on = Checklist.sections(all, isDone, true)
        assertEquals(listOf("a3"), on.first { it.key == "Johto · Azalea Town" }.shown.map { it.id })
        assertNull(on.firstOrNull { it.key == "Kanto · Route 3" })   // fully done: nothing left to list
        assertEquals(Checklist.SHOPS, on.last().key)
    }
    @Test fun sectionOrderJohtoKantoShops() {
        assertEquals(listOf("Johto · Azalea Town", "Kanto · Route 3", Checklist.SHOPS), Checklist.sections(all, isDone, false).map { it.key })
    }
    @Test fun filtersMatchPreviousBehaviour() {
        val gift = item("g", "Route 29", kind = "gift", title = "Pink Bow", howto = "When: TUESDAY only.")
        assertTrue(Checklist.matches(gift, "Items", "gift", "Johto", "tuesday"))      // search covers the how-to text
        assertFalse(Checklist.matches(gift, "Items", "ball", "All", ""))
        assertFalse(Checklist.matches(gift, "Items", "All", "Kanto", ""))
        assertTrue(Checklist.matches(s1, "Items", "All", "Kanto", ""))                // multi-region rows match both
        assertTrue(Checklist.matches(Task("t", "x", "Trainers", kind = "zzz"), "Trainers", "gift", "All", ""))  // kind only filters Items / TM/HM
    }
    @Test fun searchRevealsCollapsedSectionsWithoutChangingTheRememberedState() {
        val collapsed = setOf("Johto · Azalea Town")
        assertFalse(Checklist.isExpanded("Johto · Azalea Town", collapsed, false))
        assertTrue(Checklist.isExpanded("Johto · Azalea Town", collapsed, true))
        assertFalse(Checklist.isExpanded("Johto · Azalea Town", collapsed, false))     // back to folded when the search is cleared
    }
    @Test fun toggleCollapseAllExpandAllKeepOtherSections() {
        var c = Checklist.toggled(emptySet(), "x"); assertEquals(setOf("x"), c)
        c = Checklist.collapseAll(c, listOf("a", "b")); assertEquals(setOf("x", "a", "b"), c)
        c = Checklist.expandAll(c, listOf("a", "x")); assertEquals(setOf("b"), c)
        assertEquals(emptySet<String>(), Checklist.toggled(setOf("b"), "b"))
    }
    @Test fun undoIsTheExactInverse() {
        val c = ManualChange("p", "item:1", before = false, after = true)
        assertEquals(ManualChange("p", "item:1", before = true, after = false), c.undo()); assertEquals(c, c.undo().undo())
    }
    @Test fun undoStillTargetsTheRowAfterItDisappearedFromTheList() {
        val doneNow = mutableSetOf<String>()
        fun listed() = Checklist.sections(listOf(a1, a2), { it.id in doneNow }, true).flatMap { it.shown }.map { it.id }
        assertEquals(listOf("a1", "a2"), listed())
        val change = ManualChange("p", "a1", false, true); doneNow += change.taskId     // checked: row vanishes under "unconfirmed only"
        assertEquals(listOf("a2"), listed())
        if (!change.undo().after) doneNow -= change.undo().taskId                        // Undo works from the stored change, not from the row
        assertEquals(listOf("a1", "a2"), listed())
    }
    @Test fun gameClockWindowsAndWeekdays() {
        assertEquals("night", GameClock.windowOf(3)); assertEquals("morning", GameClock.windowOf(4)); assertEquals("morning", GameClock.windowOf(9))
        assertEquals("day", GameClock.windowOf(10)); assertEquals("day", GameClock.windowOf(17)); assertEquals("night", GameClock.windowOf(18)); assertEquals("night", GameClock.windowOf(0))
        // java.util.Calendar: 1 = Sunday ... 7 = Saturday
        assertEquals(listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"), (1..7).map { GameClock.dayName(it) })
    }
    @Test fun weekdayAndTimeFiltersOnlyKeepLimitedMatches() {
        val tue = Task("tue", "Pink Bow", "Items", kind = "gift", days = listOf("Tue"))
        val bugContest = Task("bc", "Quick Claw", "Items", kind = "gift", days = listOf("Tue", "Thu", "Sat"))
        val night = Task("n", "Gastly", "Pokédex", times = listOf("night"))
        val plain = Task("p", "Rattata", "Pokédex")
        assertTrue(Checklist.matches(tue, "Items", "All", "All", "", WhenFilter(day = "Tue")))
        assertFalse(Checklist.matches(tue, "Items", "All", "All", "", WhenFilter(day = "Wed")))
        assertTrue(Checklist.matches(bugContest, "Items", "All", "All", "", WhenFilter(day = "Sat")))
        assertTrue(Checklist.matches(night, "Pokédex", "All", "All", "", WhenFilter(time = "night")))
        assertFalse(Checklist.matches(night, "Pokédex", "All", "All", "", WhenFilter(time = "morning")))
        assertFalse(Checklist.matches(plain, "Pokédex", "All", "All", "", WhenFilter(time = "night")))   // unlimited rows are hidden while a time is chosen
        assertTrue(Checklist.matches(plain, "Pokédex", "All", "All", "", WhenFilter()))                    // no filter: everything
        assertFalse(WhenFilter().active); assertTrue(WhenFilter(time = "day").active)
    }
}
