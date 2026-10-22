package dev.savequest.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.savequest.core.*

// All icons below are drawn in code (original shapes, no game art), so they ship in every build.
private val Johto = Color(0xFF5CC8B5)
private val Kanto = Color(0xFFF0A35E)
private val Neutral = Color(0xFF8FA6AB)

private fun regionColor(region: String) = when {
    region == "johto" -> Johto
    region == "kanto" -> Kanto
    region.contains("johto") && region.contains("kanto") -> Neutral
    else -> Neutral
}

@Composable fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium,
        modifier = modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 2.dp))
}

/** Real sprite if you generated one locally, otherwise an original icon drawn from the entry's kind. */
@Composable fun TaskIcon(task: Task, size: Dp = 44.dp) {
    val tint = regionColor(task.region)
    Box(Modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        when {
            task.sprite.isNotEmpty() && SpriteCache.has(task.sprite, "items") -> Sprite("items/${task.sprite}", size * 0.8f)
            task.dexNumber != null && SpriteCache.has("%03d".format(task.dexNumber), "pokemon") -> Sprite("pokemon/%03d".format(task.dexNumber), size * 0.95f)
            task.dexNumber != null -> Text("%03d".format(task.dexNumber), color = tint, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            else -> Canvas(Modifier.size(size * 0.6f)) { drawKind(task, tint) }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawKind(task: Task, c: Color) {
    val w = size.width; val h = size.height; val sw = w * 0.1f
    val line = Stroke(width = sw, cap = StrokeCap.Round)
    when {
        task.category == "Trainers" -> { // head + shoulders
            drawCircle(c, radius = w * 0.2f, center = Offset(w / 2, h * 0.3f))
            drawRoundRect(c, Offset(w * 0.12f, h * 0.58f), Size(w * 0.76f, h * 0.36f), CornerRadius(w * 0.3f))
        }
        task.category == "TM/HM" -> { // disc
            drawCircle(c, radius = w * 0.45f, center = Offset(w / 2, h / 2), style = line)
            drawCircle(c, radius = w * 0.14f, center = Offset(w / 2, h / 2))
        }
        task.kind == "ball" -> { // capsule ball
            drawCircle(c, radius = w * 0.45f, center = Offset(w / 2, h / 2), style = line)
            drawLine(c, Offset(w * 0.05f, h / 2), Offset(w * 0.95f, h / 2), strokeWidth = sw)
            drawCircle(c, radius = w * 0.13f, center = Offset(w / 2, h / 2))
        }
        task.kind == "hidden" -> { // magnifier
            drawCircle(c, radius = w * 0.28f, center = Offset(w * 0.42f, h * 0.42f), style = line)
            drawLine(c, Offset(w * 0.62f, h * 0.62f), Offset(w * 0.92f, h * 0.92f), strokeWidth = sw * 1.4f, cap = StrokeCap.Round)
        }
        task.kind == "gift" -> { // gift box
            drawRoundRect(c, Offset(w * 0.1f, h * 0.38f), Size(w * 0.8f, h * 0.52f), CornerRadius(w * 0.08f), style = line)
            drawLine(c, Offset(w * 0.1f, h * 0.55f), Offset(w * 0.9f, h * 0.55f), strokeWidth = sw)
            drawLine(c, Offset(w / 2, h * 0.38f), Offset(w / 2, h * 0.9f), strokeWidth = sw)
            drawCircle(c, radius = w * 0.12f, center = Offset(w * 0.38f, h * 0.27f), style = line)
            drawCircle(c, radius = w * 0.12f, center = Offset(w * 0.62f, h * 0.27f), style = line)
        }
        task.kind == "shop" -> { // coin
            drawCircle(c, radius = w * 0.45f, center = Offset(w / 2, h / 2), style = line)
            drawLine(c, Offset(w * 0.5f, h * 0.25f), Offset(w * 0.5f, h * 0.75f), strokeWidth = sw)
            drawLine(c, Offset(w * 0.34f, h * 0.38f), Offset(w * 0.66f, h * 0.38f), strokeWidth = sw)
            drawLine(c, Offset(w * 0.34f, h * 0.58f), Offset(w * 0.66f, h * 0.58f), strokeWidth = sw)
        }
        else -> drawCircle(c, radius = w * 0.3f, center = Offset(w / 2, h / 2))
    }
}

private fun kindLabel(task: Task) = when (task.kind) {
    "gift" -> "NPC gift"
    "ball" -> "Item ball"
    "hidden" -> "Hidden"
    "shop" -> "Shop"
    "prize" -> "Game Corner"
    "found" -> "Found"
    else -> ""
}

@Composable fun SectionHeader(text: String, done: Int, total: Int, expanded: Boolean = true, onClick: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).testTag("section:$text")
        .clickable(enabled = onClick != null, onClickLabel = if (expanded) "Collapse $text" else "Expand $text") { onClick?.invoke() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (expanded) "▾" else "▸", color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(20.dp))
            Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Text("$done/$total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ProgressBar(if (total == 0) 0f else done.toFloat() / total, Modifier.padding(top = 4.dp))
    }
}

@Composable fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary))
    }
}

