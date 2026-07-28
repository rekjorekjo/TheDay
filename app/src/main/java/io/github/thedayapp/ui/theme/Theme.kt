package io.github.thedayapp.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import io.github.thedayapp.data.AppSettings
import io.github.thedayapp.data.ThemeMode

private val TheDayShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(30.dp),
)

@Composable
fun TheDayTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            view.context.findActivity()?.window?.let { window ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    val appearance = if (dark) 0 else mask
                    window.insetsController?.setSystemBarsAppearance(appearance, mask)
                } else {
                    @Suppress("DEPRECATION")
                    var flags = view.systemUiVisibility
                    @Suppress("DEPRECATION")
                    val lightBars = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                        View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    flags = if (dark) flags and lightBars.inv() else flags or lightBars
                    @Suppress("DEPRECATION")
                    view.systemUiVisibility = flags
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorSchemeFor(settings.paletteStyle, dark),
        typography = TheDayTypography,
        shapes = TheDayShapes,
        content = content,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
