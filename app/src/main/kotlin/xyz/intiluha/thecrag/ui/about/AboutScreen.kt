package xyz.intiluha.thecrag.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.intiluha.thecrag.BuildConfig
import xyz.intiluha.thecrag.R
import xyz.intiluha.thecrag.TheCragApp
import xyz.intiluha.thecrag.ui.common.SectionHeader

private const val BEERWARE = "\"THE BEER-WARE LICENSE\" (Revision 42): Ilya Levin wrote this app. " +
    "As long as you retain this notice you can do whatever you want with this stuff. " +
    "If we meet some day, and you think this stuff is worth it, you can buy me a beer in return. " +
    "The licence covers the app only, not the climbing data it shows."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    val cleared = stringResource(R.string.cache_cleared)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                Text(stringResource(R.string.about_blurb), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
            }

            SectionHeader("Data")
            Paragraph(stringResource(R.string.attribution))
            Links(uri, "thecrag.com" to "https://www.thecrag.com/", "CC BY-NC-SA 4.0" to "https://creativecommons.org/licenses/by-nc-sa/4.0/")

            SectionHeader("Map")
            Paragraph(stringResource(R.string.map_attribution))
            Links(uri, "OpenFreeMap" to "https://openfreemap.org/", "OpenStreetMap" to "https://www.openstreetmap.org/copyright")

            SectionHeader("Licence")
            Paragraph(BEERWARE)

            SectionHeader("Storage")
            Paragraph(stringResource(R.string.about_storage))
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Button(onClick = {
                    scope.launch {
                        val loader = SingletonImageLoader.get(context)
                        withContext(Dispatchers.IO) {
                            TheCragApp.graph(context).clearCaches()
                            loader.diskCache?.clear()
                        }
                        loader.memoryCache?.clear()
                        status = cleared
                    }
                }) { Text(stringResource(R.string.clear_cache)) }
            }
            status?.let { Paragraph(it) }
        }
    }
}

@Composable
private fun Paragraph(text: String) {
    Text(text, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Links(uri: androidx.compose.ui.platform.UriHandler, vararg links: Pair<String, String>) {
    Row(Modifier.padding(horizontal = 8.dp)) {
        links.forEach { (label, url) -> TextButton(onClick = { uri.openUri(url) }) { Text(label) } }
    }
}
