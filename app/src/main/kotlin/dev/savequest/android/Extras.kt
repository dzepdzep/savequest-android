package dev.savequest.android

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
