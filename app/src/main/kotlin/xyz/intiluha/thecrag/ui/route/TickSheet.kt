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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.intiluha.thecrag.R
import xyz.intiluha.thecrag.data.logbook.Ascent
import xyz.intiluha.thecrag.data.logbook.Climber
import xyz.intiluha.thecrag.data.logbook.LogbookRepository
import xyz.intiluha.thecrag.data.logbook.TickRequest
import xyz.intiluha.thecrag.data.logbook.TickTypes
import xyz.intiluha.thecrag.data.repo.NodeRepository
import xyz.intiluha.thecrag.domain.RouteDetail
import xyz.intiluha.thecrag.ui.common.describe
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")

/**
 * Bottom sheet to log an ascent of [route]; with [existing] it edits that ascent, with [pending] it
 * edits a tick still waiting in the outbox (replaced in place, nothing sent). Either way the form
 * opens filled in and the button reads Save. "Dry run" (new ticks only) asks
 * the server to validate without writing.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TickSheet(
    route: RouteDetail, logbook: LogbookRepository, existing: Ascent? = null, pending: TickRequest? = null,
    onDismiss: () -> Unit, onDone: (String) -> Unit,
) {
    val initial = existing?.let { Prefill(it.gearStyle, it.tick, it.date, it.comment, it.attempts, it.proposedGrade, it.partners) }
        ?: pending?.let { Prefill(it.gearStyle, it.tick, it.date, it.comment, it.attempts, it.proposedGrade, it.partners) }
    val scope = rememberCoroutineScope()
    val styles = remember(route.id) {
        (listOfNotNull(initial?.gearStyle, route.styleStub).map(TickTypes::apiGearStyle) + TickTypes.gearStyles).distinct()
    }
    var gearStyle by remember { mutableStateOf(styles.first()) }
    // The stored tick may be one the vocabulary table does not list for this gear style; keep it selectable.
    val ticks = remember(gearStyle) { (TickTypes.ticksFor(gearStyle) + listOfNotNull(initial?.tick?.takeIf { gearStyle == styles.first() })).distinct() }
    var tick by remember(gearStyle) { mutableStateOf(initial?.tick?.takeIf { it in ticks } ?: ticks.first()) }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now().toString()) }
    var comment by remember { mutableStateOf(initial?.comment ?: "") }
    var partners by remember { mutableStateOf(initial?.partners ?: "") }
    var hidePartnerMenu by remember { mutableStateOf(false) }
    var partnersFocused by remember { mutableStateOf(false) }
    // Accounts you follow and past partners, for the "climbed with" suggestions; silently none when offline.
    var suggestions by remember { mutableStateOf(emptyList<Climber>()) }
    LaunchedEffect(logbook) { suggestions = try { logbook.partnerSuggestions() } catch (e: Exception) { emptyList() } }
    var attempts by remember { mutableStateOf(initial?.attempts?.toString() ?: "") }
    var proposedGrade by remember { mutableStateOf(initial?.proposedGrade ?: "") }
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
            gradeContext = route.gradeContext, gradeStyle = route.gradeStyle, partners = partners.trim().trimEnd(',').trim(),
        )
        scope.launch {
            busy = true
            try {
                val msg = when {
                    dryRun -> logbook.tick(req, dryRun = true)
                    existing != null -> logbook.update(existing.id, req)
                    pending != null -> { logbook.editPending(pending.createdAt, req); "Pending tick updated." }
                    else -> logbook.tick(req)
                }
                if (dryRun) message = msg else onDone(msg)
            } catch (e: IOException) {
                // Both a new tick and an edit are parked in the outbox when the network is down.
                if (dryRun) message = e.describe() else onDone("Offline: the ${if (existing != null) "edit" else "tick"} waits in the outbox (${e.describe()})")
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

    // Fully expanded from the start: half-open, the sheet stays put when the keyboard appears and covers the
    // fields in its top half (the date field), while imePadding only helps once the sheet is at full height.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
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
            // "Climbed with": free text, comma separated, members as @login. The menu offers the accounts you
            // follow and past partners that match the name being typed (the text after the last comma).
            val token = partners.substringAfterLast(',').trim()
            val matches = remember(token, suggestions) {
                suggestions.filter { token.isEmpty() || it.login.startsWith(token.removePrefix("@"), ignoreCase = true) || it.name.contains(token, ignoreCase = true) }
            }
            val menuOpen = partnersFocused && matches.isNotEmpty() && !hidePartnerMenu
            ExposedDropdownMenuBox(expanded = menuOpen, onExpandedChange = { }) {
                OutlinedTextField(
                    partners, { partners = it; hidePartnerMenu = false }, label = { Text(stringResource(R.string.climbed_with)) }, singleLine = true,
                    placeholder = { Text(stringResource(R.string.climbed_with_hint)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).menuAnchor(MenuAnchorType.PrimaryEditable).onFocusChanged { partnersFocused = it.isFocused },
                )
                ExposedDropdownMenu(expanded = menuOpen, onDismissRequest = { hidePartnerMenu = true }) {
                    matches.take(8).forEach { c ->
                        DropdownMenuItem(text = { Text("${c.name} (@${c.login})") }, onClick = {
                            val head = partners.substringBeforeLast(',', "").trim()
                            partners = (if (head.isEmpty()) "" else "$head, ") + "@${c.login}, "
                            hidePartnerMenu = true
                        })
                    }
                }
            }
            OutlinedTextField(comment, { comment = it }, label = { Text("Comment") }, minLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            message?.let { Text(it, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium) }
            Row(Modifier.padding(vertical = 16.dp)) {
                if (existing == null && pending?.ascentId == null) {
                    TextButton(onClick = { submit(dryRun = true) }, enabled = !busy && valid) { Text(stringResource(R.string.dry_run)) }
                    Spacer(Modifier.width(8.dp))
                }
                Button(onClick = { submit(dryRun = false) }, enabled = !busy && valid) {
                    Text(stringResource(if (existing != null || pending != null) R.string.save else R.string.log_ascent))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** What the sheet is filled in with when it edits something; from an [Ascent] or a pending [TickRequest]. */
private data class Prefill(
    val gearStyle: String?, val tick: String?, val date: String?, val comment: String?, val attempts: Int?, val proposedGrade: String?, val partners: String?,
)

/**
 * Loads the route for an edit started where no route is loaded (the Logbook): the form needs its
 * gear style, grade context and style. [content] composes once the route is there; a failure, or
 * a missing route id, is reported through [onFailed] instead.
 */
@Composable
fun WithRoute(routeId: Long?, nodes: NodeRepository, onFailed: (String) -> Unit, content: @Composable (RouteDetail) -> Unit) {
    var route by remember { mutableStateOf<RouteDetail?>(null) }
    LaunchedEffect(routeId) {
        if (routeId == null) return@LaunchedEffect onFailed("This entry has no route in the local copy; edit it on theCrag.")
        try { route = nodes.route(routeId) } catch (e: Exception) { onFailed(e.describe()) }
    }
    route?.let { content(it) }
}
