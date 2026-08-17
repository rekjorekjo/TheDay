package io.github.thedayapp.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.RectF
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.thedayapp.BuildConfig
import io.github.thedayapp.data.AppSettings
import io.github.thedayapp.data.DayEvent
import io.github.thedayapp.data.DayRepository
import io.github.thedayapp.data.PaletteStyle
import io.github.thedayapp.data.ThemeMode
import io.github.thedayapp.domain.DayMath
import io.github.thedayapp.domain.EventOrdering
import io.github.thedayapp.domain.normalizedCategoryName
import io.github.thedayapp.sharing.GlassExportBackdrop
import io.github.thedayapp.sharing.GlassExportStyle
import io.github.thedayapp.ui.currentJavaLocale
import io.github.thedayapp.ui.theme.TheDayTheme
import io.github.thedayapp.util.DateFormatting
import java.time.LocalDate
import kotlin.math.abs

class DayWidgetConfigureActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(Activity.RESULT_CANCELED)
        appWidgetId = intent
            ?.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
            ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val repository = DayRepository(this)
        val settings = repository.loadSettings()
        val today = LocalDate.now()
        val events = EventOrdering.sort(
            events = repository.loadEvents(),
            mode = settings.sortMode,
            direction = settings.sortDirection,
            today = today,
        )

        if (BuildConfig.EDITION == "glass") {
            enableEdgeToEdge()
        }

        setContent {
            val themeSettings = if (BuildConfig.EDITION == "glass") {
                settings.copy(themeMode = ThemeMode.DARK)
            } else {
                settings
            }
            TheDayTheme(settings = themeSettings) {
                DayWidgetConfigureScreen(
                    events = events,
                    today = today,
                    settings = themeSettings,
                    onBack = ::finish,
                    onEventSelected = ::completeConfiguration,
                )
            }
        }
    }

    private fun completeConfiguration(eventId: String) {
        DayWidgetPreferences.setEventId(
            context = this,
            appWidgetId = appWidgetId,
            eventId = eventId,
        )
        DayWidgetProvider.requestUpdate(
            context = this,
            appWidgetId = appWidgetId,
        )

        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                appWidgetId,
            ),
        )
        finish()
    }
}

@Composable
private fun DayWidgetConfigureScreen(
    events: List<DayEvent>,
    today: LocalDate,
    settings: AppSettings,
    onBack: () -> Unit,
    onEventSelected: (String) -> Unit,
) {
    if (BuildConfig.EDITION == "glass") {
        GlassDayWidgetConfigureScreen(
            events = events,
            today = today,
            settings = settings,
            onBack = onBack,
            onEventSelected = onEventSelected,
        )
    } else {
        ClassicDayWidgetConfigureScreen(
            events = events,
            today = today,
            onBack = onBack,
            onEventSelected = onEventSelected,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassicDayWidgetConfigureScreen(
    events: List<DayEvent>,
    today: LocalDate,
    onBack: () -> Unit,
    onEventSelected: (String) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text("选择特殊日子") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = "取消",
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        DayWidgetConfigureContent(
            events = events,
            today = today,
            contentPadding = contentPadding,
            glass = false,
            glassSpec = null,
            onEventSelected = onEventSelected,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassDayWidgetConfigureScreen(
    events: List<DayEvent>,
    today: LocalDate,
    settings: AppSettings,
    onBack: () -> Unit,
    onEventSelected: (String) -> Unit,
) {
    val dark = true
    val ambience = widgetGlassAmbience(settings.paletteStyle)
    val clarity = settings.glassClarity.coerceIn(0, 100) / 100f
    val fade = 1f - clarity
    val glassSpec = WidgetGlassSpec(
        fill = Color.White.copy(alpha = (if (dark) 0.30f else 0.48f) * fade * fade),
        border = Color.White.copy(alpha = 0.28f + ((0.24f - 0.28f) * clarity)),
        accent = ambience.accent,
        foreground = Color.White.copy(alpha = 0.94f),
        secondary = Color.White.copy(alpha = 0.70f),
    )
    val exportStyle = GlassExportStyle(
        primary = ambience.primary.toArgb(),
        secondary = ambience.secondary.toArgb(),
        tertiary = ambience.tertiary.toArgb(),
        accent = ambience.accent.toArgb(),
        clarity = settings.glassClarity,
        isDark = true,
        backgroundPhase = 0.18f,
        backgroundMode = settings.backgroundMotionMode,
        backgroundTexture = "NONE",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            GlassExportBackdrop.draw(
                canvas = drawContext.canvas.nativeCanvas,
                rect = RectF(0f, 0f, size.width, size.height),
                style = exportStyle,
            )
        }
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = glassSpec.foreground,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        titleContentColor = glassSpec.foreground,
                        navigationIconContentColor = glassSpec.foreground,
                    ),
                    title = {
                        Text(
                            text = "选择特殊日子",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowBack,
                                contentDescription = "取消",
                            )
                        }
                    },
                )
            },
        ) { contentPadding ->
            DayWidgetConfigureContent(
                events = events,
                today = today,
                contentPadding = contentPadding,
                glass = true,
                glassSpec = glassSpec,
                onEventSelected = onEventSelected,
            )
        }
    }
}

