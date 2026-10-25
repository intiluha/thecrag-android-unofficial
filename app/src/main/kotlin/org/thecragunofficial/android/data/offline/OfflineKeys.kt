package org.thecragunofficial.android.data.offline

import org.thecragunofficial.android.data.remote.THECRAG_BASE
import org.thecragunofficial.android.data.repo.NodeRepository
import org.thecragunofficial.android.data.repo.TopoRepository
import org.thecragunofficial.android.domain.topoImageUrl

/**
 * The exact URLs the repositories request through Retrofit, so the downloader can
 * pin responses under the keys the UI will later ask for. `OfflineKeysTest`
 * checks each against what Retrofit really builds.
 */
object OfflineKeys {
    fun node(id: Long, show: List<String>): String =
        THECRAG_BASE + "api/node/id/$id?" + show.joinToString("&") { "show=$it" }

    fun area(id: Long) = node(id, NodeRepository.AREA_SHOW)
    fun route(id: Long) = node(id, NodeRepository.ROUTE_SHOW)
    fun topos(id: Long) = node(id, TopoRepository.TOPO_SHOW)
    fun childAreas(id: Long) = THECRAG_BASE + "api/node/id/$id/children/area"
    fun childRoutes(id: Long) = THECRAG_BASE + "api/node/id/$id/children/route"
    fun image(hashId: String) = topoImageUrl(hashId)

    /** Batch form: one request for many nodes, every `show` the per-node keys need. */
    fun batch(ids: List<Long>, show: List<String>): String =
        THECRAG_BASE + "api/node/ids?id=" + ids.joinToString(",") + "&show=" + show.joinToString(",")
}
