package io.github.thedayapp

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import io.github.thedayapp.widget.DayWidgetProvider
import io.github.thedayapp.widget.WidgetPinResultReceiver

class WidgetPinActivity : Activity() {
    private var requestAttempted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()

        if (requestAttempted) {
            return
        }

        requestAttempted = true
        requestWidgetPin()
    }

    private fun requestWidgetPin() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        if (!appWidgetManager.isRequestPinAppWidgetSupported) {
            showUnsupportedToast()
            finish()
            return
        }

        val eventId = intent
            .getStringExtra(EXTRA_EVENT_ID)
            ?.takeIf(String::isNotBlank)
        val provider = ComponentName(
            this,
            DayWidgetProvider::class.java,
        )

        val callbackIntent = Intent(
            this,
            WidgetPinResultReceiver::class.java,
        ).apply {
            eventId?.let {
                putExtra(EXTRA_EVENT_ID, it)
            }
        }
        val callbackRequestCode = eventId?.hashCode() ?: 0
        val mutabilityFlag = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val successCallback = PendingIntent.getBroadcast(
            this,
            callbackRequestCode,
            callbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag,
        )

        val requestAccepted = try {
            appWidgetManager.requestPinAppWidget(
                provider,
                null,
                successCallback,
            )
        } catch (_: IllegalStateException) {
            false
        } catch (_: SecurityException) {
            false
        }

        if (!requestAccepted) {
            showUnsupportedToast()
            finish()
            return
        }

        window.decorView.post {
            finish()
        }
    }

    private fun showUnsupportedToast() {
        Toast.makeText(
            this,
            R.string.widget_pin_not_supported,
            Toast.LENGTH_SHORT,
        ).show()
    }

    companion object {
        const val EXTRA_EVENT_ID = "widget_event_id"
    }
}