@Composable
private fun DayWidgetConfigureContent(
    events: List<DayEvent>,
    today: LocalDate,
    contentPadding: PaddingValues,
    glass: Boolean,
    glassSpec: WidgetGlassSpec?,
    onEventSelected: (String) -> Unit,
) {
    if (events.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "还没有可选择的日子",
                style = MaterialTheme.typography.titleMedium,
                color = if (glass) glassSpec?.foreground ?: Color.White else Color.Unspecified,
            )
            Text(
                text = "请先打开 The Day 创建一个日子，再从桌面添加小组件。",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (glass) glassSpec?.secondary ?: Color.White.copy(alpha = 0.70f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(
                horizontal = 16.dp,
                vertical = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(
                items = events,
                key = DayEvent::id,
            ) { event ->
                DayWidgetEventItem(
                    event = event,
                    today = today,
                    glass = glass,
                    glassSpec = glassSpec,
                    onClick = { onEventSelected(event.id) },
                )
            }
        }
    }
}

@Composable
private fun DayWidgetEventItem(
    event: DayEvent,
    today: LocalDate,
    glass: Boolean,
    glassSpec: WidgetGlassSpec?,
    onClick: () -> Unit,
) {
    val locale = currentJavaLocale()
    val delta = DayMath.signedDays(event, today)
    val relationText = when {
        delta > 0L -> "还有 ${abs(delta)} 天"
        delta < 0L -> "已经 ${abs(delta)} 天"
        else -> "今天"
    }
    val dateText = DateFormatting.longDate(
        DayMath.effectiveDate(event, today),
        locale,
    )
    val categoryColor = if (glass) glassSpec?.secondary ?: Color.White.copy(alpha = 0.70f)
    else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(if (glass) 22.dp else 20.dp)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (glass) glassSpec?.fill ?: Color.Transparent
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = if (glass) BorderStroke(1.dp, glassSpec?.border ?: Color.Transparent) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 18.dp,
                vertical = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = buildAnnotatedString {
                    append(event.title)
                    withStyle(SpanStyle(color = categoryColor)) {
                        append(" · ")
                        append(normalizedCategoryName(event.category))
                    }
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (glass) glassSpec?.foreground ?: Color.White else Color.Unspecified,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = relationText,
                style = MaterialTheme.typography.bodyLarge,
                color = if (glass) glassSpec?.accent ?: MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primary,
            )
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (glass) glassSpec?.secondary ?: Color.White.copy(alpha = 0.70f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class WidgetGlassSpec(
    val fill: Color,
    val border: Color,
    val accent: Color,
    val foreground: Color,
    val secondary: Color,
)

private data class WidgetGlassAmbience(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val accent: Color,
)

private fun widgetGlassAmbience(style: PaletteStyle): WidgetGlassAmbience = when (style) {
    PaletteStyle.MIDNIGHT -> WidgetGlassAmbience(Color(0xFF3987E8), Color(0xFF7656D8), Color(0xFF22BFA1), Color(0xFF7EC6FF))
    PaletteStyle.CINNABAR -> WidgetGlassAmbience(Color(0xFFF05F68), Color(0xFFE58C59), Color(0xFF9B5ACB), Color(0xFFFF9992))
    PaletteStyle.PINE -> WidgetGlassAmbience(Color(0xFF2FB58F), Color(0xFF6DA56E), Color(0xFF3F7EB9), Color(0xFF69DCB7))
    PaletteStyle.ANTIQUE_GOLD -> WidgetGlassAmbience(Color(0xFFD6A43E), Color(0xFFB86D4E), Color(0xFF6B78A9), Color(0xFFFFD37D))
    PaletteStyle.BLOOM_PETAL -> WidgetGlassAmbience(Color(0xFFE76F9D), Color(0xFFA06FDC), Color(0xFF668DD5), Color(0xFFFFA4C4))
    PaletteStyle.BLOOM_MIST -> WidgetGlassAmbience(Color(0xFF61A5D7), Color(0xFF6E84D5), Color(0xFF4FC0B5), Color(0xFF9FD7FF))
    PaletteStyle.BLOOM_VERDANT -> WidgetGlassAmbience(Color(0xFF70B85C), Color(0xFF3CA17F), Color(0xFF6B85BE), Color(0xFFA4E48C))
    PaletteStyle.BLOOM_STONE -> WidgetGlassAmbience(Color(0xFF9C8A80), Color(0xFF89768F), Color(0xFF6C8D98), Color(0xFFCDBEB2))
    PaletteStyle.BLOOM_WHEAT -> WidgetGlassAmbience(Color(0xFFD9A63D), Color(0xFFBF7751), Color(0xFF718D78), Color(0xFFFFCB73))
    PaletteStyle.BLOOM_INK -> WidgetGlassAmbience(Color(0xFF526F91), Color(0xFF766B95), Color(0xFF3D929A), Color(0xFF9AB8DC))
    PaletteStyle.BLOOM_AMBER -> WidgetGlassAmbience(Color(0xFFE38A32), Color(0xFFAE6257), Color(0xFF7E9250), Color(0xFFFFB25B))
    PaletteStyle.BLOOM_LAPIS -> WidgetGlassAmbience(Color(0xFF4D77D4), Color(0xFF6C62C5), Color(0xFF389CAC), Color(0xFF9BB8FF))
    PaletteStyle.BLOOM_RIPPLE -> WidgetGlassAmbience(Color(0xFF34AAA5), Color(0xFF477FB2), Color(0xFF7068C8), Color(0xFF74E0DC))
    PaletteStyle.BLOOM_CINNABAR -> WidgetGlassAmbience(Color(0xFFE84F5C), Color(0xFFB86576), Color(0xFF8E8153), Color(0xFFFF7E87))
    PaletteStyle.BLOOM_SAGE -> WidgetGlassAmbience(Color(0xFF42B69A), Color(0xFF4A8EAF), Color(0xFF7272B7), Color(0xFF81E3C5))
    PaletteStyle.BLOOM_SPRING -> WidgetGlassAmbience(Color(0xFF9C6DE0), Color(0xFFD45B9B), Color(0xFF4D96C2), Color(0xFFD3A4FF))
}
