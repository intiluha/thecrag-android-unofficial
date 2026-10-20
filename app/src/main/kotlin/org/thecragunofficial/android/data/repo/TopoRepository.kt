package org.thecragunofficial.android.data.repo

import android.util.LruCache
import org.thecragunofficial.android.data.html.TopoHtmlParser
import org.thecragunofficial.android.data.remote.NO_CACHE
import org.thecragunofficial.android.data.remote.TheCragApi
import org.thecragunofficial.android.domain.NodeKind
import org.thecragunofficial.android.domain.Topo

/**
 * Topos as shown on a node's web page (the page is Cloudflare-cached and needs no
 * cookie). The JSON API lists the same topo ids but without route lines.
 */
class TopoRepository(private val api: TheCragApi, private val parser: TopoHtmlParser) {
    private val cache = LruCache<String, List<Topo>>(32)

    suspend fun toposFor(kind: NodeKind, id: Long, force: Boolean = false): List<Topo> {
        val key = "$kind/$id"
        if (!force) cache.get(key)?.let { return it }
        val html = api.page(if (kind == NodeKind.ROUTE) "route" else "area", id, if (force) NO_CACHE else null).string()
        val topos = parser.parse(html)
        cache.put(key, topos)
        return topos
    }

    fun clear() = cache.evictAll()
}
