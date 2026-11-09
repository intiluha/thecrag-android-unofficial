package xyz.intiluha.thecrag.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import xyz.intiluha.thecrag.R
import xyz.intiluha.thecrag.data.repo.RecentItem
import xyz.intiluha.thecrag.domain.Crumb
import xyz.intiluha.thecrag.domain.NodeKind
import xyz.intiluha.thecrag.ui.theme.gradeBandColor

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    /** [needsLogin]: theCrag refused for lack of a logged-in session; the screen offers Log in instead of Retry. */
    data class Error(val message: String, val needsLogin: Boolean = false) : UiState<Nothing>
    data class Ready<T>(val value: T) : UiState<T>
}

@Composable
fun <T> StateBox(state: UiState<T>, onRetry: () -> Unit, onLogin: (() -> Unit)? = null, content: @Composable (T) -> Unit) {
    when (state) {
        UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is UiState.Error -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(state.message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            if (state.needsLogin && onLogin != null) {
                Button(onClick = onLogin, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.login)) }
                TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            } else {
                Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.retry)) }
            }
        }
        is UiState.Ready -> content(state.value)
    }
}

@Composable
fun Breadcrumbs(crumbs: List<Crumb>, onClick: (Long) -> Unit) {
    if (crumbs.isEmpty()) return
    LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
        items(crumbs.size) { i ->
            val c = crumbs[i]
            TextButton(onClick = { onClick(c.id) }) { Text(c.name, maxLines = 1) }
            if (i < crumbs.lastIndex) Text("›", Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Column {
        Text(
            text,
            Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        HorizontalDivider()
    }
}

@Composable
fun GradeChip(grade: String?, band: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(gradeBandColor(band), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(grade ?: "?", color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun Stars(stars: Int) {
    if (stars > 0) Text("★".repeat(stars), color = MaterialTheme.colorScheme.secondary)
}

/** Overflow ("⋮") menu for a top app bar; each item is a label and its action. */
@Composable
fun OverflowMenu(vararg items: Pair<String, () -> Unit>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { (label, action) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; action() })
            }
        }
    }
}

@Composable
fun RecentRow(item: RecentItem, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(item.name, maxLines = 1) },
        supportingContent = {
            val kind = if (item.kind == NodeKind.ROUTE) "route" else "area"
            Text(listOfNotNull(kind, item.detail).joinToString(" · "), maxLines = 1)
        },
    )
}

@Composable
fun Attribution(modifier: Modifier = Modifier, extra: String? = null) {
    Column(modifier.padding(12.dp)) {
        Text(
            stringResource(R.string.attribution),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        extra?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}
