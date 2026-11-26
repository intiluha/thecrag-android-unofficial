package xyz.intiluha.thecrag.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.intiluha.thecrag.data.logbook.Ascent
import xyz.intiluha.thecrag.data.logbook.LogbookRepository
import xyz.intiluha.thecrag.data.logbook.PitchTick
import xyz.intiluha.thecrag.data.logbook.Pitches
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

    @Test
    fun aSendOfSomePitchesLeavesTheRouteUnclimbed() {
        fun pitched(id: Long, routeId: Long, vararg pitches: Int, known: Int? = null) =
            ascent(id, routeId, "redpoint").copy(pitches = pitches.map { PitchTick(it) }, routePitches = known)
        val ascents = listOf(
            pitched(1, 10, 1),                 // first pitch of three: not sent
            pitched(2, 20, 1, 2, 3),           // every pitch listed: sent
            ascent(3, 30, "onsight"),          // no pitch details on a multi-pitch route: the whole route
            pitched(4, 40, 1),                 // single-pitch route with a pitch record (trad): sent
            pitched(5, 50, 1),                 // pitch count unknown: cannot be judged, counts
            pitched(6, 60, 1, 2, known = 4),   // count remembered from the tick sheet: not sent
            pitched(7, 70, 1), ascent(8, 70, "flash"), // a partial send and a later full one: sent
        )
        val counts = mapOf(10L to 3, 20L to 3, 30L to 3, 40L to 1, 70L to 2)
        assertEquals(setOf(20L, 30L, 40L, 50L, 70L), LogbookRepository.sentRouteIds(ascents, counts))
    }

    @Test
    fun pitchLabelsSayWhatWasClimbed() {
        assertEquals("pitch 1 of 3", Pitches.label(listOf(1), 3))
        assertEquals("pitches 1–2 of 3", Pitches.label(listOf(2, 1), 3))
        assertEquals("pitches 1, 3–4 of 5", Pitches.label(listOf(4, 1, 3), 5))
        assertEquals("all 3 pitches", Pitches.label(listOf(1, 2, 3), 3))
        assertEquals("whole route", Pitches.label(emptyList(), 3))
        assertEquals("pitch 1", Pitches.label(listOf(1), null))
        assertEquals(null, Pitches.label(emptyList(), null))
        assertEquals(null, Pitches.label(emptyList(), 1))
        assertEquals(null, Pitches.label(listOf(1), 1))
        assertEquals("P1 Onsight (led by Ann) · P2 Hang dog", Pitches.details(listOf(PitchTick(2, "dog"), PitchTick(1, "onsight", "Ann"))))
        assertEquals(null, Pitches.details(listOf(PitchTick(1))))
    }
}
