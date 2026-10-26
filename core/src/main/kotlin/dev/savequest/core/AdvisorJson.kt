package dev.savequest.core

/** Tiny JSON reader (objects -> Map, arrays -> List, numbers -> Long/Double, strings, true/false/null) so the advisor data can be loaded and tested without Android. */
internal class MiniJson(private val s: String) {
    private var i = 0
    fun parse(): Any? { val v = value(); ws(); require(i == s.length) { "trailing data at $i" }; return v }
    private fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
    private fun value(): Any? {
        ws()
        return when (val c = s[i]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> str()
            't' -> { i += 4; true }
            'f' -> { i += 5; false }
            'n' -> { i += 4; null }
            else -> { check(c == '-' || c.isDigit()) { "unexpected '$c' at $i" }
                val st = i; i++; while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
                val t = s.substring(st, i); if (t.any { it in ".eE" }) t.toDouble() else t.toLong() }
        }
    }
    private fun readObject(): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>(); expect('{'); ws()
        if (s[i] == '}') { i++; return m }
        while (true) {
            ws(); val k = str(); ws(); expect(':'); m[k] = value(); ws()
            if (s[i] == ',') i++ else { expect('}'); return m }
        }
    }
    private fun readArray(): List<Any?> {
        val l = ArrayList<Any?>(); expect('['); ws()
        if (s[i] == ']') { i++; return l }
        while (true) {
            l.add(value()); ws()
            if (s[i] == ',') i++ else { expect(']'); return l }
        }
    }
    private fun expect(c: Char) { check(s[i] == c) { "expected '$c' at $i" }; i++ }
    private fun str(): String {
        expect('"'); val sb = StringBuilder()
        while (s[i] != '"') {
            if (s[i] == '\\') { i++; when (val e = s[i]) { 'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r'); 'b' -> sb.append('\b'); 'f' -> sb.append('\u000c')
                'u' -> { sb.append(s.substring(i + 1, i + 5).toInt(16).toChar()); i += 4 }; else -> sb.append(e) } }
            else sb.append(s[i])
            i++
        }
        i++; return sb.toString()
    }
}

@Suppress("UNCHECKED_CAST")
private fun Any?.obj() = this as Map<String, Any?>
@Suppress("UNCHECKED_CAST")
private fun Any?.arr() = this as List<Any?>
private fun Any?.int() = (this as Number).toInt()

/** Builds the advisor data from assets/advisor.json. */
fun parseAdvisorData(json: String): AdvisorData {
    val r = MiniJson(json).parse().obj()
    val stages = r["stages"].arr().map { s -> val o = s.obj()
        AdvStage(o["id"] as String, o["title"] as String, o["where"] as String, o["kind"] as String, o["region"] as String, o["badge"].int(), (o["flag"] as? Number)?.toInt(),
            o["recommended"].int(), o["tips"] as String, o["source"] as String,
            o["mons"].arr().map { m -> val mo = m.obj(); AdvMon(mo["species"].int(), mo["level"].int(), mo["moves"].arr().map { it.int() }) }) }
    return AdvisorData(
        r["typeNames"].arr().map { it as String }, r["johtoBadges"].arr().map { it as String }, r["kantoBadges"].arr().map { it as String },
        r["speciesNames"].arr().map { it as String }, r["species"].arr().map { t -> t.arr().map { it.int() } },
        r["chart"].arr().map { c -> val l = c.arr(); Triple(l[0].int(), l[1].int(), l[2].int()) },
        r["moves"].obj().entries.associate { (k, v) -> val l = v.arr(); k.toInt() to AdvMove(l[0] as String, l[1].int(), l[2].int()) },
        r["hms"].arr().map { h -> val o = h.obj(); AdvHm(o["id"].int(), o["name"] as String, o["badge"] as String, o["learners"].arr().map { it.int() }.toSet()) },
        stages)
}
