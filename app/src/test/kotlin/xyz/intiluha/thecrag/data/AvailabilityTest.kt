package xyz.intiluha.thecrag.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xyz.intiluha.thecrag.data.offline.Availability
import xyz.intiluha.thecrag.data.offline.Favourite
import xyz.intiluha.thecrag.data.offline.OfflineKeys
import xyz.intiluha.thecrag.data.offline.OfflineStore
import xyz.intiluha.thecrag.data.remote.ApiFactory
import java.nio.file.Files

class AvailabilityTest {
    @Test
    fun detailUrlsYieldTheNodeId() {
        assertEquals(11962579L, Availability.nodeIdOf(OfflineKeys.area(11962579)))
        assertEquals(11962579L, Availability.nodeIdOf(OfflineKeys.route(11962579)))
        assertEquals(11962579L, Availability.nodeIdOf(OfflineKeys.topos(11962579)))
        assertEquals(7L, Availability.nodeIdOf("https://www.thecrag.com/api/node/id/7"))
        assertEquals(7L, Availability.nodeIdOf("https://www.thecrag.com/api/node/id/7?show=ancestors&show=info&show=location"))
    }

    @Test
    fun otherUrlsYieldNothing() {
        assertNull(Availability.nodeIdOf(OfflineKeys.childAreas(5)))
        assertNull(Availability.nodeIdOf(OfflineKeys.childRoutes(5)))
        assertNull(Availability.nodeIdOf(OfflineKeys.batch(listOf(5, 6), listOf("info"))))
        assertNull(Availability.nodeIdOf(OfflineKeys.image("0123456789abcdef")))
        assertNull(Availability.nodeIdOf("https://www.thecrag.com/api/node/id/"))
        assertNull(Availability.nodeIdOf("https://www.thecrag.com/api/node/id/?show=info"))
        assertNull(Availability.nodeIdOf("https://www.thecrag.com/api/node/id/12abc?show=info"))
        assertNull(Availability.nodeIdOf("https://www.thecrag.com/api/node/id/7/children/area"))
        assertNull(Availability.nodeIdOf("http://www.thecrag.com/api/node/id/7?show=info"))
        assertNull(Availability.nodeIdOf("https://example.com/api/node/id/7?show=info"))
        assertNull(Availability.nodeIdOf("https://www.thecrag.com/api/lookup/node?ancestor=7"))
        assertNull(Availability.nodeIdOf(""))
    }

    @Test
    fun unionOfPinnedAndCachedNodes() = runBlocking {
        val store = OfflineStore(Files.createTempDirectory("offline").toFile(), ApiFactory.json)
        store.upsert(Favourite(1, "A", "Crag", 2, 0, addedAt = 1))
        store.commit(
            1,
            listOf(
                store.put(OfflineKeys.area(1), "{}".toByteArray(), "application/json"),
                store.put(OfflineKeys.childRoutes(1), "{}".toByteArray(), "application/json"),
                store.put(OfflineKeys.route(2), "{}".toByteArray(), "application/json"),
            ),
        )
        // Written but no favourite lists it: not served by the interceptor, so not available.
        store.put(OfflineKeys.route(99), "{}".toByteArray(), "application/json")
        val cached = listOf(OfflineKeys.route(3), OfflineKeys.childAreas(3), OfflineKeys.area(4), OfflineKeys.image("0123456789abcdef"))

        assertEquals(setOf(1L, 2L, 3L, 4L), Availability(store) { cached.iterator() }.availableNodeIds())
        assertEquals("no HTTP cache", setOf(1L, 2L), Availability(store) { null }.availableNodeIds())
        assertEquals("a broken cache scan still reports the pinned nodes", setOf(1L, 2L), Availability(store) { error("journal") }.availableNodeIds())
    }
}
