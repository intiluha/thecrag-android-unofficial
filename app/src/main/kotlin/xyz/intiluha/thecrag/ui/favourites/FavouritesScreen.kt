package xyz.intiluha.thecrag.ui.favourites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import xyz.intiluha.thecrag.R
import xyz.intiluha.thecrag.TheCragApp
import xyz.intiluha.thecrag.data.offline.Favourite
import xyz.intiluha.thecrag.data.offline.FavouriteDownloadWorker
import xyz.intiluha.thecrag.data.offline.FavouriteStatus
import xyz.intiluha.thecrag.ui.common.formatBytes
import java.text.DateFormat
import java.util.Date

/** Starred areas with their download state; Update re-downloads, Remove drops the copy. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavouritesScreen(onOpenArea: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val graph = TheCragApp.graph(context)
    val favourites by graph.favourites.favourites.collectAsState()
    var removing by remember { mutableStateOf<Favourite?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.offline)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { Text(stringResource(R.string.offline_blurb), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
            if (favourites.isEmpty()) {
                item { Text(stringResource(R.string.offline_empty), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
            }
            items(favourites, key = { it.id }) { f ->
                FavouriteRow(
                    f,
                    onOpen = { onOpenArea(f.id) },
                    onUpdate = { FavouriteDownloadWorker.enqueue(context, f.id, refresh = true) },
                    onRetry = { FavouriteDownloadWorker.enqueue(context, f.id, refresh = false) },
                    onRemove = { removing = f },
                )
            }
            if (favourites.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.offline_total, formatBytes(favourites.sumOf { it.bytes })),
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }

    removing?.let { f ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.remove_favourite, f.name)) },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    FavouriteDownloadWorker.cancel(context, f.id)
                    graph.favourites.remove(f.id)
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun FavouriteRow(f: Favourite, onOpen: () -> Unit, onUpdate: () -> Unit, onRetry: () -> Unit, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        ListItem(
            overlineContent = { Text(f.subType) },
            headlineContent = { Text(f.name) },
            supportingContent = { Text(statusLine(f)) },
        )
        if (f.status == FavouriteStatus.DOWNLOADING) {
            val m = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            if (f.total > 0) LinearProgressIndicator(progress = { f.done.toFloat() / f.total }, modifier = m) else LinearProgressIndicator(m)
        }
        Row(Modifier.padding(horizontal = 8.dp)) {
            if (f.status == FavouriteStatus.READY) TextButton(onClick = onUpdate) { Text(stringResource(R.string.update)) }
            if (f.status == FavouriteStatus.FAILED) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            TextButton(onClick = onRemove) { Text(stringResource(R.string.remove)) }
        }
        HorizontalDivider()
    }
}

@Composable
private fun statusLine(f: Favourite): String = when (f.status) {
    FavouriteStatus.QUEUED -> stringResource(R.string.status_queued)
    FavouriteStatus.DOWNLOADING ->
        if (f.total > 0) stringResource(R.string.status_downloading, f.done, f.total) else stringResource(R.string.status_scanning)
    FavouriteStatus.READY -> listOfNotNull(
        "${f.routeCount} routes",
        "${f.topoCount} topos",
        formatBytes(f.bytes),
        f.updatedAt?.let { "updated " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) },
    ).joinToString(" · ")
    FavouriteStatus.FAILED -> stringResource(R.string.status_failed, f.error ?: "")
}
