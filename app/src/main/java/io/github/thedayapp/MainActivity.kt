package io.github.thedayapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.thedayapp.data.TheDayState
import io.github.thedayapp.ui.TheDayApp
import io.github.thedayapp.ui.theme.TheDayTheme

class MainActivity : ComponentActivity() {
    private lateinit var appState: TheDayState
    private var openEventId: String? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appState = TheDayState(applicationContext)
        openEventId = intent.getStringExtra(EXTRA_OPEN_EVENT_ID)

        setContent {
            TheDayTheme(settings = appState.settings) {
                TheDayApp(
                    state = appState,
                    requestedEventId = openEventId,
                    onRequestedEventConsumed = { openEventId = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openEventId = intent.getStringExtra(EXTRA_OPEN_EVENT_ID)
    }

    override fun onResume() {
        super.onResume()
        if (::appState.isInitialized) appState.refreshClock()
    }

    companion object {
        const val EXTRA_OPEN_EVENT_ID = "open_event_id"
    }
}
