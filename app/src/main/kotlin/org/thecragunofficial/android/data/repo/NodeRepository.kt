package org.thecragunofficial.android.data.repo

import android.util.LruCache
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.thecragunofficial.android.data.remote.TheCragApi
import org.thecragunofficial.android.domain.AreaDetail
import org.thecragunofficial.android.domain.NodeKind
import org.thecragunofficial.android.domain.RouteDetail
import org.thecragunofficial.android.domain.SearchHit
import org.thecragunofficial.android.domain.WORLD_ID

/**
 * Online-only for now (Stage 1). An in-memory cache keeps back navigation free;
 * Room persistence arrives with Stage 3.
 */
class NodeRepository(private val api: TheCragApi) {
    private val areas = LruCache<Long, AreaDetail>(64)
    private val routes = LruCache<Long, RouteDetail>(128)

    suspend fun area(id: Long, force: Boolean = false): AreaDetail {
        if (!force) areas.get(id)?.let { return it }
        val detail = coroutineScope {
            val node = async { api.node(id, show = listOf("ancestors", "info", "location")).data }
            val subAreas = async { api.childAreas(id).data }
            val n = node.await()
            // Regions have no direct routes; skip the call unless the node says it may have some.
            val routeList = if ((n.subAreaCount ?: 0) == 0 || n.subType in LEAFY) api.childRoutes(id).data else emptyList()
            n.toAreaDetail(subAreas.await(), routeList)
        }
        areas.put(id, detail)
        return detail
    }

    suspend fun route(id: Long, force: Boolean = false): RouteDetail {
        if (!force) routes.get(id)?.let { return it }
        val detail = api.node(id, show = listOf("ancestors", "info")).data.toRouteDetail()
        routes.put(id, detail)
        return detail
    }

    suspend fun search(query: String, ancestor: Long = WORLD_ID): List<SearchHit> {
        if (query.isBlank()) return emptyList()
        return api.lookup(query.trim(), ancestor).data.map {
            SearchHit(it.id, it.name, it.qualifier?.takeIf { q -> q.isNotBlank() }, if (it.type == "route") NodeKind.ROUTE else NodeKind.AREA)
        }
    }

    private companion object {
        /** Sub types that commonly hold routes directly, even with sub areas. */
        val LEAFY = setOf("Crag", "Cliff", "Sector", "Boulder", "Field", "Area", "Gym", "Wall")
    }
}
