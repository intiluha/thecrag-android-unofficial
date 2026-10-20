package org.thecragunofficial.android.ui.browse

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
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
import androidx.lifecycle.viewmodel.compose.viewModel
import org.thecragunofficial.android.R
import org.thecragunofficial.android.TheCragApp
import org.thecragunofficial.android.data.repo.RecentItem
import org.thecragunofficial.android.domain.AreaDetail
import org.thecragunofficial.android.domain.AreaSummary
import org.thecragunofficial.android.domain.LatLon
import org.thecragunofficial.android.domain.NodeKind
import org.thecragunofficial.android.domain.RouteSummary
import org.thecragunofficial.android.domain.Topo
import org.thecragunofficial.android.domain.WORLD_ID
import org.thecragunofficial.android.ui.common.Attribution
import org.thecragunofficial.android.ui.common.Breadcrumbs
import org.thecragunofficial.android.ui.common.GradeChip
import org.thecragunofficial.android.ui.common.OverflowMenu
import org.thecragunofficial.android.ui.common.RecentRow
import org.thecragunofficial.android.ui.common.SectionHeader
import org.thecragunofficial.android.ui.common.Stars
import org.thecragunofficial.android.ui.common.StateBox
import org.thecragunofficial.android.ui.common.UiState
import org.thecragunofficial.android.ui.topo.TopoView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AreaScreen(
    areaId: Long,
    onOpenArea: (Long) -> Unit,
    onOpenRoute: (Long) -> Unit,
    onSearch: (ancestorId: Long, ancestorName: String) -> Unit,
    onOpenMap: (LatLon?, Int) -> Unit,
    onAbout: () -> Unit,
    onBack: (() -> Unit)?,
) {
    val context = LocalContext.current
    val graph = TheCragApp.graph(context)
    val vm: AreaViewModel = viewModel(key = "area/$areaId") { AreaViewModel(graph.nodes, graph.topos, graph.recents, areaId) }
    val state by vm.state.collectAsState()
    val topos by vm.topos.collectAsState()
    val recents by graph.recents.items.collectAsState()
    val area = (state as? UiState.Ready)?.value
    val title = area?.name ?: stringResource(R.string.world)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                actions = {
                    if (area != null && (area.location != null || area.id == WORLD_ID)) {
                        IconButton(onClick = { onOpenMap(area.location, mapZoomFor(area.subType)) }) { Icon(Icons.Default.Map, "Map") }
                    }
                    IconButton(onClick = { onSearch(areaId, title) }) { Icon(Icons.Default.Search, stringResource(R.string.search)) }
                    OverflowMenu(
                        stringResource(R.string.refresh) to vm::reload,
                        stringResource(R.string.about) to onAbout,
                    )
                },
            )
        },
    ) { padding ->
        StateBox(state, onRetry = vm::reload) { a ->
            AreaBody(a, topos, if (a.id == WORLD_ID) recents else emptyList(), onOpenArea, onOpenRoute, Modifier.padding(padding))
        }
    }
}

@Composable
private fun AreaBody(
    area: AreaDetail,
    topos: UiState<List<Topo>>,
    recents: List<RecentItem>,
    onOpenArea: (Long) -> Unit,
    onOpenRoute: (Long) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(modifier.fillMaxSize()) {
        item { Breadcrumbs(area.ancestors, onOpenArea) }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(area.subType, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                val stats = buildList {
                    add("${area.numberRoutes} routes")
                    if (area.numberTopos > 0) add("${area.numberTopos} topos")
                    area.averageHeight?.let { add("avg $it") }
                    if (area.ascentCount > 0) add("${area.ascentCount} ascents")
                }
                Text(stats.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                area.blurb?.let { Text(it, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium) }
            }
        }
        if (recents.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.recent)) }
            items(recents, key = { "rc${it.id}" }) { r ->
                RecentRow(r) { if (r.kind == NodeKind.ROUTE) onOpenRoute(r.id) else onOpenArea(r.id) }
            }
        }
        when (val t = topos) {
            UiState.Loading -> item {
                Column {
                    SectionHeader(stringResource(R.string.topos))
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                }
            }
            is UiState.Error -> item {
                Column {
                    SectionHeader(stringResource(R.string.topos))
                    Text(stringResource(R.string.topos_failed, t.message), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            is UiState.Ready -> if (t.value.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.topos)) }
                items(t.value, key = { "t${it.id}" }) { topo ->
                    TopoView(topo, selectedRouteId = null, onRouteTap = onOpenRoute, onAreaTap = onOpenArea, Modifier.padding(vertical = 4.dp))
                }
            }
        }
        if (area.subAreas.isNotEmpty()) {
            item { SectionHeader("Areas") }
            items(area.subAreas, key = { "a${it.id}" }) { AreaRow(it) { onOpenArea(it.id) } }
        }
        if (area.routes.isNotEmpty()) {
            item { SectionHeader("Routes") }
            items(area.routes, key = { "r${it.id}" }) { RouteRow(it) { onOpenRoute(it.id) } }
        }
        area.beta.forEach { section ->
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(section.name, style = MaterialTheme.typography.titleSmall)
                    Text(section.text, style = MaterialTheme.typography.bodyMedium)
                    section.publisher?.let { Text("Source: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) }
                }
            }
        }
        item { Attribution() }
    }
}

@Composable
private fun AreaRow(a: AreaSummary, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(a.name) },
        supportingContent = {
            val parts = buildList {
                add(a.subType)
                if (a.numberRoutes > 0) add("${a.numberRoutes} routes")
                if (a.hasTopo) add("topo")
            }
            Text(parts.joinToString(" · "))
        },
    )
}

@Composable
private fun RouteRow(r: RouteSummary, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(r.label ?: "", Modifier.width(32.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        GradeChip(r.grade, r.gradeBand, Modifier.width(56.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            val parts = buildList {
                r.style?.let { add(it) }
                r.height?.let { add(it.toString()) }
                r.bolts?.let { if (it > 0) add("$it bolts") }
                r.pitches?.let { if (it > 1) add("$it pitches") }
            }
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Stars(r.stars)
    }
}

/** A sensible starting zoom when opening the map from an area of this kind. */
fun mapZoomFor(subType: String): Int = when (subType) {
    "Region" -> 5
    "Crag", "Cliff", "Sector", "Boulder", "Field", "Wall" -> 14
    else -> 11
}
