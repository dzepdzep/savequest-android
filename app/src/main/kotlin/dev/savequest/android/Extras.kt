package dev.savequest.android

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Read-only facts from the primary save block of a Gold/Silver .srm (offsets verified against
 * pret/pokegold: sGameData starts at 0x2009). Backup-block saves are not decoded here.
 */
object SaveFacts {
    private const val ID = 0x2009
    private const val NAME = 0x200B
    private const val MONEY = 0x23DB
    private fun b(r: ByteArray, i: Int) = r[i].toInt() and 0xFF
    fun usable(raw: ByteArray?) = raw != null && raw.size >= 0x2D6C
    fun trainerId(r: ByteArray) = (b(r, ID) shl 8) or b(r, ID + 1)
    /** Down + Select + B on the title screen asks for this 5-digit number; it changes when money changes. */
    fun clockPassword(r: ByteArray): String {
        var sum = b(r, ID) + b(r, ID + 1) + b(r, MONEY) + b(r, MONEY + 1) + b(r, MONEY + 2)
        for (i in 0 until 5) { val v = b(r, NAME + i); if (v == 0x50) break; sum += v }
        return (sum and 0xFFFF).toString().padStart(5, '0')
    }
}

private object SpriteCache {
    private val cache = HashMap<String, ImageBitmap?>()
    fun get(c: Context, path: String): ImageBitmap? = synchronized(cache) {
        cache.getOrPut(path) { try { c.assets.open(path).use { BitmapFactory.decodeStream(it)?.asImageBitmap() } } catch (_: Exception) { null } }
    }
}

/** Draws assets/sprites/<path>.png if you generated it (tools/build_sprites.py); otherwise nothing. */
@Composable fun Sprite(path: String, size: Dp) {
    val ctx = LocalContext.current
    val image = remember(path) { SpriteCache.get(ctx, "sprites/$path.png") }
    if (image != null) Image(image, null, Modifier.size(size), filterQuality = FilterQuality.None)
}

@Composable fun TipsTab(raw: ByteArray?, usedBackup: Boolean, open: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
            Text("Clock reset password", style = MaterialTheme.typography.titleMedium)
            Text("On the title screen hold Down + Select + B. The game asks for a 5-digit password.", style = MaterialTheme.typography.bodySmall)
            if (SaveFacts.usable(raw) && !usedBackup) {
                Text(SaveFacts.clockPassword(raw!!), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text("Calculated from your trainer ID, name and money. Refresh the save after you spend or earn money, because the password changes with it.", style = MaterialTheme.typography.bodySmall)
            } else Text("Import a save to see your number (backup-block saves are not decoded).", style = MaterialTheme.typography.bodySmall)
        } }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
            Text("Where Heracross hides", style = MaterialTheme.typography.titleMedium)
            Text("Heracross is not a normal grass encounter. In Gold and Silver it only appears when you use Headbutt on a rare tree on Routes 29, 30, 31, 32, 33, 42, 43, 44, 45 or 46 (30% of rare-tree battles; the other results are Spearow and Aipom). Trees in Azalea Town and Ilex Forest give Pineco instead.", style = MaterialTheme.typography.bodySmall)
            Text("Whether a tree is rare depends on your trainer ID and the tree's position, so it differs per save: roughly one tree in ten is rare. Headbutt each tree a few times; a rare tree gives a battle most of the time, an ordinary one far less.", style = MaterialTheme.typography.bodySmall)
            if (SaveFacts.usable(raw) && !usedBackup) Text("Your trainer ID ends in ${SaveFacts.trainerId(raw!!) % 10}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = { open("https://rankedboost.com/pokemon/location/gold-silver/") }) { Text("RankedBoost locations") }
        } }
    }
}

private class RouteTrees(val name: String, val w: Int, val h: Int, val grid: List<String>, val trees: List<Triple<Int, Int, Int>>)

private fun loadTrees(c: Context): List<RouteTrees> = try {
    val o = org.json.JSONObject(c.assets.open("trees.json").bufferedReader().use { it.readText() })
    listOf(29, 30, 31, 32, 33, 42, 43, 44, 45, 46).map { n ->
        val r = o.getJSONObject("ROUTE_$n"); val g = r.getJSONArray("grid"); val t = r.getJSONArray("trees")
        RouteTrees("Route $n", r.getInt("w"), r.getInt("h"), (0 until g.length()).map { g.getString(it) },
            (0 until t.length()).map { val e = t.getJSONObject(it); Triple(e.getInt("x"), e.getInt("y"), e.getInt("score")) })
    }
} catch (_: Exception) { emptyList() }

