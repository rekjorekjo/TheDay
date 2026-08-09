package io.github.thedayapp

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.thedayapp.data.TheDayState
import io.github.thedayapp.ui.AppEntry
import io.github.thedayapp.update.UpdateActions
import io.github.thedayapp.update.AppUpdateManager
import io.github.thedayapp.update.UpdatePreferences

class MainActivity : ComponentActivity() {
    private lateinit var appState: TheDayState
    private lateinit var updateManager: AppUpdateManager
    private var openEventId: String? by mutableStateOf(null)
    private var shouldInstallUpdate = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (redirectLegacyWidgetShortcut(intent)) {
            return
        }

        enableEdgeToEdge()
        appState = TheDayState(applicationContext)
        updateManager = AppUpdateManager(applicationContext)
        handleLaunchIntent(intent)

        setContent {
            AppEntry(
                state = appState,
                requestedEventId = openEventId,
                onRequestedEventConsumed = { openEventId = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        if (redirectLegacyWidgetShortcut(intent)) {
            return
        }

        handleLaunchIntent(intent)
    }

    override fun onResume() {
        super.onResume()

        if (::appState.isInitialized) {
            appState.refreshClock()
        }

        if (::updateManager.isInitialized) {
            if (shouldInstallUpdate) {
                shouldInstallUpdate = false
                updateManager.requestInstall(this)
            }

            val updatePreferences =
                UpdatePreferences(applicationContext)

            if (
                updatePreferences
                    .pendingInstallPermission &&
                (
                    Build.VERSION.SDK_INT <
                        Build.VERSION_CODES.O ||
                        packageManager
                            .canRequestPackageInstalls()
                )
            ) {
                updatePreferences
                    .pendingInstallPermission = false
                updateManager.requestInstall(this)
            }
        }
    }

    private fun handleLaunchIntent(intent: Intent) {
        openEventId = intent.getStringExtra(EXTRA_OPEN_EVENT_ID)

        if (intent.action == UpdateActions.ACTION_INSTALL_UPDATE) {
            shouldInstallUpdate = true
        }
    }

    private fun redirectLegacyWidgetShortcut(
        intent: Intent,
    ): Boolean {
        if (intent.action != ACTION_PIN_WIDGET) {
            return false
        }

        startActivity(
            Intent(
                this,
                WidgetPinActivity::class.java,
            ),
        )

        if (!isTaskRoot) {
            finish()
        } else {
            finishAndRemoveTask()
        }

        return true
    }

    companion object {
        const val EXTRA_OPEN_EVENT_ID = "open_event_id"
        private const val ACTION_PIN_WIDGET = "io.github.thedayapp.action.PIN_WIDGET"
    }
}