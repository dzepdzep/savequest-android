package dev.savequest.silver

import dev.savequest.core.*
import java.security.MessageDigest

/** English original Gold/Silver SRAM layout. Edition cannot be identified uniquely from SRAM. */
object Silver : GameModule {
    override val id = "pokemon-silver-en"
    override val title = "Pokémon Silver"
    override fun parse(bytes: ByteArray): SilverSave {
        require(bytes.size in setOf(32768, 32768 + 44, 32768 + 48)) { "Select a 32 KiB English Gold/Silver battery save (.sav/.srm), optionally with a 44/48-byte RTC footer. Savestates and other layouts are unsupported." }
        val b = bytes.copyOf(32768)
        fun u(i: Int) = b[i].toInt() and 255
        fun sum(a: Int, z: Int) = (a..z).sumOf { u(it) } and 65535
        fun word(i: Int) = u(i) or (u(i + 1) shl 8)
        val primary = u(0x2008) == 99 && u(0x2d6b) == 127 && sum(0x2009, 0x2d68) == word(0x2d69)
        val backup = u(0x7e38) == 99 && u(0x7e6f) == 127 && ((sum(0xc6b, 0x17ec) + sum(0x3d96, 0x3f3f) + sum(0x7e39, 0x7e6c)) and 65535) == word(0x7e6d)
        require(primary || backup) { "Save validation failed: markers/checksum do not match English Gold/Silver. Existing progress was kept." }
        if (!primary) for ((start, end, source) in listOf(Triple(0x2009,0x222e,0x15c7), Triple(0x222f,0x23d8,0x3d96), Triple(0x23d9,0x2855,0xc6b), Triple(0x2856,0x2889,0x7e39), Triple(0x288a,0x2d68,0x10e8))) bytes.copyInto(b, start, source, source + end - start + 1)
        require(u(0x288a) <= 6) { "Invalid party count" }
        return SilverSave(b, !primary)
    }
}
class SilverSave internal constructor(private val bytes: ByteArray, override val usedBackup: Boolean) : ParsedSave {
    private fun u(i: Int) = bytes[i].toInt() and 255
    override val trainer = bytes.slice(0x200b..0x2015).takeWhile { (it.toInt() and 255) != 0x50 }.joinToString("") {
        val c = it.toInt() and 255
        when(c) { in 0x80..0x99 -> ('A' + c - 0x80).toString(); in 0xa0..0xb9 -> ('a' + c - 0xa0).toString(); 0x7f -> " "; else -> "?" }
    }
    override val profile = "silver:" + MessageDigest.getInstance("SHA-256").digest(bytes.copyOfRange(0x2009, 0x2016)).take(12).joinToString("") { "%02x".format(it) }
    val legacyScope = "GSC-${u(0x2009).toString(16)}${u(0x200a).toString(16)}-$trainer"
    val badges = Integer.bitCount(u(0x23e4)) + Integer.bitCount(u(0x23e5))
    fun flag(bit: Int): Boolean { require(bit in 0 until 2000); return u(0x261f + bit / 8) and (1 shl (bit % 8)) != 0 }
    override fun evidence(task: Task): Evidence {
        task.bagIndex?.let { require(it in 0..56); if (u(0x23e6 + it) > 0) return Evidence(EvidenceKind.BAG, "Currently in Bag ×${u(0x23e6 + it)}") }
        if (task.flags.any { flag(it) }) return Evidence(if (task.bagIndex != null) EvidenceKind.RECEIPT else EvidenceKind.SAVE_FLAG, "Source-mapped event recorded in this save")
        task.dexNumber?.let { require(it in 1..251); val bit = it - 1; if (u(0x2a4c + bit / 8) and (1 shl (bit % 8)) != 0) return Evidence(EvidenceKind.SAVE_FLAG, "Pokédex caught flag; not proof of current ownership") }
        return Evidence(EvidenceKind.UNKNOWN, "Not confirmed by this save; absence is not proof it was never obtained")
    }
}
