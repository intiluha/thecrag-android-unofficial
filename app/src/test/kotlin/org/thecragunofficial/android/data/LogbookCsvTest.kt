package org.thecragunofficial.android.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.thecragunofficial.android.data.logbook.Ascent
import org.thecragunofficial.android.data.logbook.LogbookCsv

class LogbookCsvTest {
    private fun ascent(id: Long, date: String?, name: String, comment: String?) =
        Ascent(id, 100L + id, name, "6a+", 3, "RP", "Red point", "sport", date, comment, "Ohanavan, West")

    @Test
    fun quotesCommasQuotesAndNewlinesAndSortsNewestFirst() {
        val csv = LogbookCsv.toString(
            listOf(
                ascent(1, "2026-10-01", "Plain", null),
                ascent(2, "2026-10-20", "Say \"hi\", now", "two\nlines"),
                ascent(3, null, "No date", ""),
            ),
        )
        val lines = csv.split("\r\n")
        assertEquals("date,route,grade,tick,tick_name,gear_style,crag,comment,route_id,ascent_id", lines[0])
        assertEquals("2026-10-20,\"Say \"\"hi\"\", now\",6a+,RP,Red point,sport,\"Ohanavan, West\",\"two\nlines\",102,2", lines[1])
        assertEquals("2026-10-01,Plain,6a+,RP,Red point,sport,\"Ohanavan, West\",,101,1", lines[2])
        assertEquals(",No date,6a+,RP,Red point,sport,\"Ohanavan, West\",,103,3", lines[3])
        assertEquals("ends with CRLF", "", lines[4])
        assertEquals(5, lines.size)
    }

    @Test
    fun emptyLogbookIsJustTheHeader() {
        assertEquals("date,route,grade,tick,tick_name,gear_style,crag,comment,route_id,ascent_id\r\n", LogbookCsv.toString(emptyList()))
    }
}
