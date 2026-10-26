package xyz.intiluha.thecrag.data.offline

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import xyz.intiluha.thecrag.data.remote.dto.DataList
import xyz.intiluha.thecrag.data.remote.dto.DataObj
import xyz.intiluha.thecrag.data.remote.dto.NodeDto
import xyz.intiluha.thecrag.data.remote.dto.TopoDto
import xyz.intiluha.thecrag.data.repo.NodeRepository
import xyz.intiluha.thecrag.data.repo.TopoMapper
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A non-2xx answer while downloading; unlike a plain [IOException] it is not worth an automatic retry. */
class DownloadHttpException(val code: Int, url: String) : IOException("HTTP $code for $url")

/**
 * Walks an area's subtree and pins every response the browse UI will ask for:
 * the area and each sub-area (`show=` detail and topos), their children listings,
 * each route (detail and topos) and every topo image. Nodes are fetched in
 * batches of [BATCH] through `/api/node/ids` and split into per-node bodies, so
 * a crag with 300 routes costs about a dozen JSON calls plus one call per image.
 * All traffic goes through the app's normal client, hence its rate limiter.
 *
 * Without [refresh] a run skips what the store already has, so an interrupted
 * download resumes where it stopped.
 */
class FavouriteDownloader(private val http: OkHttpClient, private val store: OfflineStore, private val json: Json) {
    data class Progress(val done: Int, val total: Int)
    data class Result(val routeCount: Int, val topoCount: Int, val bytes: Long)

    suspend fun download(favId: Long, refresh: Boolean, onProgress: suspend (Progress) -> Unit): Result {
        val entries = LinkedHashMap<String, Entry>()
        if (!refresh) store.manifest(favId).forEach { entries[it.url] = it }
        var done = 0
        var total = 0
        suspend fun step() { done++; onProgress(Progress(done, maxOf(total, done))) }
        fun commit() = store.commit(favId, entries.values)

        val areaKeys = listOf(OfflineKeys::area, OfflineKeys::topos)
        val routeKeys = listOf(OfflineKeys::route, OfflineKeys::topos)
        val images = LinkedHashSet<String>()
        val routeIds = LinkedHashSet<Long>()

        val root = nodes(listOf(favId), AREA_BATCH_SHOW, areaKeys, refresh, entries).firstOrNull()
            ?: throw IOException("Area $favId not found")
        step()
        val pending = ArrayDeque(listOf(root))
        while (pending.isNotEmpty()) {
            val area = pending.removeFirst()
            area.topos.forEach { images += imageUrls(it) }
            val children = list(OfflineKeys.childAreas(area.id), refresh, entries)
            step()
            if (NodeRepository.mayHaveRoutes(area)) {
                list(OfflineKeys.childRoutes(area.id), refresh, entries).forEach { routeIds += it.id }
                step()
            }
            if (children.isNotEmpty()) {
                total += children.size * 2
                children.chunked(BATCH).forEach { chunk ->
                    val detailed = nodes(chunk.map { it.id }, AREA_BATCH_SHOW, areaKeys, refresh, entries).associateBy { it.id }
                    chunk.forEach { pending += detailed[it.id] ?: it }
                    step()
                }
            }
            commit()
        }

        val chunks = routeIds.toList().chunked(BATCH)
        total = done + chunks.size
        chunks.forEach { chunk ->
            nodes(chunk, ROUTE_BATCH_SHOW, routeKeys, refresh, entries).forEach { r ->
                r.topos.forEach { images += imageUrls(it) }
            }
            step()
            commit()
        }

        total = done + images.size
        images.forEachIndexed { i, url ->
            val previous = entries[url]
            if (refresh || previous == null || !store.file(url).exists()) {
                val (bytes, type) = fetch(url)
                entries[url] = store.put(url, bytes, type ?: "image/jpeg")
            }
            step()
            if (i % 10 == 9) commit()
        }
        commit()
        return Result(routeIds.size, images.size, store.bytesOf(favId))
    }

    /** A children listing, from the store when allowed, else fetched and pinned. */
    private suspend fun list(url: String, refresh: Boolean, entries: MutableMap<String, Entry>): List<NodeDto> {
        val stored = if (!refresh && entries.containsKey(url)) store.read(url) else null
        val bytes = stored ?: fetch(url).first.also { entries[url] = store.put(url, it, JSON) }
        return json.decodeFromString(DataList.serializer(NodeDto.serializer()), bytes.decodeToString()).data
    }

    /**
     * The nodes [ids], each pinned under every key in [keys] as a `{"data": node}`
     * body. One batch request unless the store already holds all of them.
     */
    private suspend fun nodes(
        ids: List<Long>,
        show: List<String>,
        keys: List<(Long) -> String>,
        refresh: Boolean,
        entries: MutableMap<String, Entry>,
    ): List<NodeDto> {
        if (ids.isEmpty()) return emptyList()
        if (!refresh && ids.all { id -> keys.all { k -> entries.containsKey(k(id)) && store.file(k(id)).exists() } }) {
            // The detail body may have been refreshed by the UI without `show=topo`; topos live under their own key.
            return ids.mapNotNull { id ->
                val detail = stored(keys.first()(id)) ?: return@mapNotNull null
                val topos = if (keys.size > 1) stored(keys.last()(id))?.topos ?: detail.topos else detail.topos
                detail.copy(topos = topos)
            }
        }
        val body = fetch(OfflineKeys.batch(ids, show)).first.decodeToString()
        val data = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray ?: return emptyList()
        return data.mapNotNull { el ->
            val id = el.idOrNull() ?: return@mapNotNull null
            val single = """{"data":$el}""".encodeToByteArray()
            keys.forEach { k -> entries[k(id)] = store.put(k(id), single, JSON) }
            json.decodeFromJsonElement(NodeDto.serializer(), el)
        }
    }

    /** The image URL the topo view will ask for, so the pinned key matches (rotation included). */
    private fun imageUrls(dto: TopoDto): List<String> = listOfNotNull(TopoMapper.fromDto(dto)?.imageUrl())

    private fun stored(url: String): NodeDto? =
        store.read(url)?.let { json.decodeFromString(DataObj.serializer(NodeDto.serializer()), it.decodeToString()).data }

    private fun JsonElement.idOrNull(): Long? = (this as? kotlinx.serialization.json.JsonObject)?.get("id")?.jsonPrimitive?.content?.toLongOrNull()

    private suspend fun fetch(url: String): Pair<ByteArray, String?> {
        val req = Request.Builder()
            .url(url)
            .cacheControl(CacheControl.Builder().noCache().noStore().build())
            .tag(OfflineDownload::class.java, OfflineDownload)
            .build()
        http.newCall(req).await().use { res ->
            val body = res.body
            if (!res.isSuccessful || body == null) throw DownloadHttpException(res.code, url)
            return body.bytes() to body.contentType()?.toString()
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (!cont.isCancelled) cont.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) { cont.resume(response) }
        })
        cont.invokeOnCancellation { cancel() }
    }

    companion object {
        const val BATCH = 50
        private const val JSON = "application/json"
        /** Superset of the per-node `show` lists, so one batch body serves both the detail and the topo key. */
        val AREA_BATCH_SHOW = (NodeRepository.AREA_SHOW + "topo").distinct()
        val ROUTE_BATCH_SHOW = (NodeRepository.ROUTE_SHOW + "topo").distinct()
    }
}
