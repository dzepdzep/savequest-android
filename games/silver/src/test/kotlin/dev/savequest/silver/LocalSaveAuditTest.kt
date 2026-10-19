package dev.savequest.silver

import dev.savequest.core.Task
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import dev.savequest.core.EvidenceKind

/** Opt-in local audit. Never check a player save or its derived results into Git. */
class LocalSaveAuditTest {
    @Test fun compareEveryCatalogEntryWithExternalBaseline() {
        val input = System.getenv("SAVEQUEST_AUDIT_SAVE")
        val cases = System.getenv("SAVEQUEST_AUDIT_CASES")
        assumeTrue("Local player-save audit is opt-in", input != null && cases != null)
        val raw = File(input!!).readBytes(); val before = raw.copyOf(); val parsed = Silver.parse(raw)
        val results = File(cases!!).readLines().map { line ->
            val v = line.split('\t')
            val task = Task(v[0],v[0],v[1], flags=v[4].split(',').filter { it.isNotEmpty() }.map { it.toInt() }, bagIndex=v[2].toInt().takeIf { it >= 0 }, dexNumber=v[3].toInt().takeIf { it >= 0 })
            val actual = parsed.evidence(task).kind.name
            assertEquals("Evidence mismatch for ${task.id}", v[5], actual)
            "${task.id}\t$actual"
        }
        assertEquals(725,results.size)
        assertArrayEquals(before,raw)
        // Reproduce a future consumed TM11 using a copy, never the player's file.
        val sunny = File(cases).readLines().first { it.startsWith("TM11\t") }.split('\t')
        val sunnyFlags = sunny[4].split(',').map { it.toInt() }
        val consumed = raw.copyOf(); consumed[0x23e6 + 10] = 0
        for (bit in sunnyFlags) consumed[0x261f + bit / 8] = (consumed[0x261f + bit / 8].toInt() or (1 shl (bit % 8))).toByte()
        val checksum = (0x2009..0x2d68).sumOf { consumed[it].toInt() and 255 }
        consumed[0x2d69] = checksum.toByte(); consumed[0x2d6a] = (checksum ushr 8).toByte()
        assertEquals(EvidenceKind.RECEIPT, Silver.parse(consumed).evidence(Task("TM11","Sunny Day","TM/HM",flags=sunnyFlags,bagIndex=10)).kind)
        // Recover the untouched real backup when only the primary block is damaged.
        val damaged = raw.copyOf(); damaged[0x2300] = (damaged[0x2300].toInt() xor 1).toByte()
        assertTrue(Silver.parse(damaged).usedBackup)
        assertEquals(parsed.trainer,Silver.parse(damaged).trainer)
        System.getenv("SAVEQUEST_AUDIT_OUTPUT")?.let { File(it).writeText("trainer\t${parsed.trainer}\nbadges\t${parsed.badges}\nbackup\t${parsed.usedBackup}\n"+results.joinToString("\n")) }
    }
}
