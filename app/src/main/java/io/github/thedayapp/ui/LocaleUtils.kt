package io.github.thedayapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

/** Observable Java locale for Compose formatting. */
@Composable
fun currentJavaLocale(): Locale {
    val configuration = LocalConfiguration.current
    return configuration.locales[0]
}
