package dev.savequest.android

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.room.withTransaction
import androidx.work.*
import dev.savequest.core.Task
import dev.savequest.silver.Silver
import dev.savequest.storage.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import android.util.Base64

class Repository internal constructor(private val context: Context, private val databaseName: String = "savequest.db") {
    val db = Room.databaseBuilder(context, SaveQuestDatabase::class.java, databaseName).build()
    val prefs = context.getSharedPreferences("source:$databaseName", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    val tasks: List<Task> by lazy {
        val a = JSONObject(context.assets.open("catalog.json").bufferedReader().use { it.readText() }).getJSONArray("tasks")
        (0 until a.length()).map { i -> val t = a.getJSONObject(i); val f = t.optJSONArray("flags") ?: JSONArray()
            Task(t.getString("id"), t.getString("title"), t.getString("category"), t.optString("location"), t.optString("detail"), t.optString("guide"), (0 until f.length()).map { f.getInt(it) }, if (t.has("bagIndex")) t.getInt("bagIndex") else null, if (t.has("dexNumber")) t.getInt("dexNumber") else null, t.optString("region"), t.optString("area"), t.optString("rankedboost"), t.optString("sprite"), t.optString("spot"), t.optJSONArray("wild")?.let { w -> (0 until w.length()).map { w.getString(it) } } ?: emptyList(), t.optString("kind"), t.optString("howto"))
        }
    }
    suspend fun importSave(uri: Uri, expectedProfile: String? = null, linkSource: Boolean? = null): String = mutex.withLock {
        importLocked(uri, expectedProfile, linkSource)
    }
    private suspend fun importLocked(uri: Uri, expectedProfile: String?, linkSource: Boolean?): String {
        val raw = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytesLimited(32817) } ?: error("Cannot open this document") }
        val parsed = Silver.parse(raw)
        require(expectedProfile == null || parsed.profile == expectedProfile) { "The linked file now belongs to another trainer. Select it again explicitly to switch profiles." }
        val hash = hash(raw)
        val added = db.progress().insert(Snapshot(UUID.randomUUID().toString(), parsed.profile, hash, System.currentTimeMillis(), raw, parsed.usedBackup))
        prefs.edit().apply {
            putString("currentHash:${parsed.profile}", hash)
            if (expectedProfile == null) putString("active", parsed.profile)
            if (linkSource != null) {
                putString("uri", if (linkSource) uri.toString() else null)
                putString("linkedProfile", if (linkSource) parsed.profile else null)
            }
        }.apply()
        return "${parsed.trainer}: " + (if (added == -1L) "save unchanged" else "snapshot saved") + if (parsed.usedBackup) " (recovered backup block)" else ""
    }
    suspend fun refresh(): String = mutex.withLock {
        val uri = prefs.getString("uri", null) ?: return@withLock "No linked save"
        val profile = prefs.getString("linkedProfile", null) ?: error("Select the save again to establish its trainer identity")
        importLocked(Uri.parse(uri), profile, null)
    }
    fun autoRefresh(enabled: Boolean) {
        prefs.edit().putBoolean("auto", enabled).apply()
        val manager = WorkManager.getInstance(context)
        if (enabled) manager.enqueueUniquePeriodicWork("save-refresh", ExistingPeriodicWorkPolicy.UPDATE, PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build())
        else manager.cancelUniqueWork("save-refresh")
    }
    suspend fun export(uri: Uri) = mutex.withLock {
        val data = db.withTransaction {
            JSONObject().put("format", "savequest-backup-v2").put("active", prefs.getString("active", "")).put("current", JSONObject().apply {
                db.progress().snapshots().distinctBy { it.profile }.forEach { s -> prefs.getString("currentHash:${s.profile}", null)?.let { put(s.profile,it) } }
            }).put("snapshots", JSONArray().apply {
                db.progress().snapshots().forEach { put(JSONObject().put("raw", Base64.encodeToString(it.raw, Base64.NO_WRAP)).put("at", it.importedAt)) }
            }).put("manual", JSONArray().apply {
                db.progress().manual().forEach { put(JSONObject().put("profile", it.profile).put("task", it.task).put("checked", it.checked).put("at", it.updatedAt).put("origin", it.origin)) }
            })
        }
        withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(data.toString(2)) } ?: error("Cannot write backup") }
    }
    suspend fun restore(uri: Uri) = mutex.withLock {
        val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytesLimited(32 * 1024 * 1024) } ?: error("Cannot open backup") }
        val data = JSONObject(bytes.toString(Charsets.UTF_8))
        require(data.getString("format") in setOf("savequest-backup-v1", "savequest-backup-v2")) { "Unsupported backup format" }
        val s = data.getJSONArray("snapshots"); val m = data.getJSONArray("manual")
        val snapshots = (0 until s.length()).map { val o = s.getJSONObject(it); val raw = Base64.decode(o.getString("raw"), Base64.DEFAULT); val p = Silver.parse(raw); Snapshot(UUID.randomUUID().toString(), p.profile, hash(raw), o.getLong("at"), raw, p.usedBackup) }
        val manual = (0 until m.length()).map { val o = m.getJSONObject(it); Manual(o.getString("profile"), o.getString("task"), o.getBoolean("checked"), o.getLong("at"), o.getString("origin")) }.groupBy { it.profile to it.task }.values.map { entries -> entries.maxBy { it.updatedAt } }
        require(snapshots.all { it.importedAt >= 0 } && manual.all { it.updatedAt >= 0 && it.profile.isNotBlank() && it.task.isNotBlank() }) { "Invalid backup metadata" }
        db.withTransaction {
            snapshots.forEach { db.progress().insert(it) }
            val existing = db.progress().manual().associateBy { it.profile to it.task }
            manual.filter { (existing[it.profile to it.task]?.updatedAt ?: Long.MIN_VALUE) < it.updatedAt }.forEach { db.progress().set(it) }
        }
        // Restore view state only where there is no existing selection. Merging cannot roll back a live profile.
        val current = data.optJSONObject("current") ?: JSONObject()
        val all = db.progress().snapshots()
        prefs.edit().apply {
            current.keys().forEach { profile ->
                val hash = current.getString(profile)
                if (!prefs.contains("currentHash:$profile") && all.any { it.profile == profile && it.hash == hash }) putString("currentHash:$profile", hash)
            }
            val active = data.optString("active")
            if (!prefs.contains("active") && all.any { it.profile == active }) putString("active",active)
        }.apply()
    }
    private fun hash(raw: ByteArray) = MessageDigest.getInstance("SHA-256").digest(raw.copyOf(32768)).joinToString("") { "%02x".format(it) }
    companion object {
        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository = instance ?: synchronized(this) { instance ?: Repository(context.applicationContext).also { instance = it } }
    }
}
private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (true) { val n = read(buffer); if (n < 0) break; require(out.size() + n <= limit) { "File is too large" }; out.write(buffer, 0, n) }
    return out.toByteArray()
}
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = Repository.get(applicationContext)
        if (!repo.prefs.getBoolean("auto", false)) return Result.success()
        return try { val message = repo.refresh(); repo.prefs.edit().putString("refreshStatus", message).apply(); Result.success() }
        catch (e: Exception) { repo.prefs.edit().putString("refreshStatus", "Last refresh failed: ${e.message}. Last valid snapshot retained; Android will retry on the next scheduled check.").apply(); Result.success() }
    }
}
