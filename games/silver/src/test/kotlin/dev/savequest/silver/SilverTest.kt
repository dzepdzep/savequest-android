package dev.savequest.silver

import dev.savequest.core.*
import org.junit.Assert.*
import org.junit.Test

class SilverTest {
    private fun fixture(change: (ByteArray) -> Unit = {}): ByteArray {
        val b = ByteArray(32768)
        b[0x2008] = 99; b[0x2d6b] = 127
        b[0x2009] = 1; b[0x200a] = 2
        b[0x200b] = 0x93.toByte(); b[0x200c] = 0x50
        change(b)
        val sum = (0x2009..0x2d68).sumOf { b[it].toInt() and 255 } and 65535
        b[0x2d69] = sum.toByte(); b[0x2d6a] = (sum ushr 8).toByte()
        return b
    }
    private val tm = Task("TM01", "DynamicPunch", "TM/HM", flags = listOf(42), bagIndex = 0)
    @Test fun bagAndConsumedReceiptAreDifferentEvidence() {
        assertEquals(EvidenceKind.BAG, Silver.parse(fixture { it[0x23e6] = 2 }).evidence(tm).kind)
        assertEquals(EvidenceKind.RECEIPT, Silver.parse(fixture { it[0x261f + 42/8] = (1 shl (42%8)).toByte() }).evidence(tm).kind)
    }
    @Test fun knownMoveAloneDoesNotProveAcquisition() {
        val save = fixture { it[0x288a] = 1; it[0x2894] = 223.toByte() }
        assertEquals(EvidenceKind.UNKNOWN, Silver.parse(save).evidence(tm).kind)
    }
    @Test fun caughtFlagsRespectBitBoundaries() {
        val save = Silver.parse(fixture { it[0x2a4c+31] = 4 })
        assertEquals(EvidenceKind.SAVE_FLAG, save.evidence(Task("dex", "Celebi", "dex", dexNumber=251)).kind)
        assertEquals(EvidenceKind.UNKNOWN, save.evidence(Task("dex", "Ho-Oh", "dex", dexNumber=250)).kind)
    }
    @Test fun rejectsZeroFilledEvenWithMatchingZeroChecksum() { assertThrows(IllegalArgumentException::class.java) { Silver.parse(ByteArray(32768)) } }
    @Test fun rejectsCorruptChecksum() { val b = fixture(); b[0x2300] = 1; assertThrows(IllegalArgumentException::class.java) { Silver.parse(b) } }
    @Test fun rejectsWrongLengths() { listOf(0, 32767, 65536, 32769).forEach { n -> assertThrows(IllegalArgumentException::class.java) { Silver.parse(ByteArray(n)) } } }
    @Test fun acceptsKnownRtcFooters() { for (n in listOf(44,48)) assertEquals("T", Silver.parse(fixture().copyOf(32768+n)).trainer) }
    @Test fun trainerIdentityIsStableAcrossProgressAndSeparatesIds() {
        assertEquals(Silver.parse(fixture()).profile, Silver.parse(fixture { it[0x23e6] = 1 }).profile)
        assertNotEquals(Silver.parse(fixture()).profile, Silver.parse(fixture { it[0x200a] = 3 }).profile)
    }
    @Test fun recoversNoncontiguousBackupWithoutChangingInput() {
        val primary = fixture { it[0x23e6] = 1 }
        val b = ByteArray(32768)
        for ((start,end,target) in listOf(Triple(0x2009,0x222e,0x15c7),Triple(0x222f,0x23d8,0x3d96),Triple(0x23d9,0x2855,0xc6b),Triple(0x2856,0x2889,0x7e39),Triple(0x288a,0x2d68,0x10e8))) primary.copyInto(b,target,start,end+1)
        b[0x7e38] = 99; b[0x7e6f] = 127; b[0x7e6d] = primary[0x2d69]; b[0x7e6e] = primary[0x2d6a]
        val before = b.copyOf(); val parsed = Silver.parse(b)
        assertTrue(parsed.usedBackup); assertEquals(EvidenceKind.BAG, parsed.evidence(tm).kind); assertArrayEquals(before,b)
    }
    @Test fun rejectsInvalidPartyCount() { assertThrows(IllegalArgumentException::class.java) { Silver.parse(fixture { it[0x288a] = 7 }) } }
}
