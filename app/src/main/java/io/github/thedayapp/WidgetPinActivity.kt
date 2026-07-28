package io.github.thedayapp

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.widget.Toast
import io.github.thedayapp.widget.DayWidgetProvider

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
        val appWidgetManager =
            AppWidgetManager.getInstance(this)

        if (
            !appWidgetManager
                .isRequestPinAppWidgetSupported
        ) {
            showUnsupportedToast()
            finish()
            return
        }

        val provider =
            ComponentName(
                this,
                DayWidgetProvider::class.java,
            )

        val requestAccepted =
            try {
                appWidgetManager
                    .requestPinAppWidget(
                        provider,
                        null,
                        null,
                    )
            } catch (
                exception: IllegalStateException,
            ) {
                false
            } catch (
                exception: SecurityException,
            ) {
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
}
