package dev.savequest.android

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.savequest.core.*
import dev.savequest.silver.Silver
import dev.savequest.storage.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private const val COLLAPSED_KEY = "collapsed:Items"

class MainActivity : ComponentActivity() {
    private val repo by lazy { Repository.get(this) }
    private var snapshots by mutableStateOf<List<Snapshot>>(emptyList())
    private var manuals by mutableStateOf<List<Manual>>(emptyList())
    private var message by mutableStateOf("Choose an English original Silver battery save to begin.")
    private var busy by mutableStateOf(false)
    private var active by mutableStateOf("")
    private var auto by mutableStateOf(false)
    private val refreshListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key?.startsWith("currentHash:") == true || key == "refreshStatus") lifecycleScope.launch {
            reload()
            if (key == "refreshStatus") message = repo.prefs.getString("refreshStatus", message) ?: message
        }
    }
    override fun onStart() { super.onStart(); repo.prefs.registerOnSharedPreferenceChangeListener(refreshListener) }
    override fun onStop() { repo.prefs.unregisterOnSharedPreferenceChangeListener(refreshListener); super.onStop() }
    private val savePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) launchAction {
        var persistent = false
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); persistent = true } catch (_: SecurityException) { }
        message = repo.importSave(uri, linkSource = persistent)
        if (!persistent) { repo.autoRefresh(false); auto = false; message += "; this provider permits one-time import only" }
    } }
    private val exportPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri != null) launchAction { repo.export(uri); message = "Backup exported: snapshots and manual progress" } }
    private val restorePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) launchAction { repo.restore(uri); message = "Backup merged; newer manual entries preserved" } }
    private fun launchAction(action: suspend () -> Unit) { if (busy) return; lifecycleScope.launch {
        busy = true
        try { action(); reload() } catch (e: Exception) { message = e.message ?: "Operation failed; existing progress retained" }
        finally { busy = false }
    } }
    private suspend fun reload() {
        snapshots = repo.db.progress().snapshots(); manuals = repo.db.progress().manual()
        active = repo.prefs.getString("active", "") ?: ""
        if (snapshots.none { it.profile == active }) active = snapshots.firstOrNull()?.profile ?: ""
    }
    override fun onResume() { super.onResume(); launchAction { reload(); auto = repo.prefs.getBoolean("auto", false); if (auto) message = repo.refresh() } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF7FDCC4), onPrimary = Color(0xFF00382D), background = Color(0xFF0E1A1D), surface = Color(0xFF172629), surfaceVariant = Color(0xFF263A3E), onSurfaceVariant = Color(0xFFB4C6C9))) { Surface(Modifier.fillMaxSize()) { Screen() } } }
    }
    /** Saves one manual checkbox change, then offers Undo. The change object (not the row) is what Undo uses, so it still works when the row has left the list. */
    private fun changeManual(change: ManualChange, title: String, host: SnackbarHostState, scope: CoroutineScope) {
        if (busy || change.profile.isEmpty()) return
        launchAction {
            repo.db.progress().set(Manual(change.profile, change.taskId, change.after, System.currentTimeMillis()))
            scope.launch {
                host.currentSnackbarData?.dismiss()
                val result = host.showSnackbar(message = if (change.after) "Marked \"$title\" as done manually" else "Unmarked \"$title\"", actionLabel = "Undo", withDismissAction = true, duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) undoManual(change.undo())
            }
        }
    }
    private fun undoManual(change: ManualChange) { lifecycleScope.launch {
        try { repo.db.progress().set(Manual(change.profile, change.taskId, change.after, System.currentTimeMillis())); reload() }
        catch (e: Exception) { message = e.message ?: "Undo failed; the earlier state was kept" }
    } }
    private fun openUrl(url: String) { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { message = "No browser available" } }
    @Composable private fun Screen() {
        var category by rememberSaveable { mutableStateOf("TM/HM") }
        // four bottom tabs; `category` stays the detailed page, the tab is derived from it
        val tab = when (category) { in CHECKLIST_PAGES -> "Checklist"; "Team" -> "Team"; "Pokémon" -> "Pokémon"; else -> "More" }
        // saved with the screen state so rotating the phone keeps the search and every filter
        var query by rememberSaveable { mutableStateOf("") }
        var incomplete by rememberSaveable { mutableStateOf(false) }
        var region by rememberSaveable { mutableStateOf("All") }
        var kind by rememberSaveable { mutableStateOf("All") }
        var whenDay by rememberSaveable { mutableStateOf("") }
        var whenTime by rememberSaveable { mutableStateOf("") }
        var rbOpen by rememberSaveable { mutableStateOf(false) }
        var rbUrl by rememberSaveable { mutableStateOf(RB_LOCATIONS) }
        var selectedSnapshot by remember(active) { mutableStateOf<String?>(null) }
        val history = remember(snapshots, active) { snapshots.filter { it.profile == active } }
        val selected = history.find { it.id == selectedSnapshot } ?: history.find { it.hash == repo.prefs.getString("currentHash:$active", null) } ?: history.firstOrNull()
        val parsed = remember(selected) { selected?.let { Silver.parse(it.raw) } }
        val earlier = remember(history, selected) { history.filter { it.importedAt < (selected?.importedAt ?: 0) }.map { Silver.parse(it.raw) } }
        val manual = manuals.filter { it.profile == active }.associateBy { it.task }
        val results = remember(selected, manuals, active, history) { repo.tasks.associate { task ->
            val current = parsed?.evidence(task) ?: Evidence(EvidenceKind.UNKNOWN, "Import a save to detect progress")
            task.id to Progress.resolve(current, earlier.any { it.evidence(task).kind != EvidenceKind.UNKNOWN }, manual[task.id]?.checked == true)
        } }
        fun isDone(t: Task) = results[t.id]?.kind != EvidenceKind.UNKNOWN
        // HMs the save shows you own (bag or receipt evidence); the advisor only nags about HMs you can actually use
        val ownedHms = listOf("Cut", "Fly", "Surf", "Strength", "Flash", "Whirlpool", "Waterfall").filterIndexed { i, _ -> results["HM0${i + 1}"]?.kind.let { it != null && it != EvidenceKind.UNKNOWN } }.toSet()
        // progress is counted on `base` (every filter except "Show unconfirmed only"); `visible` is what gets listed
        val base = repo.tasks.filter { Checklist.matches(it, category, kind, region, query, WhenFilter(whenDay, whenTime)) }
        val visible = Checklist.visibleRows(base, ::isDone, incomplete)
        val sections = if (category == "Items") Checklist.sections(base, ::isDone, incomplete) else emptyList()
        val searching = query.isNotBlank()
        var collapsed by remember { mutableStateOf(repo.prefs.getStringSet(COLLAPSED_KEY, null)?.toSet() ?: emptySet()) }
        fun setCollapsed(next: Set<String>) { collapsed = next; repo.prefs.edit().putStringSet(COLLAPSED_KEY, HashSet(next)).apply() }
        val snack = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val card: @Composable (Task) -> Unit = { task ->
            TaskCard(task, results.getValue(task.id), manual[task.id]?.checked == true, active.isNotEmpty() && !busy, region,
                onManual = { checked -> changeManual(ManualChange(active, task.id, manual[task.id]?.checked == true, checked), task.title, snack, scope) },
                openUrl = { openUrl(it) })
        }
        Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding().testTag("catalog"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          item { Column {
            var menu by remember { mutableStateOf(false) }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("SaveQuest", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Pokémon Silver · early access", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    TextButton(onClick = { menu = true }) { Text("More ▾") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Export backup") }, enabled = !busy, onClick = { menu = false; exportPicker.launch("savequest-backup.json") })
                        DropdownMenuItem(text = { Text("Restore backup") }, enabled = !busy, onClick = { menu = false; restorePicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
                        DropdownMenuItem(text = { Text("Silver walkthrough") }, onClick = { menu = false; openUrl("https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Gold_and_Silver") })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = { savePicker.launch(arrayOf("*/*")) }) { Text("Import save") }
                OutlinedButton(enabled = !busy && repo.prefs.contains("uri"), onClick = { launchAction { message = repo.refresh() } }) { Text("Refresh") }
            }
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
            if (parsed != null) Card(Modifier.fillMaxWidth().padding(top = 8.dp)) { Column(Modifier.padding(12.dp)) {
                Text(parsed.trainer, style = MaterialTheme.typography.titleMedium)
                Text("${parsed.badges}/16 badges · ${parsed.caughtCount} caught · ${parsed.seenCount} seen · ${history.size} snapshots${if (parsed.usedBackup) " · backup recovered" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ProgressBar(parsed.caughtCount / 251f, Modifier.padding(top = 8.dp))
            } }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(checked = auto, enabled = repo.prefs.getString("uri", null) != null, onCheckedChange = { auto = it; repo.autoRefresh(it) }); Text("Auto refresh linked file (on return + about every 15 min)", style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                snapshots.distinctBy { it.profile }.forEach { s -> FilterChip(selected = active == s.profile, onClick = { active = s.profile; repo.prefs.edit().putString("active", active).apply() }, label = { Text(Silver.parse(s.raw).trainer + " · " + s.profile.takeLast(4)) }) }
            }
            val pages = when (tab) { "Checklist" -> CHECKLIST_PAGES; "More" -> MORE_PAGES; else -> emptyList() }
            if (pages.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pages.forEach { name -> val total = repo.tasks.count { it.category == name }; val done = if (total > 0) repo.tasks.count { it.category == name && results[it.id]?.kind != EvidenceKind.UNKNOWN } else 0; FilterChip(selected = category == name, onClick = { category = name; kind = "All" }, label = { Text(if (total > 0 && parsed != null) "$name $done/$total" else name) }) }
            }
          } }
            if (category == "Team") {
                item { TeamTab(selected?.raw, parsed?.usedBackup == true, repo.tasks.filter { it.dexNumber != null }.sortedBy { it.dexNumber }.map { it.title.substringAfter(" · ") }, ownedHms) { openUrl(it) } }
            } else if (category == "Pokémon") {
                item { PokemonTab(selected?.raw, parsed?.usedBackup == true, repo.tasks.filter { it.dexNumber != null }.sortedBy { it.dexNumber }.map { it.title.substringAfter(" · ") }) }
            } else if (category == "Heracross") {
                item { HeracrossTab(selected?.raw, parsed?.usedBackup == true) { openUrl(it) } }
            } else if (category == "Tips") {
                item { TipsTab(selected?.raw, parsed?.usedBackup == true) { openUrl(it) } }
            } else if (category == "RankedBoost") {
                item { RankedBoostTab(selected != null) { rbUrl = it; rbOpen = true } }
            } else if (category == "History") {
                item { Text("Viewing a snapshot keeps all imports. Manual confirmations apply to this profile today, not to the historical date. Historical save evidence uses only earlier imports. Import time is not in-game chronology.", style = MaterialTheme.typography.bodySmall) }
                items(history, key = { it.id }) { s -> OutlinedButton(onClick = { selectedSnapshot = s.id; category = "TM/HM" }, modifier = Modifier.fillMaxWidth()) { Text(DateFormat.getDateTimeInstance().format(Date(s.importedAt)) + " · " + s.hash.take(8)) } }
            } else {
              item { Column {
                if (selectedSnapshot != null) TextButton(onClick = { selectedSnapshot = null }) { Text("Viewing history · return to current save") }
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search name or location") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (category == "Items" || category == "TM/HM") Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (if (category == "Items") listOf("All" to "All types", "gift" to "NPC gifts", "ball" to "Item balls (overworld)", "hidden" to "Hidden (Itemfinder)", "shop" to "Can be bought")
                     else listOf("All" to "All", "shop" to "Can be bought", "prize" to "Game Corner prize", "found" to "Found / earned")).forEach { (k, label) -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(label) }) }
                }
                if (category in setOf("Items", "Pokédex", "Trainers", "TM/HM")) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("All", "Johto", "Kanto").forEach { r -> FilterChip(selected = region == r, onClick = { region = r }, label = { Text(r) }) }
                }
                if (category in setOf("Items", "Pokédex", "TM/HM")) {
                    // weekday / time-of-day limits come from the game data; "today" and "now" use the phone clock (the game uses the cartridge clock)
                    val nowCal = java.util.Calendar.getInstance()
                    val today = GameClock.dayName(nowCal.get(java.util.Calendar.DAY_OF_WEEK)); val nowWindow = GameClock.windowOf(nowCal.get(java.util.Calendar.HOUR_OF_DAY))
                    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Day", style = MaterialTheme.typography.labelMedium)
                        FilterChip(selected = whenDay.isEmpty(), onClick = { whenDay = "" }, label = { Text("Any") })
                        FilterChip(selected = whenDay == today, onClick = { whenDay = if (whenDay == today) "" else today }, label = { Text("Today · $today") })
                        GameClock.DAYS.filter { it != today }.forEach { d -> FilterChip(selected = whenDay == d, onClick = { whenDay = if (whenDay == d) "" else d }, label = { Text(d) }) }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Time", style = MaterialTheme.typography.labelMedium)
                        FilterChip(selected = whenTime.isEmpty(), onClick = { whenTime = "" }, label = { Text("Any") })
                        FilterChip(selected = whenTime == nowWindow, onClick = { whenTime = if (whenTime == nowWindow) "" else nowWindow }, label = { Text("Now · $nowWindow") })
                        GameClock.TIMES.filter { it != nowWindow }.forEach { t -> FilterChip(selected = whenTime == t, onClick = { whenTime = if (whenTime == t) "" else t }, label = { Text(t.replaceFirstChar { c -> c.uppercase() }) }) }
                    }
                    if (whenDay.isNotEmpty() || whenTime.isNotEmpty()) Text("Showing only entries limited to " + listOf(whenDay.takeIf { it.isNotEmpty() }, whenTime.takeIf { it.isNotEmpty() }).filterNotNull().joinToString(" · ") + ". Pokémon appear under a time if they have a spawn that only exists then.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row { Checkbox(checked = incomplete, onCheckedChange = { incomplete = it }); Text("Show unconfirmed only", modifier = Modifier.padding(top = 12.dp)) }
                if (category == "Items") Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    val listed = sections.map { it.key }
                    TextButton(enabled = !searching && listed.isNotEmpty(), modifier = Modifier.testTag("items:expandAll"), onClick = { setCollapsed(Checklist.expandAll(collapsed, listed)) }) { Text("Expand all") }
                    TextButton(enabled = !searching && listed.isNotEmpty(), modifier = Modifier.testTag("items:collapseAll"), onClick = { setCollapsed(Checklist.collapseAll(collapsed, listed)) }) { Text("Collapse all") }
                    if (searching) Text("Search is showing every match; folded locations come back when you clear it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val found = base.count { isDone(it) }
                Text("$found of ${base.size} confirmed" + (if (incomplete) " · listing ${visible.size} unconfirmed" else "") + " · evidence is shown separately from manual checks", style = MaterialTheme.typography.bodySmall)
              } }
                    if (category == "Items") sections.forEach { sec ->
                        val open = Checklist.isExpanded(sec.key, collapsed, searching)
                        item(key = "h:${sec.key}") { SectionHeader(sec.key, sec.done, sec.total, open, if (searching) null else ({ setCollapsed(Checklist.toggled(collapsed, sec.key)) })) }
                        if (open) items(sec.shown, key = { it.id }) { task -> card(task) }
                    } else items(visible, key = { it.id }) { task -> card(task) }
            }
        }
        if (rbOpen) Surface(Modifier.fillMaxSize()) { RankedBoostScreen(selected?.raw, rbUrl, { rbUrl = it }, { rbOpen = false }) }
        SnackbarHost(snack, Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(8.dp))
        }
        NavigationBar(Modifier.testTag("tabs")) {
            listOf("Checklist" to "TM/HM", "Team" to "Team", "Pokémon" to "Pokémon", "More" to "Heracross").forEach { (name, first) ->
                NavigationBarItem(
                    selected = tab == name,
                    onClick = { rbOpen = false; if (tab != name) { category = first; kind = "All" } },
                    icon = { Text(TAB_MARK.getValue(name), style = MaterialTheme.typography.titleMedium) },
                    label = { Text(name) },
                )
            }
        }
        }
    }
}

private val CHECKLIST_PAGES = listOf("TM/HM", "Trainers", "Items", "Pokédex")
private val MORE_PAGES = listOf("Heracross", "Tips", "RankedBoost", "History")
private val TAB_MARK = mapOf("Checklist" to "✔", "Team" to "★", "Pokémon" to "●", "More" to "≡")

