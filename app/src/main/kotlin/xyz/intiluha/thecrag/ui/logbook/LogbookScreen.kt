package xyz.intiluha.thecrag.ui.logbook

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.intiluha.thecrag.R
import xyz.intiluha.thecrag.TheCragApp
import xyz.intiluha.thecrag.data.logbook.Ascent
import xyz.intiluha.thecrag.data.logbook.LogbookCsv
import xyz.intiluha.thecrag.data.logbook.TickRequest
import xyz.intiluha.thecrag.data.logbook.TickTypes
import xyz.intiluha.thecrag.ui.account.AccountViewModel
import xyz.intiluha.thecrag.ui.common.GradeChip
import xyz.intiluha.thecrag.ui.common.SectionHeader
import xyz.intiluha.thecrag.ui.route.TickSheet
import xyz.intiluha.thecrag.ui.route.WithRoute
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogbookScreen(onOpenRoute: (Long) -> Unit, onAccount: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val graph = TheCragApp.graph(context)
    val vm: AccountViewModel = viewModel { AccountViewModel(graph.auth, graph.logbook) }
    val account by graph.auth.state.collectAsState()
    val ascents by graph.logbook.ascents.collectAsState()
    val outbox by graph.logbook.outbox.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    var selected by remember { mutableStateOf<Ascent?>(null) }
    var editing by remember { mutableStateOf<Ascent?>(null) }
    var selectedPending by remember { mutableStateOf<TickRequest?>(null) }
    var editingPending by remember { mutableStateOf<TickRequest?>(null) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val exportedText = stringResource(R.string.exported, ascents.size)
    // A save dialog (defaults to Downloads); the CSV is written from the local copy, nothing is fetched.
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        exportMessage = try {
            context.contentResolver.openOutputStream(uri)!!.bufferedWriter().use { LogbookCsv.write(ascents, it) }
            exportedText
        } catch (e: Exception) {
            context.getString(R.string.export_failed, e.message ?: e.javaClass.simpleName)
        }
    }
    selected?.let { a ->
        AscentActionsDialog(
            a.routeName, listOfNotNull(a.tickName, a.date, a.cragName).joinToString(" · "), stringResource(R.string.delete),
            onEdit = { editing = a; selected = null }, onDelete = { vm.delete(a.id); selected = null }, onDismiss = { selected = null },
        )
    }
    selectedPending?.let { p ->
        AscentActionsDialog(
            p.routeName, listOf(TickTypes.name(p.tick), p.date, stringResource(if (p.ascentId != null) R.string.pending_edit else R.string.pending)).joinToString(" · "),
            stringResource(R.string.remove),
            onEdit = { editingPending = p; selectedPending = null }, onDelete = { graph.logbook.removeFromOutbox(p); selectedPending = null },
            onDismiss = { selectedPending = null },
        )
    }
    editing?.let { a ->
        WithRoute(a.routeId, graph.nodes, onFailed = { editing = null; vm.message.value = it }) { route ->
            TickSheet(route, graph.logbook, existing = a, onDismiss = { editing = null }) { msg -> editing = null; vm.message.value = msg }
        }
    }
    editingPending?.let { p ->
        WithRoute(p.routeId, graph.nodes, onFailed = { editingPending = null; vm.message.value = it }) { route ->
            TickSheet(route, graph.logbook, pending = p, onDismiss = { editingPending = null }) { msg -> editingPending = null; vm.message.value = msg }
        }
    }
    val sorted = ascents.sortedWith(compareByDescending<Ascent> { it.date ?: "" }.thenByDescending { it.id })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.logbook)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    if (account != null) IconButton(onClick = vm::refresh, enabled = !busy) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh_logbook)) }
                    if (ascents.isNotEmpty()) {
                        IconButton(onClick = { exportCsv.launch(LogbookCsv.fileName(LocalDate.now().toString())) }) {
                            Icon(Icons.Default.Download, stringResource(R.string.export_csv))
                        }
                    }
                    IconButton(onClick = onAccount) { Icon(Icons.Default.AccountCircle, stringResource(R.string.account)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            (exportMessage ?: message)?.let { Text(it, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
            if (account == null) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.not_logged_in))
                    Button(onClick = onAccount, Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.login)) }
                }
                return@Column
            }
            LazyColumn(Modifier.fillMaxSize()) {
                if (outbox.isNotEmpty()) {
                    item { SectionHeader("Waiting to be sent") }
                    items(outbox, key = { "p${it.createdAt}" }) { p -> PendingRow(p, onClick = { onOpenRoute(p.routeId) }, onLongClick = { selectedPending = p }) }
                }
                if (sorted.isEmpty()) {
                    item { Text("No ascents on this device yet. Refresh to download your logbook.", Modifier.padding(16.dp)) }
                }
                items(sorted, key = { "a${it.id}" }) { a -> AscentRow(a, onClick = { a.routeId?.let(onOpenRoute) }, onLongClick = { selected = a }) }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun AscentRow(a: Ascent, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradeChip(a.grade, a.gradeBand, Modifier.width(56.dp))
        Spacer(Modifier.width(12.dp))
        AscentBadge(a.tick, a.gearStyle)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(a.routeName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                listOfNotNull(a.tickName, a.attempts?.let { pluralStringResource(R.plurals.attempt_count, it, it) }, a.date, a.cragName).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
            )
            a.comment?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun PendingRow(t: TickRequest, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradeChip(t.grade, t.gradeBand, Modifier.width(56.dp))
        Spacer(Modifier.width(12.dp))
        AscentBadge(t.tick, t.gearStyle, pending = true)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(t.routeName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                listOfNotNull(TickTypes.name(t.tick), t.date, stringResource(if (t.ascentId != null) R.string.pending_edit else R.string.pending)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
            )
            t.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2) }
        }
    }
}

/** What a long press on an ascent or an outbox entry offers: edit it in the tick sheet, or delete it ([deleteLabel] says where). */
@Composable
fun AscentActionsDialog(title: String, details: String, deleteLabel: String, onEdit: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(details) },
        confirmButton = {
            Row {
                TextButton(onClick = onEdit) { Text(stringResource(R.string.edit)) }
                TextButton(onClick = onDelete) { Text(deleteLabel) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
