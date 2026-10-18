package dev.savequest.core

enum class EvidenceKind { BAG, RECEIPT, SAVE_FLAG, HISTORICAL, MANUAL, UNKNOWN }
data class Evidence(val kind: EvidenceKind, val detail: String)
data class Task(val id: String, val title: String, val category: String, val location: String = "", val detail: String = "", val guide: String = "", val flags: List<Int> = emptyList(), val bagIndex: Int? = null, val dexNumber: Int? = null)
interface ParsedSave { val profile: String; val trainer: String; val usedBackup: Boolean; fun evidence(task: Task): Evidence }
interface GameModule { val id: String; val title: String; fun parse(bytes: ByteArray): ParsedSave }
object Progress {
    fun resolve(current: Evidence, historical: Boolean, manual: Boolean): Evidence = when {
        current.kind != EvidenceKind.UNKNOWN -> current
        manual -> Evidence(EvidenceKind.MANUAL, "Manually confirmed; independent of save imports")
        historical -> Evidence(EvidenceKind.HISTORICAL, "Confirmed in an earlier imported snapshot; may differ on this timeline")
        else -> current
    }
}
