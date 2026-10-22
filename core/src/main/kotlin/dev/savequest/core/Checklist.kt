package dev.savequest.core

/**
 * One location section of the Items checklist.
 * [total] and [done] count every row that passes the type / region / search filters, including rows that
 * "Show unconfirmed only" hides, so a location's progress never changes just because that switch is on.
 * [shown] are the rows actually listed.
 */
data class Section(val key: String, val total: Int, val done: Int, val shown: List<Task>)

/** A manual checkbox change. Kept apart from save evidence; [undo] is the exact inverse. */
data class ManualChange(val profile: String, val taskId: String, val before: Boolean, val after: Boolean) {
    fun undo() = ManualChange(profile, taskId, after, before)
}

object Checklist {
    const val SHOPS = "Shops (price lists)"

    /** Section header text. Stable for a given task, so it doubles as the key of the remembered fold state. */
    fun sectionKey(t: Task): String = if (t.kind == "shop") SHOPS else t.region.replaceFirstChar { it.uppercase() } + " · " + t.area

    /** Every filter except "Show unconfirmed only" (that one is applied later, after progress is counted). */
    fun matches(t: Task, category: String, kind: String, region: String, query: String): Boolean =
        t.category == category &&
            (kind == "All" || t.kind == kind || (category != "Items" && category != "TM/HM")) &&
            (region == "All" || t.region.isEmpty() || t.region.split(",").any { it.equals(region, true) }) &&
            "${t.title} ${t.location} ${t.area} ${t.detail} ${t.howto}".contains(query, true)

    /** Rows to list for categories without sections. */
    fun visibleRows(base: List<Task>, isDone: (Task) -> Boolean, onlyUnconfirmed: Boolean): List<Task> =
        if (onlyUnconfirmed) base.filter { !isDone(it) } else base

    /** Sections in display order. Progress comes from [base]; sections with nothing left to show are dropped. */
    fun sections(base: List<Task>, isDone: (Task) -> Boolean, onlyUnconfirmed: Boolean): List<Section> =
        base.sortedWith(compareBy({ if (it.kind == "shop") 2 else if (it.region == "kanto") 1 else 0 }, { it.area }, { it.title }))
            .groupBy { sectionKey(it) }
            .map { (key, rows) -> Section(key, rows.size, rows.count(isDone), if (onlyUnconfirmed) rows.filter { !isDone(it) } else rows) }
            .filter { it.shown.isNotEmpty() }

    /** While a search is active every matching section is revealed, whatever the remembered fold state says. */
    fun isExpanded(key: String, collapsed: Set<String>, searching: Boolean): Boolean = searching || key !in collapsed

    fun toggled(collapsed: Set<String>, key: String): Set<String> = if (key in collapsed) collapsed - key else collapsed + key
    fun collapseAll(collapsed: Set<String>, listed: Collection<String>): Set<String> = collapsed + listed
    fun expandAll(collapsed: Set<String>, listed: Collection<String>): Set<String> = collapsed - listed.toSet()
}
