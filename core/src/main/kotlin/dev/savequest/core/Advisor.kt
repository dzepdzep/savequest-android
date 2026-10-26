package dev.savequest.core

/*
 * Team advisor: given a Pokemon Gold/Silver save (badges, event flags, party) it says what is next, how the team matches up,
 * and what is missing. Pure Kotlin, no Android types, so it is unit tested on the JVM. All numbers (types, type chart, move
 * types, boss teams, HM learnsets) come from assets/advisor.json, which tools/build_advisor.py generates from pret/pokegold.
 * The written tips in that file are short notes paraphrased from Serebii / NationalDex.
 */

data class AdvMove(val name: String, val power: Int, val type: Int)               // type -1 = none (status / curse)
data class AdvMon(val species: Int, val level: Int, val moves: List<Int>)          // species is the national dex number (1..251)
data class AdvStage(
    val id: String, val title: String, val where: String, val kind: String, val region: String,
    val badge: Int, val flag: Int?, val recommended: Int, val tips: String, val source: String, val mons: List<AdvMon>,
) {
    val topLevel get() = mons.maxOf { it.level }
    val lowLevel get() = mons.minOf { it.level }
}
data class AdvHm(val id: Int, val name: String, val badge: String, val learners: Set<Int>)

class AdvisorData(
    val typeNames: List<String>,
    val johtoBadges: List<String>,
    val kantoBadges: List<String>,
    val speciesNames: List<String>,             // index = dex number - 1
    val speciesTypes: List<List<Int>>,          // index = dex number - 1
    chartRules: List<Triple<Int, Int, Int>>,    // attacker type, defender type, multiplier x10 (20, 5 or 0); anything else is 10
    val moves: Map<Int, AdvMove>,
    val hms: List<AdvHm>,
    val stages: List<AdvStage>,
) {
    private val chart: Map<Int, Int> = chartRules.associate { (a, d, m) -> a * 64 + d to m }
    /** Damage multiplier of one attacking type against a defender with the given types (0, .25, .5, 1, 2, 4). */
    fun effect(attackType: Int, defender: List<Int>): Double {
        if (attackType < 0) return 1.0
        var m = 1.0
        for (d in defender) m *= (chart[attackType * 64 + d] ?: 10) / 10.0
        return m
    }
    fun typesOf(dex: Int): List<Int> = speciesTypes.getOrElse(dex - 1) { emptyList() }
    fun typeName(t: Int) = typeNames.getOrElse(t) { "?" }
    fun speciesName(dex: Int) = speciesNames.getOrElse(dex - 1) { "#$dex" }
}

/** One party member as read from the save. [moves] are move ids (0 = empty slot). */
data class PartyMember(val species: Int, val level: Int, val moves: List<Int>, val name: String, val hp: Int? = null, val maxHp: Int? = null, val egg: Boolean = false) {
    val fainted get() = hp != null && hp == 0
}

/** What the advisor needs from the save. [johto] / [kanto] are the badge bytes (bit 0 = first badge, in the order of AdvisorData). */
class SaveView(val johto: Int, val kanto: Int, val party: List<PartyMember>, val isFlagSet: (Int) -> Boolean)

enum class Level { NEED, WARN, INFO }
data class Advice(val level: Level, val title: String, val detail: String)
/** How the party answers one boss Pokemon: the best move-or-STAB option, or none. */
data class EnemyAnswer(val enemy: AdvMon, val enemyName: String, val answerer: String?, val move: String?, val multiplier: Double)
/** How many of the boss's Pokemon can hit a party member super-effectively with a move they know. */
data class Exposure(val member: String, val hitBy: Int, val of: Int)

data class AdvicePlan(
    val next: AdvStage?,
    val upcoming: List<AdvStage>,
    val answers: List<EnemyAnswer>,
    val exposure: List<Exposure>,
    val suggestedTypes: List<String>,
    val advice: List<Advice>,
    val badgeCount: Int,
)

object Advisor {
    fun hasBadge(data: AdvisorData, view: SaveView, stage: AdvStage): Boolean =
        stage.badge >= 0 && ((if (stage.region == "kanto") view.kanto else view.johto) shr stage.badge) and 1 == 1

