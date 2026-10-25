package org.thecragunofficial.android.ui.common

import java.util.Locale

fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format(Locale.ROOT, "%.1f GB", bytes / 1e9)
    bytes >= 1_000_000 -> String.format(Locale.ROOT, "%.1f MB", bytes / 1e6)
    bytes >= 1_000 -> String.format(Locale.ROOT, "%.0f kB", bytes / 1e3)
    else -> "$bytes B"
}