@Composable fun TaskCard(
    task: Task, evidence: Evidence, manualChecked: Boolean, canEdit: Boolean, region: String,
    onManual: (Boolean) -> Unit, openUrl: (String) -> Unit,
) {
    var open by remember(task.id) { mutableStateOf(false) }
    val done = evidence.kind != EvidenceKind.UNKNOWN
    val scheme = MaterialTheme.colorScheme
    val subtitle = listOf(task.location.takeIf { it.isNotBlank() && task.wild.isEmpty() }, task.area.takeIf { it.isNotBlank() && task.location.isBlank() })
        .firstOrNull { it != null } ?: if (task.wild.isNotEmpty()) "${task.wild.size} place(s)" else ""
    val preview = when {
        task.howto.isNotEmpty() -> task.howto.lineSequence().first().removePrefix("Where: ")
        task.wild.isNotEmpty() -> task.wild.first().split("\t").let { "${it[0]} · ${it[2]}" }
        task.category == "Trainers" && task.detail.contains(" / ") -> "Team of ${task.detail.split(" / ").size}: " + task.detail
        else -> task.detail
    }
    Card(Modifier.fillMaxWidth().clickable { open = !open },
        colors = CardDefaults.cardColors(containerColor = if (done) scheme.primary.copy(alpha = 0.10f).compositeOver(scheme.surface) else scheme.surface)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TaskIcon(task)
                Column(Modifier.weight(1f)) {
                    Text(task.title, style = MaterialTheme.typography.titleMedium, maxLines = if (open) 4 else 2, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Checkbox(modifier = Modifier.testTag("manual:${task.id}"), checked = manualChecked, enabled = canEdit, onCheckedChange = onManual)
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(if (done) "✔ " + evidence.kind.name.lowercase().replace('_', ' ') else "not confirmed", if (done) scheme.primary else scheme.onSurfaceVariant)
                kindLabel(task).takeIf { it.isNotEmpty() }?.let { Pill(it, Neutral) }
                task.region.split(",").filter { it.isNotEmpty() }.forEach { Pill(it.replaceFirstChar { c -> c.uppercase() }, regionColor(it)) }
            }
            if (!open && preview.isNotBlank()) Text(preview, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            if (open) {
                Spacer(Modifier.height(8.dp))
                Text(evidence.detail, style = MaterialTheme.typography.bodySmall, color = scheme.primary)
                if (task.howto.isNotEmpty()) {
                    Text("How to get it", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    task.howto.split("\n").forEach { line ->
                        val label = line.substringBefore(": ", "")
                        if (label.length in 2..6 && line.contains(": ")) Text(buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = scheme.primary)) { append("$label  ") }; append(line.substringAfter(": ")) }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                        else Text(line, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                    }
                } else if (task.category == "Trainers" && task.detail.contains(" / ")) {
                    val team = task.detail.split(" / ")
                    Text("Team of ${team.size}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    team.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                } else if (task.detail.isNotBlank()) Text(task.detail, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                if (task.spot.isNotEmpty()) Text("Spot hint: ${task.spot}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                if (task.wild.isNotEmpty()) {
                    val lines = task.wild.map { it.split("\t") }.filter { l -> region == "All" || l[1].isEmpty() || l[1].equals(region, true) }
                    Text(if (task.category == "Pokédex") "Where to catch it" else "Places", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    lines.forEach { l -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${l[0]} · ${l[2]}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { openUrl(l[3]) }) { Text("Map") }
                    } }
                }
                Row(Modifier.padding(top = 4.dp)) {
                    // one RankedBoost button per card; entries with a list of places already have one per line
                    if (task.rankedboost.isNotEmpty() && task.wild.isEmpty()) TextButton(onClick = { openUrl(task.rankedboost) }) { Text("RankedBoost") }
                    if (task.guide.isNotEmpty()) TextButton(onClick = { openUrl(task.guide) }) { Text("Source") }
                }
            }
        }
    }
}
