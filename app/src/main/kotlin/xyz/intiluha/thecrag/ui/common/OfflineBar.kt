package xyz.intiluha.thecrag.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import xyz.intiluha.thecrag.R

/**
 * Slim strip shown above every screen while the device has no network. It takes
 * the status bar inset itself (the strip colour runs up behind the status bar);
 * the caller consumes that inset for the screen below so its top bar does not
 * pad for it a second time.
 */
@Composable
fun OfflineBar(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.offline_bar),
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
