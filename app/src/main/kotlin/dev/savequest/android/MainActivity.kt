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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.savequest.core.*
import dev.savequest.silver.Silver
import dev.savequest.storage.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

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
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFA8DDB5), background = Color(0xFF101C20), surface = Color(0xFF19292D))) { Surface(Modifier.fillMaxSize()) { Screen() } } }
    }
    @Composable private fun Screen() {
        var category by remember { mutableStateOf("TM/HM") }
        var query by remember { mutableStateOf("") }
        var incomplete by remember { mutableStateOf(false) }
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
        val visible = repo.tasks.filter { it.category == category && (!incomplete || results[it.id]?.kind == EvidenceKind.UNKNOWN) && "${it.title} ${it.location} ${it.detail}".contains(query, true) }
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().testTag("catalog"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          item { Column {
            Text("SaveQuest", style = MaterialTheme.typography.headlineLarge)
            Text("Pokémon Silver · original English edition", style = MaterialTheme.typography.titleMedium)
            Text("Early access · partial completion catalog", color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = { savePicker.launch(arrayOf("*/*")) }) { Text("Import save") }
                OutlinedButton(enabled = !busy && repo.prefs.contains("uri"), onClick = { launchAction { message = repo.refresh() } }) { Text("Refresh") }
                OutlinedButton(enabled = !busy, onClick = { exportPicker.launch("savequest-backup.json") }) { Text("Export backup") }
                OutlinedButton(enabled = !busy, onClick = { restorePicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("Restore backup") }
                OutlinedButton(onClick = { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://bulbapedia.bulbagarden.net/wiki/Walkthrough:Pok%C3%A9mon_Gold_and_Silver"))) } catch (_: Exception) { message = "No browser available" } }) { Text("Silver walkthrough") }
            }
            Text(message, style = MaterialTheme.typography.bodySmall)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row { Checkbox(checked = auto, enabled = repo.prefs.getString("uri", null) != null, onCheckedChange = { auto = it; repo.autoRefresh(it) }); Text("Auto refresh linked file\nOn return + best effort every 15+ minutes", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            if (parsed != null) Text("${parsed.trainer} · ${parsed.badges}/16 badges · ${parsed.caughtCount} caught · ${parsed.seenCount} seen · ${history.size} snapshots${if (parsed.usedBackup) " · backup recovered" else ""}")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                snapshots.distinctBy { it.profile }.forEach { s -> FilterChip(selected = active == s.profile, onClick = { active = s.profile; repo.prefs.edit().putString("active", active).apply() }, label = { Text(Silver.parse(s.raw).trainer + " · " + s.profile.takeLast(4)) }) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("TM/HM", "Trainers", "Items", "Pokédex", "History").forEach { name -> FilterChip(selected = category == name, onClick = { category = name }, label = { Text(name) }) }
            }
          } }
            if (category == "History") {
                item { Text("Viewing a snapshot keeps all imports. Manual confirmations apply to this profile today, not to the historical date. Historical save evidence uses only earlier imports. Import time is not in-game chronology.", style = MaterialTheme.typography.bodySmall) }
                items(history, key = { it.id }) { s -> OutlinedButton(onClick = { selectedSnapshot = s.id; category = "TM/HM" }, modifier = Modifier.fillMaxWidth()) { Text(DateFormat.getDateTimeInstance().format(Date(s.importedAt)) + " · " + s.hash.take(8)) } }
            } else {
              item { Column {
                if (selectedSnapshot != null) TextButton(onClick = { selectedSnapshot = null }) { Text("Viewing history · return to current save") }
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search name or location") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row { Checkbox(checked = incomplete, onCheckedChange = { incomplete = it }); Text("Show unconfirmed only", modifier = Modifier.padding(top = 12.dp)) }
                Text("${visible.size} entries · evidence is shown separately from manual checks", style = MaterialTheme.typography.bodySmall)
              } }
                    items(visible, key = { it.id }) { task ->
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                            Text(task.title, style = MaterialTheme.typography.titleMedium)
                            if (task.location.isNotBlank()) Text(task.location, style = MaterialTheme.typography.bodyMedium)
                            val evidence = results.getValue(task.id)
                            Text("${evidence.kind.name.lowercase().replaceFirstChar { it.uppercase() }} · ${evidence.detail}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                            Text(task.detail, style = MaterialTheme.typography.bodySmall)
                            Row {
                                Checkbox(modifier = Modifier.testTag("manual:${task.id}"), checked = manual[task.id]?.checked == true, enabled = active.isNotEmpty() && !busy, onCheckedChange = { checked -> launchAction { repo.db.progress().set(Manual(active, task.id, checked, System.currentTimeMillis())); message = "Manual confirmation saved separately" } })
                                Text("Manual confirmation", modifier = Modifier.padding(top = 12.dp))
                                if (task.guide.isNotEmpty()) TextButton(onClick = { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(task.guide))) } catch (_: Exception) { message = "No browser available" } }) { Text("Source") }
                            }
                        } }
                    }
            }
        }
    }
}

