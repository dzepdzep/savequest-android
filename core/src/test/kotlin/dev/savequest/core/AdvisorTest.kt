package dev.savequest.core
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AdvisorTest {
    // the real generated data (tools/build_advisor.py), found whether Gradle runs from the module or the repo root
    private val data: AdvisorData by lazy {
        val f = listOf("../app/src/main/assets/advisor.json", "app/src/main/assets/advisor.json").map(::File).first { it.exists() }
        parseAdvisorData(f.readText())
    }
    private fun idOf(name: String) = data.speciesNames.indexOf(name) + 1
    private fun moveId(name: String) = data.moves.entries.first { it.value.name.equals(name, true) }.key
    private fun mon(species: String, level: Int, vararg moves: String, hp: Int? = null) =
        PartyMember(idOf(species), level, moves.map { moveId(it) }, species, hp, hp?.let { 100 })
    private fun view(johto: Int = 0, kanto: Int = 0, flags: Set<Int> = emptySet(), vararg party: PartyMember) =
        SaveView(johto, kanto, party.toList()) { it in flags }

    @Test fun dataLoadsAndIsComplete() {
        assertEquals(251, data.speciesNames.size); assertEquals(251, data.speciesTypes.size)
        assertEquals(22, data.stages.size); assertEquals(7, data.hms.size)
        assertEquals(listOf("Zephyr", "Hive", "Plain", "Fog", "Mineral", "Storm", "Glacier", "Rising"), data.johtoBadges)
        assertEquals(listOf("Fire", "Flying"), data.typesOf(idOf("Ho-Oh")).map { data.typeName(it) })
        assertEquals(listOf("Electric", "Steel"), data.typesOf(idOf("Magnemite")).map { data.typeName(it) })
    }
    @Test fun typeChartMatchesGen2() {
        fun t(n: String) = data.typeNames.indexOf(n)
        assertEquals(2.0, data.effect(t("Fire"), listOf(t("Grass"))), 0.0)
        assertEquals(4.0, data.effect(t("Ice"), listOf(t("Dragon"), t("Flying"))), 0.0)          // Dragonite
        assertEquals(0.0, data.effect(t("Ground"), listOf(t("Flying"))), 0.0)
        assertEquals(0.0, data.effect(t("Normal"), listOf(t("Ghost"))), 0.0)
        assertEquals(2.0, data.effect(t("Dark"), listOf(t("Psychic"))), 0.0)                       // Gen 2 added Dark
        assertEquals(0.5, data.effect(t("Normal"), listOf(t("Steel"))), 0.0)
        assertEquals(1.0, data.effect(t("Water"), listOf(t("Normal"))), 0.0)
    }
    @Test fun bossTeamsComeFromTheGame() {
        val falkner = data.stages.first { it.id == "falkner" }
        assertEquals(listOf("Pidgey" to 7, "Pidgeotto" to 9), falkner.mons.map { data.speciesName(it.species) to it.level })
        assertEquals(58, data.stages.first { it.id == "blue" }.topLevel)
        assertEquals(81, data.stages.first { it.id == "red" }.topLevel)
        assertEquals(1468, data.stages.first { it.id == "lance" }.flag)
    }
    @Test fun storyOrderAndNextGoal() {
        val ord = Advisor.order(data).map { it.id }
        assertEquals(listOf("falkner", "bugsy", "whitney", "morty", "chuck", "jasmine", "pryce", "clair"), ord.take(8))
        assertEquals(listOf("will", "koga", "bruno", "karen", "lance"), ord.subList(8, 13))
        assertEquals("blue", ord[ord.size - 2]); assertEquals("red", ord.last())
        assertEquals("falkner", Advisor.plan(data, view(party = arrayOf(mon("Cyndaquil", 5, "Tackle"))), null).next!!.id)
        // Zephyr + Hive + Plain badges (bits 0..2): Morty is next
        assertEquals("morty", Advisor.plan(data, view(johto = 0b111, party = arrayOf(mon("Quilava", 20, "Ember"))), null).next!!.id)
        // all Johto badges, Will + Koga flags set: Bruno is next
        val flags = setOf(data.stages.first { it.id == "will" }.flag!!, data.stages.first { it.id == "koga" }.flag!!)
        assertEquals("bruno", Advisor.plan(data, view(johto = 0xFF, flags = flags, party = arrayOf(mon("Typhlosion", 50, "Flamethrower"))), null).next!!.id)
    }
    @Test fun fireBeatsBugsyAndFlagsTheMissingCoverage() {
        val good = Advisor.plan(data, view(johto = 1, party = arrayOf(mon("Quilava", 17, "Ember", "Tackle"))), null)
        assertEquals("bugsy", good.next!!.id)
        assertTrue(good.answers.all { it.multiplier >= 2.0 })                  // Fire beats Metapod, Kakuna and Scyther
        assertEquals("Ember", good.answers.first().move)
        val bad = Advisor.plan(data, view(johto = 1, party = arrayOf(mon("Rattata", 17, "Tackle", "Tail Whip"))), null)
        assertTrue(bad.advice.any { it.level == Level.NEED && it.title.startsWith("No super-effective answer") })
        assertTrue(bad.suggestedTypes.containsAll(listOf("Fire")))             // Fire is the most useful type against a Bug team
    }
    @Test fun underLevelledTeamIsWarnedAndOverLevelledIsNot() {
        val low = Advisor.plan(data, view(johto = 0b11, party = arrayOf(mon("Quilava", 12, "Ember"), mon("Pidgey", 10, "Gust"))), null)   // Whitney next, Lv 20
        assertTrue(low.advice.any { it.level == Level.WARN && it.title.startsWith("Under-levelled") })
        val ok = Advisor.plan(data, view(johto = 0b11, party = arrayOf(mon("Quilava", 20, "Ember"), mon("Pidgeotto", 19, "Gust"))), null)
        assertFalse(ok.advice.any { it.title.startsWith("Under-levelled") })
    }
    @Test fun hmCarrierOnlyAskedWhenBadgeAndHmAreOwned() {
        val party = arrayOf(mon("Quilava", 20, "Ember"), mon("Marill", 18, "Bubble"))
        fun titles(johto: Int, owned: Set<String>?) = Advisor.plan(data, view(johto = johto, party = party), owned).advice.map { it.title }
        assertFalse(titles(0, null).any { it.startsWith("Nobody knows") })           // no Fog badge: Surf is not usable yet
        assertTrue("Nobody knows Surf" in titles(0b1000, setOf("Surf")))                    // Fog badge (bit 3), HM unknown -> assume owned
        assertFalse(titles(0b1000, emptySet()).any { it.startsWith("Nobody knows") })   // HM known not to be owned
        val withSurf = Advisor.plan(data, view(johto = 0b1000, party = arrayOf(mon("Marill", 18, "Surf"))), setOf("Surf")).advice.map { it.title }
        assertFalse("Nobody knows Surf" in withSurf)
        val detail = Advisor.plan(data, view(johto = 0b1000, party = party), setOf("Surf")).advice.first { it.title == "Nobody knows Surf" }.detail
        assertTrue(detail.contains("Marill"))                                        // Marill can learn Surf
    }
    @Test fun exposureStackingAndFaintedAreReported() {
        val p = Advisor.plan(data, view(johto = 0b11111111, flags = emptySet(), party = arrayOf(
            mon("Charizard", 50, "Flamethrower", hp = 0), mon("Arcanine", 50, "Flamethrower"), mon("Rapidash", 50, "Flamethrower"))), null)
        assertEquals("will", p.next!!.id)
        assertTrue(p.advice.any { it.title == "Three or more Fire types" })
        assertTrue(p.advice.any { it.title == "1 fainted" })
        assertTrue(p.advice.any { it.title == "Only 3 in the party" })
        assertEquals(8, p.badgeCount)
        // levels / severities are sorted NEED, WARN, INFO
        assertEquals(p.advice.sortedBy { it.level.ordinal }.map { it.title }, p.advice.map { it.title })
    }
    @Test fun finishedGameHasNoNextGoal() {
        val flags = data.stages.mapNotNull { it.flag }.toSet()
        val p = Advisor.plan(data, view(johto = 0xFF, kanto = 0xFF, flags = flags, party = arrayOf(mon("Typhlosion", 80, "Flamethrower"))), null)
        assertNull(p.next); assertTrue(p.upcoming.isEmpty())
    }
}
