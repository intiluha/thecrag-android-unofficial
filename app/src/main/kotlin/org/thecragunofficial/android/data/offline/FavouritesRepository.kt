package org.thecragunofficial.android.data.offline

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import org.thecragunofficial.android.domain.AreaDetail

/** Starred areas: the list, adding and removing, and running a download while keeping the status current. */
class FavouritesRepository(private val store: OfflineStore, private val downloader: FavouriteDownloader) {
    val favourites: StateFlow<List<Favourite>> = store.favourites

    fun get(id: Long): Favourite? = store.favourite(id)
    fun isFavourite(id: Long): Boolean = store.favourite(id) != null

    fun add(area: AreaDetail): Favourite {
        val f = Favourite(area.id, area.name, area.subType, area.numberRoutes, area.numberTopos, addedAt = System.currentTimeMillis())
        store.upsert(f)
        return f
    }

    fun remove(id: Long) = store.remove(id)

    fun totalBytes(): Long = store.totalBytes()

    /**
     * Downloads (or, with [refresh], re-downloads) the subtree of favourite [id].
     * The status in the store follows along; errors are recorded and rethrown so
     * the caller can decide about retries.
     */
    suspend fun download(id: Long, refresh: Boolean, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }) {
        store.favourite(id) ?: return
        store.update(id) { it.copy(status = FavouriteStatus.DOWNLOADING, error = null, done = 0, total = 0) }
        var lastWrite = 0L
        try {
            val r = downloader.download(id, refresh) { p ->
                onProgress(p.done, p.total)
                val now = System.currentTimeMillis()
                if (now - lastWrite > 500) {
                    lastWrite = now
                    store.update(id) { it.copy(done = p.done, total = p.total) }
                }
            }
            store.update(id) {
                it.copy(
                    status = FavouriteStatus.READY, done = it.total, routeCount = r.routeCount, topoCount = r.topoCount,
                    bytes = r.bytes, updatedAt = System.currentTimeMillis(), error = null,
                )
            }
        } catch (e: CancellationException) {
            store.update(id) { it.copy(status = FavouriteStatus.QUEUED) }
            throw e
        } catch (e: Exception) {
            store.update(id) { it.copy(status = FavouriteStatus.FAILED, error = e.message ?: e.javaClass.simpleName) }
            throw e
        }
    }

    companion object {
        /** Areas with more routes than this cannot be starred: a region would mean thousands of calls. */
        const val MAX_ROUTES = 3000

        /** Rough download size: topo images 1200 px wide run about 400 kB, route JSON about 6 kB. */
        fun estimateBytes(numberRoutes: Int, numberTopos: Int): Long = numberTopos * 400_000L + numberRoutes * 6_000L
    }
}