    fun isDone(data: AdvisorData, view: SaveView, stage: AdvStage): Boolean =
        if (stage.badge >= 0) hasBadge(data, view, stage) else stage.flag?.let(view.isFlagSet) ?: false

    /** Story order: Johto gyms, Elite Four, Lance, then the Kanto gyms (lowest-level first, Blue last), then Red. */
    fun order(data: AdvisorData): List<AdvStage> {
        val s = data.stages
        val johto = s.filter { it.kind == "gym" && it.region == "johto" }
        val e4 = s.filter { it.kind == "e4" }
        val lance = s.filter { it.kind == "champion" }
        val kanto = s.filter { it.kind == "gym" && it.region == "kanto" }.sortedBy { if (it.id == "blue") 99 else it.topLevel }
        val red = s.filter { it.kind == "red" }
        return johto + e4 + lance + kanto + red
    }

    fun badgeCount(view: SaveView) = Integer.bitCount(view.johto and 0xFF) + Integer.bitCount(view.kanto and 0xFF)

    /** Best damaging option [member] has against [enemy]: highest effective power over its known moves and its own types (STAB-style fallback). */
    private fun bestHit(data: AdvisorData, member: PartyMember, enemy: AdvMon): Pair<AdvMove?, Double> {
        val def = data.typesOf(enemy.species)
        val own = data.typesOf(member.species)
        var best: AdvMove? = null; var bestScore = 0.0; var bestMult = 0.0
        for (id in member.moves) {
            val mv = data.moves[id] ?: continue
            if (mv.power <= 1 || mv.type < 0) continue                         // 1-power moves (fixed damage) are not a real answer
            val mult = data.effect(mv.type, def)
            val stab = if (mv.type in own) 1.5 else 1.0
            val score = mult * stab * mv.power
            if (score > bestScore) { bestScore = score; best = mv; bestMult = mult }
        }
        return best to bestMult
    }

