package org.thecragunofficial.android.ui.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.thecragunofficial.android.R
import org.thecragunofficial.android.TheCragApp
import org.thecragunofficial.android.data.auth.AuthRepository
import org.thecragunofficial.android.data.logbook.LogbookRepository
import org.thecragunofficial.android.ui.common.SectionHeader
import org.thecragunofficial.android.ui.common.describe
import java.text.DateFormat
import java.util.Date

class AccountViewModel(private val auth: AuthRepository, private val logbook: LogbookRepository) : ViewModel() {
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    private fun run(block: suspend () -> String) {
        viewModelScope.launch {
            busy.value = true
            message.value = try { block() } catch (e: Exception) { e.describe() }
            busy.value = false
        }
    }

    fun login(user: String, password: String) = run {
        val a = auth.login(user.trim(), password)
        "Logged in as ${a.name}."
    }

    fun refresh() = run { "Logbook refreshed: ${logbook.refresh()} ascents." }

    fun retry() = run { "Sent ${logbook.retryOutbox()} pending tick(s)." }

    fun delete(id: Long) = run { logbook.delete(id); "Ascent deleted." }

    fun logout() {
        auth.logout()
        logbook.clearLocal()
        message.value = "Logged out; local logbook copy removed."
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(onBack: () -> Unit, onLogbook: () -> Unit) {
    val context = LocalContext.current
    val graph = TheCragApp.graph(context)
    val vm: AccountViewModel = viewModel { AccountViewModel(graph.auth, graph.logbook) }
    val account by graph.auth.state.collectAsState()
    val ascents by graph.logbook.ascents.collectAsState()
    val outbox by graph.logbook.outbox.collectAsState()
    val refreshedAt by graph.logbook.refreshedAt.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    var user by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.account_blurb), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            val a = account
            if (a == null) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(user, { user = it }, label = { Text(stringResource(R.string.username)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        password, { password = it }, label = { Text(stringResource(R.string.password)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Button(
                        onClick = { vm.login(user, password); password = "" },
                        enabled = !busy && user.isNotBlank() && password.isNotEmpty(),
                        modifier = Modifier.padding(top = 12.dp),
                    ) { Text(stringResource(R.string.login)) }
                }
            } else {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Logged in as ${a.name}", style = MaterialTheme.typography.titleMedium)
                    Text("Account id ${a.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                SectionHeader(stringResource(R.string.logbook))
                Column(Modifier.padding(16.dp)) {
                    val when_ = refreshedAt?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) }
                    Text("${ascents.size} ascents on this device" + (when_?.let { ", refreshed $it" } ?: ", not refreshed yet"))
                    if (outbox.isNotEmpty()) Text("${outbox.size} tick(s) waiting to be sent", color = MaterialTheme.colorScheme.error)
                    Row(Modifier.padding(top = 8.dp)) {
                        Button(onClick = vm::refresh, enabled = !busy) { Text(stringResource(R.string.refresh_logbook)) }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = onLogbook) { Text(stringResource(R.string.logbook)) }
                    }
                    if (outbox.isNotEmpty()) TextButton(onClick = vm::retry, enabled = !busy) { Text(stringResource(R.string.retry_outbox)) }
                }
                SectionHeader(stringResource(R.string.account))
                TextButton(onClick = vm::logout, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.logout)) }
            }
            message?.let { Text(it, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
