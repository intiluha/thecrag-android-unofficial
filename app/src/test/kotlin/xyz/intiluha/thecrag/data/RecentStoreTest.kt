package xyz.intiluha.thecrag.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.intiluha.thecrag.data.remote.ApiFactory
import xyz.intiluha.thecrag.data.remote.InMemoryStore
import xyz.intiluha.thecrag.data.repo.RecentStore
import xyz.intiluha.thecrag.domain.NodeKind
import xyz.intiluha.thecrag.domain.WORLD_ID

class RecentStoreTest {
    @Test
    fun newestFirstDedupedLimitedAndPersisted() {
        var t = 0L
        val store = InMemoryStore()
        val recents = RecentStore(store, ApiFactory.json, limit = 3, clock = { ++t })
        recents.record(WORLD_ID, "World", NodeKind.AREA, null)
        recents.record(1, "Arapiles", NodeKind.AREA, "Crag · Grampians")
        recents.record(2, "Kachoong", NodeKind.ROUTE, "21 · Arapiles")
        recents.record(1, "Arapiles", NodeKind.AREA, "Crag · Grampians")
        recents.record(3, "Taipan Wall", NodeKind.AREA, "Crag")
        recents.record(4, "Serpentine", NodeKind.ROUTE, "29 · Taipan Wall")

        assertEquals(listOf(4L, 3L, 1L), recents.items.value.map { it.id })
        assertTrue(recents.items.value.none { it.id == WORLD_ID })

        val reloaded = RecentStore(store, ApiFactory.json, limit = 3)
        assertEquals(recents.items.value, reloaded.items.value)
        assertEquals(NodeKind.ROUTE, reloaded.items.value.first().kind)
    }

    @Test
    fun corruptPreferenceIsIgnored() {
        val store = InMemoryStore().apply { put(RecentStore.KEY, "not json") }
        assertTrue(RecentStore(store, ApiFactory.json).items.value.isEmpty())
    }
}
