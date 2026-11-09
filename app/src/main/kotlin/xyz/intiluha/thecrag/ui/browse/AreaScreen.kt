package xyz.intiluha.thecrag.ui.browse

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import xyz.intiluha.thecrag.R
import xyz.intiluha.thecrag.TheCragApp
import xyz.intiluha.thecrag.data.logbook.LogbookRepository
import xyz.intiluha.thecrag.data.offline.FavouriteDownloadWorker
import xyz.intiluha.thecrag.data.offline.FavouritesRepository
import xyz.intiluha.thecrag.data.repo.RecentItem
import xyz.intiluha.thecrag.domain.AreaDetail
import xyz.intiluha.thecrag.domain.AreaSummary
import xyz.intiluha.thecrag.domain.LatLon
import xyz.intiluha.thecrag.domain.NodeKind
import xyz.intiluha.thecrag.domain.RouteSummary
import xyz.intiluha.thecrag.domain.Topo
import xyz.intiluha.thecrag.domain.WORLD_ID
import xyz.intiluha.thecrag.ui.common.Attribution
import xyz.intiluha.thecrag.ui.common.Breadcrumbs
import xyz.intiluha.thecrag.ui.common.GradeChip
import xyz.intiluha.thecrag.ui.common.OverflowMenu
import xyz.intiluha.thecrag.ui.common.RecentRow
import xyz.intiluha.thecrag.ui.common.SectionHeader
import xyz.intiluha.thecrag.ui.common.Stars
import xyz.intiluha.thecrag.ui.common.StateBox
import xyz.intiluha.thecrag.ui.common.UiState
import xyz.intiluha.thecrag.ui.common.formatBytes
import xyz.intiluha.thecrag.ui.topo.TopoView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AreaScreen(
    areaId: Long,
    onOpenArea: (Long) -> Unit,
    onOpenRoute: (Long) -> Unit,
    onSearch: (ancestorId: Long, ancestorName: String) -> Unit,
    onOpenMap: (LatLon?, Int) -> Unit,
    onAbout: () -> Unit,
    onFavourites: () -> Unit,
    onLogbook: () -> Unit,
    onAccount: () -> Unit,
    onBack: (() -> Unit)?,
) {
    val context = LocalContext.current
    val graph = TheCragApp.graph(context)
    val vm: AreaViewModel = viewModel(key = "area/$areaId") { AreaViewModel(graph.nodes, graph.topos, graph.recents, areaId) }
    val state by vm.state.collectAsState()
    val topos by vm.topos.collectAsState()
    val recents by graph.recents.items.collectAsState()
    val favourites by graph.favourites.favourites.collectAsState()
    val account by graph.auth.state.collectAsState()
    // Coming back from a successful login: load what the anonymous attempt could not.
    LaunchedEffect(account) { if (account != null && state is UiState.Error) vm.reload() }
    val ascents by graph.logbook.ascents.collectAsState()
    val sentIds = remember(ascents) { LogbookRepository.sentRouteIds(ascents) }
    // A view of the list, not a setting: nothing stored, survives rotation only.
    var unclimbedOnly by rememberSaveable { mutableStateOf(false) }
    val area = (state as? UiState.Ready)?.value
    val title = area?.name ?: stringResource(R.string.world)
    val starred = favourites.any { it.id == areaId }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmStar by remember { mutableStateOf(false) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val tooLarge = area?.let { stringResource(R.string.offline_too_large, it.name, it.numberRoutes, FavouritesRepository.MAX_ROUTES) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                actions = {
                    if (area != null && area.id != WORLD_ID) {
                        IconButton(onClick = {
                            when {
                                starred -> onFavourites()
                                area.numberRoutes > FavouritesRepository.MAX_ROUTES -> scope.launch { snackbar.showSnackbar(tooLarge ?: "") }
                                else -> confirmStar = true
                            }
                        }) {
                            Icon(
                                if (starred) Icons.Default.Star else Icons.Default.StarBorder,
                                stringResource(if (starred) R.string.starred else R.string.star),
                            )
                        }
                    }
                    if (area != null && (area.location != null || area.id == WORLD_ID)) {
                        IconButton(onClick = { onOpenMap(area.location, mapZoomFor(area.subType)) }) { Icon(Icons.Default.Map, "Map") }
                    }
                    IconButton(onClick = { onSearch(areaId, title) }) { Icon(Icons.Default.Search, stringResource(R.string.search)) }
                    OverflowMenu(
                        stringResource(R.string.refresh) to vm::reload,
                        stringResource(R.string.offline) to onFavourites,
                        stringResource(R.string.logbook) to onLogbook,
                        stringResource(R.string.account) to onAccount,
                        stringResource(R.string.about) to onAbout,
                    )
                },
            )
        },
    ) { padding ->
        StateBox(state, onRetry = vm::reload, onLogin = onAccount) { a ->
            AreaBody(
                a, topos, if (a.id == WORLD_ID) recents else emptyList(), onOpenArea, onOpenRoute,
                if (ascents.isEmpty()) null else sentIds, unclimbedOnly, { unclimbedOnly = it }, Modifier.padding(padding),
            )
        }
    }

    if (confirmStar && area != null) {
        val size = formatBytes(FavouritesRepository.estimateBytes(area.numberRoutes, area.numberTopos))
        AlertDialog(
            onDismissRequest = { confirmStar = false },
            title = { Text(stringResource(R.string.offline_confirm_title, area.name)) },
            text = { Text(stringResource(R.string.offline_confirm, area.numberRoutes, area.numberTopos, size)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmStar = false
                    graph.favourites.add(area)
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    FavouriteDownloadWorker.enqueue(context, area.id, refresh = false)
                }) { Text(stringResource(R.string.download)) }
            },
            dismissButton = { TextButton(onClick = { confirmStar = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun AreaBody(
    area: AreaDetail,
    topos: UiState<List<Topo>>,
    recents: List<RecentItem>,
    onOpenArea: (Long) -> Unit,
    onOpenRoute: (Long) -> Unit,
    /** Routes with a clean ascent in the logbook; null when there is no logbook, which hides the view switch. */
    sentIds: Set<Long>?,
    unclimbedOnly: Boolean,
    onUnclimbedOnly: (Boolean) -> Unit,
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
            val shown = if (unclimbedOnly && sentIds != null) area.routes.filter { it.id !in sentIds } else area.routes
            item { RoutesHeader(sentIds != null, unclimbedOnly, onUnclimbedOnly) }
            if (shown.isEmpty()) {
                item { Text(stringResource(R.string.all_climbed), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
            }
            items(shown, key = { "r${it.id}" }) { RouteRow(it) { onOpenRoute(it.id) } }
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

/**
 * "Routes" with, once a logbook is loaded, a two-way view of the list: all routes, or only
 * those without a clean ascent. A view, not a setting: nothing is stored.
 */
@Composable
private fun RoutesHeader(hasLogbook: Boolean, unclimbedOnly: Boolean, onUnclimbedOnly: (Boolean) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Routes",
                Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (hasLogbook) {
                SingleChoiceSegmentedButtonRow(Modifier.padding(end = 16.dp, top = 8.dp)) {
                    SegmentedButton(
                        selected = !unclimbedOnly,
                        onClick = { onUnclimbedOnly(false) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text(stringResource(R.string.view_all)) }
                    SegmentedButton(
                        selected = unclimbedOnly,
                        onClick = { onUnclimbedOnly(true) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text(stringResource(R.string.view_unclimbed)) }
                }
            }
        }
        HorizontalDivider()
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
