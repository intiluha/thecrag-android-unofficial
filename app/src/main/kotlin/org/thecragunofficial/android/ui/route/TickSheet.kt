package org.thecragunofficial.android.ui.route

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.thecragunofficial.android.R
import org.thecragunofficial.android.data.logbook.LogbookRepository
import org.thecragunofficial.android.data.logbook.TickRequest
import org.thecragunofficial.android.data.logbook.TickTypes
import org.thecragunofficial.android.domain.RouteDetail
import org.thecragunofficial.android.ui.common.describe
import java.io.IOException
import java.time.LocalDate

private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")

/** Bottom sheet to log an ascent of [route]. "Dry run" asks the server to validate without writing. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TickSheet(route: RouteDetail, logbook: LogbookRepository, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val styles = remember(route.id) { (listOfNotNull(route.styleStub?.lowercase()) + TickTypes.gearStyles).distinct() }
    var gearStyle by remember { mutableStateOf(styles.first()) }
    val ticks = remember(gearStyle) { TickTypes.ticksFor(gearStyle) }
    var tick by remember(gearStyle) { mutableStateOf(ticks.first()) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var comment by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val valid = DATE.matches(date)

    fun submit(dryRun: Boolean) {
        val req = TickRequest(
            routeId = route.id, routeName = route.name, gearStyle = gearStyle, tick = tick, date = date, comment = comment.trim(),
            grade = route.grade, gradeBand = route.gradeBand, cragName = route.ancestors.lastOrNull()?.name, createdAt = System.currentTimeMillis(),
        )
        scope.launch {
            busy = true
            try {
                val msg = logbook.tick(req, dryRun)
                if (dryRun) message = msg else onDone(msg)
            } catch (e: IOException) {
                if (dryRun) message = e.describe() else onDone("Offline: the tick waits in the outbox (${e.describe()})")
            } catch (e: Exception) {
                message = e.describe()
            } finally {
                busy = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            Text(route.name, style = MaterialTheme.typography.titleLarge)
            route.grade?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text("Gear style", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                styles.forEach { s -> FilterChip(selected = s == gearStyle, onClick = { gearStyle = s }, label = { Text(s) }) }
            }
            Text(stringResource(R.string.tick), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ticks.forEach { t -> FilterChip(selected = t == tick, onClick = { tick = t }, label = { Text(TickTypes.name(t)) }) }
            }
            OutlinedTextField(
                date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true, isError = !valid,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(comment, { comment = it }, label = { Text("Comment") }, minLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            message?.let { Text(it, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium) }
            Row(Modifier.padding(vertical = 16.dp)) {
                TextButton(onClick = { submit(dryRun = true) }, enabled = !busy && valid) { Text(stringResource(R.string.dry_run)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { submit(dryRun = false) }, enabled = !busy && valid) { Text(stringResource(R.string.log_ascent)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