/** Headbutt tree map: trees that are RARE for this save's trainer ID are highlighted (Heracross comes only from those). */
@Composable fun HeracrossTab(raw: ByteArray?, usedBackup: Boolean, open: (String) -> Unit) {
    val ctx = LocalContext.current
    val routes = remember { loadTrees(ctx) }
    var sel by remember { mutableStateOf(0) }
    val digit = if (SaveFacts.usable(raw) && !usedBackup) SaveFacts.trainerId(raw!!) % 10 else null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Heracross (Headbutt)", style = MaterialTheme.typography.titleMedium)
        Text(if (digit != null) "Your trainer ID ends in $digit, so the orange trees are RARE for you. Use Headbutt on those: 80% chance of a battle, 30% of those battles are Heracross (the rest Spearow or Aipom). Green trees are ordinary and almost never give anything good."
            else "Import a save to highlight your rare trees. Without a save every tree is shown as ordinary.", style = MaterialTheme.typography.bodySmall)
        Text("Gold and Silver only. Routes 29-33 and 42-46 use this tree set; Routes 33, 45 and 46 have no headbuttable trees in the map data.", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            routes.forEachIndexed { i, r -> FilterChip(selected = sel == i, onClick = { sel = i }, label = { Text(r.name.removePrefix("Route ") + " (" + (if (digit != null) r.trees.count { it.third == digit } else r.trees.size) + ")") }) }
        }
        routes.getOrNull(sel)?.let { r ->
            val rare = if (digit != null) r.trees.filter { it.third == digit } else emptyList()
            if (r.trees.isEmpty()) Text("No headbuttable trees on ${r.name}.", style = MaterialTheme.typography.bodyMedium)
            else {
                Text(if (digit != null) "${r.name}: ${rare.size} rare tree(s) out of ${r.trees.size}. North is up; grid lines every 5 squares." else "${r.name}: ${r.trees.size} trees. North is up; grid lines every 5 squares.", style = MaterialTheme.typography.bodySmall)
                androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().aspectRatio(r.w.toFloat() / r.h)) {
                    val cell = size.width / r.w
                    r.grid.forEachIndexed { y, row -> row.forEachIndexed { x, ch ->
                        val color = when (ch) {
                            '#' -> androidx.compose.ui.graphics.Color(0xFF3A3F42); '~' -> androidx.compose.ui.graphics.Color(0xFF2F5D8C)
                            '"' -> androidx.compose.ui.graphics.Color(0xFF3F7A45); 'T' -> androidx.compose.ui.graphics.Color(0xFF1F4A28)
                            'c' -> androidx.compose.ui.graphics.Color(0xFF6B6A2B); 'l' -> androidx.compose.ui.graphics.Color(0xFF7A5A3A)
                            else -> androidx.compose.ui.graphics.Color(0xFFC9D6C0)
                        }
                        drawRect(color, androidx.compose.ui.geometry.Offset(x * cell, y * cell), androidx.compose.ui.geometry.Size(cell, cell))
                    } }
                    for (i in 5 until maxOf(r.w, r.h) step 5) {
                        val line = androidx.compose.ui.graphics.Color(0x33000000)
                        if (i < r.w) drawLine(line, androidx.compose.ui.geometry.Offset(i * cell, 0f), androidx.compose.ui.geometry.Offset(i * cell, size.height))
                        if (i < r.h) drawLine(line, androidx.compose.ui.geometry.Offset(0f, i * cell), androidx.compose.ui.geometry.Offset(size.width, i * cell))
                    }
                    r.trees.forEach { (x, y, s) ->
                        val isRare = digit != null && s == digit
                        drawCircle(if (isRare) androidx.compose.ui.graphics.Color(0xFFFF9800) else androidx.compose.ui.graphics.Color(0xFF7FD68A),
                            if (isRare) cell * 0.9f else cell * 0.35f, androidx.compose.ui.geometry.Offset((x + 0.5f) * cell, (y + 0.5f) * cell))
                    }
                }
                if (rare.isNotEmpty()) Text("Rare tree squares (x across from the west edge, y down from the north edge, counting from 0): " + rare.sortedWith(compareBy({ it.second }, { it.first })).joinToString("; ") { "${it.first},${it.second}" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        TextButton(onClick = { open("https://rankedboost.com/pokemon/location/gold-silver/") }) { Text("RankedBoost locations") }
    }
}