    fun plan(data: AdvisorData, view: SaveView, ownedHms: Set<String>? = null): AdvicePlan {
        val ordered = order(data)
        val remaining = ordered.filter { !isDone(data, view, it) }
        val next = remaining.firstOrNull()
        val party = view.party.filter { !it.egg }
        val out = mutableListOf<Advice>()

        // 1) the next fight
        var answers = emptyList<EnemyAnswer>(); var exposure = emptyList<Exposure>(); var suggested = emptyList<String>()
        if (next != null) {
            answers = next.mons.map { en ->
                val options = party.map { it to bestHit(data, it, en) }.filter { it.second.first != null }
                val chosen = options.filter { it.second.second >= 2.0 }.maxByOrNull { (_, h) -> h.second * h.first!!.power }
                    ?: options.maxByOrNull { (_, h) -> h.second * h.first!!.power }
                EnemyAnswer(en, data.speciesName(en.species), chosen?.first?.name, chosen?.second?.first?.name, chosen?.second?.second ?: 0.0)
            }
            exposure = party.map { m ->
                val own = data.typesOf(m.species)
                val hit = next.mons.count { en -> en.moves.any { id -> data.moves[id]?.let { mv -> mv.power > 1 && mv.type >= 0 && data.effect(mv.type, own) >= 2.0 } == true } }
                Exposure(m.name, hit, next.mons.size)
            }
            // which attacking types would hit the most of the boss team super-effectively
            val perType = data.typeNames.indices.associateWith { t ->
                next.mons.count { en -> data.effect(t, data.typesOf(en.species)) >= 2.0 }
            }
            suggested = perType.filter { it.value * 2 >= next.mons.size && it.value > 0 }.entries
                .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key }).take(3).map { data.typeName(it.key) }
        }

        // 2) level check against the pro-site target
        if (next != null && party.isNotEmpty()) {
            val top = party.map { it.level }.sortedDescending().take(3)
            val avg = top.average()
            val gap = next.recommended - avg
            if (gap >= 3) out += Advice(Level.WARN, "Under-levelled for ${next.title}",
                "Your best ${top.size} average Lv ${"%.0f".format(avg)}; ${next.title}'s team tops out at Lv ${next.topLevel} (aim for about Lv ${next.recommended}).")
            else if (avg >= next.recommended + 12) out += Advice(Level.INFO, "Plenty of levels for ${next.title}",
                "Your best ${top.size} average Lv ${"%.0f".format(avg)} against a top level of ${next.topLevel}. You could move on to the next goal.")
        }

        // 3) matchup holes
        if (next != null && party.isNotEmpty()) {
            val holes = answers.filter { it.multiplier < 2.0 }
            if (holes.size == answers.size && answers.isNotEmpty()) out += Advice(Level.NEED, "No super-effective answer for ${next.title}",
                "None of your moves hits ${next.title}'s team super-effectively. ${if (suggested.isNotEmpty()) "Bring ${suggested.joinToString(" / ")} moves." else ""}".trim())
            else if (holes.isNotEmpty()) out += Advice(Level.WARN, "${holes.size} of ${answers.size} of ${next.title}'s Pokémon have no super-effective answer",
                holes.joinToString(", ") { "${it.enemyName} Lv ${it.enemy.level}" } + (if (suggested.isNotEmpty()) ". Useful types: ${suggested.joinToString(" / ")}." else "."))
            val worst = exposure.filter { it.hitBy * 2 > it.of && it.of > 0 }
            for (e in worst) out += Advice(Level.WARN, "${e.member} is exposed",
                "${e.hitBy} of ${next.title}'s ${e.of} Pokémon know a move that is super-effective against it. Keep it in reserve or heal up.")
        }

        // 4) HM carriers: only for HMs whose badge you hold and (if known) whose HM you own
        val johtoNames = data.johtoBadges
        val missing = mutableListOf<String>(); val lines = mutableListOf<String>()
        for (hm in data.hms) {
            val badgeBit = johtoNames.indexOf(hm.badge)
            val usable = badgeBit >= 0 && (view.johto shr badgeBit) and 1 == 1
            val owned = ownedHms?.contains(hm.name) ?: true
            if (!usable || !owned || party.isEmpty() || party.any { hm.id in it.moves }) continue
            missing += hm.name
            val can = party.filter { it.species in hm.learners }.map { it.name }
            lines += if (can.isNotEmpty()) "${hm.name}: ${can.take(3).joinToString(", ")} can learn it." else "${hm.name}: nobody in the party can learn it; keep a user in the party or PC."
        }
        if (missing.isNotEmpty()) out += Advice(Level.WARN, "Nobody knows ${missing.joinToString(", ")}",
            "You can already use ${if (missing.size == 1) "this HM" else "these HMs"} (badge earned), but no party member has ${if (missing.size == 1) "it" else "them"}.\n" + lines.joinToString("\n"))

        // 5) team shape
        if (party.size < 6 && party.isNotEmpty()) out += Advice(Level.INFO, "Only ${party.size} in the party", "A full party of six gives the most type coverage.")
        val fainted = party.count { it.fainted }
        if (fainted > 0) out += Advice(Level.INFO, "$fainted fainted", "Revive or heal ${if (fainted == 1) "it" else "them"} before the next trainer.")
        val typeCount = HashMap<Int, Int>()
        for (m in party) for (t in data.typesOf(m.species)) typeCount.merge(t, 1, Int::plus)
        val stacked = typeCount.filter { it.value >= 3 }.keys.sorted()
        for (t in stacked) out += Advice(Level.INFO, "Three or more ${data.typeName(t)} types", "Several ${data.typeName(t)} Pokémon share the same weaknesses. Mixing types gives better coverage.")
        if (party.isNotEmpty() && party.all { m -> m.moves.none { id -> (data.moves[id]?.power ?: 0) > 1 } }) out += Advice(Level.NEED, "No attacking moves", "Nobody in the party knows a damaging move.")

        // 6) the plain tip for the next fight
        if (next != null) out += Advice(Level.INFO, "${next.title}: ${next.where}", next.tips)

        val sorted = out.withIndex().sortedWith(compareBy({ it.value.level.ordinal }, { it.index })).map { it.value }
        return AdvicePlan(next, remaining.take(4), answers, exposure, suggested, sorted, badgeCount(view))
    }
}
