package org.thecragunofficial.android.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.thecragunofficial.android.TheCragApp
import org.thecragunofficial.android.data.repo.NodeRepository
import org.thecragunofficial.android.domain.NodeKind
import org.thecragunofficial.android.domain.SearchHit
import org.thecragunofficial.android.ui.common.describe

class SearchViewModel(private val repo: NodeRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val results = MutableStateFlow<List<SearchHit>>(emptyList())
    val error = MutableStateFlow<String?>(null)

    @OptIn(FlowPreview::class)
    private val job = query.debounce(350).distinctUntilChanged().onEach { q ->
        error.value = null
        if (q.length < 2) { results.value = emptyList(); return@onEach }
        try { results.value = repo.search(q) } catch (e: Exception) { error.value = e.describe() }
    }.launchIn(viewModelScope)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onOpenArea: (Long) -> Unit, onOpenRoute: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val vm: SearchViewModel = viewModel { SearchViewModel(TheCragApp.graph(context).nodes) }
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val error by vm.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { vm.query.value = it },
                        singleLine = true,
                        placeholder = { Text("Crag or route name") },
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            error?.let { item { Text(it, Modifier.padding(16.dp)) } }
            items(results, key = { it.id }) { hit ->
                ListItem(
                    modifier = Modifier.clickable {
                        if (hit.kind == NodeKind.ROUTE) onOpenRoute(hit.id) else onOpenArea(hit.id)
                    },
                    headlineContent = { Text(hit.name) },
                    supportingContent = { Text(listOfNotNull(if (hit.kind == NodeKind.ROUTE) "route" else "area", hit.qualifier).joinToString(" ")) },
                )
            }
        }
    }
}
