package dev.savequest.android

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** One Pokemon read from a Gold/Silver save (read only). */
data class Mon(val species: Int, val egg: Boolean, val nickname: String, val level: Int, val shiny: Boolean, val hp: Int?, val maxHp: Int?)
data class PcBox(val number: Int, val mons: List<Mon>, val valid: Boolean)
data class MonData(val party: List<Mon>, val boxes: List<PcBox>)

/**
 * Layout (pret/pokegold sram + the JohtoAtlas viewer): party block at 0x288A (count, 7 species bytes, 6 x 48-byte mons, names),
 * PC boxes 1-7 at 0x4000 and 8-14 at 0x6000, 0x450 bytes each. Only decodes saves whose primary block is intact.
 */
object SaveMons {
    private const val PARTY = 0x288A
    private const val BOX_BYTES = 0x450
    private fun b(r: ByteArray, i: Int) = r[i].toInt() and 0xFF
    private fun text(r: ByteArray, o: Int): String {
        val sb = StringBuilder()
        for (i in 0 until 11) {
            val x = b(r, o + i)
            if (x == 0x50 || x == 0 || x == 0xFF) break
            sb.append(when (x) {
                in 0x80..0x99 -> 'A' + (x - 0x80); in 0xA0..0xB9 -> 'a' + (x - 0xA0); in 0xF6..0xFF -> '0' + (x - 0xF6)
                0x7F -> ' '; 0xE0 -> '\''; 0xE3 -> '-'; 0xE6 -> '?'; 0xE7 -> '!'; 0xE8 -> '.'; 0xEF -> '♂'; 0xF5 -> '♀'; else -> '·'
            })
        }
        return sb.toString().trim()
    }
    private fun shiny(r: ByteArray, o: Int): Boolean {
        val d1 = b(r, o + 21); val d2 = b(r, o + 22)
        return (d1 and 15) == 10 && (d2 shr 4) == 10 && (d2 and 15) == 10 && (d1 shr 4) in setOf(2, 3, 6, 7, 10, 11, 14, 15)
    }
    private fun mon(r: ByteArray, o: Int, nameAt: Int, party: Boolean, names: List<String>): Mon? {
        val sp = b(r, o); val egg = sp == 253
        if (!(sp in 1..251 || egg)) return null
        val lv = b(r, o + 31)
        if (!egg && lv !in 1..100) return null
        val name = text(r, nameAt)
        return Mon(sp, egg, name.ifEmpty { if (egg) "EGG" else names.getOrElse(sp - 1) { "?" } }, lv, !egg && shiny(r, o),
            if (party) b(r, o + 34) * 256 + b(r, o + 35) else null, if (party) b(r, o + 36) * 256 + b(r, o + 37) else null)
    }
    fun read(r: ByteArray, names: List<String>): MonData? {
        if (r.size < 0x8000) return null
        val count = b(r, PARTY); if (count > 6) return null
        val party = (0 until count).mapNotNull { i ->
            mon(r, PARTY + 8 + i * 48, PARTY + 8 + 48 * 6 + 11 * 6 + i * 11, true, names)?.takeIf { it.species == b(r, PARTY + 1 + i) }
        }
        val boxes = (0 until 14).map { n ->
            val off = (if (n < 7) 0x4000 else 0x6000) + (n % 7) * BOX_BYTES
            val c = b(r, off)
            if (c > 20) PcBox(n + 1, emptyList(), false) else {
                val nameStart = off + 22 + 20 * 32 + 20 * 11
                val mons = (0 until c).mapNotNull { i -> mon(r, off + 22 + i * 32, nameStart + i * 11, false, names)?.takeIf { it.species == b(r, off + 1 + i) } }
                PcBox(n + 1, mons, mons.size == c)
            }
        }
        return MonData(party, boxes)
    }
}

@Composable private fun MonCard(m: Mon, names: List<String>) {
    Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!m.egg) Sprite("pokemon/%03d".format(m.species), 48.dp)
        Column(Modifier.weight(1f)) {
            Text(m.nickname, style = MaterialTheme.typography.titleSmall)
            Text((if (m.egg) "Egg" else "#%03d %s".format(m.species, names.getOrElse(m.species - 1) { "?" })) + " · Lv ${m.level}" + (if (m.shiny) " · Shiny" else ""), style = MaterialTheme.typography.bodySmall, color = if (m.shiny) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            if (m.hp != null && m.maxHp != null && m.maxHp > 0) {
                LinearProgressIndicator(progress = { (m.hp.toFloat() / m.maxHp).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                Text("HP ${m.hp} / ${m.maxHp}", style = MaterialTheme.typography.bodySmall)
            }
        }
    } }
}

@Composable fun PokemonTab(raw: ByteArray?, usedBackup: Boolean, names: List<String>) {
    val data = remember(raw, usedBackup) { if (raw == null || usedBackup) null else try { SaveMons.read(raw, names) } catch (_: Exception) { null } }
    var box by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var shinyOnly by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (data == null) { Text("Import a save to see your party and PC boxes (saves recovered from the backup block are not decoded).", style = MaterialTheme.typography.bodyMedium); return@Column }
        Text("Current party", style = MaterialTheme.typography.titleMedium)
        if (data.party.isEmpty()) Text("No Pokémon in the party.", style = MaterialTheme.typography.bodySmall)
        data.party.forEach { MonCard(it, names) }
        Text("PC boxes · ${data.boxes.sumOf { it.mons.size }} stored", style = MaterialTheme.typography.titleMedium)
        Text("Shows the state at your last in-game save.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search every box") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(checked = shinyOnly, onCheckedChange = { shinyOnly = it }); Text("Shiny only") }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            data.boxes.forEachIndexed { i, b -> FilterChip(selected = box == i && query.isEmpty(), onClick = { box = i; query = "" }, label = { Text("${b.number} (${b.mons.size})") }) }
        }
        val searching = query.isNotBlank() || shinyOnly
        val list = (if (searching) data.boxes.flatMap { it.mons } else data.boxes.getOrNull(box)?.mons.orEmpty())
            .filter { (query.isBlank() || it.nickname.contains(query, true) || names.getOrElse(it.species - 1) { "" }.contains(query, true)) && (!shinyOnly || it.shiny) }
        if (!searching && data.boxes.getOrNull(box)?.valid == false) Text("This box could not be read cleanly.", style = MaterialTheme.typography.bodySmall)
        if (list.isEmpty()) Text("Nothing here.", style = MaterialTheme.typography.bodySmall)
        list.forEach { MonCard(it, names) }
    }
}
