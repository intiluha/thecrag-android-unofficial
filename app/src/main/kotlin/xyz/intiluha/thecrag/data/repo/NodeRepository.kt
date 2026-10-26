package xyz.intiluha.thecrag.data.repo

import androidx.collection.LruCache
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import xyz.intiluha.thecrag.data.remote.NO_CACHE
import xyz.intiluha.thecrag.data.remote.TheCragApi
import xyz.intiluha.thecrag.data.remote.dto.NodeDto
import xyz.intiluha.thecrag.domain.AreaDetail
import xyz.intiluha.thecrag.domain.NodeKind
import xyz.intiluha.thecrag.domain.RouteDetail
import xyz.intiluha.thecrag.domain.SearchHit
import xyz.intiluha.thecrag.domain.WORLD_ID

/**
 * Online-first. An in-memory cache keeps back navigation free; the OkHttp disk
 * cache behind the API serves recently seen nodes offline; starred areas are
 * answered from the pinned store by `OfflineInterceptor` (Stage 3).
 */
class NodeRepository(private val api: TheCragApi) {
    private val areas = LruCache<Long, AreaDetail>(64)
    private val routes = LruCache<Long, RouteDetail>(128)

    suspend fun area(id: Long, force: Boolean = false): AreaDetail {
        if (!force) areas.get(id)?.let { return it }
        val cc = if (force) NO_CACHE else null
        val detail = coroutineScope {
            val node = async { api.node(id, show = AREA_SHOW, cacheControl = cc).data }
            val subAreas = async { api.childAreas(id, cc).data }
            val n = node.await()
            val routeList = if (mayHaveRoutes(n)) api.childRoutes(id, cc).data else emptyList()
            n.toAreaDetail(subAreas.await(), routeList)
        }
        areas.put(id, detail)
        return detail
    }

    suspend fun route(id: Long, force: Boolean = false): RouteDetail {
        if (!force) routes.get(id)?.let { return it }
        val detail = api.node(id, show = ROUTE_SHOW, cacheControl = if (force) NO_CACHE else null).data.toRouteDetail()
        routes.put(id, detail)
        return detail
    }

    suspend fun search(query: String, ancestor: Long = WORLD_ID): List<SearchHit> {
        if (query.isBlank()) return emptyList()
        return api.lookup(query.trim(), ancestor).data.map {
            SearchHit(it.id, it.name, it.qualifier?.takeIf { q -> q.isNotBlank() }, if (it.type == "route") NodeKind.ROUTE else NodeKind.AREA)
        }
    }

    fun clear() {
        areas.evictAll()
        routes.evictAll()
    }

    companion object {
        /** `show=` lists of the two node reads; the offline downloader pins responses under the same URLs. */
        val AREA_SHOW = listOf("ancestors", "info", "location")
        val ROUTE_SHOW = listOf("ancestors", "info")

        /** Sub types that commonly hold routes directly, even with sub areas. */
        private val LEAFY = setOf("Crag", "Cliff", "Sector", "Boulder", "Field", "Area", "Gym", "Wall")

        /** Regions have no direct routes; the children/route call is skipped unless the node says it may have some. */
        fun mayHaveRoutes(n: NodeDto): Boolean = (n.subAreaCount ?: 0) == 0 || n.subType in LEAFY
    }
}
