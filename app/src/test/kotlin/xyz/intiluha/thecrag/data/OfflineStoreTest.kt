package xyz.intiluha.thecrag.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.intiluha.thecrag.data.offline.Favourite
import xyz.intiluha.thecrag.data.offline.FavouriteStatus
import xyz.intiluha.thecrag.data.offline.OfflineStore
import xyz.intiluha.thecrag.data.remote.ApiFactory
import java.nio.file.Files

class OfflineStoreTest {
    private fun store() = OfflineStore(Files.createTempDirectory("offline").toFile(), ApiFactory.json)

    @Test
    fun blobsAreServedOnceCommittedAndSweptWhenUnreferenced() {
        val dir = Files.createTempDirectory("offline").toFile()
        val store = OfflineStore(dir, ApiFactory.json)
        store.upsert(Favourite(1, "A", "Crag", 10, 2, addedAt = 1))
        store.upsert(Favourite(2, "B", "Crag", 10, 2, addedAt = 2))
        val shared = store.put("https://x/shared", "s".toByteArray(), "text/plain")
        val only1 = store.put("https://x/one", "1".toByteArray(), "text/plain")
        assertNull("not served before a manifest lists it", store.entry("https://x/shared"))

        store.commit(1, listOf(shared, only1))
        store.commit(2, listOf(shared))
        assertEquals("text/plain", store.entry("https://x/one")!!.contentType)
        assertEquals(2L, store.totalBytes())

        store.remove(1)
        assertNull(store.entry("https://x/one"))
        assertFalse(store.file("https://x/one").exists())
        assertNotNull("still referenced by favourite 2", store.entry("https://x/shared"))
        assertTrue(store.file("https://x/shared").exists())

        // Everything survives a restart.
        val again = OfflineStore(dir, ApiFactory.json)
        assertEquals(listOf(2L), again.favourites.value.map { it.id })
        assertEquals("s", again.read("https://x/shared")!!.decodeToString())
        assertNotNull(again.entry("https://x/shared"))
    }

    @Test
    fun statusUpdatesPersistAndIgnoreUnknownIds() {
        val store = store()
        store.upsert(Favourite(7, "C", "Sector", 3, 1, addedAt = 1))
        store.update(7) { it.copy(status = FavouriteStatus.READY, routeCount = 3, bytes = 1234) }
        store.update(8) { it.copy(status = FavouriteStatus.FAILED) }
        store.commit(8, emptyList())
        val f = store.favourite(7)!!
        assertEquals(FavouriteStatus.READY, f.status)
        assertEquals(3, f.routeCount)
        assertEquals(1, store.favourites.value.size)
    }

    @Test
    fun putReplacesInPlace() {
        val store = store()
        store.put("https://x/a", "old".toByteArray(), "text/plain")
        store.put("https://x/a", "new".toByteArray(), "text/plain")
        assertEquals("new", store.read("https://x/a")!!.decodeToString())
    }
}
