package dev.savequest.storage

import androidx.room.*

@Entity(tableName = "snapshots", indices = [Index(value = ["profile", "hash"], unique = true)])
data class Snapshot(@PrimaryKey val id: String, val profile: String, val hash: String, val importedAt: Long, val raw: ByteArray, val recovered: Boolean)
@Entity(tableName = "manual", primaryKeys = ["profile", "task"])
data class Manual(val profile: String, val task: String, val checked: Boolean, val updatedAt: Long, val origin: String = "manual")
@Dao interface ProgressDao {
    @Query("SELECT * FROM snapshots ORDER BY importedAt DESC, id DESC") suspend fun snapshots(): List<Snapshot>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(snapshot: Snapshot): Long
    @Query("SELECT * FROM manual") suspend fun manual(): List<Manual>
    @Upsert suspend fun set(manual: Manual)
}
@Database(entities = [Snapshot::class, Manual::class], version = 1, exportSchema = true)
abstract class SaveQuestDatabase : RoomDatabase() { abstract fun progress(): ProgressDao }
