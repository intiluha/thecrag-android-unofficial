package xyz.intiluha.thecrag.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import xyz.intiluha.thecrag.R
import xyz.intiluha.thecrag.TheCragApp
import xyz.intiluha.thecrag.data.repo.NodeRepository
import xyz.intiluha.thecrag.data.repo.RecentStore
import xyz.intiluha.thecrag.domain.NodeKind
import xyz.intiluha.thecrag.domain.SearchHit
import xyz.intiluha.thecrag.domain.WORLD_ID
import xyz.intiluha.thecrag.ui.common.RecentRow
import xyz.intiluha.thecrag.ui.common.SectionHeader
import xyz.intiluha.thecrag.ui.common.describe

private const val MIN_QUERY = 2

class SearchViewModel(private val repo: NodeRepository, recents: RecentStore, private val scopeId: Long) : ViewModel() {
    val query = MutableStateFlow("")
    /** Restrict the lookup to [scopeId] (the area the search was opened from) rather than the whole world. */
    val scoped = MutableStateFlow(scopeId != WORLD_ID)
    val results = MutableStateFlow<List<SearchHit>>(emptyList())
    val error = MutableStateFlow<String?>(null)
    val searching = MutableStateFlow(false)
    val recent = recents.items

    init {
        viewModelScope.launch {
            @OptIn(FlowPreview::class)
            combine(query, scoped) { q, s -> q.trim() to s }
                .debounce(350)
                .distinctUntilChanged()
                .collectLatest { (q, s) ->
                    error.value = null
                    if (q.length < MIN_QUERY) { results.value = emptyList(); searching.value = false; return@collectLatest }
                    searching.value = true
                    try {
                        results.value = repo.search(q, if (s) scopeId else WORLD_ID)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error.value = e.describe()
                    }
                    searching.value = false
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(scopeId: Long, scopeName: String?, onOpenArea: (Long) -> Unit, onOpenRoute: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val graph = TheCragApp.graph(context)
    val vm: SearchViewModel = viewModel(key = "search/$scopeId") { SearchViewModel(graph.nodes, graph.recents, scopeId) }
    val query by vm.query.collectAsState()
    val scoped by vm.scoped.collectAsState()
    val results by vm.results.collectAsState()
    val error by vm.error.collectAsState()
    val searching by vm.searching.collectAsState()
    val recent by vm.recent.collectAsState()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { vm.query.value = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        trailingIcon = if (query.isNotEmpty()) {
                            { IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Default.Close, stringResource(R.string.clear)) } }
                        } else null,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp).focusRequester(focus),
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (scopeName != null && scopeId != WORLD_ID) {
                Row(Modifier.padding(horizontal = 16.dp)) {
                    FilterChip(
                        selected = scoped,
                        onClick = { vm.scoped.value = !scoped },
                        label = { Text(stringResource(R.string.search_in, scopeName)) },
                        leadingIcon = if (scoped) ({ Icon(Icons.Default.Check, null) }) else null,
                    )
                }
            }
            if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.fillMaxSize()) {
                error?.let { item { Text(it, Modifier.padding(16.dp)) } }
                when {
                    query.trim().length < MIN_QUERY -> if (recent.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.recent)) }
                        items(recent, key = { "rc${it.id}" }) { r ->
                            RecentRow(r) { if (r.kind == NodeKind.ROUTE) onOpenRoute(r.id) else onOpenArea(r.id) }
                        }
                    }
                    results.isEmpty() && !searching && error == null -> item { Text(stringResource(R.string.no_matches), Modifier.padding(16.dp)) }
                    else -> items(results, key = { it.id }) { hit ->
                        ListItem(
                            modifier = Modifier.clickable {
                                if (hit.kind == NodeKind.ROUTE) onOpenRoute(hit.id) else onOpenArea(hit.id)
                            },
                            headlineContent = { Text(hit.name) },
                            supportingContent = {
                                Text(listOfNotNull(if (hit.kind == NodeKind.ROUTE) "route" else "area", hit.qualifier).joinToString(" "))
                            },
                        )
                    }
                }
            }
        }
    }
}
