package io.github.thedayapp.ui

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.thedayapp.GlassFlutterActivity
import io.github.thedayapp.data.TheDayState

@Composable
fun AppEntry(
    state: TheDayState,
    requestedEventId: String?,
    onRequestedEventConsumed: () -> Unit,
) {
    val context = LocalContext.current

    LaunchedEffect(requestedEventId) {
        context.startActivity(
            GlassFlutterActivity.createIntent(
                context = context,
                eventId = requestedEventId,
            ),
        )
        onRequestedEventConsumed()
        (context as? Activity)?.finish()
    }

    Box(modifier = Modifier.fillMaxSize())
}
