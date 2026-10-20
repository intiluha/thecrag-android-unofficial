package org.thecragunofficial.android.ui.logbook

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AccountCircle
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.thecragunofficial.android.R
import org.thecragunofficial.android.TheCragApp
import org.thecragunofficial.android.data.logbook.Ascent
import org.thecragunofficial.android.data.logbook.TickRequest
import org.thecragunofficial.android.data.logbook.TickTypes
import org.thecragunofficial.android.ui.account.AccountViewModel
import org.thecragunofficial.android.ui.common.GradeChip
import org.thecragunofficial.android.ui.common.SectionHeader

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
    var confirmDelete by remember { mutableStateOf<Ascent?>(null) }
    confirmDelete?.let { a ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.delete_ascent)) },
            text = { Text("${a.routeName}, ${a.tickName}, ${a.date ?: ""}") },
            confirmButton = { TextButton(onClick = { vm.delete(a.id); confirmDelete = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    val sorted = ascents.sortedWith(compareByDescending<Ascent> { it.date ?: "" }.thenByDescending { it.id })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.logbook)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    if (account != null) IconButton(onClick = vm::refresh, enabled = !busy) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh_logbook)) }
                    IconButton(onClick = onAccount) { Icon(Icons.Default.AccountCircle, stringResource(R.string.account)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
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
                    items(outbox, key = { "p${it.createdAt}" }) { PendingRow(it) { onOpenRoute(it.routeId) } }
                }
                if (sorted.isEmpty()) {
                    item { Text("No ascents on this device yet. Refresh to download your logbook.", Modifier.padding(16.dp)) }
                }
                items(sorted, key = { "a${it.id}" }) { a -> AscentRow(a, onClick = { a.routeId?.let(onOpenRoute) }, onLongClick = { confirmDelete = a }) }
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
        Column(Modifier.weight(1f)) {
            Text(a.routeName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                listOfNotNull(a.tickName, a.date, a.cragName).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
            )
            a.comment?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
        }
    }
}

@Composable
fun PendingRow(t: TickRequest, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradeChip(t.grade, t.gradeBand, Modifier.width(56.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.routeName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                listOfNotNull(TickTypes.name(t.tick), t.date, stringResource(R.string.pending)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
            )
            t.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2) }
        }
    }
}
