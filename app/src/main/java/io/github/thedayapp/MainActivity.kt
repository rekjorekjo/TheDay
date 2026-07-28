package io.github.thedayapp

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.thedayapp.data.TheDayState
import io.github.thedayapp.ui.TheDayApp
import io.github.thedayapp.ui.theme.TheDayTheme
import io.github.thedayapp.widget.DayWidgetProvider

class MainActivity : ComponentActivity() {
    private lateinit var appState: TheDayState
    private var openEventId: String? by mutableStateOf(null)
    private var shouldRequestWidgetPin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appState = TheDayState(applicationContext)
        handleLaunchIntent(intent)

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
        handleLaunchIntent(intent)
    }

    override fun onResume() {
        super.onResume()

        if (::appState.isInitialized) {
            appState.refreshClock()
        }

        if (shouldRequestWidgetPin) {
            shouldRequestWidgetPin = false
            requestWidgetPin()
        }
    }

    private fun handleLaunchIntent(intent: Intent) {
        openEventId = intent.getStringExtra(EXTRA_OPEN_EVENT_ID)

        if (intent.action == ACTION_PIN_WIDGET) {
            shouldRequestWidgetPin = true
        }
    }

    private fun requestWidgetPin() {
        val appWidgetManager = AppWidgetManager.getInstance(this)

        if (!appWidgetManager.isRequestPinAppWidgetSupported()) {
            Toast.makeText(
                this,
                R.string.widget_pin_not_supported,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        val provider = ComponentName(
            this,
            DayWidgetProvider::class.java,
        )

        val requestAccepted = try {
            appWidgetManager.requestPinAppWidget(
                provider,
                null,
                null,
            )
        } catch (exception: IllegalStateException) {
            false
        } catch (exception: SecurityException) {
            false
        }

        if (!requestAccepted) {
            Toast.makeText(
                this,
                R.string.widget_pin_not_supported,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    companion object {
        const val EXTRA_OPEN_EVENT_ID = "open_event_id"
        private const val ACTION_PIN_WIDGET = "io.github.thedayapp.action.PIN_WIDGET"
    }
}
