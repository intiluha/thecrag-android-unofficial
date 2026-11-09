package xyz.intiluha.thecrag.ui.route

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import xyz.intiluha.thecrag.R
import xyz.intiluha.thecrag.TheCragApp
import xyz.intiluha.thecrag.data.repo.NodeRepository
import xyz.intiluha.thecrag.data.repo.RecentStore
import xyz.intiluha.thecrag.data.repo.TopoRepository
import xyz.intiluha.thecrag.domain.NodeKind
import xyz.intiluha.thecrag.domain.RouteDetail
import xyz.intiluha.thecrag.domain.Topo
import xyz.intiluha.thecrag.ui.common.Attribution
import xyz.intiluha.thecrag.ui.common.Breadcrumbs
import xyz.intiluha.thecrag.ui.common.GradeChip
import xyz.intiluha.thecrag.ui.common.SectionHeader
import xyz.intiluha.thecrag.ui.logbook.AscentRow
import xyz.intiluha.thecrag.ui.logbook.PendingRow
import xyz.intiluha.thecrag.ui.common.OverflowMenu
import xyz.intiluha.thecrag.ui.common.Stars
import xyz.intiluha.thecrag.ui.common.StateBox
import xyz.intiluha.thecrag.ui.common.UiState
import xyz.intiluha.thecrag.ui.common.describe
import xyz.intiluha.thecrag.ui.common.needsLogin
import xyz.intiluha.thecrag.ui.topo.TopoView

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
                state.value = UiState.Error(e.describe(), e.needsLogin()); return@launch
            }
            state.value = UiState.Ready(route)
            recents.record(route.id, route.name, NodeKind.ROUTE, listOfNotNull(route.grade, route.ancestors.lastOrNull()?.name).joinToString(" · "))
            topos.value = UiState.Loading
            topos.value = try {
                UiState.Ready(topoRepo.toposFor(id, force))
            } catch (e: Exception) {
                UiState.Error(e.describe(), e.needsLogin())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteScreen(routeId: Long, onOpenArea: (Long) -> Unit, onOpenRoute: (Long) -> Unit, onAccount: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val graph = TheCragApp.graph(context)
    val vm: RouteViewModel = viewModel(key = "route/$routeId") { RouteViewModel(graph.nodes, graph.topos, graph.recents, routeId) }
    val state by vm.state.collectAsState()
    val topos by vm.topos.collectAsState()
    val route = (state as? UiState.Ready)?.value
    val account by graph.auth.state.collectAsState()
    LaunchedEffect(account) { if (account != null && state is UiState.Error) vm.reload() }
    val myAscents by graph.logbook.ascents.collectAsState()
    val pending by graph.logbook.outbox.collectAsState()
    var showTick by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (route != null) ExtendedFloatingActionButton(
                onClick = { if (account != null) showTick = true else onAccount() },
                icon = { Icon(Icons.Default.Check, null) },
                text = { Text(stringResource(R.string.tick)) },
            )
        },
    ) { padding ->
        if (showTick && route != null) {
            TickSheet(route, graph.logbook, onDismiss = { showTick = false }) { msg ->
                showTick = false
                scope.launch { snackbar.showSnackbar(msg) }
            }
        }
        StateBox(state, onRetry = vm::reload, onLogin = onAccount) { r ->
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
                val mine = myAscents.filter { it.routeId == r.id }
                val minePending = pending.filter { it.routeId == r.id }
                if (mine.isNotEmpty() || minePending.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.your_ascents)) }
                    items(minePending, key = { "p${it.createdAt}" }) { PendingRow(it) {} }
                    items(mine, key = { "a${it.id}" }) { AscentRow(it, onClick = {}) }
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
