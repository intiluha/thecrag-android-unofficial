package xyz.intiluha.thecrag.data.offline

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

enum class FavouriteStatus { QUEUED, DOWNLOADING, READY, FAILED }

/** A starred area and the state of its offline copy. */
@Serializable
data class Favourite(
    val id: Long,
    val name: String,
    val subType: String,
    val numberRoutes: Int,
    val numberTopos: Int,
    val addedAt: Long,
    val status: FavouriteStatus = FavouriteStatus.QUEUED,
    val done: Int = 0,
    val total: Int = 0,
    val routeCount: Int = 0,
    val topoCount: Int = 0,
    val bytes: Long = 0,
    val updatedAt: Long? = null,
    val error: String? = null,
)

/** One pinned response: the exact request URL the app will ask for, and the content type to answer with. */
@Serializable
data class Entry(val url: String, val contentType: String)

@Serializable
private data class Manifest(val entries: List<Entry> = emptyList())

@Serializable
private data class Index(val favourites: List<Favourite> = emptyList())

/**
 * Pinned copies of HTTP responses, keyed by request URL, for the starred areas.
 * Unlike the OkHttp disk cache nothing here is ever evicted: a blob lives until
 * no favourite's manifest lists its URL. Layout under [dir]:
 * `index.json` (the favourites), `fav_{id}.json` (that favourite's URLs),
 * `blobs/{sha1(url)}` (the bodies). Safe to call from any thread.
 */
class OfflineStore(private val dir: File, private val json: Json) {
    private val blobs = File(dir, "blobs")
    private val indexFile = File(dir, "index.json")
    private val lock = Any()
    private val pinned = HashMap<String, Entry>()
    private val manifests = HashMap<Long, List<Entry>>()
    private val _favourites = MutableStateFlow<List<Favourite>>(emptyList())
    val favourites: StateFlow<List<Favourite>> = _favourites

    init {
        blobs.mkdirs()
        val index = indexFile.takeIf { it.exists() }?.let { f -> runCatching { json.decodeFromString(Index.serializer(), f.readText()) }.getOrNull() }
        _favourites.value = index?.favourites?.sortedBy { it.addedAt } ?: emptyList()
        for (f in _favourites.value) manifests[f.id] = readManifest(f.id)
        rebuildPinned()
    }

    fun entry(url: String): Entry? = synchronized(lock) { pinned[url] }
    fun has(url: String): Boolean = synchronized(lock) { pinned.containsKey(url) }
    fun file(url: String): File = File(blobs, sha1(url))

    /** The stored body for [url], or null when there is none (pinned or not). */
    fun read(url: String): ByteArray? = file(url).takeIf { it.exists() }?.readBytes()

    /** Writes the body for [url]. It is served only once a manifest lists it, see [commit]. */
    fun put(url: String, bytes: ByteArray, contentType: String): Entry {
        val target = file(url)
        val tmp = File(blobs, target.name + ".tmp")
        tmp.writeBytes(bytes)
        synchronized(lock) {
            if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
        }
        return Entry(url, contentType)
    }

    fun manifest(id: Long): List<Entry> = synchronized(lock) { manifests[id] ?: emptyList() }

    /** Replaces the URL list of favourite [id]; blobs no favourite references any more are deleted. */
    fun commit(id: Long, entries: Collection<Entry>) {
        synchronized(lock) {
            if (_favourites.value.none { it.id == id }) return
            val list = entries.distinctBy { it.url }
            manifestFile(id).writeText(json.encodeToString(Manifest.serializer(), Manifest(list)))
            manifests[id] = list
            rebuildPinned()
            sweep()
        }
    }

    fun bytesOf(id: Long): Long = manifest(id).sumOf { file(it.url).length() }
    fun totalBytes(): Long = synchronized(lock) { pinned.keys.sumOf { file(it).length() } }

    fun favourite(id: Long): Favourite? = _favourites.value.firstOrNull { it.id == id }

    fun upsert(f: Favourite) {
        synchronized(lock) {
            _favourites.value = (_favourites.value.filter { it.id != f.id } + f).sortedBy { it.addedAt }
            saveIndex()
        }
    }

    fun update(id: Long, change: (Favourite) -> Favourite) {
        synchronized(lock) {
            if (_favourites.value.none { it.id == id }) return
            _favourites.value = _favourites.value.map { if (it.id == id) change(it) else it }
            saveIndex()
        }
    }

    fun remove(id: Long) {
        synchronized(lock) {
            _favourites.value = _favourites.value.filter { it.id != id }
            manifests.remove(id)
            manifestFile(id).delete()
            saveIndex()
            rebuildPinned()
            sweep()
        }
    }

    private fun manifestFile(id: Long) = File(dir, "fav_$id.json")

    private fun readManifest(id: Long): List<Entry> =
        manifestFile(id).takeIf { it.exists() }?.let { f -> runCatching { json.decodeFromString(Manifest.serializer(), f.readText()).entries }.getOrNull() } ?: emptyList()

    private fun saveIndex() {
        dir.mkdirs()
        indexFile.writeText(json.encodeToString(Index.serializer(), Index(_favourites.value)))
    }

    private fun rebuildPinned() {
        pinned.clear()
        manifests.values.forEach { list -> list.forEach { pinned[it.url] = it } }
    }

    private fun sweep() {
        val keep = pinned.keys.mapTo(HashSet()) { sha1(it) }
        blobs.listFiles()?.forEach { if (it.name !in keep && !it.name.endsWith(".tmp")) it.delete() }
    }

    private companion object {
        fun sha1(s: String): String =
            MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
