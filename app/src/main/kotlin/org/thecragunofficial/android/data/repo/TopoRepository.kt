package org.thecragunofficial.android.data.repo

import androidx.collection.LruCache
import org.thecragunofficial.android.data.remote.NO_CACHE
import org.thecragunofficial.android.data.remote.TheCragApi
import org.thecragunofficial.android.domain.Topo

/** Topos (image + route lines + area outlines) of an area or route, from `show=topo`. */
class TopoRepository(private val api: TheCragApi) {
    private val cache = LruCache<Long, List<Topo>>(64)

    suspend fun toposFor(id: Long, force: Boolean = false): List<Topo> {
        if (!force) cache.get(id)?.let { return it }
        val dto = api.node(id, show = TOPO_SHOW, cacheControl = if (force) NO_CACHE else null).data
        val topos = dto.topos.mapNotNull(TopoMapper::fromDto)
        cache.put(id, topos)
        return topos
    }

    fun clear() = cache.evictAll()

    companion object {
        val TOPO_SHOW = listOf("topo")
    }
}
