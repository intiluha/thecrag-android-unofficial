package xyz.intiluha.thecrag.data.repo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import xyz.intiluha.thecrag.data.remote.KeyValueStore
import xyz.intiluha.thecrag.domain.NodeKind
import xyz.intiluha.thecrag.domain.WORLD_ID

@Serializable
data class RecentItem(
    val id: Long,
    val name: String,
    val kind: NodeKind,
    /** One line of context, e.g. "Crag · Arapiles" or "25 · Taipan Wall". */
    val detail: String?,
    val at: Long,
)

/** The last few areas and routes the user opened, newest first, kept in preferences. */
class RecentStore(
    private val store: KeyValueStore,
    private val json: Json,
    private val limit: Int = 40,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<RecentItem>> = _items

    @Synchronized
    fun record(id: Long, name: String, kind: NodeKind, detail: String?) {
        if (id == WORLD_ID) return
        val next = listOf(RecentItem(id, name, kind, detail, clock())) + _items.value.filterNot { it.id == id }
        _items.value = next.take(limit)
        store.put(KEY, json.encodeToString(SERIALIZER, _items.value))
    }

    @Synchronized
    fun clear() {
        _items.value = emptyList()
        store.put(KEY, "[]")
    }

    private fun load(): List<RecentItem> =
        store.get(KEY)?.let { runCatching { json.decodeFromString(SERIALIZER, it) }.getOrNull() } ?: emptyList()

    companion object {
        const val KEY = "thecrag.recent"
        private val SERIALIZER = ListSerializer(RecentItem.serializer())
    }
}
