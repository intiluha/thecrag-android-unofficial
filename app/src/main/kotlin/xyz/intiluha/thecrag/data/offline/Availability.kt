package xyz.intiluha.thecrag.data.offline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import xyz.intiluha.thecrag.data.remote.THECRAG_BASE

/**
 * Which nodes can be opened without a network: every node whose detail
 * (`/api/node/id/{id}?show=…`) is either pinned by a starred area in the
 * [OfflineStore] or still in the OkHttp disk cache. The area screen uses this,
 * only while offline, to dim rows that would fail to open. Both sources are
 * scanned by URL with [nodeIdOf], so the rule is the same for each.
 *
 * [cachedUrls] yields the request URLs the HTTP cache holds (`Cache.urls()` in
 * the app; a plain list in tests), or null when there is no cache.
 */
class Availability(private val store: OfflineStore, private val cachedUrls: () -> Iterator<String>?) {
    constructor(store: OfflineStore, cache: Cache?) : this(store, { cache?.urls() })

    /** Reads the cache journal and the manifests; call from a coroutine, it switches to IO itself. */
    suspend fun availableNodeIds(): Set<Long> = withContext(Dispatchers.IO) {
        val ids = HashSet<Long>()
        for (f in store.favourites.value) store.manifest(f.id).forEach { e -> nodeIdOf(e.url)?.let(ids::add) }
        // The journal can be mid-rewrite by a concurrent request; a failed scan just dims more rows.
        runCatching {
            val urls = cachedUrls() ?: return@runCatching
            while (urls.hasNext()) nodeIdOf(urls.next())?.let(ids::add)
        }
        ids
    }

    companion object {
        private const val PREFIX = THECRAG_BASE + "api/node/id/"

        /**
         * The node id of a detail URL `https://www.thecrag.com/api/node/id/{id}` with any
         * query (`?show=…`), or null for anything else: children listings
         * (`/children/route`), the batch endpoint (`/api/node/ids?…`), images, other hosts.
         */
        fun nodeIdOf(url: String): Long? {
            if (!url.startsWith(PREFIX)) return null
            val rest = url.substring(PREFIX.length)
            val end = rest.indexOf('?').let { if (it < 0) rest.length else it }
            if (end == 0) return null
            val digits = rest.substring(0, end)
            if (!digits.all { it in '0'..'9' }) return null
            return digits.toLongOrNull()
        }
    }
}
