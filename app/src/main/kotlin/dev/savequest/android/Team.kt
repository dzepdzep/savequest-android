package dev.savequest.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.savequest.core.*

/**
 * Team advisor tab: what to do next, how the party matches up against the next boss, and what is missing.
 * Every number comes from the save and from assets/advisor.json (pret/pokegold data); the written tips are paraphrased from Serebii / NationalDex.
 * Badges live at SRAM 0x23E4 (Johto) and 0x23E5 (Kanto); event flags start at 0x261F (same layout the checklist uses).
 */
private const val JOHTO_BADGES = 0x23E4
private const val KANTO_BADGES = 0x23E5
private const val EVENT_FLAGS = 0x261F

private val Warn = Color(0xFFFFC857)

private fun loadAdvisor(ctx: android.content.Context): AdvisorData? = try {
    parseAdvisorData(ctx.assets.open("advisor.json").bufferedReader().use { it.readText() })
} catch (_: Exception) { null }

/** Turns the raw save into what the advisor needs, or null if the primary block could not be decoded. */
internal fun saveView(raw: ByteArray, names: List<String>): SaveView? {
    if (raw.size < 0x8000) return null
    val mons = try { SaveMons.read(raw, names) } catch (_: Exception) { null } ?: return null
    val party = mons.party.map { PartyMember(it.species, it.level, it.moves, it.nickname, it.hp, it.maxHp, it.egg) }
    fun flag(n: Int): Boolean {
        val i = EVENT_FLAGS + (n shr 3)
        return i < raw.size && (raw[i].toInt() shr (n and 7)) and 1 == 1
    }
    return SaveView(raw[JOHTO_BADGES].toInt() and 0xFF, raw[KANTO_BADGES].toInt() and 0xFF, party) { n -> flag(n) }
}

@Composable fun TeamTab(raw: ByteArray?, usedBackup: Boolean, names: List<String>, ownedHms: Set<String>, openUrl: (String) -> Unit) {
    val ctx = LocalContext.current
    val data = remember { loadAdvisor(ctx) }
    val view = remember(raw, usedBackup) { if (raw == null || usedBackup) null else saveView(raw, names) }
    val plan = remember(view, data, ownedHms) { if (view == null || data == null) null else Advisor.plan(data, view, ownedHms) }
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.testTag("team"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (data == null) { Text("The advisor data could not be loaded.", style = MaterialTheme.typography.bodyMedium); return@Column }
        if (plan == null || view == null) {
            Text("Import a save to get team advice (saves recovered from the backup block are not decoded).", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        val next = plan.next
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
            Text("What to do next", style = MaterialTheme.typography.titleMedium)
            if (next == null) Text("You have beaten everything this advisor knows about. Nice.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            else {
                Text(next.title + " · " + next.where, style = MaterialTheme.typography.titleLarge, color = scheme.primary, modifier = Modifier.padding(top = 4.dp))
                Text("Their Pokémon: Lv ${next.lowLevel}-${next.topLevel} · aim for about Lv ${next.recommended} · ${plan.badgeCount}/16 badges", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                if (plan.suggestedTypes.isNotEmpty()) Text("Best move types: " + plan.suggestedTypes.joinToString(" / "), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                if (plan.upcoming.size > 1) Text("Then: " + plan.upcoming.drop(1).joinToString(" → ") { it.title }, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        } }

        if (plan.advice.isNotEmpty()) {
            Text("Advice", style = MaterialTheme.typography.titleMedium)
            plan.advice.forEach { a ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(when (a.level) { Level.NEED -> "needs fixing"; Level.WARN -> "watch out"; Level.INFO -> "note" },
                            when (a.level) { Level.NEED -> scheme.error; Level.WARN -> Warn; Level.INFO -> scheme.primary })
                        Text(a.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    }
                    Text(a.detail, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                } }
            }
        }

        if (next != null && plan.answers.isNotEmpty()) {
            Text("Matchups against ${next.title}", style = MaterialTheme.typography.titleMedium)
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                plan.answers.forEach { r ->
                    val good = r.multiplier >= 2.0
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(if (good) "✔" else "–", if (good) scheme.primary else scheme.onSurfaceVariant)
                        Text("${r.enemyName} Lv ${r.enemy.level}", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text(if (r.answerer != null && r.move != null) "${r.answerer} · ${r.move} ×${trim(r.multiplier)}" else "no attacking move", style = MaterialTheme.typography.bodySmall, color = if (good) scheme.primary else scheme.onSurfaceVariant)
                    }
                }
                Text("✔ = one of your Pokémon has a super-effective attack. Based on the moves each Pokémon knows right now.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            } }
        }

        if (plan.exposure.isNotEmpty() && next != null) {
            Text("Your team", style = MaterialTheme.typography.titleMedium)
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                view.party.filter { !it.egg }.forEachIndexed { i, m ->
                    val e = plan.exposure.getOrNull(i)
                    Text("${m.name} · Lv ${m.level}" + (if (e != null) " · hit super-effectively by ${e.hitBy} of ${e.of}" else ""), style = MaterialTheme.typography.bodyMedium)
                }
            } }
        }

        if (next != null) TextButton(onClick = { openUrl(next.source) }) { Text("Tips from the pro sites: " + hostOf(next.source)) }
        Text("Boss teams, types and moves come from the game data (pret/pokegold). Tips are paraphrased from Serebii and NationalDex; the advice only knows what is in your last save.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}

private fun trim(x: Double) = if (x == x.toLong().toDouble()) x.toLong().toString() else x.toString()
private fun hostOf(url: String) = url.removePrefix("https://").removePrefix("www.").substringBefore('/')
