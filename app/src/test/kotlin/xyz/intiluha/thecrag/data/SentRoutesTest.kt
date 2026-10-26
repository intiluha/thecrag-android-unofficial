package xyz.intiluha.thecrag.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.intiluha.thecrag.data.logbook.Ascent
import xyz.intiluha.thecrag.data.logbook.LogbookRepository
import xyz.intiluha.thecrag.data.logbook.TickTypes

class SentRoutesTest {
    private fun ascent(id: Long, routeId: Long?, tick: String) =
        Ascent(id, routeId, "R", "6a", 3, tick, TickTypes.name(tick), "sport", "2026-10-26", null, null)

    @Test
    fun cleanTicksCountAsSends() {
        listOf("onsight", "flash", "redpoint", "clean", "Redpoint", "pinkpoint", "send", "tick").forEach {
            assertTrue(it, TickTypes.isSend(it))
        }
        listOf("dog", "working", "attempt", "retreat", "dab", "ghost", "hit", "allfreewithrest").forEach {
            assertFalse(it, TickTypes.isSend(it))
        }
    }

    @Test
    fun routesWithAtLeastOneSendAreHidden() {
        val sent = LogbookRepository.sentRouteIds(
            listOf(
                ascent(1, 10, "dog"),
                ascent(2, 10, "redpoint"), // a later send after hang-dogging: hidden
                ascent(3, 20, "dog"),      // only dogged: stays listed
                ascent(4, 30, "flash"),
                ascent(5, null, "onsight"), // no route id: ignored
            ),
        )
        assertEquals(setOf(10L, 30L), sent)
    }
}
