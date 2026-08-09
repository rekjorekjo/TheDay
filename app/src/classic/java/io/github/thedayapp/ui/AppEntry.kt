package io.github.thedayapp.ui

import androidx.compose.runtime.Composable
import io.github.thedayapp.data.TheDayState
import io.github.thedayapp.ui.theme.TheDayTheme

@Composable
fun AppEntry(
    state: TheDayState,
    requestedEventId: String?,
    onRequestedEventConsumed: () -> Unit,
) {
    TheDayTheme(settings = state.settings) {
        TheDayApp(
            state = state,
            requestedEventId = requestedEventId,
            onRequestedEventConsumed = onRequestedEventConsumed,
        )
    }
}
