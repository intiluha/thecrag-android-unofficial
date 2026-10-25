package org.thecragunofficial.android.data.logbook

/**
 * The downloaded logbook as RFC 4180 CSV (comma separated, quotes doubled,
 * CRLF line ends, UTF-8 with no BOM). One row per ascent, newest first, the
 * same fields the app stores; nothing is fetched for this.
 */
object LogbookCsv {
    val HEADER = listOf("date", "route", "grade", "tick", "tick_name", "gear_style", "crag", "comment", "route_id", "ascent_id")

    fun write(ascents: List<Ascent>, out: Appendable) {
        out.row(HEADER)
        ascents.sortedWith(compareByDescending<Ascent> { it.date ?: "" }.thenByDescending { it.id }).forEach { a ->
            out.row(
                listOf(
                    a.date, a.routeName, a.grade, a.tick, a.tickName, a.gearStyle, a.cragName, a.comment,
                    a.routeId?.toString(), a.id.toString(),
                ),
            )
        }
    }

    fun toString(ascents: List<Ascent>): String = StringBuilder().also { write(ascents, it) }.toString()

    /** File name for a save dialog, e.g. `thecrag-logbook-2026-10-25.csv`. */
    fun fileName(date: String): String = "thecrag-logbook-$date.csv"

    private fun Appendable.row(cells: List<String?>) {
        cells.forEachIndexed { i, c ->
            if (i > 0) append(',')
            append(quote(c ?: ""))
        }
        append("\r\n")
    }

    private fun quote(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}
