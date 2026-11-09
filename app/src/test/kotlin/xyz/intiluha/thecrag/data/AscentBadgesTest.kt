package xyz.intiluha.thecrag.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.intiluha.thecrag.data.logbook.Ascent
import xyz.intiluha.thecrag.data.logbook.LogbookRepository
import xyz.intiluha.thecrag.data.logbook.TickTypes

/** The pure selection behind the ascent badges on route rows. */
class AscentBadgesTest {
    private fun ascent(id: Long, routeId: Long?, tick: String, date: String?) =
        Ascent(id, routeId, "R", "6a", 3, tick, TickTypes.name(tick), "sport", date, null, null)

    @Test
    fun groupsByRouteInChronologicalOrder() {
        val byRoute = LogbookRepository.ascentsByRoute(
            listOf(
                ascent(5, 10, "redpoint", "2026-03-01"),
                ascent(3, 10, "dog", null),          // undated: goes last
                ascent(2, 10, "attempt", "2025-12-24"),
                ascent(9, 20, "flash", "2026-01-01"),
                ascent(7, null, "onsight", "2026-01-01"), // no route: dropped
            ),
        )
        assertEquals(setOf(10L, 20L), byRoute.keys)
        assertEquals(listOf(2L, 5L, 3L), byRoute.getValue(10).map { it.id })
        assertEquals(listOf(9L), byRoute.getValue(20).map { it.id })
    }

    @Test
    fun undatedAscentsAreOrderedByIdAfterTheDatedOnes() {
        val (shown, _) = LogbookRepository.badgeAscents(
            listOf(ascent(8, 1, "dog", null), ascent(4, 1, "dog", null), ascent(6, 1, "flash", "2026-01-01")),
        )
        assertEquals(listOf(6L, 4L, 8L), shown.map { it.id })
    }

    @Test
    fun sameDateIsOrderedById() {
        val (shown, _) = LogbookRepository.badgeAscents(
            listOf(ascent(12, 1, "redpoint", "2026-01-01"), ascent(11, 1, "dog", "2026-01-01")),
        )
        assertEquals(listOf(11L, 12L), shown.map { it.id })
    }

    @Test
    fun threeAscentsAreAllShown() {
        val (shown, truncated) = LogbookRepository.badgeAscents(
            listOf(ascent(1, 1, "dog", "2026-01-01"), ascent(2, 1, "dog", "2026-01-02"), ascent(3, 1, "redpoint", "2026-01-03")),
        )
        assertEquals(listOf(1L, 2L, 3L), shown.map { it.id })
        assertFalse(truncated)
    }

    @Test
    fun fourAscentsShowTheFirstTwoAndATruncationMark() {
        val (shown, truncated) = LogbookRepository.badgeAscents(
            listOf(
                ascent(4, 1, "redpoint", "2026-01-04"),
                ascent(1, 1, "attempt", "2026-01-01"),
                ascent(3, 1, "dog", "2026-01-03"),
                ascent(2, 1, "dog", "2026-01-02"),
            ),
        )
        assertEquals(listOf(1L, 2L), shown.map { it.id })
        assertTrue(truncated)
    }

    @Test
    fun noAscentsMeansNoBadges() {
        val (shown, truncated) = LogbookRepository.badgeAscents(emptyList())
        assertTrue(shown.isEmpty())
        assertFalse(truncated)
    }
}
