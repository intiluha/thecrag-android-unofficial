package org.thecragunofficial.android.ui.route

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.thecragunofficial.android.R
import org.thecragunofficial.android.TheCragApp
import org.thecragunofficial.android.data.repo.NodeRepository
import org.thecragunofficial.android.data.repo.RecentStore
import org.thecragunofficial.android.data.repo.TopoRepository
import org.thecragunofficial.android.domain.NodeKind
import org.thecragunofficial.android.domain.RouteDetail
import org.thecragunofficial.android.domain.Topo
import org.thecragunofficial.android.ui.common.Attribution
import org.thecragunofficial.android.ui.common.Breadcrumbs
import org.thecragunofficial.android.ui.common.GradeChip
import org.thecragunofficial.android.ui.common.OverflowMenu
import org.thecragunofficial.android.ui.common.Stars
import org.thecragunofficial.android.ui.common.StateBox
import org.thecragunofficial.android.ui.common.UiState
import org.thecragunofficial.android.ui.common.describe
import org.thecragunofficial.android.ui.topo.TopoView

class RouteViewModel(
    private val repo: NodeRepository,
    private val topoRepo: TopoRepository,
    private val recents: RecentStore,
    private val id: Long,
) : ViewModel() {
    val state = MutableStateFlow<UiState<RouteDetail>>(UiState.Loading)
    val topos = MutableStateFlow<UiState<List<Topo>>>(UiState.Loading)

    init { load(false) }

    fun reload() = load(true)

    private fun load(force: Boolean) {
        state.value = UiState.Loading
        viewModelScope.launch {
            val route = try {
                repo.route(id, force)
            } catch (e: Exception) {
                state.value = UiState.Error(e.describe()); return@launch
            }
            state.value = UiState.Ready(route)
            recents.record(route.id, route.name, NodeKind.ROUTE, listOfNotNull(route.grade, route.ancestors.lastOrNull()?.name).joinToString(" · "))
            topos.value = UiState.Loading
            topos.value = try {
                UiState.Ready(topoRepo.toposFor(NodeKind.ROUTE, id, force))
            } catch (e: Exception) {
                UiState.Error(e.describe())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteScreen(routeId: Long, onOpenArea: (Long) -> Unit, onOpenRoute: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val graph = TheCragApp.graph(context)
    val vm: RouteViewModel = viewModel(key = "route/$routeId") { RouteViewModel(graph.nodes, graph.topos, graph.recents, routeId) }
    val state by vm.state.collectAsState()
    val topos by vm.topos.collectAsState()
    val route = (state as? UiState.Ready)?.value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(route?.name ?: "", maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    if (route != null) IconButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(route.webUrl)))
                    }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.open_on_thecrag)) }
                    OverflowMenu(stringResource(R.string.refresh) to vm::reload)
                },
            )
        },
    ) { padding ->
        StateBox(state, onRetry = vm::reload) { r ->
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item { Breadcrumbs(r.ancestors, onOpenArea) }
                item {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GradeChip(r.grade, r.gradeBand)
                            Spacer(Modifier.width(12.dp))
                            Stars(r.stars)
                            Spacer(Modifier.width(12.dp))
                            r.style?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                        }
                        val facts = buildList {
                            r.gradeContext?.let { add("grade context $it") }
                            r.height?.let { add("$it") }
                            r.bolts?.let { if (it > 0) add("$it bolts") }
                            r.pitches?.let { if (it > 1) add("$it pitches") }
                            add("${r.ascentCount} ascents")
                        }
                        Text(facts.joinToString(" · "), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
                        r.firstAscent?.let { Text("FA: $it", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                when (val t = topos) {
                    UiState.Loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
                    is UiState.Error -> item {
                        Text(stringResource(R.string.topos_failed, t.message), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                    is UiState.Ready -> items(t.value, key = { "t${it.id}" }) { topo ->
                        TopoView(
                            topo,
                            selectedRouteId = r.id,
                            onRouteTap = { if (it != r.id) onOpenRoute(it) },
                            onAreaTap = onOpenArea,
                            Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
                r.beta.forEach { s ->
                    item {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(s.name, style = MaterialTheme.typography.titleSmall)
                            Text(s.text, style = MaterialTheme.typography.bodyMedium)
                            s.publisher?.let { Text("Source: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) }
                        }
                    }
                }
                item { Attribution() }
            }
        }
    }
}
