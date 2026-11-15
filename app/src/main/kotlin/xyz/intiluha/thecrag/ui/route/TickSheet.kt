package xyz.intiluha.thecrag.ui.route

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.intiluha.thecrag.R
import xyz.intiluha.thecrag.data.logbook.LogbookRepository
import xyz.intiluha.thecrag.data.logbook.TickRequest
import xyz.intiluha.thecrag.data.logbook.TickTypes
import xyz.intiluha.thecrag.domain.RouteDetail
import xyz.intiluha.thecrag.ui.common.describe
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

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
    var attempts by remember { mutableStateOf("") }
    var proposedGrade by remember { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val attemptsOk = attempts.isBlank() || (attempts.toIntOrNull() ?: 0) > 0
    val valid = DATE.matches(date) && attemptsOk

    fun submit(dryRun: Boolean) {
        val req = TickRequest(
            routeId = route.id, routeName = route.name, gearStyle = gearStyle, tick = tick, date = date, comment = comment.trim(),
            grade = route.grade, gradeBand = route.gradeBand, cragName = route.ancestors.lastOrNull()?.name, createdAt = System.currentTimeMillis(),
            attempts = attempts.trim().toIntOrNull(), proposedGrade = proposedGrade.trim().ifBlank { null },
            gradeContext = route.gradeContext, gradeStyle = route.gradeStyle,
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

    if (showPicker) {
        // The picker works in UTC midnights; the typed date (or today) seeds it, the pick comes back as YYYY-MM-DD.
        val seed = runCatching { LocalDate.parse(date) }.getOrElse { LocalDate.now() }
        val state = rememberDatePickerState(initialSelectedDateMillis = seed.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    showPicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        // imePadding: the sheet has its own window, so without it the keyboard covers the lower fields.
        Column(Modifier.imePadding().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
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
                trailingIcon = { IconButton(onClick = { showPicker = true }) { Icon(Icons.Default.CalendarMonth, stringResource(R.string.pick_date)) } },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    attempts, { attempts = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.attempts)) }, singleLine = true,
                    isError = !attemptsOk, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    proposedGrade, { proposedGrade = it }, label = { Text(stringResource(R.string.proposed_grade)) }, singleLine = true,
                    placeholder = { route.grade?.let { Text(stringResource(R.string.proposed_grade_hint, it)) } },
                    modifier = Modifier.weight(2f),
                )
            }
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
